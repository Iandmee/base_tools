/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.packaging;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Preconditions;

import java.io.File;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * Factory that creates instances of {@link ApkCreator}.
 */
public interface ApkCreatorFactory {
    /**
     * Creates an {@link ApkCreator} with a given output location, and signing information.
     *
     * @param creationData the information to create the APK
     * @throws PackagerException failed to create the builder
     */
    ApkCreator make(@NonNull CreationData creationData) throws PackagerException;

    /**
     * Data structure with the required information to initiate the creation of an APK. See
     * {@link ApkCreatorFactory#make(CreationData)}.
     */
    class CreationData {

        /**
         * The path where the APK should be located. May already exist or not (if it does, then
         * the APK may be updated instead of created).
         */
        @NonNull
        private final File mApkPath;

        /**
         * Key used to sign the APK. May be {@code null}.
         */
        @Nullable
        private final PrivateKey mKey;

        /**
         * Certificate used to sign the APK. Is {@code null} if and only if {@link #mKey} is
         * {@code null}.
         */
        @Nullable
        private final X509Certificate mCertificate;

        /**
         * Built-by information for the APK, if any.
         */
        @Nullable
        private final String mBuiltBy;

        /**
         * Created-by information for the APK, if any.
         */
        @Nullable
        private final String mCreatedBy;

        /**
         * Minimum SDk version that will run the APK.
         */
        private final int mMinSdkVersion;

        /**
         *
         * @param apkPath the path where the APK should be located. May already exist or not (if it
         * does, then the APK may be updated instead of created)
         * @param key key used to sign the APK. May be {@code null}
         * @param certificate certificate used to sign the APK. Is {@code null} if and only if
         * {@code key} is {@code null}
         * @param builtBy built-by information for the APK, if any; if {@code null} then the default
         * should be used
         * @param createdBy created-by information for the APK, if any; if {@code null} then the
         * default should be used
         * @param minSdkVersion minimum SDK version that will run the APK
         */
        public CreationData(@NonNull File apkPath, @Nullable PrivateKey key,
                @Nullable X509Certificate certificate, @Nullable String builtBy,
                @Nullable String createdBy, int minSdkVersion) {
            Preconditions.checkArgument((key == null) == (certificate == null),
                    "(key == null) != (certificate == null)");
            Preconditions.checkArgument(minSdkVersion >= 0, "minSdkVersion < 0");

            mApkPath = apkPath;
            mKey = key;
            mCertificate = certificate;
            mBuiltBy = builtBy;
            mCreatedBy = createdBy;
            mMinSdkVersion = minSdkVersion;
        }

        /**
         * Obtains the path where the APK should be located. If the path already exists, then the
         * APK may be updated instead of re-created.
         *
         * @return the path that may already exist or not
         */
        @NonNull
        public File getApkPath() {
            return mApkPath;
        }

        /**
         * Obtains the private key used to sign the APK.
         *
         * @return the private key or {@code null} if the APK should not be signed
         */
        @Nullable
        public PrivateKey getPrivateKey() {
            return mKey;
        }

        /**
         * Obtains the certificate used to sign the APK.
         *
         * @return the certificate or {@code null} if the APK should not be signed; this will return
         * {@code null} if and only if {@link #getPrivateKey()} returns {@code null}
         */
        @Nullable
        public X509Certificate getCertificate() {
            return mCertificate;
        }

        /**
         * Obtains the "built-by" text for the APK.
         *
         * @return the text or {@code null} if the default should be used
         */
        @Nullable
        public String getBuiltBy() {
            return mBuiltBy;
        }

        /**
         * Obtains the "created-by" text for the APK.
         *
         * @return the text or {@code null} if the default should be used
         */
        @Nullable
        public String getCreatedBy() {
            return mCreatedBy;
        }

        /**
         * Obtains the minimum SDK version to run the APK.
         *
         * @return the minimum SDK version
         */
        public int getMinSdkVersion() {
            return mMinSdkVersion;
        }
    }
}
