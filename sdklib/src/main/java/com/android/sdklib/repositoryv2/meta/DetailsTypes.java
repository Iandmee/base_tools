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

package com.android.sdklib.repositoryv2.meta;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repositoryv2.IdDisplay;

import java.io.File;
import java.util.List;

import javax.xml.bind.annotation.XmlTransient;

/**
 * Container for the subclasses of {@link TypeDetails} used by the android SDK.
 * Concrete classes are generated by xjc.
 */
public final class DetailsTypes {

    private DetailsTypes() {}

    /**
     * Common methods shared by all android version-specific details types.
     */
    public interface ApiDetailsType {

        /**
         * Sets the api level this package corresponds to.
         */
        void setApiLevel(int apiLevel);

        /**
         * Gets the api level of this package.
         */
        int getApiLevel();

        /**
         * If this is a preview release the api is identified by a codename in addition to the api
         * level. In this case {@code codename} should be non-null.
         */
        void setCodename(@Nullable String codename);

        /**
         * Gets the codename of this release. Should be {@code null} for regular releases, and non-
         * null for preview releases.
         */
        String getCodename();
    }

    /**
     * Trivial details type for source packages.
     */
    @XmlTransient
    public interface SourceDetailsType extends ApiDetailsType {}

    /**
     * Trivial details type for build tools packages.
     */
    @XmlTransient
    public interface BuildToolDetailsType {}

    /**
     * Trivial details type for doc packages.
     */
    @XmlTransient
    public interface DocDetailsType {}

    /**
     * Details type for platform packages. Contains info on the layout lib version provided.
     */
    @XmlTransient
    public interface PlatformDetailsType extends ApiDetailsType {

        void setLayoutlib(@NonNull LayoutlibType layoutLib);

        @NonNull
        LayoutlibType getLayoutlib();

        /**
         * Parent class for xjc-generated classes containing info on the layout lib version.
         */
        @XmlTransient
        abstract class LayoutlibType {

            /**
             * Sets the layout lib api level.
             */
            public abstract void setApi(int api);

            /**
             * Gets the layout lib api level.
             */
            public abstract int getApi();
        }
    }

    /**
     * Trivial details type for platform-tool packages.
     */
    @XmlTransient
    public interface PlatformToolDetailsType {}

    /**
     * Trivial details type for tool packages.
     */
    @XmlTransient
    public interface ToolDetailsType {}

    /**
     * Details type for extra packages. Includes a {@link IdDisplay} for the vendor.
     */
    @XmlTransient
    public interface ExtraDetailsType {

        /**
         * Sets the vendor for this package.
         */
        void setVendor(@NonNull IdDisplay vendor);

        /**
         * Gets the vendor for this package.
         * @return
         */
        @NonNull
        IdDisplay getVendor();
    }

    /**
     * Details type for addon packages. Includes a {@link IdDisplay} for the vendor.
     */
    @XmlTransient
    public interface AddonDetailsType extends ApiDetailsType {
        void setVendor(@NonNull IdDisplay vendor);

        @NonNull
        IdDisplay getVendor();

        /**
         * Gets the {@link IAndroidTarget.OptionalLibrary}s provided by this
         * package.
         */
        @Nullable
        Libraries getLibraries();

        /**
         * Sets the tag for this package. Used to match addon packages with corresponding
         * system images.
         */
        void setTag(@NonNull IdDisplay tag);

        /**
         * Gets the tag for this package. Used to match addon packages with corresponding
         * system images.
         */
        @NonNull
        IdDisplay getTag();

        /**
         * Gets the default skin included in this package.
         */
        @Nullable
        String getDefaultSkin();

        /**
         * List of all {@link Library}s included in this package.
         */
        abstract class Libraries {
            @NonNull
            public abstract List<Library> getLibrary();
        }

    }

    /**
     * Details type for system images packages. Includes information on the abi (architecture),
     * tag (device type), and vendor.
     */
    @XmlTransient
    public interface SysImgDetailsType extends ApiDetailsType {

        /**
         * Sets the abi type (x86, armeabi-v7a, etc.) for this package.
         */
        void setAbi(@NonNull String abi);

        /**
         * Gets the abi type (x86, armeabi-v7a, etc.) for this package.
         */
        @NonNull
        String getAbi();

        /**
         * Checks whether {@code value} is a valid abi type.
         */
        boolean isValidAbi(@Nullable String value);

        /**
         * Sets the tag for this package. Used to match addon packages with corresponding
         * system images.
         */
        void setTag(@NonNull IdDisplay tag);

        /**
         * Sets the tag for this package. Used to match addon packages with corresponding
         * system images.
         */
        @NonNull
        IdDisplay getTag();

        /**
         * Sets the vendor of this package.
         */
        void setVendor(@Nullable IdDisplay vendor);

        /**
         * Gets the vendor of this package.
         */
        @Nullable
        IdDisplay getVendor();
    }

    /**
     * Details type for packages that will be installed as maven artifacts in our local maven
     * repository.
     */
    @XmlTransient
    public interface MavenType {}

    /**
     * Convenience method to create an {@link AndroidVersion} with the information from the
     * given {@link ApiDetailsType}.
     */
    @NonNull
    public static AndroidVersion getAndroidVersion(@NonNull ApiDetailsType details) {
        return new AndroidVersion(details.getApiLevel(), details.getCodename());
    }

    /**
     * Information about a {@link IAndroidTarget.OptionalLibrary} provided by a package.
     */
    public abstract static class Library implements IAndroidTarget.OptionalLibrary {

        /**
         * Reference to the path of the containing package.
         */
        private File mPackagePath;

        /**
         * Sets the path of the containing package. Must be called before calling {@link #getJar()}.
         */
        public void setPackagePath(@NonNull File packagePath) {
            mPackagePath = packagePath;
        }

        /**
         * Absolute path to the library jar file.
         */
        @Override
        @NonNull
        public File getJar() {
            assert mPackagePath != null;
            String localPath = getLocalJarPath();
            localPath = localPath.replaceAll("/", File.separator);
            return new File(mPackagePath, SdkConstants.OS_ADDON_LIBS_FOLDER + localPath);
        }

        /**
         * Path to the library jar file relative to the {@code libs} directory in the package.
         */
        @NonNull
        public abstract String getLocalJarPath();

        /**
         * The name of the library.
         */
        @Override
        @NonNull
        public abstract String getName();

        /**
         * User-friendly description of the library.
         */
        @Override
        @NonNull
        public abstract String getDescription();

        /**
         * Whether a manifest entry is required for this library.
         */
        @Override
        public abstract boolean isManifestEntryRequired();

    }
}
