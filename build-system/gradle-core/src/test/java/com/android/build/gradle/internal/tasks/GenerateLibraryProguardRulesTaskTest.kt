package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.builder.files.SerializableChange
import com.android.ide.common.resources.FileStatus
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GenerateLibraryProguardRulesTaskTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /**
     * Test to verify only added layout resources (no changed or removed) can trigger incremental
     * task run.
     */
    @Test
    fun testCanBeProcessedIncrementally_layoutFiles() {
        val layoutsFolder = temporaryFolder.newFolder("layout")
        val layoutFile = File(layoutsFolder, "test_activity.xml")

        val newLayoutFile = SerializableChange(
          layoutFile, FileStatus.NEW, layoutFile.absolutePath)
        val changedLayoutFile = SerializableChange(
          layoutFile, FileStatus.CHANGED, layoutFile.absolutePath)
        val removedLayoutFile = SerializableChange(
          layoutFile, FileStatus.REMOVED, layoutFile.absolutePath)
        assertThat(canResourcesBeProcessedIncrementally(newLayoutFile)).isTrue()
        assertThat(canResourcesBeProcessedIncrementally(changedLayoutFile)).isFalse()
        assertThat(canResourcesBeProcessedIncrementally(removedLayoutFile)).isFalse()
    }

    /**
     * Test to verify that adding, changing or removing non-layout resources allows for incremental
     * task run.
     */
    @Test
    fun testCanBeProcessedIncrementally_nonLayoutFiles() {
        val valuesFolder = temporaryFolder.newFolder("values")
        val stringsFile = File(valuesFolder, "strings.xml")

        val newStringsFile = SerializableChange(
          stringsFile, FileStatus.NEW, stringsFile.absolutePath)
        val changedStringsFile = SerializableChange(
          stringsFile, FileStatus.CHANGED, stringsFile.absolutePath)
        val removedStringsFile = SerializableChange(
          stringsFile, FileStatus.REMOVED, stringsFile.absolutePath)
        assertThat(canResourcesBeProcessedIncrementally(newStringsFile)).isTrue()
        assertThat(canResourcesBeProcessedIncrementally(changedStringsFile)).isTrue()
        assertThat(canResourcesBeProcessedIncrementally(removedStringsFile)).isTrue()
    }

    /**
     * Test to verify that adding a new layout file will only add the new unique keep classes to
     * the Proguard keep classes file ("aapt_rules.txt"). This
     * assumes that the other conditions are set for an incremental run.
     */
    @Test
    fun testRunIncrementalTask_appendsNewLayoutClasspaths() {
        val parentFolder = temporaryFolder.newFolder("parent")
        val resourcesFolder = File(parentFolder, "res")
        val layoutFolder = File(resourcesFolder, "layout")
        val previousProguardOutput = File(parentFolder, "aapt_rules.txt")
        val addedLayout = File(layoutFolder, "main_activity.xml")
        val changedResources = listOf(
          SerializableChange(addedLayout, FileStatus.NEW, addedLayout.absolutePath)
        )
        FileUtils.createFile(previousProguardOutput,
          """# Generated by the gradle plugin
            |-keep class android.support.v7.widget.Toolbar { <init>(...); }
            |-keep class com.google.android.material.chip.Chip { <init>(...); }""".trimMargin()
        )
        FileUtils.createFile(addedLayout,
        """<root>
            <org.sampleapp.android.Instrument>txt</org.sampleapp.android.Instrument>
            <com.google.android.material.chip.Chip>txt</com.google.android.material.chip.Chip>
            </root>"""
        )

        val params = object
            : GenerateLibraryProguardRulesTask.GenerateProguardRulesWorkAction.Params()
        {
            override val manifestFile = FakeObjectFactory.factory.fileProperty().fileValue(
                File(parentFolder, "AndroidManifiest.xml"))
            override val proguardOutputFile =
                FakeObjectFactory.factory.fileProperty().fileValue(previousProguardOutput)
            override val inputResourcesDir =
                FakeObjectFactory.factory.directoryProperty().fileValue(resourcesFolder)
            override val changedResources = FakeObjectFactory.factory.listProperty(
                SerializableChange::class.java).value(changedResources)
            override val incremental = FakeGradleProperty(true)
            override val projectName = FakeGradleProperty("project")
            override val taskOwner = FakeGradleProperty("taskOwner")
            override val workerKey = FakeGradleProperty("workerKey")
        }

        runIncrementalTask(params)

        assertThat(params.proguardOutputFile.get().asFile.readLines()).containsNoDuplicates()
        assertThat(params.proguardOutputFile.get().asFile.readLines()).containsExactly(
          "# Generated by the gradle plugin",
          "-keep class org.sampleapp.android.Instrument { <init>(...); }",
          "-keep class com.google.android.material.chip.Chip { <init>(...); }",
          "-keep class android.support.v7.widget.Toolbar { <init>(...); }"
        )
    }

    /**
     * Test to verify that adding a new layout file will only add the new unique keep classes to
     * the Proguard keep classes file ("aapt_rules.txt"). This assumes that the other conditions
     * are set for an incremental run.
     */
    @Test
    fun testRunFullTask_createsExpectedKeepRules() {
        val parentFolder = temporaryFolder.newFolder("parent")
        val resourcesFolder = File(parentFolder, "res")
        val layoutFolder = File(resourcesFolder, "layout")
        val previousProguardOutput = File(parentFolder, "aapt_rules.txt")
        val manifestFile = File(parentFolder, "AndroidManifiest.xml")
        val layoutFile = File(layoutFolder, "main_activity.xml")

        FileUtils.createFile(manifestFile,
          """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.google.android.apps.santatracker"
                    xmlns:tools="http://schemas.android.com/tools">
                        <uses-feature
                        android:name="android.hardware.screen.landscape"
                        android:required="false"/>
                            <application
                                android:allowBackup="false"
                                android:icon="@mipmap/ic_launcher_santa"
                                android:label="@string/app_name_santa"
                                android:name=".SantaApplication"
                                tools:replace="android:allowBackup, icon, label"
                                android:banner="@drawable/santatracker_logo_banner"
                                android:usesCleartextTraffic="false"
                                android:largeHeap="true"
                                android:theme="@style/SantaTheme">
                            </application>
                    </manifest>""")
        FileUtils.createFile(layoutFile,
          """<root>
            <org.sampleapp.android.Instrument>txt</org.sampleapp.android.Instrument>
            <com.google.android.material.chip.Chip>txt</com.google.android.material.chip.Chip>
            </root>"""
        )

        val params = object
            : GenerateLibraryProguardRulesTask.GenerateProguardRulesWorkAction.Params()
        {
            override val manifestFile
                    = FakeObjectFactory.factory.fileProperty().fileValue(manifestFile)
            override val proguardOutputFile =
                FakeObjectFactory.factory.fileProperty().fileValue(previousProguardOutput)
            override val inputResourcesDir =
                FakeObjectFactory.factory.directoryProperty().fileValue(resourcesFolder)
            override val changedResources =
                FakeObjectFactory.factory.listProperty(SerializableChange::class.java)
            override val incremental = FakeGradleProperty(false)
            override val projectName = FakeGradleProperty("project")
            override val taskOwner = FakeGradleProperty("taskOwner")
            override val workerKey = FakeGradleProperty("workerKey")
        }

        runFullTask(params)

        assertThat(params.proguardOutputFile.get().asFile.readLines()).containsNoDuplicates()
        assertThat(params.proguardOutputFile.get().asFile.readLines()).containsExactly(
          "# Generated by the gradle plugin",
          "-keep class com.google.android.apps.santatracker.SantaApplication { <init>(...); }",
          "-keep class org.sampleapp.android.Instrument { <init>(...); }",
          "-keep class com.google.android.material.chip.Chip { <init>(...); }"
        )
    }
}