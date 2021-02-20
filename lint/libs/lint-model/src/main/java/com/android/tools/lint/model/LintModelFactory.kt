/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.lint.model

import com.android.AndroidProjectTypes
import com.android.builder.model.AndroidProject
import com.android.builder.model.LintOptions
import com.android.ide.common.gradle.model.IdeAaptOptions
import com.android.ide.common.gradle.model.IdeAndroidArtifact
import com.android.ide.common.gradle.model.IdeAndroidLibrary
import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.ide.common.gradle.model.IdeApiVersion
import com.android.ide.common.gradle.model.IdeBaseArtifact
import com.android.ide.common.gradle.model.IdeBuildType
import com.android.ide.common.gradle.model.IdeClassField
import com.android.ide.common.gradle.model.IdeJavaArtifact
import com.android.ide.common.gradle.model.IdeJavaLibrary
import com.android.ide.common.gradle.model.IdeLintOptions
import com.android.ide.common.gradle.model.IdeSourceProvider
import com.android.ide.common.gradle.model.IdeSourceProviderContainer
import com.android.ide.common.gradle.model.IdeVariant
import com.android.ide.common.gradle.model.IdeLibrary
import com.android.ide.common.gradle.model.IdeModuleLibrary
import com.android.ide.common.gradle.model.impl.ModelCache
import com.android.ide.common.repository.GradleVersion
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import java.io.File

/**
 * Converter from the builder model library to lint's own model.
 */
class LintModelFactory : LintModelModuleLoader {
    init {
        // We're just copying by value so make sure our constants match
        assert(LintModelMavenName.LOCAL_AARS == ModelCache.LOCAL_AARS)
    }

    private val libraryResolverMap = mutableMapOf<String, LintModelLibrary>()
    private val libraryResolver = DefaultLintModelLibraryResolver(libraryResolverMap)

    /**
     * Kotlin source folders to merge in for the main source set. This should <b>not</b>
     * be necessary but is here temporarily since it's missing from the builder model.
     * This should be removed ASAP.
     */
    var kotlinSourceFolderLookup: ((variantName: String) -> List<File>)? = null

    /**
     * Factory from an XML folder to a [LintModelModule].
     * The files were previously saved by [LintModelSerialization.writeModule].
     */
    fun create(source: File): LintModelModule = LintModelSerialization.readModule(source)

    /**
     * Converter from the builder model library to lint's own model.
     * If [deep] is true, it will create a deep copy; otherwise, it will
     * create wrapper objects. The advantage of a shallow copy is that
     * some expensive fields are only computed lazily (such as all the
     * variant data, which may not be needed in the IDE when running
     * on the fly analysis). The advantage of a deep copy is that (at
     * least during testing) all fields are accessed so we can make sure
     * there are no inconvertible data, and when all the data is going
     * to be used anyway there's no benefit in the additional overhead
     * of lazy lookup.
     */
    fun create(project: IdeAndroidProject, variants: Collection<IdeVariant>, dir: File, deep: Boolean = true): LintModelModule {
        val gradleVersion = getGradleVersion(project)

        return if (deep) {
            val variantList = mutableListOf<LintModelVariant>()
            val module = DefaultLintModelModule(
                loader = this,
                dir = dir,
                modulePath = project.name,
                type = getModuleType(project.projectType),
                mavenName = getMavenName(project),
                gradleVersion = gradleVersion,
                buildFolder = project.buildFolder,
                lintOptions = getLintOptions(project),
                lintRuleJars = project.getLintRuleJarsForAnyAgpVersion(),
                resourcePrefix = project.resourcePrefix,
                dynamicFeatures = project.dynamicFeatures,
                bootClassPath = project.bootClasspath.map { File(it) },
                javaSourceLevel = project.javaCompileOptions.sourceCompatibility,
                compileTarget = project.compileTarget,
                neverShrinking = isNeverShrinking(project),
                variants = variantList
            )

            for (variant in variants) {
                variantList.add(getVariant(module, project, variant))
            }

            module
        } else {
            LazyLintModelModule(
                loader = this,
                project = project,
                projectVariants = variants,
                dir = dir,
                gradleVersion = gradleVersion
            )
        }
    }

    /**
     * Returns the list of Lint Rule file, no matter what the AGP version is.
     */
    private fun IdeAndroidProject.getLintRuleJarsForAnyAgpVersion() = lintRuleJars ?: listOf(
        FileUtils.join(buildFolder, "intermediates", "lint", "lint.jar"),
        FileUtils.join(buildFolder, "intermediates", "lint_jar", "lint.jar"),
        FileUtils.join(
            buildFolder,
            "intermediates",
            "lint_jar",
            "global",
            "prepareLintJar",
            "lint.jar"
        )
    )

    private fun getLibrary(library: IdeAndroidLibrary): LintModelLibrary {
        // TODO: Construct file objects lazily!
        return DefaultLintModelAndroidLibrary(
            artifactAddress = library.getMavenArtifactAddress(),
            manifest = File(library.manifest),
            // TODO - expose compile jar vs impl jar?
            jarFiles = (library.localJars + library.jarFile).map { File(it) },
            folder = library.folder!!, // Needed for workaround for b/66166521
            resFolder = File(library.resFolder),
            assetsFolder = File(library.assetsFolder),
            lintJar = File(library.lintJar!!),
            publicResources = File(library.publicResources),
            symbolFile = File(library.symbolFile),
            externalAnnotations = File(library.externalAnnotations),
            provided = library.isProvided,
            resolvedCoordinates = library.getMavenName(),
            proguardRules = File(library.proguardRules)
        )
    }

    private fun getLibrary(library: IdeJavaLibrary): LintModelLibrary {
        return DefaultLintModelJavaLibrary(
            artifactAddress = library.getMavenArtifactAddress(),
            // TODO - expose compile jar vs impl jar?
            jarFiles = listOf(library.artifact),
            provided = library.isProvided,
            resolvedCoordinates = library.getMavenName()
        )
    }

    private fun getLibrary(library: IdeModuleLibrary): LintModelLibrary {
        val projectPath = library.projectPath
        return DefaultLintModelModuleLibrary(
            artifactAddress = library.getMavenArtifactAddress(),
            projectPath = projectPath,
            lintJar = library.lintJar?.let(::File),
            provided = library.isProvided
        )
    }

    private fun IdeAndroidLibrary.getArtifactName(): String =
        getMavenName().let { mavenName -> "${mavenName.groupId}:${mavenName.artifactId}" }

    private fun IdeJavaLibrary.getArtifactName(): String =
        getMavenName().let { mavenName -> "${mavenName.groupId}:${mavenName.artifactId}" }

    private fun IdeModuleLibrary.getArtifactName(): String = "artifacts:$projectPath"

    private fun IdeLibrary.getMavenName(): LintModelMavenName = getMavenName(artifactAddress)

    private fun IdeAndroidLibrary.getMavenArtifactAddress(): String = artifactAddress.substringBefore("@")

    private fun IdeJavaLibrary.getMavenArtifactAddress(): String = artifactAddress.substringBefore("@")

    private fun IdeModuleLibrary.getMavenArtifactAddress(): String = "artifacts:$projectPath:unspecified" // TODO(b/158346611): Review artifact names for modules.

    private fun getGraphItem(
        artifactName: String,
        artifactAddress: String,
        lintModelLibrary: () -> LintModelLibrary
    ): LintModelDependency {
        @Suppress("UNUSED_VARIABLE")
        val lintLibrary = libraryResolverMap[artifactAddress]
            ?: lintModelLibrary().also { libraryResolverMap[artifactAddress] = it }

        return DefaultLintModelDependency(
            artifactName = artifactName,
            artifactAddress = artifactAddress,
            requestedCoordinates = null, // Always null in builder models and not present in Ide* models.
            // Deep copy
            dependencies = emptyList(), // Dependency hierarchy is not yet supported.
            libraryResolver = libraryResolver
        )
    }

    private fun getDependencies(
        artifact: IdeBaseArtifact
    ): LintModelDependencies {
        val compileItems = mutableListOf<LintModelDependency>()
        val packagedItems = mutableListOf<LintModelDependency>()
        val dependencies = artifact.level2Dependencies

        for (dependency in dependencies.androidLibraries) {
            if (dependency.isValid()) {
                val lintModelDependency = getGraphItem(dependency.getArtifactName(), dependency.getMavenArtifactAddress()) { getLibrary(dependency) }
                compileItems.add(lintModelDependency)
                if (!dependency.isProvided) {
                    packagedItems.add(lintModelDependency)
                }
            }
        }
        for (dependency in dependencies.javaLibraries) {
            if (dependency.isValid()) {
                val lintModelDependency = getGraphItem(dependency.getArtifactName(), dependency.getMavenArtifactAddress()) { getLibrary(dependency) }
                compileItems.add(lintModelDependency)
                if (!dependency.isProvided) {
                    packagedItems.add(lintModelDependency)
                }
            }
        }

        for (dependency in dependencies.moduleDependencies) {
            if (dependency.isValid()) {
                val lintModelDependency = getGraphItem(dependency.getArtifactName(), dependency.getMavenArtifactAddress()) { getLibrary(dependency) }
                compileItems.add(lintModelDependency)
                if (!dependency.isProvided) {
                    packagedItems.add(lintModelDependency)
                }
            }
        }

        val compileDependencies = DefaultLintModelDependencyGraph(compileItems, libraryResolver)
        val packageDependencies = DefaultLintModelDependencyGraph(packagedItems, libraryResolver)

        return DefaultLintModelDependencies(
            compileDependencies = compileDependencies,
            packageDependencies = packageDependencies,
            libraryResolver = libraryResolver
        )
    }

    private fun IdeLibrary.isValid(): Boolean {
        return artifactAddress.isNotEmpty()
    }

    private fun getArtifact(
        artifact: IdeAndroidArtifact
    ): LintModelAndroidArtifact {
        return DefaultLintModelAndroidArtifact(
            applicationId = artifact.applicationId,
            dependencies = getDependencies(artifact),
            generatedSourceFolders = artifact.generatedSourceFolders,
            generatedResourceFolders = artifact.generatedResourceFolders,
            classOutputs = artifact.getClassFolders()
        )
    }

    private fun getArtifact(
        artifact: IdeJavaArtifact
    ): LintModelJavaArtifact {
        return DefaultLintModelJavaArtifact(
            dependencies = getDependencies(artifact),
            classFolders = artifact.getClassFolders()
        )
    }

    private fun IdeBaseArtifact.getClassFolders(): List<File> {
        return if (additionalClassesFolders.isEmpty()) {
            listOf(classesFolder)
        } else {
            val folders = ArrayList<File>(additionalClassesFolders.size + 1)
            folders.add(classesFolder)
            folders.addAll(additionalClassesFolders)
            folders
        }
    }

    private fun getBuildType(project: IdeAndroidProject, variant: IdeVariant): IdeBuildType {
        val buildTypeName = variant.buildType
        return project.buildTypes.first { it.buildType.name == buildTypeName }.buildType
    }

    private fun getVariant(
        module: LintModelModule,
        project: IdeAndroidProject,
        variant: IdeVariant
    ): LintModelVariant {
        val buildType = getBuildType(project, variant)
        return DefaultLintModelVariant(
            module = module,
            name = variant.name,
            useSupportLibraryVectorDrawables = useSupportLibraryVectorDrawables(variant),
            mainArtifact = getArtifact(variant.mainArtifact),
            testArtifact = getTestArtifact(variant),
            androidTestArtifact = getAndroidTestArtifact(variant),
            mergedManifest = null, // Injected elsewhere by the legacy Android Gradle Plugin lint runner
            manifestMergeReport = null, // Injected elsewhere by the legacy Android Gradle Plugin lint runner
            `package` = null, // not in the old builder model
            minSdkVersion = variant.minSdkVersion?.toAndroidVersion(),
            targetSdkVersion = variant.targetSdkVersion?.toAndroidVersion(),
            resValues = variant.resValues.mapValues { it.value.toResourceField() },
            manifestPlaceholders = variant.manifestPlaceholders,
            resourceConfigurations = variant.resourceConfigurations,
            proguardFiles = variant.proguardFiles,
            consumerProguardFiles = variant.consumerProguardFiles,
            sourceProviders = computeSourceProviders(project, variant),
            testSourceProviders = computeTestSourceProviders(project, variant),
            debuggable = buildType.isDebuggable,
            shrinkable = buildType.isMinifyEnabled,
            buildFeatures = getBuildFeatures(project, module.gradleVersion),
            libraryResolver = libraryResolver,
            partialResultsDir = null
        )
    }

    private fun getAndroidTestArtifact(variant: IdeVariant): LintModelAndroidArtifact? {
        val artifact = variant.androidTestArtifact ?: return null
        return getArtifact(artifact)
    }

    private fun getTestArtifact(variant: IdeVariant): LintModelJavaArtifact? {
        val artifact = variant.unitTestArtifact ?: return null
        return getArtifact(artifact)
    }

    private fun computeSourceProviders(
        project: IdeAndroidProject,
        variant: IdeVariant
    ): List<LintModelSourceProvider> {
        val providers = mutableListOf<LintModelSourceProvider>()

        // Instead of just
        //  providers.add(getSourceProvider(project.defaultConfig.sourceProvider))
        // we need to merge in any Kotlin source folders for now
        var mainProvider: LintModelSourceProvider? = null
        val kotlinSourceFolders = kotlinSourceFolderLookup?.invoke(variant.name)
            ?: emptyList()
        if (kotlinSourceFolders.isNotEmpty()) {
            val provider = project.defaultConfig.sourceProvider
            if (!provider.javaDirectories.containsAll(kotlinSourceFolders)) {
                val extra = kotlinSourceFolders.toMutableList()
                extra.removeAll(provider.javaDirectories)
                if (extra.isNotEmpty()) {
                    mainProvider = DefaultLintModelSourceProvider(
                        manifestFile = provider.manifestFile,
                        javaDirectories = provider.javaDirectories + extra,
                        resDirectories = provider.resDirectories,
                        assetsDirectories = provider.assetsDirectories,
                        unitTestOnly = false,
                        instrumentationTestOnly = false,
                        debugOnly = false
                    )
                }
            }
        }
        if (mainProvider == null) {
            mainProvider = getSourceProvider(project.defaultConfig.sourceProvider)
        }
        providers.add(mainProvider)

        for (flavorContainer in project.productFlavors) {
            if (variant.productFlavors.contains(flavorContainer.productFlavor.name)) {
                providers.add(getSourceProvider(flavorContainer.sourceProvider))
            }
        }

        val mainArtifact = variant.mainArtifact
        mainArtifact.multiFlavorSourceProvider?.let { sourceProvider ->
            providers.add(getSourceProvider(sourceProvider))
        }

        var debugVariant = false
        for (buildTypeContainer in project.buildTypes) {
            if (variant.buildType == buildTypeContainer.buildType.name) {
                debugVariant = buildTypeContainer.buildType.isDebuggable
                providers.add(
                    getSourceProvider(
                        provider = buildTypeContainer.sourceProvider,
                        debugOnly = debugVariant
                    )
                )
            }
        }

        mainArtifact.variantSourceProvider?.let { sourceProvider ->
            providers.add(
                getSourceProvider(
                    provider = sourceProvider,
                    debugOnly = debugVariant
                )
            )
        }
        return providers
    }

    private fun IdeSourceProviderContainer.isTest(): Boolean {
        return isUnitTest() || isInstrumentationTest()
    }

    private fun IdeSourceProviderContainer.isUnitTest(): Boolean {
        return AndroidProject.ARTIFACT_UNIT_TEST == artifactName
    }

    private fun IdeSourceProviderContainer.isInstrumentationTest(): Boolean {
        return AndroidProject.ARTIFACT_ANDROID_TEST == artifactName
    }

    /**
     * TODO: This is not correct; this method simultaneously returns both the
     * unit test and instrumentation test folders. These two are not normally
     * combined in the build system (they can contain conflicting definitions of
     * the class for example). Lint uses this method in a couple of different
     * ways: (1) to find all the source files it must analyze in turn (for that
     * purpose, this method is okay), and (2) to set up the class path in the
     * CLI setup for PSI. This is problematic, but solving it properly is going
     * to take more work (e.g. we need to do separate handling for each test
     * target), and since this is the way lint has always worked we're leaving
     * this brokenness here for now until we address this with the dependency
     * graph rewrite.
     */
    private fun computeTestSourceProviders(
        project: IdeAndroidProject,
        variant: IdeVariant
    ): List<LintModelSourceProvider> {
        val providers = mutableListOf<LintModelSourceProvider>()

        for (extra in project.defaultConfig.extraSourceProviders) {
            if (extra.isTest()) {
                providers.add(getSourceProvider(extra))
            }
        }

        for (flavorContainer in project.productFlavors) {
            if (variant.productFlavors.contains(flavorContainer.productFlavor.name)) {
                for (extra in flavorContainer.extraSourceProviders) {
                    if (extra.isTest()) {
                        providers.add(getSourceProvider(extra))
                    }
                }
            }
        }

        for (buildTypeContainer in project.buildTypes) {
            if (variant.buildType == buildTypeContainer.buildType.name) {
                for (extra in buildTypeContainer.extraSourceProviders) {
                    if (extra.isTest()) {
                        providers.add(
                            getSourceProvider(
                                providerContainer = extra,
                                debugOnly = buildTypeContainer.buildType.isDebuggable
                            )
                        )
                    }
                }
            }
        }

        return providers
    }

    private fun getSourceProvider(
        providerContainer: IdeSourceProviderContainer,
        debugOnly: Boolean = false
    ): LintModelSourceProvider {
        val provider = providerContainer.sourceProvider
        return DefaultLintModelSourceProvider(
            manifestFile = provider.manifestFile,
            javaDirectories = provider.javaDirectories,
            resDirectories = provider.resDirectories,
            assetsDirectories = provider.assetsDirectories,
            unitTestOnly = providerContainer.isUnitTest(),
            instrumentationTestOnly = providerContainer.isInstrumentationTest(),
            debugOnly = debugOnly
        )
    }

    private fun getSourceProvider(
        provider: IdeSourceProvider,
        unitTestOnly: Boolean = false,
        instrumentationTestOnly: Boolean = false,
        debugOnly: Boolean = false
    ): LintModelSourceProvider {
        return DefaultLintModelSourceProvider(
            manifestFile = provider.manifestFile,
            javaDirectories = provider.javaDirectories,
            resDirectories = provider.resDirectories,
            assetsDirectories = provider.assetsDirectories,
            unitTestOnly = unitTestOnly,
            instrumentationTestOnly = instrumentationTestOnly,
            debugOnly = debugOnly
        )
    }

    private fun IdeClassField.toResourceField(): LintModelResourceField {
        return DefaultLintModelResourceField(
            type = type,
            name = name,
            value = value
        )
    }

    private fun getBuildFeatures(
        project: IdeAndroidProject,
        gradleVersion: GradleVersion?
    ): LintModelBuildFeatures {
        return DefaultLintModelBuildFeatures(
            viewBinding = usesViewBinding(project, gradleVersion),
            coreLibraryDesugaringEnabled = project.javaCompileOptions.isCoreLibraryDesugaringEnabled,
            namespacingMode = getNamespacingMode(project)

        )
    }

    private fun usesViewBinding(
        project: IdeAndroidProject,
        gradleVersion: GradleVersion?
    ): Boolean {
        return if (gradleVersion != null && gradleVersion.isAtLeast(3, 6, 0)) {
            project.viewBindingOptions?.enabled == true
        } else {
            false
        }
    }

    private fun isNeverShrinking(project: IdeAndroidProject): Boolean {
        return project.buildTypes.none { it.buildType.isMinifyEnabled }
    }

    private fun useSupportLibraryVectorDrawables(variant: IdeVariant): Boolean {
        return variant.vectorDrawablesUseSupportLibrary
    }

    private fun getGradleVersion(project: IdeAndroidProject): GradleVersion? {
        return GradleVersion.tryParse(project.modelVersion)
    }

    private fun getNamespacingMode(project: IdeAndroidProject): LintModelNamespacingMode {
        return when (project.aaptOptions.namespacing) {
            IdeAaptOptions.Namespacing.DISABLED -> LintModelNamespacingMode.DISABLED
            IdeAaptOptions.Namespacing.REQUIRED -> LintModelNamespacingMode.REQUIRED
        }
    }

    private fun getMavenName(androidProject: IdeAndroidProject): LintModelMavenName? {
        val groupId = androidProject.groupId ?: return null
        return DefaultLintModelMavenName(groupId, androidProject.name, "")
    }

    private fun getMavenName(artifactAddress: String): LintModelMavenName {
        fun Int.nextDelimiterIndex(vararg delimiters: Char): Int {
            return delimiters.asSequence()
                .map {
                    val index = artifactAddress.indexOf(it, startIndex = this + 1)
                    if (index == -1) artifactAddress.length else index
                }.min() ?: artifactAddress.length
        }

        val lastDelimiterIndex = 0
            .nextDelimiterIndex(':')
            .nextDelimiterIndex(':')
            .nextDelimiterIndex(':', '@')

        // Currently [LintModelMavenName] supports group:name:version format only.
        return LintModelMavenName.parse(artifactAddress.substring(0, lastDelimiterIndex))
            ?: error("Cannot parse '$artifactAddress'")
    }

    private fun getLintOptions(project: IdeAndroidProject): LintModelLintOptions =
        getLintOptions(project.lintOptions)

    private fun getLintOptions(options: IdeLintOptions): LintModelLintOptions {
        val severityOverrides = options.severityOverrides?.let { source ->
            val map = LinkedHashMap<String, LintModelSeverity>()
            for ((id, severityInt) in source.entries) {
                map[id] = getSeverity(severityInt)
            }
            map
        }

        return DefaultLintModelLintOptions(
            // Not all DSL LintOptions; only some are actually accessed from outside
            // the Gradle/CLI configuration currently
            baselineFile = options.baselineFile,
            lintConfig = options.lintConfig,
            severityOverrides = severityOverrides,
            checkTestSources = options.isCheckTestSources,
            checkDependencies = options.isCheckDependencies,
            disable = options.disable,
            enable = options.enable,
            check = options.check,
            abortOnError = options.isAbortOnError,
            absolutePaths = options.isAbsolutePaths,
            noLines = options.isNoLines,
            quiet = options.isQuiet,
            checkAllWarnings = options.isCheckAllWarnings,
            ignoreWarnings = options.isIgnoreWarnings,
            warningsAsErrors = options.isWarningsAsErrors,
            ignoreTestSources = options.isIgnoreTestSources,
            checkGeneratedSources = options.isCheckGeneratedSources,
            explainIssues = options.isExplainIssues,
            showAll = options.isShowAll,
            textReport = options.textReport,
            textOutput = options.textOutput,
            htmlReport = options.htmlReport,
            htmlOutput = options.htmlOutput,
            xmlReport = options.xmlReport,
            xmlOutput = options.xmlOutput,
            sarifReport = options.sarifReport,
            sarifOutput = options.sarifOutput,
            checkReleaseBuilds = options.isCheckReleaseBuilds
        )
    }

    private fun IdeApiVersion.toAndroidVersion(): AndroidVersion {
        return AndroidVersion(apiLevel, codename)
    }

    /**
     * An [LintModelModule] which holds on to the underlying builder-model and lazily constructs
     * parts of the model less likely to be needed (such as all the variants). This is particularly
     * useful when lint is running on a subset of checks on the fly in the editor in the IDE
     * for example.
     */
    inner class LazyLintModelModule(
        override val loader: LintModelModuleLoader,
        private val project: IdeAndroidProject,
        private val projectVariants: Collection<IdeVariant>,
        override val dir: File,
        override val gradleVersion: GradleVersion?
    ) : LintModelModule {
        override val modulePath: String
            get() = project.name
        override val type: LintModelModuleType
            get() = getModuleType(project.projectType)
        override val mavenName: LintModelMavenName?
            get() = getMavenName(project)
        override val buildFolder: File
            get() = project.buildFolder
        override val resourcePrefix: String?
            get() = project.resourcePrefix
        override val dynamicFeatures: Collection<String>
            get() = project.dynamicFeatures
        override val bootClassPath: List<File>
            get() = project.bootClasspath.map { File(it) }
        override val javaSourceLevel: String
            get() = project.javaCompileOptions.sourceCompatibility
        override val compileTarget: String
            get() = project.compileTarget
        override val lintRuleJars: List<File> = project.getLintRuleJarsForAnyAgpVersion()

        override fun neverShrinking(): Boolean {
            return isNeverShrinking(project)
        }

        // Lazy properties

        private var _lintOptions: LintModelLintOptions? = null
        override val lintOptions: LintModelLintOptions
            get() = _lintOptions ?: getLintOptions(project).also { _lintOptions = it }

        private var _variants: List<LintModelVariant>? = null
        override val variants: List<LintModelVariant>
            // Lazily initialize the _variants property, reusing any already
            // looked up variants from the [variantMap] and also populating that map
            // for latest retrieval
            get() = _variants
                ?: projectVariants.map { variant ->
                    // (Not just using findVariant since that searches linearly
                    // through variant list to match by name)
                    variantMap[variant.name]
                        ?: LazyLintModelVariant(this, project, variant, libraryResolver).also {
                            variantMap[it.name] = it
                        }
                }.also {
                    _variants = it
                }

        /** Map from variant name to variant */
        private val variantMap = mutableMapOf<String, LintModelVariant>()

        override fun findVariant(name: String): LintModelVariant? = variantMap[name] ?: run {
            val buildVariant = projectVariants.firstOrNull { it.name == name }
            buildVariant?.let {
                LazyLintModelVariant(this, project, it, libraryResolver)
            }?.also {
                variantMap[name] = it
            }
        }

        override fun defaultVariant(): LintModelVariant? {
            return projectVariants.firstOrNull()?.let { findVariant(it.name) }
        }
    }

    inner class LazyLintModelVariant(
        override val module: LintModelModule,
        private val project: IdeAndroidProject,
        private val variant: IdeVariant,
        override val libraryResolver: LintModelLibraryResolver
    ) : LintModelVariant {
        private val buildType = getBuildType(project, variant)

        override val name: String
            get() = variant.name
        override val useSupportLibraryVectorDrawables: Boolean
            get() = useSupportLibraryVectorDrawables(variant)
        override val mergedManifest: File? get() = null // Injected by legacy AGP lint runner
        override val manifestMergeReport: File? get() = null // Injected by legacy AGP lint runner
        override val `package`: String?
            get() = null // no in the old builder model
        override val minSdkVersion: AndroidVersion?
            get() = variant.minSdkVersion?.toAndroidVersion()
        override val targetSdkVersion: AndroidVersion?
            get() = variant.targetSdkVersion?.toAndroidVersion()
        override val resourceConfigurations: Collection<String>
            get() = variant.resourceConfigurations
        override val debuggable: Boolean
            get() = buildType.isDebuggable
        override val shrinkable: Boolean
            get() = buildType.isMinifyEnabled

        // Lazy properties

        private var _sourceProviders: List<LintModelSourceProvider>? = null
        override val sourceProviders: List<LintModelSourceProvider>
            get() = _sourceProviders ?: computeSourceProviders(
                project,
                variant
            ).also { _sourceProviders = it }

        private var _testSourceProviders: List<LintModelSourceProvider>? = null
        override val testSourceProviders: List<LintModelSourceProvider>
            get() = _testSourceProviders ?: computeTestSourceProviders(
                project,
                variant
            ).also { _testSourceProviders = it }

        private var _resValues: Map<String, LintModelResourceField>? = null
        override val resValues: Map<String, LintModelResourceField>
            get() = _resValues
                ?: variant.resValues.mapValues { it.value.toResourceField() }.also { _resValues = it }

        private var _manifestPlaceholders: Map<String, String>? = null
        override val manifestPlaceholders: Map<String, String>
            get() = _manifestPlaceholders
                ?: variant.manifestPlaceholders.also { _manifestPlaceholders = it }

        private var _mainArtifact: LintModelAndroidArtifact? = null
        override val mainArtifact: LintModelAndroidArtifact
            get() = _mainArtifact
                ?: getArtifact(variant.mainArtifact).also { _mainArtifact = it }

        private var _testArtifact: LintModelJavaArtifact? = null
        override val testArtifact: LintModelJavaArtifact?
            get() = _testArtifact ?: getTestArtifact(variant).also { _testArtifact = it }

        private var _androidTestArtifact: LintModelAndroidArtifact? = null
        override val androidTestArtifact: LintModelAndroidArtifact?
            get() = _androidTestArtifact
                ?: getAndroidTestArtifact(variant).also { _androidTestArtifact = it }

        private var _proguardFiles: Collection<File>? = null
        override val proguardFiles: Collection<File>
            get() = _proguardFiles
                ?: variant.proguardFiles.also { _proguardFiles = it }

        private var _consumerProguardFiles: Collection<File>? = null
        override val consumerProguardFiles: Collection<File>
            get() = _consumerProguardFiles
                ?: variant.consumerProguardFiles.also { _consumerProguardFiles = it }

        private var _buildFeatures: LintModelBuildFeatures? = null
        override val buildFeatures: LintModelBuildFeatures
            get() = _buildFeatures
                ?: getBuildFeatures(project, module.gradleVersion).also { _buildFeatures = it }

        override val partialResultsDir: File?
            get() = null
    }

    companion object {
        /**
         * Returns the [LintModelModuleType] for the given type ID. Type ids must be one of the values defined by
         * AndroidProjectTypes.PROJECT_TYPE_*.
         */
        @JvmStatic
        fun getModuleType(typeId: Int): LintModelModuleType {
            return when (typeId) {
                AndroidProjectTypes.PROJECT_TYPE_APP -> LintModelModuleType.APP
                AndroidProjectTypes.PROJECT_TYPE_LIBRARY -> LintModelModuleType.LIBRARY
                AndroidProjectTypes.PROJECT_TYPE_TEST -> LintModelModuleType.TEST
                AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP -> LintModelModuleType.INSTANT_APP
                AndroidProjectTypes.PROJECT_TYPE_FEATURE -> LintModelModuleType.FEATURE
                AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE -> LintModelModuleType.DYNAMIC_FEATURE
                // 999: Special value defined in GradleModelMocker#PROJECT_TYPE_JAVA_LIBRARY
                999 -> LintModelModuleType.JAVA_LIBRARY
                998 -> LintModelModuleType.JAVA_LIBRARY
                else -> throw IllegalArgumentException("The value $typeId is not a valid project type ID")
            }
        }

        private fun getSeverity(severity: Int): LintModelSeverity =
            when (severity) {
                LintOptions.SEVERITY_FATAL -> LintModelSeverity.FATAL
                LintOptions.SEVERITY_ERROR -> LintModelSeverity.ERROR
                LintOptions.SEVERITY_WARNING -> LintModelSeverity.WARNING
                LintOptions.SEVERITY_INFORMATIONAL -> LintModelSeverity.INFORMATIONAL
                LintOptions.SEVERITY_IGNORE -> LintModelSeverity.IGNORE
                LintOptions.SEVERITY_DEFAULT_ENABLED -> LintModelSeverity.WARNING
                else -> LintModelSeverity.IGNORE
            }

        @Suppress("unused") // Used from the lint-gradle module in AGP
        @JvmStatic
        fun getLintOptions(options: LintOptions): DefaultLintModelLintOptions {
            val severityOverrides = options.severityOverrides?.let { source ->
                val map = LinkedHashMap<String, LintModelSeverity>()
                for ((id, severityInt) in source.entries) {
                    map[id] = getSeverity(severityInt)
                }
                map
            }

            return DefaultLintModelLintOptions(
                // Not all DSL LintOptions; only some are actually accessed from outside
                // the Gradle/CLI configuration currently
                baselineFile = options.baselineFile,
                lintConfig = options.lintConfig,
                severityOverrides = severityOverrides,
                checkTestSources = options.isCheckTestSources,
                checkDependencies = options.isCheckDependencies,
                disable = options.disable,
                enable = options.enable,
                check = options.check,
                abortOnError = options.isAbortOnError,
                absolutePaths = options.isAbsolutePaths,
                noLines = options.isNoLines,
                quiet = options.isQuiet,
                checkAllWarnings = options.isCheckAllWarnings,
                ignoreWarnings = options.isIgnoreWarnings,
                warningsAsErrors = options.isWarningsAsErrors,
                ignoreTestSources = options.isIgnoreTestSources,
                checkGeneratedSources = options.isCheckGeneratedSources,
                explainIssues = options.isExplainIssues,
                showAll = options.isShowAll,
                textReport = options.textReport,
                textOutput = options.textOutput,
                htmlReport = options.htmlReport,
                htmlOutput = options.htmlOutput,
                xmlReport = options.xmlReport,
                xmlOutput = options.xmlOutput,
                sarifReport = options.sarifReport,
                sarifOutput = options.sarifOutput,
                checkReleaseBuilds = options.isCheckReleaseBuilds
            )
        }
    }
}
// TODO(b/120870752): Remove.
