<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <dependency mavenUrl="${resolveDependency("androidx.ui:ui-layout:+", "0.1.0-dev02")}" />
    <dependency mavenUrl="${resolveDependency("androidx.ui:ui-material:+", "0.1.0-dev02")}" />
    <dependency mavenUrl="${resolveDependency("androidx.ui:ui-tooling:+", "0.1.0-dev02")}" />

    <#include "../common/recipe_theme.xml.ftl" />
    <#include "../common/recipe_manifest.xml.ftl" />

    <instantiate from="root/src/app_package/MainActivity.kt.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.kt" />

    <merge from="root://activities/ComposeActivity/build-compose.gradle.ftl"
                 to="${escapeXmlAttribute(projectOut)}/build.gradle" />
    <merge from="root://activities/common/navigation/navigation-kotlin-build.gradle.ftl"
                 to="${escapeXmlAttribute(projectOut)}/build.gradle" />

    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.kt" />
</recipe>
