<?xml version="1.0" encoding="utf-8"?>
<!--
     Copyright (C) 2014 The Android Open Source Project

     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.
-->

<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent" >

    <VideoView android:id="@+id/videoView"
        android:layout_width="fill_parent"
        android:layout_alignParentRight="true"
        android:layout_alignParentLeft="true"
        android:layout_alignParentTop="true"
        android:layout_alignParentBottom="true"
        android:layout_height="fill_parent"
        android:layout_gravity="center"
        android:layout_centerInParent="true">
    </VideoView>

    <fragment
        android:id="@+id/playback_controls_fragment"
        android:name="${packageName}.PlaybackOverlayFragment"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

</FrameLayout>
