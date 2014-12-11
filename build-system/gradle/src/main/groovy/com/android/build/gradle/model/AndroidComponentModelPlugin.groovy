/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.model

import com.android.build.gradle.internal.ProductFlavorCombo
import com.android.build.gradle.internal.dsl.BuildTypeFactory
import com.android.build.gradle.internal.dsl.GroupableProductFlavor
import com.android.build.gradle.internal.dsl.GroupableProductFlavorFactory
import com.android.builder.core.BuilderConstants
import com.android.builder.core.DefaultBuildType
import com.android.builder.model.BuildType
import groovy.transform.CompileStatic
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.base.ProjectSourceSet
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.model.Model
import org.gradle.model.Mutate
import org.gradle.model.RuleSource
import org.gradle.model.collection.CollectionBuilder
import org.gradle.model.internal.core.ModelCreators
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.platform.base.BinaryType
import org.gradle.platform.base.BinaryTypeBuilder
import org.gradle.platform.base.ComponentBinaries
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.ComponentType
import org.gradle.platform.base.ComponentTypeBuilder

import javax.inject.Inject

/**
 * Plugin to set up infrastructure for other android plugins.
 */
public class AndroidComponentModelPlugin implements Plugin<Project> {
    /**
     * The name of ComponentSpec created with android component model plugin.
     */
    public static final String COMPONENT_NAME = "android";

    private final ModelRegistry modelRegistry

    @Inject
    public AndroidComponentModelPlugin(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry
    }

    @Override
    public void apply(Project project) {
        project.apply plugin: ComponentModelBasePlugin

        // Remove this when our models no longer depends on Project.
        modelRegistry.create(
                ModelCreators.bridgedInstance(
                        ModelReference.of("projectModel", Project), project)
                                .simpleDescriptor("Model of project.")
                                .build())
    }

    @RuleSource
    static class Rules {

        @Model("androidBuildTypes")
        NamedDomainObjectContainer<DefaultBuildType> createBuildTypes(
                ServiceRegistry serviceRegistry,
                Project project) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class)
            def buildTypeContainer = project.container(DefaultBuildType,
                    new BuildTypeFactory(instantiator, project, project.getLogger()))

            // create default Objects, signingConfig first as its used by the BuildTypes.
            buildTypeContainer.create(BuilderConstants.DEBUG)
            buildTypeContainer.create(BuilderConstants.RELEASE)

            buildTypeContainer.whenObjectRemoved {
                throw new UnsupportedOperationException("Removing build types is not supported.")
            }
            return buildTypeContainer
        }

        @Model("androidProductFlavors")
        NamedDomainObjectContainer<GroupableProductFlavor> createProductFlavors(
                ServiceRegistry serviceRegistry,
                Project project) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class)
            def productFlavorContainer = project.container(GroupableProductFlavor,
                    new GroupableProductFlavorFactory(instantiator, project, project.getLogger()))

            productFlavorContainer.whenObjectRemoved {
                throw new UnsupportedOperationException(
                        "Removing product flavors is not supported.")
            }

            return productFlavorContainer
        }

        @Model
        List<ProductFlavorCombo> createProductFlavorCombo (
                NamedDomainObjectContainer<GroupableProductFlavor> productFlavors) {
            // TODO: Create custom product flavor container to manually configure flavor dimensions.
            List<String> flavorDimensionList = productFlavors*.flavorDimension.unique();
            flavorDimensionList.removeAll([null])

            return  ProductFlavorCombo.createCombinations(flavorDimensionList, productFlavors);
        }

        @ComponentType
        void defineComponentType(ComponentTypeBuilder<AndroidComponentSpec> builder) {
            builder.defaultImplementation(DefaultAndroidComponentSpec)
        }

        @Mutate
        void createAndroidComponents(CollectionBuilder<AndroidComponentSpec> androidComponents) {
            androidComponents.create(COMPONENT_NAME)
        }

        @Model
        AndroidComponentSpec androidComponentSpec(ComponentSpecContainer specs) {
            return (AndroidComponentSpec) specs.getByName(COMPONENT_NAME)
        }

        @Model("androidsources")
        AndroidComponentModelSourceSet createAndroidSourceSet (
                ServiceRegistry serviceRegistry,
                ProjectSourceSet projectSourceSet) {
            def instantiator = serviceRegistry.get(Instantiator.class)
            def fileResolver = serviceRegistry.get(FileResolver.class);
            def sources =
                    new AndroidComponentModelSourceSet(instantiator, projectSourceSet, fileResolver)

            // Create main source set.
            sources.create("main")

            return sources
        }

        @BinaryType
        void defineBinaryType(BinaryTypeBuilder<AndroidBinary> builder) {
            builder.defaultImplementation(DefaultAndroidBinary)
        }

        @ComponentBinaries
        void createBinaries(
                CollectionBuilder<AndroidBinary> binaries,
                NamedDomainObjectContainer<DefaultBuildType> buildTypes,
                List<ProductFlavorCombo> flavorCombos,
                AndroidComponentSpec spec) {
            if (flavorCombos.isEmpty()) {
                flavorCombos.add(new ProductFlavorCombo());
            }

            for (def buildType : buildTypes) {
                for (def flavorCombo : flavorCombos) {
                    binaries.create(getBinaryName(buildType, flavorCombo)) {
                        def binary = it as DefaultAndroidBinary
                        binary.buildType = buildType
                        binary.productFlavors = flavorCombo.flavorList
                    }
                }
            }
        }
    }

    private static String getBinaryName(BuildType buildType, ProductFlavorCombo flavorCombo) {
        if (flavorCombo.flavorList.isEmpty()) {
            return  buildType.name
        } else {
            return  flavorCombo.name + buildType.name.capitalize()
        }
    }
}
