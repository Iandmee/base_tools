/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.LoggingUtil;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.DefaultBuildType;
import com.android.builder.model.BaseConfig;
import com.android.builder.model.ClassField;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.internal.reflect.Instantiator;

import java.io.Serializable;

/**
 * DSL object to configure build types.
 */
public class BuildType extends DefaultBuildType implements CoreBuildType, Serializable {

    private static final long serialVersionUID = 1L;

    @NonNull
    private final Project project;

    @NonNull
    private final Logger logger;

    @Nullable
    private final NdkOptions ndkConfig;

    @NonNull
    private final JackOptions jackOptions;

    /** Opt-in for now until we've validated it in the field. */
    private boolean shrinkResources = false;

    /** Opt-in for now until we've validated the new shrinker in the field. */
    private boolean useProguard = true;

    public BuildType(@NonNull String name,
                     @NonNull Project project,
                     @NonNull Instantiator instantiator,
                     @NonNull Logger logger) {
        super(name);
        this.project = project;
        this.logger = logger;
        jackOptions = instantiator.newInstance(JackOptions.class);
        ndkConfig = instantiator.newInstance(NdkOptions.class);
    }

    @VisibleForTesting
    BuildType(@NonNull String name,
              @NonNull Project project,
              @NonNull Logger logger) {
        super(name);
        this.project = project;
        this.logger = logger;
        jackOptions = new JackOptions();
        ndkConfig = null;
    }

    @Override
    @Nullable
    public CoreNdkOptions getNdkConfig() {
        return ndkConfig;
    }

    @Override
    @NonNull
    public JackOptions getJackOptions() {
        return jackOptions;
    }

    /**
     * Initialize the DSL object. Not meant to be used from the build scripts.
     */
    public void init(SigningConfig debugSigningConfig) {
        if (BuilderConstants.DEBUG.equals(getName())) {
            setDebuggable(true);
            setEmbedMicroApp(false);

            assert debugSigningConfig != null;
            setSigningConfig(debugSigningConfig);
        } else if (BuilderConstants.RELEASE.equals(getName())) {
            // no config needed for now.
        }
    }

    /** The signing configuration. */
    @Override
    @Nullable
    public SigningConfig getSigningConfig() {
        return (SigningConfig) super.getSigningConfig();
    }

    @Override
    protected void _initWith(@NonNull BaseConfig that) {
        super._initWith(that);
        BuildType thatBuildType = (BuildType) that;
        jackOptions._initWith(thatBuildType.getJackOptions());
        shrinkResources = thatBuildType.isShrinkResources();
    }

    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + getJackOptions().hashCode();
        result = 31 * result + (shrinkResources ? 1 : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BuildType)) return false;
        if (!super.equals(o)) return false;
        BuildType other = (BuildType) o;
        if (!jackOptions.equals(other.jackOptions)) return false;
        if (shrinkResources != other.isShrinkResources()) return false;

        return true;
    }

    // -- DSL Methods. TODO remove once the instantiator does what I expect it to do.

    /**
     * Adds a new field to the generated BuildConfig class.
     *
     * <p>The field is generated as: {@code <type> <name> = <value>;}
     *
     * <p>This means each of these must have valid Java content. If the type is a String, then the
     * value should include quotes.
     *
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
    public void buildConfigField(
            @NonNull String type,
            @NonNull String name,
            @NonNull String value) {
        ClassField alreadyPresent = getBuildConfigFields().get(name);
        if (alreadyPresent != null) {
            logger.info("BuildType({}): buildConfigField '{}' value is being replaced: {} -> {}",
                    getName(), name, alreadyPresent.getValue(), value);
        }
        addBuildConfigField(AndroidBuilder.createClassField(type, name, value));
    }

    /**
     * Adds a new generated resource.
     *
     * <p>This is equivalent to specifying a resource in res/values.
     *
     * <p>See <a href="http://developer.android.com/guide/topics/resources/available-resources.html">Resource Types</a>.
     *
     * @param type the type of the resource
     * @param name the name of the resource
     * @param value the value of the resource
     */
    public void resValue(
            @NonNull String type,
            @NonNull String name,
            @NonNull String value) {
        ClassField alreadyPresent = getResValues().get(name);
        if (alreadyPresent != null) {
            logger.info("BuildType({}): resValue '{}' value is being replaced: {} -> {}",
                    getName(), name, alreadyPresent.getValue(), value);
        }
        addResValue(AndroidBuilder.createClassField(type, name, value));
    }

    /**
     * Adds a new ProGuard configuration file.
     *
     * <p><code>proguardFile getDefaultProguardFile('proguard-android.txt')</code></p>
     *
     * <p>There are 2 default rules files
     * <ul>
     *     <li>proguard-android.txt
     *     <li>proguard-android-optimize.txt
     * </ul>
     * <p>They are located in the SDK. Using <code>getDefaultProguardFile(String filename)</code>
     * will return the full path to the files. They are identical except for enabling optimizations.
     */
    @NonNull
    public BuildType proguardFile(@NonNull Object proguardFile) {
        getProguardFiles().add(project.file(proguardFile));
        return this;
    }

    /**
     * Adds new ProGuard configuration files.
     *
     * <p>There are 2 default rules files
     * <ul>
     *     <li>proguard-android.txt
     *     <li>proguard-android-optimize.txt
     * </ul>
     * <p>They are located in the SDK. Using <code>getDefaultProguardFile(String filename)</code> will return the
     * full path to the files. They are identical except for enabling optimizations.
     */
    @NonNull
    public BuildType proguardFiles(@NonNull Object... proguardFiles) {
        getProguardFiles().addAll(project.files(proguardFiles).getFiles());
        return this;
    }

    /**
     * Sets the ProGuard configuration files.
     *
     * <p>There are 2 default rules files
     * <ul>
     *     <li>proguard-android.txt
     *     <li>proguard-android-optimize.txt
     * </ul>
     * <p>They are located in the SDK. Using <code>getDefaultProguardFile(String filename)</code> will return the
     * full path to the files. They are identical except for enabling optimizations.
     */
    @NonNull
    public BuildType setProguardFiles(@NonNull Iterable<?> proguardFileIterable) {
        getProguardFiles().clear();
        for (Object proguardFile : proguardFileIterable) {
            getProguardFiles().add(project.file(proguardFile));
        }
        return this;
    }

    /**
     * Adds a proguard rule file to be used when processing test code.
     *
     * <p>Test code needs to be processed to apply the same obfuscation as was done to main code.
     */
    @NonNull
    public BuildType testProguardFile(@NonNull Object proguardFile) {
        getTestProguardFiles().add(project.file(proguardFile));
        return this;
    }

    /**
     * Adds proguard rule files to be used when processing test code.
     *
     * <p>Test code needs to be processed to apply the same obfuscation as was done to main code.
     */
    @NonNull
    public BuildType testProguardFiles(@NonNull Object... proguardFiles) {
        getTestProguardFiles().addAll(project.files(proguardFiles).getFiles());
        return this;
    }

    /**
     * Specifies proguard rule files to be used when processing test code.
     *
     * <p>Test code needs to be processed to apply the same obfuscation as was done to main code.
     */
    public void setTestProguardFiles(@NonNull Iterable<?> files) {
        getTestProguardFiles().clear();
        for (Object proguardFile : files) {
            getTestProguardFiles().add(project.file(proguardFile));
        }
    }

    /**
     * Adds proguard rule files to be included in the published AAR.
     *
     * <p>This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     * <p>This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * <p>This is only valid for Library project. This is ignored in Application project.
     */
    @NonNull
    public BuildType consumerProguardFiles(@NonNull Object... proguardFiles) {
        getConsumerProguardFiles().addAll(project.files(proguardFiles).getFiles());
        return this;
    }

    /**
     * Adds a proguard rule file to be included in the published AAR.
     *
     * <p>This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     * <p>This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * <p>This is only valid for Library project. This is ignored in Application project.
     */
    public void consumerProguardFile(@NonNull Object proguardFile) {
        getConsumerProguardFiles().add(project.file(proguardFile));
    }

    /**
     * Specifies a proguard rule file to be included in the published AAR.
     *
     * This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     * This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * This is only valid for Library project. This is ignored in Application project.
     */
    @NonNull
    public BuildType setConsumerProguardFiles(@NonNull Iterable<?> proguardFileIterable) {
        getConsumerProguardFiles().clear();
        for (Object proguardFile : proguardFileIterable) {
            getConsumerProguardFiles().add(project.file(proguardFile));
        }
        return this;
    }

    public void ndk(@NonNull Action<NdkOptions> action) {
        action.execute(ndkConfig);
    }

    /**
     * Configure Jack options for this build type.
     */
    public void jackOptions(@NonNull Action<JackOptions> action) {
        action.execute(jackOptions);
    }

    /**
     * Whether the experimental Jack toolchain should be used.
     *
     * @deprecated use getJackOptions().isEnabled() instead.
     */
    @Deprecated
    @Nullable
    public Boolean getUseJack() {
        LoggingUtil.displayDeprecationWarning(
                logger, project, "useJack is deprecated.  Use jackOptions.enabled instead.");
        return jackOptions.isEnabled();
    }

    /**
     * Whether the experimental Jack toolchain should be used.
     *
     * @deprecated use jack.setEnabled instead.
     */
    @Deprecated
    public void setUseJack(@Nullable Boolean useJack) {
        LoggingUtil.displayDeprecationWarning(
                logger, project, "useJack is deprecated.  Use jackOptions.enabled instead.");
        jackOptions.setEnabled(useJack);
    }

    /**
     * Whether the experimental Jack toolchain should be used.
     *
    * @deprecated use jack.setEnabled instead.
    */
    @Deprecated
    public void useJack(@Nullable Boolean useJack) {
        LoggingUtil.displayDeprecationWarning(
                logger, project, "useJack is deprecated.  Use jackOptions.enabled instead.");
        jackOptions.setEnabled(useJack);
    }

    /**
     * Whether shrinking of unused resources is enabled.
     *
     * Default is false;
     */
    @Override
    public boolean isShrinkResources() {
        return shrinkResources;
    }

    @Override
    public boolean isUseProguard() {
        return useProguard;
    }

    public void setUseProguard(boolean useProguard) {
        this.useProguard = useProguard;
    }

    public void useProguard(boolean useProguard) {
        setUseProguard(useProguard);
    }

    public void setShrinkResources(boolean shrinkResources) {
        this.shrinkResources = shrinkResources;
    }

    /**
     * Whether shrinking of unused resources is enabled.
     *
     * Default is false;
     */
    public void shrinkResources(boolean flag) {
        this.shrinkResources = flag;
    }

    public void jarJarRuleFile(@NonNull Object file) {
        getJarJarRuleFiles().add(project.file(file));
    }

    public void jarJarRuleFiles(@NonNull Object... files) {
        getJarJarRuleFiles().clear();
        for (Object file : files) {
            getJarJarRuleFiles().add(project.file(file));
        }
    }
}
