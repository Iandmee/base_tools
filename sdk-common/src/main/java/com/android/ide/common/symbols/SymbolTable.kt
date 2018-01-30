/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.common.symbols

import com.android.annotations.concurrency.Immutable
import com.android.resources.ResourceAccessibility
import com.android.resources.ResourceType
import com.google.common.base.Splitter
import com.google.common.collect.ImmutableTable
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Table
import com.google.common.collect.Tables
import java.io.File
import java.util.Arrays
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import javax.lang.model.SourceVersion

/**
 * List of symbols identifying resources in an Android application. Symbol tables do not only exist
 * for applications: they can be used for other building blocks for applications, such as libraries
 * or atoms.
 *
 * A symbol table keeps a list of instances of [Symbol], each one with a unique pair class
 * / name. Tables have one main attribute: a package name. This should be unique and are used to
 * generate the `R.java` file.
 */
@Immutable
abstract class SymbolTable protected constructor() {

    abstract val tablePackage: String
    abstract val symbols: ImmutableTable<ResourceType, String, Symbol>

    private data class SymbolTableImpl(
            override val tablePackage: String,
            override val symbols: ImmutableTable<ResourceType, String, Symbol>) : SymbolTable()

    /**
     * Produces a subset of this symbol table that has the symbols with resource type / name defined
     * in `filter`. In other words, a symbol `s` will exist in the result if and only
     * if `s` exists in `this` and there is a symbol `s1` in `table`
     * such that `s.resourceType == s1.resourceType && s.name == s1.name`.
     *
     * @param table the filter table
     * @return the filter result; this table will have the same name and package as this one
     */
    fun filter(table: SymbolTable): SymbolTable {
        val builder = ImmutableTable.builder<ResourceType, String, Symbol>()

        for (resourceType in symbols.rowKeySet()) {
            val symbols = symbols.row(resourceType).values
            val filteringSymbolNames = table.symbols.row(resourceType).keys

            for (symbol in symbols) {
                if (filteringSymbolNames.contains(symbol.name)) {
                    builder.put(resourceType, symbol.name, symbol)
                }
            }
        }

        return SymbolTableImpl(tablePackage,
                builder.build())
    }

    /**
     * Short for merging `this` and `m`.
     *
     * @param m the table to add to `this`
     * @return the result of merging `this` with `m`
     */
    fun merge(m: SymbolTable): SymbolTable {
        return merge(Arrays.asList(this, m))
    }

    /**
     * Builds a new symbol table that has the same symbols as this one, but was renamed with
     * the given package.
     *
     * @param tablePackage the table package
     * @return the new renamed symbol table
     */
    fun rename(tablePackage: String): SymbolTable {
        return SymbolTableImpl(tablePackage, symbols)
    }

    /**
     * Collect all the symbols for a particular symbol type to a sorted list of symbols.
     *
     * The symbols are sorted by name to make output predicable and, therefore, testing easier.
     */
    fun getSymbolByResourceType(type: ResourceType): List<Symbol> {
        val symbols = Lists.newArrayList(symbols.row(type).values)
        symbols.sortWith(compareBy { it.name })
        return Collections.unmodifiableList(symbols)
    }

    /**
     * Collect all the symbols for a particular resource accessibility to a sorted list of symbols.
     *
     * The symbols are sorted by name to make the output predicable.
     */
    fun getSymbolByAccessibility(accessibility: ResourceAccessibility): List<Symbol> {
        val symbols =
                Lists.newArrayList(
                        symbols.values().filter { it.resourceAccessibility == accessibility })
        symbols.sortWith(compareBy { it.name })
        return Collections.unmodifiableList(symbols)
    }

    /** Builder that creates a symbol table.  */
    class Builder {

        private var tablePackage = ""

        private val symbols: Table<ResourceType, String, Symbol> =
                Tables.newCustomTable(
                        Maps.newEnumMap<ResourceType, Map<String, Symbol>>(ResourceType::class.java),
                        { HashMap() })

        /**
         * Adds a symbol to the table to be built. The table must not contain a symbol with the same
         * resource type and name.
         *
         * @param symbol the symbol to add
         */
        fun add(symbol: Symbol): Builder {
            if (symbols.contains(symbol.resourceType, symbol.name)) {
                throw IllegalArgumentException(
                        "Duplicate symbol in table with resource type '${symbol.resourceType}' " +
                                "and symbol name '${symbol.name}'")
            }
            symbols.put(symbol.resourceType, symbol.name, symbol)
            return this
        }

        /**
         * Adds all symbols in the given collection to the table. This is semantically equivalent
         * to calling [.add] for all symbols.
         *
         * @param symbols the symbols to add
         */
        fun addAll(symbols: Collection<Symbol>): Builder {
            symbols.forEach { this.add(it) }
            return this
        }

        /**
         * Adds a symbol if it doesn't exist in the table yet. If a symbol already exists, choose
         * the correct resource accessibility.
         *
         * @param table the other table to merge into the current symbol table.
         */
        internal fun addFromPartial(table: SymbolTable): Builder {
            table.symbols.values().forEach {
                if (!this.symbols.contains(it.resourceType, it.name)) {
                    // If this symbol hasn't been encountered yet, simply add it as is.
                    this.symbols.put(it.resourceType, it.name, it)
                } else {
                    val existing = this.symbols.get(it.resourceType, it.name)
                    // If we already encountered it, check the qualifiers.
                    // - if they're the same, leave the existing one (the existing one overrode the
                    //   new one)
                    // - if the existing one is DEFAULT, use the new one (overriding resource was
                    //   defined as PRIVATE or PUBLIC)
                    // - if the new one is DEFAULT, leave the existing one (overridden resource was
                    //   defined as PRIVATE or PUBLIC)
                    // - if neither of them is DEFAULT and they differ, that's an error
                    if (existing.resourceAccessibility != it.resourceAccessibility) {
                        if (existing.resourceAccessibility == ResourceAccessibility.DEFAULT) {
                            this.symbols.remove(existing.resourceType, existing.name)
                            this.symbols.put(it.resourceType, it.name, it)
                        } else if (it.resourceAccessibility != ResourceAccessibility.DEFAULT) {
                            // they differ and neither is DEFAULT
                            throw IllegalResourceAccessibilityException(
                                    "Symbol with resource type ${it.resourceType} and name " +
                                            "${it.name} defined both as private and public.")
                        }
                    }
                }
            }
            return this
        }

        /**
         * Sets the table package. See `SymbolTable` description.
         *
         * @param tablePackage; must be a valid java package name
         */
        fun tablePackage(tablePackage: String): Builder {
            if (!tablePackage.isEmpty() && !SourceVersion.isName(tablePackage)) {
                for (segment in Splitter.on('.').split(tablePackage)) {
                    if (!SourceVersion.isIdentifier(segment)) {
                        throw IllegalArgumentException(
                                "Package '$tablePackage' from AndroidManifest.xml is not a valid " +
                                        "Java package name as '$segment' is not a valid Java " +
                                        "identifier.")
                    }
                    if (SourceVersion.isKeyword(segment)) {
                        throw IllegalArgumentException(
                                "Package '$tablePackage' from AndroidManifest.xml is not a valid " +
                                        "Java package name as '$segment' is a Java keyword.")
                    }
                }
                // Shouldn't happen.
                throw IllegalArgumentException(
                        "Package '$tablePackage' from AndroidManifest.xml is not a valid Java " +
                                "package name.")
            }
            this.tablePackage = tablePackage
            return this
        }

        /**
         * Checks if a symbol with the same resource type and name as `symbol` have been
         * added.
         *
         * @param symbol the symbol to check
         *
         * @return has a symbol with the same resource type / name been added?
         */
        operator fun contains(symbol: Symbol): Boolean {
            return contains(symbol.resourceType, symbol.name)
        }

        /**
         * Checks if the table contains a symbol with the given resource type / name.
         *
         * @param resourceType the resource type
         *
         * @param name the name
         *
         * @return does the table contain a symbol with the given resource type / name?
         */
        fun contains(resourceType: ResourceType, name: String): Boolean {
            return symbols.contains(resourceType, name)
        }

        /**
         * Returns the symbol form the table matching the provided symbol
         *
         * @param symbol the symbol
         */
        operator fun get(symbol: Symbol): Symbol? {
            return symbols.get(symbol.resourceType, symbol.name)
        }

        /**
         * Builds a symbol table with all symbols added.
         *
         * @return the symbol table
         */
        fun build(): SymbolTable {
            return SymbolTableImpl(tablePackage,
                    ImmutableTable.copyOf(symbols))
        }
    }

    companion object {

        /**
         * Merges a list of tables into a single table. The merge is order-sensitive: when multiple
         * symbols with the same class / name exist in multiple tables, the first one will be used.
         *
         * @param tables the tables to merge
         *
         * @return the table with the result of the merge; this table will have the package of
         *  the first table in `tables`, or the default one if there are no tables in `tables`
         */
        @JvmStatic fun merge(tables: List<SymbolTable>): SymbolTable {
            val builder = ImmutableTable.builder<ResourceType, String, Symbol>()

            val present = HashSet<String>()

            for (resourceType in ResourceType.values()) {
                present.clear()
                for (t in tables) {
                    val tableSymbolMap = t.symbols.row(resourceType)
                    if (tableSymbolMap != null && !tableSymbolMap.isEmpty()) {
                        for (s in tableSymbolMap.values) {
                            val name = s.name
                            if (!present.contains(name)) {
                                present.add(name)
                                builder.put(resourceType, name, s)
                            }
                        }
                    }
                }
            }

            val packageName = if (tables.isEmpty()) "" else tables[0].tablePackage

            return SymbolTableImpl(packageName,
                    builder.build())
        }

        /**
         * Merges a list of partial R files. See 'package-info.java' for a detailed description of
         * the merging algorithm.
         *
         * @param tables partial R files in oder of the source-sets relation (base first, overriding
         *  source-set afterwards etc).
         * @param packageName the package name for the merged symbol table.
         */
        @JvmStatic fun mergePartialTables(tables: List<File>, packageName: String?): SymbolTable {
            val builder = SymbolTable.builder()

            // A set to keep the names of the visited layout files.
            val visitedFiles = HashSet<String>()

            try {
                // Reverse the file list, since we have to start from the 'highest' source-set (base
                // source-set will be last).
                tables.reversed().forEach {
                    if (it.name.startsWith("layout")) {
                        // When a layout file is overridden, its' contents get overridden too. That
                        // is why we need to keep the 'highest' version of the file.
                        if (!visitedFiles.contains(it.name)) {
                            // If we haven't encountered a file with this name yet, remember it and
                            // process the partial R file.
                            visitedFiles.add(it.name)
                            builder.addFromPartial(SymbolIo.readFromPartialRFile(it, null))
                        }
                    } else {
                        // Partial R files for values XML files and non-XML files need to be parsed
                        // always. The order matters for declare-styleables and for resource
                        // accessibility.
                        builder.addFromPartial(SymbolIo.readFromPartialRFile(it, null))
                    }
                }
            } catch (e: Exception) {
                throw PartialRMergingException(
                        "An error occurred during merging of the partial R files", e)
            }

            builder.tablePackage(packageName ?: "")

            return builder.build()
        }

        /**
         * Creates a new builder to create a `SymbolTable`.
         *
         * @return a builder
         */
        @JvmStatic fun builder(): Builder {
            return Builder()
        }
    }

    class IllegalResourceAccessibilityException(description: String) : Exception(description)
}
