/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ide.common.gradle.model.impl;

import static com.android.ide.common.gradle.model.impl.IdeModelTestUtils.*;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.ide.common.gradle.model.UnusedModelMethodException;
import com.android.ide.common.gradle.model.impl.ndk.v1.IdeNativeArtifactImpl;
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeArtifact;
import com.android.ide.common.gradle.model.stubs.NativeArtifactStub;
import com.android.testutils.Serialization;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeNativeArtifactImpl}. */
public class IdeNativeArtifactTest {
    private ModelCache myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = new ModelCache();
    }

    @Test
    public void serializable() {
        assertThat(IdeNativeArtifactImpl.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeNativeArtifact nativeArtifact =
                myModelCache.nativeArtifactFrom(new NativeArtifactStub());
        byte[] bytes = Serialization.serialize(nativeArtifact);
        Object o = Serialization.deserialize(bytes);
        assertEquals(nativeArtifact, o);
    }

    @Test(expected = UnusedModelMethodException.class)
    public void getRuntimeFilesWithPlugin2dot2() {
        NativeArtifactStub original =
                new NativeArtifactStub() {
                    @Override
                    @NonNull
                    public Collection<File> getRuntimeFiles() {
                        throw new UnsupportedOperationException("getRuntimeFiles()");
                    }

                    @Override
                    public int hashCode() {
                        return Objects.hash(
                                getName(),
                                getToolChain(),
                                getGroupName(),
                                getAssembleTaskName(),
                                getSourceFiles(),
                                getExportedHeaders(),
                                getAbi(),
                                getTargetName(),
                                getOutputFile());
                    }
                };
        IdeNativeArtifactImpl artifact = myModelCache.nativeArtifactFrom(original);
        artifact.getRuntimeFiles();
    }

    @Test(expected = UnusedModelMethodException.class)
    public void getAssembleTaskNameWithExperimentalPlugin0dot7() {
        NativeArtifactStub original =
                new NativeArtifactStub() {
                    @Override
                    @NonNull
                    public String getAssembleTaskName() {
                        throw new UnsupportedOperationException("getAssembleTaskName");
                    }

                    @Override
                    public int hashCode() {
                        return Objects.hash(
                                getName(),
                                getToolChain(),
                                getGroupName(),
                                getSourceFiles(),
                                getExportedHeaders(),
                                getAbi(),
                                getTargetName(),
                                getOutputFile(),
                                getRuntimeFiles());
                    }
                };
        IdeNativeArtifactImpl artifact = myModelCache.nativeArtifactFrom(original);
        artifact.getAssembleTaskName();
    }

    @Test
    public void getAbiWithExperimentalPlugin0dot7() {
        NativeArtifactStub original =
                new NativeArtifactStub() {
                    @Override
                    @NonNull
                    public String getAbi() {
                        throw new UnsupportedOperationException("getAbi");
                    }

                    @Override
                    public int hashCode() {
                        return Objects.hash(
                                getName(),
                                getToolChain(),
                                getGroupName(),
                                getAssembleTaskName(),
                                getSourceFiles(),
                                getExportedHeaders(),
                                getTargetName(),
                                getOutputFile(),
                                getRuntimeFiles());
                    }
                };
        IdeNativeArtifactImpl artifact = myModelCache.nativeArtifactFrom(original);
        assertThat(artifact.getAbi()).isEqualTo("");
    }

    @Test
    public void getTargetNameWithExperimentalPlugin0dot7() {
        NativeArtifactStub original =
                new NativeArtifactStub() {
                    @Override
                    @NonNull
                    public String getTargetName() {
                        throw new UnsupportedOperationException("getTargetName");
                    }

                    @Override
                    public int hashCode() {
                        return Objects.hash(
                                getName(),
                                getToolChain(),
                                getGroupName(),
                                getAssembleTaskName(),
                                getSourceFiles(),
                                getExportedHeaders(),
                                getAbi(),
                                getOutputFile(),
                                getRuntimeFiles());
                    }
                };
        IdeNativeArtifactImpl artifact = myModelCache.nativeArtifactFrom(original);
        assertThat(artifact.getTargetName()).isEqualTo("");
    }

    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeNativeArtifactImpl.class).verify();
    }
}
