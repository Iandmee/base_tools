<!-- A DrawerLayout is intended to be used as the top-level content view using match_parent for both width and height to consume the full space available. -->
<android.support.v4.widget.DrawerLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/drawer_layout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context="${relativePackage}.${activityClass}">

    <!-- As the main content view, the view below consumes the entire
         space available using match_parent in both dimensions. -->
    <FrameLayout
        android:id="@+id/container"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- The drawer is given a fixed width in dp and extends the full height of
         the container. -->
    <fragment android:id="@+id/navigation_drawer"
        android:layout_width="@dimen/navigation_drawer_width"
        android:layout_height="match_parent"
        android:layout_gravity="<#if buildApi gte 17>start<#else>left</#if>"
        android:name="${packageName}.NavigationDrawerFragment"
        tools:layout="@layout/${navigationDrawerLayout}" />

</android.support.v4.widget.DrawerLayout>
