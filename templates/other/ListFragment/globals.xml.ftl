<?xml version="1.0"?>
<globals>
    <#assign useSupport=(minApiLevel lt 23)>
    <global id="useSupport" type="boolean" value="${useSupport?string}" />
    <global id="SupportPackage" value="${useSupport?string('.support.v4','')}" />
    <global id="resOut" value="${resDir}" />
    <global id="srcOut" value="${srcDir}/${slashedPackageName(packageName)}" />
    <global id="collection_name" value="${extractLetters(objectKind?lower_case)}" />
    <global id="kotlinEscapedPackageName" value="${escapeKotlinIdentifiers(packageName)}" />
    <#include "root://activities/common/kotlin_globals.xml.ftl" />
</globals>
