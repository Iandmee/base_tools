/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.idea.wizard.template.impl.activities.googleWalletActivity.res.layout

fun activityWalletXml(
  activityClass: String,
  activityPackage: String
): String {

  return """
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context="$activityPackage.$activityClass">

    <TextView
        android:id="@+id/title"
        style="@style/TextAppearance.AppCompat.Display1"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="@dimen/margin_large"
        android:layout_marginTop="@dimen/margin_large"
        android:text="@string/sample_pass"
        android:textColor="?android:textColorPrimary"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <ImageView
        android:id="@+id/detailImage"
        android:layout_width="match_parent"
        android:layout_height="350dp"
        android:layout_marginStart="@dimen/margin_large"
        android:layout_marginTop="@dimen/margin_large"
        android:layout_marginEnd="@dimen/margin_large"
        android:src="@drawable/sample_pass"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@+id/title" />

    <!-- Check out the g.co/wallet/button-guidelines to learn more -->
    <include
        android:id="@+id/addToGoogleWalletButton"
        layout="@layout/add_to_google_wallet_button"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:visibility="gone"
        app:layout_constraintBottom_toBottomOf="parent"
        tools:visibility="visible" />

</androidx.constraintlayout.widget.ConstraintLayout>
"""
}
