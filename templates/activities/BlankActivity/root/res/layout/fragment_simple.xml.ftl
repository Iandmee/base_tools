<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:paddingLeft="@dimen/activity_horizontal_margin"
    android:paddingRight="@dimen/activity_horizontal_margin"
    android:paddingTop="@dimen/activity_vertical_margin"
    android:paddingBottom="@dimen/activity_vertical_margin"
    tools:context=".${activityClass}$PlaceholderFragment">

    <TextView
        <#if hasSections?has_content>android:id="@+id/section_label"<#else>android:text="@string/hello_world"</#if>
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />

</RelativeLayout>
