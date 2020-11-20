"""Aspect to validate that only the specified targets are tagged as flaky.

When bazel is invoked with this aspect attached, it validates that only
the allowlisted targets below can set "flaky" to true.
"""

APPROVED_FLAKY_TESTS = [
    "//tools/base/build-system/integration-test/application:tests__GradlePluginMemoryLeakTest",  # b/153972155
    "//tools/base/build-system/integration-test/connected:ParentLibsConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:NdkJniLibConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:MlModelBindingConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:MinifyConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:MigratedConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:KotlinAppConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:JacocoConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:FlavoredConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:DependenciesConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:DensitySplitConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:D8DesugaringConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:CoreLibraryDesugarConversionConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:ComposeHelloWorldConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:BasicConnectedTest2",  # b/148626301
    "//tools/base/build-system/integration-test/connected:BasicConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:ApiConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:AnnotationProcessorConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:AndroidTestResourcesConnectedTest",  # b/148626301
    "//tools/adt/idea/app-inspection/inspectors/workmanager/view:intellij.android.app-inspection.inspectors.workmanager.view.tests_tests",  # b/169760203
    "//tools/adt/idea/adt-ui:intellij.android.adt.ui_tests",  # b/172521726
]

FAILURE_MESSAGE = """Test target {} has flaky set to true.
Only approved targets can set flaky attribute to true (b/159928949).
If this is intentional, contact android-devtools-infra@ to approve the target."""

IGNORE_TAG = []

def _has_intersect(this, other):
    for item in this:
        if item in other:
            return True
    return False

def _limit_flaky_tests_impl(target, ctx):
    if ctx.rule.kind.endswith("_test"):
        if ctx.rule.attr.flaky and str(ctx.label) not in APPROVED_FLAKY_TESTS:
            if not _has_intersect(IGNORE_TAG, ctx.rule.attr.tags):
                fail(FAILURE_MESSAGE.format(str(ctx.label)))
    return []

limit_flaky_tests = aspect(
    implementation = _limit_flaky_tests_impl,
    attr_aspects = [],
)
