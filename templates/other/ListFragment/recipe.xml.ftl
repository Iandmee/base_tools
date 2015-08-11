<?xml version="1.0"?>
<recipe>

    <#if useSupport><dependency mavenUrl="com.android.support:support-v4:${targetApi}.+"/></#if>
<#if switchGrid == true>
    <merge from="root/res/values/refs.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/refs.xml" />
    <merge from="root/res/values/refs_lrg.xml.ftl"
           to="${escapeXmlAttribute(resOut)}/values-large/refs.xml" />
    <merge from="root/res/values/refs_lrg.xml.ftl"
           to="${escapeXmlAttribute(resOut)}/values-sw600dp/refs.xml" />

    <instantiate from="root/res/layout/fragment_grid.xml"
                 to="${escapeXmlAttribute(resOut)}/layout/${fragment_layout_grid}.xml" />

    <instantiate from="root/res/layout/fragment_list.xml"
                 to="${escapeXmlAttribute(resOut)}/layout/${fragment_layout_list}.xml" />
</#if>

    <instantiate from="root/src/app_package/ListFragment.java.ftl"
                 to="${escapeXmlAttribute(srcOut)}/${className}.java" />

    <instantiate from="root/src/app_package/dummy/DummyContent.java.ftl"
                 to="${escapeXmlAttribute(srcOut)}/dummy/DummyContent.java" />

    <open file="${escapeXmlAttribute(srcOut)}/${className}.java" />

</recipe>
