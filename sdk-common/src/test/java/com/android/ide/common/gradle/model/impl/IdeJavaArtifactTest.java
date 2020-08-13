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

import static com.android.ide.common.gradle.model.impl.IdeModelTestUtils.assertEqualsOrSimilar;
import static com.android.ide.common.gradle.model.impl.IdeModelTestUtils.createEqualsVerifier;
import static com.android.ide.common.gradle.model.impl.IdeModelTestUtils.verifyUsageOfImmutableCollections;

import com.android.builder.model.JavaArtifact;
import com.android.ide.common.gradle.model.IdeJavaArtifact;
import com.android.ide.common.gradle.model.stubs.JavaArtifactStub;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeJavaArtifact}. */
public class IdeJavaArtifactTest {
    private ModelCacheTesting myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = ModelCache.createForTesting();
    }

    @Test
    public void constructor() throws Throwable {
        JavaArtifact original = new JavaArtifactStub();
        IdeJavaArtifact copy = myModelCache.javaArtifactFrom(original);
        assertEqualsOrSimilar(original, copy);
        verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeJavaArtifactImpl.class)
                .withRedefinedSuperclass()
                .withIgnoredFields("hashCode")
                .verify();
    }
}
