<recipe folder="root://activities/common">
    <#if isInstantApp>
    <merge from="root/res/values/no_actionbar_styles.xml.ftl"
             to="${escapeXmlAttribute(baseLibResOut)}/values/styles.xml" />
    <#else>
    <merge from="root/res/values/no_actionbar_styles.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/styles.xml" />
    </#if>
</recipe>
