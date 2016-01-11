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
package com.android.sdklib.repositoryv2.targets;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.io.FileOp;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.repository.local.PackageParserUtils;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.google.common.base.Charsets;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a platform target in the SDK.
 */
public class PlatformTarget implements IAndroidTarget {

    /**
     * Default vendor for platform targets
     */
    private static final String PLATFORM_VENDOR = "Android Open Source Project";

    /**
     * "Android NN" is the default name for platform targets.
     */
    private static final String PLATFORM_NAME = "Android %s";

    /**
     * "Android NN (Preview)" is the default name for preview platform targets.
     */
    private static final String PLATFORM_NAME_PREVIEW = "Android %s (Preview)";

    /**
     * The {@link LocalPackage} from which this target was created.
     */
    private LocalPackage mPackage;

    /**
     * The {@link TypeDetails} of {@link #mPackage}.
     */
    private DetailsTypes.PlatformDetailsType mDetails;

    /**
     * Additional {@link IAndroidTarget.OptionalLibrary}s provided by this target.
     */
    private List<OptionalLibrary> mOptionalLibraries = ImmutableList.of();

    /**
     * The emulator skins for this target, including those included in the package as well as those
     * from associated system images.
     */
    private Set<File> mSkins;

    /**
     * Parsed version of the {@code build.prop} file in {@link #mPackage}.
     */
    private Map<String, String> mBuildProps;

    /**
     * Reference to the latest {@link BuildToolInfo}.
     */
    private BuildToolInfo mBuildToolInfo;

    /**
     * Map from tag and abi to the system images associated with this platform.
     */
    private Table<IdDisplay, String, ISystemImage> mSystemImages;

    /**
     * Construct a new {@code PlatformTarget} based on the given package.
     */
    public PlatformTarget(@NonNull LocalPackage p, @NonNull AndroidSdkHandler sdkHandler,
            @NonNull FileOp fop, @NonNull ProgressIndicator progress) {
        mPackage = p;
        TypeDetails details = p.getTypeDetails();
        assert details instanceof DetailsTypes.PlatformDetailsType;
        mDetails = (DetailsTypes.PlatformDetailsType) details;

        File optionalDir = new File(p.getLocation(), "optional");
        if (optionalDir.isDirectory()) {
            File optionalJson = new File(optionalDir, "optional.json");
            if (optionalJson.isFile()) {
                mOptionalLibraries = getLibsFromJson(optionalJson);
            }
        }

        File buildProp = new File(getLocation(), SdkConstants.FN_BUILD_PROP);

        if (!fop.isFile(buildProp)) {
            String message = "Build properties not found for package " + p.getDisplayName();
            progress.logWarning(message);
            throw new IllegalArgumentException(message);
        }

        try {
            mBuildProps = ProjectProperties.parsePropertyStream(fop.newFileInputStream(buildProp),
                    buildProp.getPath(), null);
        } catch (FileNotFoundException ignore) {
        }
        if (mBuildProps == null) {
            mBuildProps = Maps.newHashMap();
        }
        mBuildToolInfo = sdkHandler.getLatestBuildTool(progress);
        SystemImageManager sysImgMgr = sdkHandler.getSystemImageManager(progress);
        mSystemImages = HashBasedTable.create();
        Map<SystemImage, LocalPackage> systemImages = sysImgMgr.getImageMap();
        mSkins = Sets
                .newTreeSet(PackageParserUtils.parseSkinFolder(getFile(IAndroidTarget.SKINS), fop));
        for (SystemImage img : systemImages.keySet()) {
            LocalPackage pkg = systemImages.get(img);
            TypeDetails typeDetails = pkg.getTypeDetails();
            if (pkg.equals(mPackage) || (typeDetails instanceof DetailsTypes.SysImgDetailsType &&
                    ((DetailsTypes.SysImgDetailsType) typeDetails).getVendor() == null &&
                    ((DetailsTypes.SysImgDetailsType) typeDetails).getApiLevel() == mDetails
                            .getApiLevel())) {
                mSystemImages.put(img.getTag(), img.getAbiType(), img);
                // We don't worry about duplicate skins here, so we can have them all available
                // when assigning one to a system image and can pick the most relevant one.
                mSkins.addAll(Arrays.asList(img.getSkins()));
            }
        }
    }

    /**
     * Simple struct used by {@link Gson} when parsing the library file.
     */
    public static class Library {

        String name;

        String jar;

        boolean manifest;
    }

    /**
     * Parses {@link IAndroidTarget.OptionalLibrary}s from the given json file.
     */
    @VisibleForTesting
    @NonNull
    static List<OptionalLibrary> getLibsFromJson(@NonNull File jsonFile) {

        Gson gson = new Gson();

        try {
            Type collectionType = new TypeToken<Collection<Library>>() {
            }.getType();
            Collection<Library> libs = gson
                    .fromJson(Files.newReader(jsonFile, Charsets.UTF_8), collectionType);

            // convert into the right format.
            List<OptionalLibrary> optionalLibraries = Lists.newArrayListWithCapacity(libs.size());

            File rootFolder = jsonFile.getParentFile();
            for (Library lib : libs) {
                optionalLibraries.add(new OptionalLibraryImpl(
                        lib.name,
                        new File(rootFolder, lib.jar),
                        lib.name,
                        lib.manifest));
            }

            return optionalLibraries;
        } catch (FileNotFoundException e) {
            // shouldn't happen since we've checked the file is here, but can happen in
            // some cases (too many files open).
            return Collections.emptyList();
        }
    }


    @Override
    public String getLocation() {
        return mPackage.getLocation().getPath() + File.separator;
    }

    /**
     * {@inheritDoc}
     *
     * For platform, this is always {@link #PLATFORM_VENDOR}
     */
    @Override
    public String getVendor() {
        return PLATFORM_VENDOR;
    }

    @Override
    public String getName() {
        AndroidVersion version = getVersion();
        if (version.isPreview()) {
            return String.format(PLATFORM_NAME_PREVIEW, version);
        } else {
            return String.format(PLATFORM_NAME, version);
        }
    }

    @Override
    public String getFullName() {
        return getName();
    }

    @Override
    public String getDescription() {
        // Unused outside swt
        return getName();
    }

    @NonNull
    @Override
    public AndroidVersion getVersion() {
        return new AndroidVersion(mDetails.getApiLevel(), mDetails.getCodename());
    }

    @Override
    public String getVersionName() {
        return SdkVersionInfo.getVersionString(mDetails.getApiLevel());
    }

    @Override
    public int getRevision() {
        return mPackage.getVersion().getMajor();
    }

    @Override
    public boolean isPlatform() {
        return true;
    }

    @Override
    public IAndroidTarget getParent() {
        return null;
    }

    @NonNull
    @Override
    public String getPath(int pathId) {
        switch (pathId) {
            case ANDROID_JAR:
                return getLocation() + SdkConstants.FN_FRAMEWORK_LIBRARY;
            case UI_AUTOMATOR_JAR:
                return getLocation() + SdkConstants.FN_UI_AUTOMATOR_LIBRARY;
            case SOURCES:
                return getLocation() + SdkConstants.FD_ANDROID_SOURCES;
            case ANDROID_AIDL:
                return getLocation() + SdkConstants.FN_FRAMEWORK_AIDL;
            case SAMPLES:
                return getLocation() + SdkConstants.OS_PLATFORM_SAMPLES_FOLDER;
            case SKINS:
                return getLocation() + SdkConstants.OS_SKINS_FOLDER;
            case TEMPLATES:
                return getLocation() + SdkConstants.OS_PLATFORM_TEMPLATES_FOLDER;
            case DATA:
                return getLocation() + SdkConstants.OS_PLATFORM_DATA_FOLDER;
            case ATTRIBUTES:
                return getLocation() + SdkConstants.OS_PLATFORM_ATTRS_XML;
            case MANIFEST_ATTRIBUTES:
                return getLocation() + SdkConstants.OS_PLATFORM_ATTRS_MANIFEST_XML;
            case RESOURCES:
                return getLocation() + SdkConstants.OS_PLATFORM_RESOURCES_FOLDER;
            case FONTS:
                return getLocation() + SdkConstants.OS_PLATFORM_FONTS_FOLDER;
            case LAYOUT_LIB:
                return getLocation() + SdkConstants.OS_PLATFORM_DATA_FOLDER +
                        SdkConstants.FN_LAYOUTLIB_JAR;
            case WIDGETS:
                return getLocation() + SdkConstants.OS_PLATFORM_DATA_FOLDER +
                        SdkConstants.FN_WIDGETS;
            case ACTIONS_ACTIVITY:
                return getLocation() + SdkConstants.OS_PLATFORM_DATA_FOLDER +
                        SdkConstants.FN_INTENT_ACTIONS_ACTIVITY;
            case ACTIONS_BROADCAST:
                return getLocation() + SdkConstants.OS_PLATFORM_DATA_FOLDER +
                        SdkConstants.FN_INTENT_ACTIONS_BROADCAST;
            case ACTIONS_SERVICE:
                return getLocation() + SdkConstants.OS_PLATFORM_DATA_FOLDER +
                        SdkConstants.FN_INTENT_ACTIONS_SERVICE;
            case CATEGORIES:
                return getLocation() + SdkConstants.OS_PLATFORM_DATA_FOLDER +
                        SdkConstants.FN_INTENT_CATEGORIES;
            case ANT:
                return getLocation() + SdkConstants.OS_PLATFORM_ANT_FOLDER;
            default:
                return getLocation();
        }
    }

    @NonNull
    @Override
    public File getFile(int pathId) {
        return new File(getPath(pathId));
    }

    @Nullable
    @Override
    public BuildToolInfo getBuildToolInfo() {
        return mBuildToolInfo;
    }

    @NonNull
    @Override
    public List<String> getBootClasspath() {
        return ImmutableList.of(getPath(IAndroidTarget.ANDROID_JAR));
    }

    @NonNull
    @Override
    public List<OptionalLibrary> getOptionalLibraries() {
        return mOptionalLibraries;
    }

    @NonNull
    @Override
    public List<OptionalLibrary> getAdditionalLibraries() {
        return ImmutableList.of();
    }

    @Override
    public boolean hasRenderingLibrary() {
        return true;
    }

    @NonNull
    @Override
    public File[] getSkins() {
        return mSkins.toArray(new File[mSkins.size()]);
    }

    public int getLayoutlibApi() {
        return mDetails.getLayoutlib().getApi();
    }


    @Nullable
    @Override
    public File getDefaultSkin() {
        // TODO: validate choice to ignore property in sdk.properties

        // only one skin? easy.
        if (mSkins.size() == 1) {
            return mSkins.iterator().next();
        }
        String skinName;
        // otherwise try to find a good default.
        if (getVersion().getApiLevel() >= 11 && getVersion().getApiLevel() <= 13) {
            skinName = "WXGA";
        } else if (getVersion().getApiLevel() >= 4) {
            // at this time, this is the default skin for all older platforms that had 2+ skins.
            skinName = "WVGA800";
        } else {
            skinName = "HVGA"; // this is for 1.5 and earlier.
        }

        return new File(getFile(IAndroidTarget.SKINS), skinName);
    }

    /**
     * {@inheritDoc}
     *
     * For platforms this is always {@link SdkConstants#ANDROID_TEST_RUNNER_LIB}.
     */
    @NonNull
    @Override
    public String[] getPlatformLibraries() {
        return new String[]{SdkConstants.ANDROID_TEST_RUNNER_LIB};
    }

    @Nullable
    @Override
    public String getProperty(@NonNull String name) {
        return mBuildProps.get(name);
    }

    @Nullable
    @Override
    public Map<String, String> getProperties() {
        return mBuildProps;
    }

    @NonNull
    @Override
    public ISystemImage[] getSystemImages() {
        Collection<ISystemImage> values = mSystemImages.values();
        return values.toArray(new ISystemImage[values.size()]);
    }

    @NonNull
    @Override
    public String getShortClasspathName() {
        return getName();
    }

    @NonNull
    @Override
    public String getClasspathName() {
        return getName();
    }

    @Nullable
    @Override
    public ISystemImage getSystemImage(@NonNull IdDisplay tag, @NonNull String abiType) {
        return mSystemImages.get(tag, abiType);
    }

    @Override
    public boolean canRunOn(@NonNull IAndroidTarget target) {
        if (getVersion().isPreview()) {
            return target.getVersion().equals(getVersion());
        }
        return target.getVersion().getApiLevel() > getVersion().getApiLevel();
    }

    @NonNull
    @Override
    public String hashString() {
        return AndroidTargetHash.getPlatformHashString(getVersion());
    }

    @Override
    public int compareTo(@NonNull IAndroidTarget o) {
        int res = getVersion().compareTo(o.getVersion());
        if (res != 0) {
            return res;
        }
        return o.isPlatform() ? 0 : -1;
    }
}
