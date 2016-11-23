/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.common.truth;

import static com.google.common.truth.Truth.assert_;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.Logcat;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeSettings;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.android.testutils.incremental.FileRecord;
import com.android.testutils.truth.FileRecordSubject;
import com.android.testutils.truth.FileSubject;
import com.android.testutils.truth.FileSubjectFactory;
import com.android.testutils.truth.Java8OptionalSubject;
import com.android.testutils.truth.ZipFileSubject;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Optional;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Table;
import com.google.common.truth.BigDecimalSubject;
import com.google.common.truth.BooleanSubject;
import com.google.common.truth.ClassSubject;
import com.google.common.truth.ComparableSubject;
import com.google.common.truth.DefaultSubject;
import com.google.common.truth.DoubleSubject;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.GuavaOptionalSubject;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.ListMultimapSubject;
import com.google.common.truth.LongSubject;
import com.google.common.truth.MapSubject;
import com.google.common.truth.MultimapSubject;
import com.google.common.truth.MultisetSubject;
import com.google.common.truth.ObjectArraySubject;
import com.google.common.truth.PrimitiveBooleanArraySubject;
import com.google.common.truth.PrimitiveByteArraySubject;
import com.google.common.truth.PrimitiveCharArraySubject;
import com.google.common.truth.PrimitiveDoubleArraySubject;
import com.google.common.truth.PrimitiveFloatArraySubject;
import com.google.common.truth.PrimitiveIntArraySubject;
import com.google.common.truth.PrimitiveLongArraySubject;
import com.google.common.truth.SetMultimapSubject;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.TableSubject;
import com.google.common.truth.TestVerb;
import com.google.common.truth.ThrowableSubject;
import java.io.File;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Helper for custom Truth factories.
 *
 * TODO: Remove methods that should be imported directly by statically importing Truth.assertThat
 */
public class TruthHelper {
    @NonNull
    public static FileSubject assertThat(@Nullable File file) {
        return assert_().about(FileSubjectFactory.factory()).that(file);
    }

    @NonNull
    public static FileRecordSubject assertThat(@NonNull FileRecord fileRecord) {
        return assert_().about(FileRecordSubject.FACTORY).that(fileRecord);
    }

    @NonNull
    public static NativeLibrarySubject assertThatNativeLib(@Nullable File file) {
        return assert_().about(NativeLibrarySubject.FACTORY).that(file);
    }

    @NonNull
    public static ApkSubject assertThatApk(@Nullable File apk) {
        return assert_().about(ApkSubject.FACTORY).that(apk);
    }

    @NonNull
    public static DexFileSubject assertThatDex(@Nullable File dex) {
        return assert_().about(DexFileSubject.FACTORY).that(dex);
    }

    @NonNull
    public static AarSubject assertThatAar(@Nullable File aar) {
        return assert_().about(AarSubject.Factory.get()).that(aar);
    }

    @NonNull
    public static AtomBundleSubject assertThatAtomBundle(@Nullable File atombundle) {
        return assert_().about(AtomBundleSubject.FACTORY).that(atombundle);
    }

    @NonNull
    public static ZipFileSubject assertThatZip(@Nullable File file) {
        return assert_()
                .about(
                        new SubjectFactory<ZipFileSubject, File>() {
                            @Override
                            public ZipFileSubject getSubject(FailureStrategy fs, File that) {
                                return new ZipFileSubject(fs, that);
                            }
                        })
                .that(file);
    }

    @NonNull
    public static ModelSubject assertThat(@Nullable AndroidProject androidProject) {
        return assert_().about(ModelSubject.Factory.get()).that(androidProject);
    }

    @NonNull
    public static IssueSubject assertThat(@Nullable SyncIssue issue) {
        return assert_().about(IssueSubject.Factory.get()).that(issue);
    }

    @NonNull
    public static VariantSubject assertThat(@Nullable Variant variant) {
        return assert_().about(VariantSubject.Factory.get()).that(variant);
    }

    @NonNull
    public static ArtifactSubject assertThat(@Nullable AndroidArtifact artifact) {
        return assert_().about(ArtifactSubject.Factory.get()).that(artifact);
    }

    @NonNull
    public static DependenciesSubject assertThat(@Nullable Dependencies dependencies) {
        return assert_().about(DependenciesSubject.Factory.get()).that(
                dependencies);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @NonNull
    public static <T> Java8OptionalSubject<T> assertThat(@NonNull java.util.Optional<T> optional) {
        // need to create a new factory here so that it's generic
        return assert_().about(new SubjectFactory<Java8OptionalSubject<T>, java.util.Optional<T>>() {
            @Override
            public Java8OptionalSubject<T> getSubject(FailureStrategy fs, java.util.Optional<T> that) {
                return new Java8OptionalSubject<>(fs, that);
            }
        }).that(optional);
    }

    public static LogCatMessagesSubject assertThat(Logcat logcat) {
        return assert_().about(LogCatMessagesSubject.FACTORY).that(logcat);
    }

    @NonNull
    public static MavenCoordinatesSubject assertThat(@Nullable MavenCoordinates coordinates) {
        return assert_().about(MavenCoordinatesSubject.Factory.get()).that(coordinates);
    }

    @NonNull
    public static NativeSettingsSubject assertThat(@Nullable NativeSettings settings) {
        return assert_().about(NativeSettingsSubject.Factory.get()).that(settings);
    }

    @NonNull
    public static NativeAndroidProjectSubject assertThat(@Nullable NativeAndroidProject project) {
        return assert_().about(NativeAndroidProjectSubject.Factory.get()).that(project);
    }

    // ---- helper method from com.google.common.truth.Truth
    // this to allow a single static import of assertThat

    /**
     * Returns a {@link TestVerb} that will prepend the given message to the failure message in
     * the event of a test failure.
     */
    public static TestVerb assertWithMessage(String messageToPrepend) {
        return assert_().withFailureMessage(messageToPrepend);
    }

    public static <T extends Comparable<?>> ComparableSubject<?, T> assertThat(@Nullable T target) {
        return assert_().that(target);
    }

    public static BigDecimalSubject assertThat(@Nullable BigDecimal target) {
        return assert_().that(target);
    }

    public static Subject<DefaultSubject, Object> assertThat(@Nullable Object target) {
        return assert_().that(target);
    }

    @GwtIncompatible("ClassSubject.java")
    public static ClassSubject assertThat(@Nullable Class<?> target) {
        return assert_().that(target);
    }

    public static ThrowableSubject assertThat(@Nullable Throwable target) {
        return assert_().that(target);
    }

    public static LongSubject assertThat(@Nullable Long target) {
        return assert_().that(target);
    }

    public static DoubleSubject assertThat(@Nullable Double target) {
        return assert_().that(target);
    }

    public static IntegerSubject assertThat(@Nullable Integer target) {
        return assert_().that(target);
    }

    public static BooleanSubject assertThat(@Nullable Boolean target) {
        return assert_().that(target);
    }

    public static StringSubject assertThat(@Nullable String target) {
        return assert_().that(target);
    }

    public static <T, C extends Iterable<T>> IterableSubject<? extends IterableSubject<?, T, C>, T, C>
    assertThat(@Nullable Iterable<T> target) {
        return assert_().<T,C>that(target);
    }

    public static <T> ObjectArraySubject<T> assertThat(@Nullable T[] target) {
        return assert_().that(target);
    }

    public static PrimitiveBooleanArraySubject assertThat(@Nullable boolean[] target) {
        return assert_().that(target);
    }

    public static PrimitiveIntArraySubject assertThat(@Nullable int[] target) {
        return assert_().that(target);
    }

    public static PrimitiveLongArraySubject assertThat(@Nullable long[] target) {
        return assert_().that(target);
    }

    public static PrimitiveByteArraySubject assertThat(@Nullable byte[] target) {
        return assert_().that(target);
    }

    public static PrimitiveCharArraySubject assertThat(@Nullable char[] target) {
        return assert_().that(target);
    }

    public static PrimitiveFloatArraySubject assertThat(@Nullable float[] target) {
        return assert_().that(target);
    }

    public static PrimitiveDoubleArraySubject assertThat(@Nullable double[] target) {
        return assert_().that(target);
    }

    public static <T> GuavaOptionalSubject assertThat(@Nullable Optional<T> target) {
        return assert_().that(target);
    }

    public static MapSubject assertThat(@Nullable Map<?, ?> target) {
        return assert_().that(target);
    }

    public static <K, V, M extends Multimap<K, V>>
    MultimapSubject<? extends MultimapSubject<?, K, V, M>, K, V, M> assertThat(
            @Nullable Multimap<K, V> target) {
        return assert_().<K,V,M>that(target);
    }

    public static <K, V, M extends ListMultimap<K, V>>
    ListMultimapSubject<? extends ListMultimapSubject<?, K, V, M>, K, V, M> assertThat(
            @Nullable ListMultimap<K, V> target) {
        return assert_().<K,V,M>that(target);
    }

    public static <K, V, M extends SetMultimap<K, V>>
    SetMultimapSubject<? extends SetMultimapSubject<?, K, V, M>, K, V, M> assertThat(
            @Nullable SetMultimap<K, V> target) {
        return assert_().<K,V,M>that(target);
    }

    public static <E, M extends Multiset<E>>
    MultisetSubject<? extends MultisetSubject<?, E, M>, E, M> assertThat(
            @Nullable Multiset<E> target) {
        return assert_().<E,M>that(target);
    }

    public static TableSubject assertThat(@Nullable Table<?, ?, ?> target) {
        return assert_().that(target);
    }
}
