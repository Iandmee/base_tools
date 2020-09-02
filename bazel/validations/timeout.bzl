"""Aspect to validate that only the specified targets use eternal timeout.

When bazel is invoked with this aspect attached, it validates that only
the allowlisted targets below can use the eternal timeout.
"""
APPROVED_ETERNAL_TESTS = [
    "//tools/vendor/google/android-apk:android-apk.tests_tests",
    "//tools/base/build-system/integration-test/native:VulkanTest",
    "//tools/base/build-system/integration-test/native:V2NativeModelTest",
    "//tools/base/build-system/integration-test/native:V1NativeModelTest",
    "//tools/base/build-system/integration-test/native:RsSupportModeTest",
    "//tools/base/build-system/integration-test/native:RsEnabledAnnotationTest",
    "//tools/base/build-system/integration-test/native:RenderscriptNdkTest",
    "//tools/base/build-system/integration-test/native:PrefabTest",
    "//tools/base/build-system/integration-test/native:PrefabPublishingTest",
    "//tools/base/build-system/integration-test/native:Pre21SplitTest",
    "//tools/base/build-system/integration-test/native:NoSplitNdkVariantsTest",
    "//tools/base/build-system/integration-test/native:NdkSanAngelesTest",
    "//tools/base/build-system/integration-test/native:NdkPrebuiltsTest",
    "//tools/base/build-system/integration-test/native:NdkLibPrebuiltsTest",
    "//tools/base/build-system/integration-test/native:NdkJniLibTest",
    "//tools/base/build-system/integration-test/native:NdkBuildTest",
    "//tools/base/build-system/integration-test/native:NdkBuildTargetsTest",
    "//tools/base/build-system/integration-test/native:NdkBuildJniLibTest",
    "//tools/base/build-system/integration-test/native:NdkBuildBuildSettingsTest",
    "//tools/base/build-system/integration-test/native:NdkBuildAndroidMkLibraryTest",
    "//tools/base/build-system/integration-test/native:NativeBuildOutputTest",
    "//tools/base/build-system/integration-test/native:MergeNativeDebugMetadataTaskTest",
    "//tools/base/build-system/integration-test/native:InjectedAbiNativeLibraryTest",
    "//tools/base/build-system/integration-test/native:HeaderInCmakeListsTest",
    "//tools/base/build-system/integration-test/native:ExtractNativeDebugMetadataTaskTest",
    "//tools/base/build-system/integration-test/native:CombinedAbiDensitySplits",
    "//tools/base/build-system/integration-test/native:CmakeTargetsTest",
    "//tools/base/build-system/integration-test/native:CmakeSysrootTest",
    "//tools/base/build-system/integration-test/native:CmakeStlMatrixTest",
    "//tools/base/build-system/integration-test/native:CmakeSettingsTest",
    "//tools/base/build-system/integration-test/native:CmakeSettingsSharedBuildTest",
    "//tools/base/build-system/integration-test/native:CmakeMultiModuleTest",
    "//tools/base/build-system/integration-test/native:CmakeJniLibTest",
    "//tools/base/build-system/integration-test/native:CmakeInjectedAbiSplitTest",
    "//tools/base/build-system/integration-test/native:CmakeGradleTargetsTest",
    "//tools/base/build-system/integration-test/native:CmakeExtensionsTest",
    "//tools/base/build-system/integration-test/native:CmakeBasicProjectTest",
    "//tools/base/build-system/integration-test/native:CMakeBuildSettingsTest",
    "//tools/base/build-system/integration-test/application/src/test/java/com/android/build/gradle/integration/testing/unit:tests",
    "//tools/base/build-system/integration-test/application/src/test/java/com/android/build/gradle/integration/sdk:sdk_tests",
    "//tools/base/build-system/integration-test/application/src/test/java/com/android/build/gradle/integration/gradlecompat:GradleVersionCheckTest",
    "//tools/base/build-system/integration-test/application:library.MultiprojectTest",
    "//tools/base/build-system/integration-test/application:library.DslTest",
    "//tools/base/build-system/integration-test/application:coupled_tests",
    "//tools/base/build-system/integration-test/application:WindowsSystemDependencyTest",
    "//tools/base/build-system/integration-test/application:WearWithCustomApplicationIdTest",
    "//tools/base/build-system/integration-test/application:WearVariantTest",
    "//tools/base/build-system/integration-test/application:WearSimpleUnbundledTest",
    "//tools/base/build-system/integration-test/application:WearSimpleTest",
    "//tools/base/build-system/integration-test/application:VectorDrawableTest_Library",
    "//tools/base/build-system/integration-test/application:VectorDrawableTest",
    "//tools/base/build-system/integration-test/application:VariantsApiTest",
    "//tools/base/build-system/integration-test/application:VariantFilteringTest",
    "//tools/base/build-system/integration-test/application:VariantDependencyTest",
    "//tools/base/build-system/integration-test/application:VariantApiPropertiesTest",
    "//tools/base/build-system/integration-test/application:VariantApiMisuseTest",
    "//tools/base/build-system/integration-test/application:VariantApiLibraryPropertiesTest",
    "//tools/base/build-system/integration-test/application:VariantApiCustomizationTest",
    "//tools/base/build-system/integration-test/application:VariantApiCompatTest",
    "//tools/base/build-system/integration-test/application:VariantApiArtifactAccessTest",
    "//tools/base/build-system/integration-test/application:ValidateTaskPropertiesTest",
    "//tools/base/build-system/integration-test/application:UseEmbeddedDexPackagingTest",
    "//tools/base/build-system/integration-test/application:TransformVariantApiTest",
    "//tools/base/build-system/integration-test/application:TransformInModuleWithKotlinTest",
    "//tools/base/build-system/integration-test/application:TransformApiTest",
    "//tools/base/build-system/integration-test/application:TictactoeTest",
    "//tools/base/build-system/integration-test/application:TextureTargetedAssetPackTest",
    "//tools/base/build-system/integration-test/application:TestingSupportLibraryTest",
    "//tools/base/build-system/integration-test/application:TestWithSameDepAsAppWithProguard",
    "//tools/base/build-system/integration-test/application:TestWithSameDepAsAppWithClassifier",
    "//tools/base/build-system/integration-test/application:TestWithSameDepAsApp",
    "//tools/base/build-system/integration-test/application:TestWithRemoteAndroidLibDepTest",
    "//tools/base/build-system/integration-test/application:TestWithMismatchDep",
    "//tools/base/build-system/integration-test/application:TestWithJavaLibDepTest",
    "//tools/base/build-system/integration-test/application:TestWithFlavorsWithCompileDirectJarTest",
    "//tools/base/build-system/integration-test/application:TestWithDepTest",
    "//tools/base/build-system/integration-test/application:TestWithCompileLibTest",
    "//tools/base/build-system/integration-test/application:TestWithCompileDirectJarTest",
    "//tools/base/build-system/integration-test/application:TestWithAndroidLibDepTest",
    "//tools/base/build-system/integration-test/application:TestOptionsExecutionTest",
    "//tools/base/build-system/integration-test/application:TestLibraryWithDep",
    "//tools/base/build-system/integration-test/application:SwitchMultidexTest",
    "//tools/base/build-system/integration-test/application:StripDebugSymbolsTaskTest",
    "//tools/base/build-system/integration-test/application:StableResourceIDsTest",
    "//tools/base/build-system/integration-test/application:SplitHandlingTest",
    "//tools/base/build-system/integration-test/application:SpecialCharactersBasicTest",
    "//tools/base/build-system/integration-test/application:SourceSetsTaskTest",
    "//tools/base/build-system/integration-test/application:SourceSetFilteringTest",
    "//tools/base/build-system/integration-test/application:SimpleCompositeBuildTest",
    "//tools/base/build-system/integration-test/application:ShrinkResourcesOldShrinkerTest",
    "//tools/base/build-system/integration-test/application:ShrinkResourcesNewShrinkerTest",
    "//tools/base/build-system/integration-test/application:SeparateTestWithoutMinificationWithDependenciesTest",
    "//tools/base/build-system/integration-test/application:SeparateTestWithMinificationButNoObfuscationTest",
    "//tools/base/build-system/integration-test/application:SeparateTestWithDependenciesTest",
    "//tools/base/build-system/integration-test/application:SeparateTestWithAarDependencyTest",
    "//tools/base/build-system/integration-test/application:SeparateTestModuleWithVariantsTest",
    "//tools/base/build-system/integration-test/application:SeparateTestModuleWithMinifiedAppTest",
    "//tools/base/build-system/integration-test/application:SeparateTestModuleWithAppDependenciesTest",
    "//tools/base/build-system/integration-test/application:SeparateTestModuleTest",
    "//tools/base/build-system/integration-test/application:SameNamedLibsTest",
    "//tools/base/build-system/integration-test/application:RewriteLocalLibraryResourceNamespaceTest",
    "//tools/base/build-system/integration-test/application:ResourcesOverridingTest",
    "//tools/base/build-system/integration-test/application:ResourceValidationTest",
    "//tools/base/build-system/integration-test/application:ResourceNamespaceOverrideTest",
    "//tools/base/build-system/integration-test/application:ResourceNamespaceLibrariesTest",
    "//tools/base/build-system/integration-test/application:ResValueTypeTest",
    "//tools/base/build-system/integration-test/application:ResValueTest",
    "//tools/base/build-system/integration-test/application:ResPackagingTest",
    "//tools/base/build-system/integration-test/application:RepoTest",
    "//tools/base/build-system/integration-test/application:RenderscriptTest",
    "//tools/base/build-system/integration-test/application:RenderScriptLinkerTest",
    "//tools/base/build-system/integration-test/application:RenamedApkTest",
    "//tools/base/build-system/integration-test/application:RegisterExternalAptJavaOutputTest",
    "//tools/base/build-system/integration-test/application:RClassPackageTest",
    "//tools/base/build-system/integration-test/application:R8TaskTest",
    "//tools/base/build-system/integration-test/application:PublishLegacyMultidexLibTest",
    "//tools/base/build-system/integration-test/application:PseudoLocalizationTest",
    "//tools/base/build-system/integration-test/application:ProjectNoJavaSourcesTest",
    "//tools/base/build-system/integration-test/application:ProguardDesugaringTest",
    "//tools/base/build-system/integration-test/application:ProguardAarPackagingTest",
    "//tools/base/build-system/integration-test/application:ProfileContentTest",
    "//tools/base/build-system/integration-test/application:ProfileCapturerTest",
    "//tools/base/build-system/integration-test/application:ProcessTestManifestTest",
    "//tools/base/build-system/integration-test/application:PrivateResourcesTest",
    "//tools/base/build-system/integration-test/application:PrivateResourceTest",
    "//tools/base/build-system/integration-test/application:PrecompileRemoteResourcesTest",
    "//tools/base/build-system/integration-test/application:PostprocessingTest",
    "//tools/base/build-system/integration-test/application:PluginVersionCheckTest",
    "//tools/base/build-system/integration-test/application:PlatformLoaderTest",
    "//tools/base/build-system/integration-test/application:PlaceholderInLibsTest",
    "//tools/base/build-system/integration-test/application:PartialRTest",
    "//tools/base/build-system/integration-test/application:ParseLibraryResourcesPartialRTest",
    "//tools/base/build-system/integration-test/application:ParentLibsTest",
    "//tools/base/build-system/integration-test/application:ParameterizedModelTest",
    "//tools/base/build-system/integration-test/application:PackagingOptionsTest",
    "//tools/base/build-system/integration-test/application:PackagingOptionsFilteringTest",
    "//tools/base/build-system/integration-test/application:OverlayableResourcesTest",
    "//tools/base/build-system/integration-test/application:Overlay3Test",
    "//tools/base/build-system/integration-test/application:Overlay2Test",
    "//tools/base/build-system/integration-test/application:Overlay1Test",
    "//tools/base/build-system/integration-test/application:OutputRenamingTest",
    "//tools/base/build-system/integration-test/application:OptionalLibraryWithProguardTest",
    "//tools/base/build-system/integration-test/application:OptionalLibraryTest",
    "//tools/base/build-system/integration-test/application:OptionalAarTest",
    "//tools/base/build-system/integration-test/application:OlderStudioModel",
    "//tools/base/build-system/integration-test/application:ObsoleteApiTest",
    "//tools/base/build-system/integration-test/application:NonTransitiveCompileRClassFlowTest",
    "//tools/base/build-system/integration-test/application:NonTransitiveAppRClassesTest",
    "//tools/base/build-system/integration-test/application:NonNamespacedCompileRClassTest",
    "//tools/base/build-system/integration-test/application:NonNamespacedApplicationLightRClassesTest",
    "//tools/base/build-system/integration-test/application:NoOpIncrementalBuildTaskStatesTest",
    "//tools/base/build-system/integration-test/application:NoOpIncrementalBuildMinifyTest",
    "//tools/base/build-system/integration-test/application:NoMappingTest",
    "//tools/base/build-system/integration-test/application:NoManifestTest",
    "//tools/base/build-system/integration-test/application:NoCruncherTest",
    "//tools/base/build-system/integration-test/application:NoCompressTest",
    "//tools/base/build-system/integration-test/application:NativeSoPackagingTest",
    "//tools/base/build-system/integration-test/application:NativeSoPackagingOptionsTest",
    "//tools/base/build-system/integration-test/application:NativeSoPackagingFromRemoteAarTest",
    "//tools/base/build-system/integration-test/application:NativeSoPackagingFromJarTest",
    "//tools/base/build-system/integration-test/application:NativeSoPackagingDirectSubprojectAarTest",
    "//tools/base/build-system/integration-test/application:NamespacedDynamicFeatureIntegrationTest",
    "//tools/base/build-system/integration-test/application:NamespacedApplicationLightRClassesTest",
    "//tools/base/build-system/integration-test/application:NamespacedAarTest",
    "//tools/base/build-system/integration-test/application:MultiresTest",
    "//tools/base/build-system/integration-test/application:MultiProjectTest",
    "//tools/base/build-system/integration-test/application:MultiDexWithLibTest",
    "//tools/base/build-system/integration-test/application:MultiDexCacheTest",
    "//tools/base/build-system/integration-test/application:MultiCompositeBuildTest",
    "//tools/base/build-system/integration-test/application:ModelWithDataBindingTest",
    "//tools/base/build-system/integration-test/application:ModelTest",
    "//tools/base/build-system/integration-test/application:ModelInstantAppCompatibleTest",
    "//tools/base/build-system/integration-test/application:MlModelBindingInLibTest",
    "//tools/base/build-system/integration-test/application:MlGeneratedClassTest",
    "//tools/base/build-system/integration-test/application:MistypedSourceSetTest",
    "//tools/base/build-system/integration-test/application:MissingSdkTest",
    "//tools/base/build-system/integration-test/application:MissingDimensionStrategyTest",
    "//tools/base/build-system/integration-test/application:MissingCompileSdkVersionTest",
    "//tools/base/build-system/integration-test/application:MisplacedMissingDimensionStrategyTest",
    "//tools/base/build-system/integration-test/application:MinimalKeepRulesTest",
    "//tools/base/build-system/integration-test/application:MinifyTest",
    "//tools/base/build-system/integration-test/application:MinifyLibTest",
    "//tools/base/build-system/integration-test/application:MinifyLibProvidedDepTest",
    "//tools/base/build-system/integration-test/application:MinifyLibAndAppWithJavaResTest",
    "//tools/base/build-system/integration-test/application:MinifyLibAndAppKeepRules",
    "//tools/base/build-system/integration-test/application:MinifyFeaturesTest",
    "//tools/base/build-system/integration-test/application:MinifyCacheabilityTest",
    "//tools/base/build-system/integration-test/application:MigratedTest",
    "//tools/base/build-system/integration-test/application:MessageRewriteWithJvmResCompilerTest",
    "//tools/base/build-system/integration-test/application:MessageRewriteTest",
    "//tools/base/build-system/integration-test/application:MessageRewrite2Test",
    "//tools/base/build-system/integration-test/application:MergeResourcesTest",
    "//tools/base/build-system/integration-test/application:MergeJavaResourceTaskTest",
    "//tools/base/build-system/integration-test/application:MergeGeneratedProguardFilesTest",
    "//tools/base/build-system/integration-test/application:MergeFileTaskTest",
    "//tools/base/build-system/integration-test/application:MaxSdkVersionTest",
    "//tools/base/build-system/integration-test/application:MatchingFallbackTest",
    "//tools/base/build-system/integration-test/application:MappingFileAccessTest",
    "//tools/base/build-system/integration-test/application:ManifestMergingTest",
    "//tools/base/build-system/integration-test/application:LocalJarsTest",
    "//tools/base/build-system/integration-test/application:LocalJarInAarInModelTest",
    "//tools/base/build-system/integration-test/application:LocalAarTest",
    "//tools/base/build-system/integration-test/application:LibsTestTest",
    "//tools/base/build-system/integration-test/application:LibraryProfileContentTest",
    "//tools/base/build-system/integration-test/application:LibraryIntermediateArtifactPublishingTest",
    "//tools/base/build-system/integration-test/application:LibraryInstrumentationTestSigningTest",
    "//tools/base/build-system/integration-test/application:LibraryCacheabilityTest",
    "//tools/base/build-system/integration-test/application:LibraryBuildConfigTest",
    "//tools/base/build-system/integration-test/application:LibraryApiJarPublishTest",
    "//tools/base/build-system/integration-test/application:LibraryAarJarsTest",
    "//tools/base/build-system/integration-test/application:LibWithResourcesTest",
    "//tools/base/build-system/integration-test/application:LibWithProvidedLocalJarTest",
    "//tools/base/build-system/integration-test/application:LibWithProvidedDirectJarTest",
    "//tools/base/build-system/integration-test/application:LibWithProvidedAarAsJarTest",
    "//tools/base/build-system/integration-test/application:LibWithPackageLocalJarTest",
    "//tools/base/build-system/integration-test/application:LibWithNavigationTest",
    "//tools/base/build-system/integration-test/application:LibWithLocalDepsTest",
    "//tools/base/build-system/integration-test/application:LibWithCompileLocalJarTest",
    "//tools/base/build-system/integration-test/application:LibTestDepTest",
    "//tools/base/build-system/integration-test/application:LibProguardConsumerFilesTest",
    "//tools/base/build-system/integration-test/application:LibMinifyTest",
    "//tools/base/build-system/integration-test/application:LibMinifyLibDepTest",
    "//tools/base/build-system/integration-test/application:LibMinifyJarDepTest",
    "//tools/base/build-system/integration-test/application:LibDependencySourceChange",
    "//tools/base/build-system/integration-test/application:L8DexDesugarTest",
    "//tools/base/build-system/integration-test/application:KotlinWithEclipseSourceSetTest",
    "//tools/base/build-system/integration-test/application:KotlinTestCompilationTest",
    "//tools/base/build-system/integration-test/application:KotlinAppTest",
    "//tools/base/build-system/integration-test/application:KaptTest",
    "//tools/base/build-system/integration-test/application:JetifierTest",
    "//tools/base/build-system/integration-test/application:JavaResPackagingTest",
    "//tools/base/build-system/integration-test/application:JavaResMergePackagingTest",
    "//tools/base/build-system/integration-test/application:JarJarTest",
    "//tools/base/build-system/integration-test/application:JarJarLibTest",
    "//tools/base/build-system/integration-test/application:JacocoWithKotlinTest",
    "//tools/base/build-system/integration-test/application:JacocoWithButterKnifeTest",
    "//tools/base/build-system/integration-test/application:JacocoTest",
    "//tools/base/build-system/integration-test/application:JacocoOnlySubprojectBuildScriptDependency",
    "//tools/base/build-system/integration-test/application:JacocoDependenciesTest",
    "//tools/base/build-system/integration-test/application:InvalidResourceDirectoryTest",
    "//tools/base/build-system/integration-test/application:InvalidNdkTest",
    "//tools/base/build-system/integration-test/application:InjectedPreviewSdkTest",
    "//tools/base/build-system/integration-test/application:InjectedDensityTest",
    "//tools/base/build-system/integration-test/application:InjectedAbiTest",
    "//tools/base/build-system/integration-test/application:InjectedAbiAndDensitySplitTest",
    "//tools/base/build-system/integration-test/application:InitWithThisTest",
    "//tools/base/build-system/integration-test/application:IncrementalJavaCompileWithAPsTest",
    "//tools/base/build-system/integration-test/application:IncrementalDexingWithDesugaringTest",
    "//tools/base/build-system/integration-test/application:IncrementalDexMergingTest",
    "//tools/base/build-system/integration-test/application:IncrementalCodeChangeTest",
    "//tools/base/build-system/integration-test/application:GuavaSpecialHandlingForTestTest",
    "//tools/base/build-system/integration-test/application:GradleTestProjectTest",
    "//tools/base/build-system/integration-test/application:GradlePropertiesTest",
    "//tools/base/build-system/integration-test/application:GradlePluginMemoryLeakTest",
    "//tools/base/build-system/integration-test/application:GenerateSourcesOnlyTest",
    "//tools/base/build-system/integration-test/application:GenerateManifestJarTaskTest",
    "//tools/base/build-system/integration-test/application:GenerateAnnotationsClassPathTest",
    "//tools/base/build-system/integration-test/application:GenFolderKotlinOnlyApiTest",
    "//tools/base/build-system/integration-test/application:GenFolderApiTest",
    "//tools/base/build-system/integration-test/application:GenFolderApi2Test",
    "//tools/base/build-system/integration-test/application:FullSplitsHandlingTest",
    "//tools/base/build-system/integration-test/application:FlavorsTest",
    "//tools/base/build-system/integration-test/application:FlavorlibTest",
    "//tools/base/build-system/integration-test/application:FlavoredlibTest",
    "//tools/base/build-system/integration-test/application:FlatJavaLibTest",
    "//tools/base/build-system/integration-test/application:FeatureOnFeatureDependencyTest",
    "//tools/base/build-system/integration-test/application:ExtractNativeLibsPackagingTest",
    "//tools/base/build-system/integration-test/application:ExtractAnnotationTest",
    "//tools/base/build-system/integration-test/application:ExternalTestProjectTest",
    "//tools/base/build-system/integration-test/application:EmptyExtractAnnotationTest",
    "//tools/base/build-system/integration-test/application:EarlyTaskConfigurationTest",
    "//tools/base/build-system/integration-test/application:DynamicFeaturesCacheabilityTest",
    "//tools/base/build-system/integration-test/application:DynamicFeatureUnitTestSanityTest",
    "//tools/base/build-system/integration-test/application:DynamicFeatureJavaResTest",
    "//tools/base/build-system/integration-test/application:DynamicFeatureDependsOnJavaLibTest",
    "//tools/base/build-system/integration-test/application:DynamicFeatureAndroidTestBuildTest",
    "//tools/base/build-system/integration-test/application:DynamicAppPackageDependenciesTest",
    "//tools/base/build-system/integration-test/application:DynamicAppMultidexTest",
    "//tools/base/build-system/integration-test/application:DynamicAppLintTest",
    "//tools/base/build-system/integration-test/application:DxFeaturesTest",
    "//tools/base/build-system/integration-test/application:DuplicateModuleNameImportTest",
    "//tools/base/build-system/integration-test/application:DuplicateClassesTest",
    "//tools/base/build-system/integration-test/application:DslTest",
    "//tools/base/build-system/integration-test/application:DisabledSrcResGenTest",
    "//tools/base/build-system/integration-test/application:DisableLibraryResourcesTest",
    "//tools/base/build-system/integration-test/application:DifferentProjectClassLoadersTest",
    "//tools/base/build-system/integration-test/application:DexingArtifactTransformWithTransformApiTest",
    "//tools/base/build-system/integration-test/application:DexingArtifactTransformTest",
    "//tools/base/build-system/integration-test/application:DexingArtifactTransformMultiModuleTest",
    "//tools/base/build-system/integration-test/application:DexLimitTest",
    "//tools/base/build-system/integration-test/application:DexArchivesTest",
    "//tools/base/build-system/integration-test/application:DexArchivesKotlinTest",
    "//tools/base/build-system/integration-test/application:DeterministicTaskOutputsTest",
    "//tools/base/build-system/integration-test/application:DeterministicApkTest",
    "//tools/base/build-system/integration-test/application:DeprecatedConfigurationTest",
    "//tools/base/build-system/integration-test/application:DeploymentApiOverrideTest",
    "//tools/base/build-system/integration-test/application:DependencyReportDslTest",
    "//tools/base/build-system/integration-test/application:DependencyOrderTest",
    "//tools/base/build-system/integration-test/application:DependencyCheckerTest",
    "//tools/base/build-system/integration-test/application:DependenciesReportTest",
    "//tools/base/build-system/integration-test/application:DependenciesFilePublicOutputTest",
    "//tools/base/build-system/integration-test/application:DepOnLocalJarThroughAModuleTest",
    "//tools/base/build-system/integration-test/application:DensitySplitWithPublishNonDefaultTest",
    "//tools/base/build-system/integration-test/application:DensitySplitTest",
    "//tools/base/build-system/integration-test/application:D8DexingTest",
    "//tools/base/build-system/integration-test/application:D8DesugaringTest",
    "//tools/base/build-system/integration-test/application:D8DesugarMethodsTest",
    "//tools/base/build-system/integration-test/application:CustomArtifactDepTest",
    "//tools/base/build-system/integration-test/application:CrashlyticsTest",
    "//tools/base/build-system/integration-test/application:CoreLibraryDesugarGeneralizationTest",
    "//tools/base/build-system/integration-test/application:CoreLibraryDesugarDynamicFeatureTest",
    "//tools/base/build-system/integration-test/application:CoreLibraryDesugarCachingTest",
    "//tools/base/build-system/integration-test/application:ConditionalKeepRulesTest",
    "//tools/base/build-system/integration-test/application:CompoundSyncModelBuilderTest",
    "//tools/base/build-system/integration-test/application:CompositeBuildTest",
    "//tools/base/build-system/integration-test/application:ComposeHelloWorldTest",
    "//tools/base/build-system/integration-test/application:ComposeFlagsTest",
    "//tools/base/build-system/integration-test/application:ComposeCompilerFlagsTest",
    "//tools/base/build-system/integration-test/application:CompileSdkAndLanguageLevelTest",
    "//tools/base/build-system/integration-test/application:CompileRClassFlowTest",
    "//tools/base/build-system/integration-test/application:CompileLibraryResourcesTest",
    "//tools/base/build-system/integration-test/application:CompileAndRuntimeClasspathTest",
    "//tools/base/build-system/integration-test/application:CleanBuildTaskStatesTest",
    "//tools/base/build-system/integration-test/application:CleanBuildCacheTest",
    "//tools/base/build-system/integration-test/application:CheckMultiApkLibrariesTaskTest",
    "//tools/base/build-system/integration-test/application:CheckAarMetadataTaskTest",
    "//tools/base/build-system/integration-test/application:CacheabilityTest",
    "//tools/base/build-system/integration-test/application:BytecodeGenerationHooksTest",
    "//tools/base/build-system/integration-test/application:ButterKnifeTest",
    "//tools/base/build-system/integration-test/application:BundleOptionsTest",
    "//tools/base/build-system/integration-test/application:BundleLibraryJavaResTest",
    "//tools/base/build-system/integration-test/application:BuiltArtifactsWithWorkerTest",
    "//tools/base/build-system/integration-test/application:BuilderTestingApiTest",
    "//tools/base/build-system/integration-test/application:BuildToolsTest",
    "//tools/base/build-system/integration-test/application:BuildFeaturesTest",
    "//tools/base/build-system/integration-test/application:BuildDirTest",
    "//tools/base/build-system/integration-test/application:BuildDirRelocationTest",
    "//tools/base/build-system/integration-test/application:BuildConfigTest",
    "//tools/base/build-system/integration-test/application:BrokenTestModuleSyncTest",
    "//tools/base/build-system/integration-test/application:BootClasspathTest",
    "//tools/base/build-system/integration-test/application:BasicTest2",
    "//tools/base/build-system/integration-test/application:BasicTest",
    "//tools/base/build-system/integration-test/application:BasicMultiFlavorTest",
    "//tools/base/build-system/integration-test/application:BasicModelV2Test",
    "//tools/base/build-system/integration-test/application:BasicKotlinDslTest",
    "//tools/base/build-system/integration-test/application:BasicInstantExecutionTest",
    "//tools/base/build-system/integration-test/application:AutoServiceTest",
    "//tools/base/build-system/integration-test/application:AutoNamespaceTest",
    "//tools/base/build-system/integration-test/application:AutoEnableMultidexTest",
    "//tools/base/build-system/integration-test/application:AttrOrderTest",
    "//tools/base/build-system/integration-test/application:AssetPackagingTest",
    "//tools/base/build-system/integration-test/application:AssetPackTest",
    "//tools/base/build-system/integration-test/application:ArtifactReplacementTest",
    "//tools/base/build-system/integration-test/application:ArtifactApiTest",
    "//tools/base/build-system/integration-test/application:ArchivesBaseNameTest",
    "//tools/base/build-system/integration-test/application:ApplicationIdTest",
    "//tools/base/build-system/integration-test/application:ApplicationIdReset",
    "//tools/base/build-system/integration-test/application:ApplicationIdInLibsTest",
    "//tools/base/build-system/integration-test/application:ApplibtestTest",
    "//tools/base/build-system/integration-test/application:AppWithRuntimeDependencyTest",
    "//tools/base/build-system/integration-test/application:AppWithResolutionStrategyForJarTest",
    "//tools/base/build-system/integration-test/application:AppWithResolutionStrategyForAarTest",
    "//tools/base/build-system/integration-test/application:AppWithProvidedRemoteJarTest",
    "//tools/base/build-system/integration-test/application:AppWithProvidedProjectJarTest",
    "//tools/base/build-system/integration-test/application:AppWithProvidedLocalJarTest",
    "//tools/base/build-system/integration-test/application:AppWithProvidedLocalAarTest",
    "//tools/base/build-system/integration-test/application:AppWithProvidedLibTest",
    "//tools/base/build-system/integration-test/application:AppWithProvidedAarAsJarTest",
    "//tools/base/build-system/integration-test/application:AppWithPackageLocalJarTest",
    "//tools/base/build-system/integration-test/application:AppWithPackageDirectJarTest",
    "//tools/base/build-system/integration-test/application:AppWithNonExistentResolutionStrategyForAarTest",
    "//tools/base/build-system/integration-test/application:AppWithJarDependOnLibTest",
    "//tools/base/build-system/integration-test/application:AppWithIvyDependencyTest",
    "//tools/base/build-system/integration-test/application:AppWithCompileLocalAarTest",
    "//tools/base/build-system/integration-test/application:AppWithCompileLibTest",
    "//tools/base/build-system/integration-test/application:AppWithCompileIndirectJavaProjectTest",
    "//tools/base/build-system/integration-test/application:AppWithCompileIndirectJarTest",
    "//tools/base/build-system/integration-test/application:AppWithCompileDirectJarTest",
    "//tools/base/build-system/integration-test/application:AppWithClassifierDepTest",
    "//tools/base/build-system/integration-test/application:AppWithAndroidTestDependencyOnLibTest",
    "//tools/base/build-system/integration-test/application:AppTestWithSkippedModuleDepTest",
    "//tools/base/build-system/integration-test/application:AppPublishingTest",
    "//tools/base/build-system/integration-test/application:AppModelTest",
    "//tools/base/build-system/integration-test/application:AppIntegrityConfigTest",
    "//tools/base/build-system/integration-test/application:AppAndLibNoBuildConfigTest",
    "//tools/base/build-system/integration-test/application:ApkOutputFileChangeTest",
    "//tools/base/build-system/integration-test/application:ApkLocationTest",
    "//tools/base/build-system/integration-test/application:ApkCreatedByTest",
    "//tools/base/build-system/integration-test/application:ApiTest",
    "//tools/base/build-system/integration-test/application:AnnotationProcessorTest",
    "//tools/base/build-system/integration-test/application:AnnotationProcessorCompileClasspathTest",
    "//tools/base/build-system/integration-test/application:AndroidXPropertySyncIssueTest",
    "//tools/base/build-system/integration-test/application:AndroidTestUtilTest",
    "//tools/base/build-system/integration-test/application:AndroidTestResourcesTest",
    "//tools/base/build-system/integration-test/application:AndroidTestClasspathTest",
    "//tools/base/build-system/integration-test/application:AndroidManifestInTestTest",
    "//tools/base/build-system/integration-test/application:AnalyticsConfigurationCachingTest",
    "//tools/base/build-system/integration-test/application:AidlTest",
    "//tools/base/build-system/integration-test/application:AgpVersionCheckerTest",
    "//tools/base/build-system/integration-test/application:AccentCharacterAndProguardTest",
    "//tools/base/build-system/integration-test/application:AbiRelatedDslUsageTest",
    "//tools/base/build-system/integration-test/application:AarWithLocalJarsTest",
    "//tools/base/build-system/integration-test/application:AarMetadataTaskTest",
    "//tools/base/build-system/integration-test/application:AarApiJarTest",
    "//tools/base/build-system/integration-test/application:AaptOptionsTest",
    "//tools/base/build-system/integration-test/api:tests",
    "//tools/adt/idea/sync-perf-tests:intellij.android.sync-perf-tests_tests__all",
    "//tools/adt/idea/studio:searchable_options_test",
    "//tools/adt/idea/old-agp-tests:intellij.android.old-agp-tests_tests",
    "//tools/adt/idea/ide-perf-tests:intellij.android.ide-perf-tests_tests__all",
    "//tools/adt/idea/designer-perf-tests:intellij.android.designer-perf-tests_tests",
    "//tools/adt/idea/android-uitests:X86AbiSplitApksTest",
    "//tools/adt/idea/android-uitests:WatchpointTest",
    "//tools/adt/idea/android-uitests:SmartStepIntoTest",
    "//tools/adt/idea/android-uitests:SessionRestartTest",
    "//tools/adt/idea/android-uitests:RunInstrumentationTest",
    "//tools/adt/idea/android-uitests:NativeDebuggerInNdkProjectTest",
    "//tools/adt/idea/android-uitests:NativeDebuggerBreakpointsTest",
    "//tools/adt/idea/android-uitests:JavaDebuggerTest",
    "//tools/adt/idea/android-uitests:InstrumentationTest",
    "//tools/adt/idea/android-uitests:InstantAppRunFromCmdLineTest",
    "//tools/adt/idea/android-uitests:ImportAndRunInstantAppTest",
    "//tools/adt/idea/android-uitests:FlavorsExecutionTest",
    "//tools/adt/idea/android-uitests:EspressoRecorderTest",
    "//tools/adt/idea/android-uitests:DualDebuggerInNdkProjectTest",
    "//tools/adt/idea/android-uitests:DualDebuggerBreakpointsTest",
    "//tools/adt/idea/android-uitests:DebugOnEmulatorTest",
    "//tools/adt/idea/android-uitests:CreateAndRunInstantAppTest",
    "//tools/adt/idea/android-uitests:CreateAPKProjectTest",
    "//tools/adt/idea/android-uitests:BuildAndRunCMakeProjectTest",
    "//tools/adt/idea/android-uitests:AutoDebuggerInNdkProjectTest",
    "//tools/adt/idea/android-uitests:AbiSplitApksTest",
    "//tools/adt/idea/android-templates:intellij.android.templates.tests_tests",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__testartifacts",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__other",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__navigator",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__jetbrains.android.refactoring.MigrateToAndroidxGradleTest",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__jetbrains",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__gradle.structure.model.android",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__gradle.structure",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__gradle.project.sync.snapshots",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__gradle.project.sync",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__gradle",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__AndroidLintCustomCheckTest",
]

FAILURE_MESSAGE = """Test target {} has timeout set to eternal.
We do not want any new target with eternal timeout (b/162943254).
If this is intentional, contact android-devtools-infra@ to relax the restriction on the target."""

IGNORE_TAG = ["perfgate"]

def _has_intersect(this, other):
    for item in this:
        if item in other:
            return True
    return False

def _no_eternal_tests_impl(target, ctx):
    if ctx.rule.kind.endswith("_test"):
        if ctx.rule.attr.timeout == "eternal" and str(ctx.label) not in APPROVED_ETERNAL_TESTS:
            if not _has_intersect(IGNORE_TAG, ctx.rule.attr.tags):
                fail(FAILURE_MESSAGE.format(str(ctx.label)))
    return []

no_eternal_tests = aspect(
    implementation = _no_eternal_tests_impl,
    attr_aspects = [],
)
