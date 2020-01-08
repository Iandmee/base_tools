/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.build.gradle.internal.api.dsl.DslScope;
import com.android.builder.model.TestOptions.Execution;
import com.android.utils.HelpfulEnumConverter;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import groovy.lang.Closure;
import javax.inject.Inject;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.tasks.testing.Test;
import org.gradle.util.ConfigureUtil;

/** Options for running tests. */
@SuppressWarnings("unused") // Exposed in the DSL.
public class TestOptions
        implements com.android.build.api.dsl.TestOptions<TestOptions.UnitTestOptions> {
    private static final HelpfulEnumConverter<Execution> EXECUTION_CONVERTER =
            new HelpfulEnumConverter<>(Execution.class);

    @Nullable private String resultsDir;

    @Nullable private String reportDir;

    private boolean animationsDisabled;

    @NonNull private Execution execution = Execution.HOST;

    /**
     * Options for controlling unit tests execution.
     *
     * @since 1.1.0
     */
    @NonNull private final UnitTestOptions unitTests;

    @Inject
    public TestOptions(DslScope dslScope) {
        this.unitTests = dslScope.getObjectFactory().newInstance(UnitTestOptions.class, dslScope);
    }

    public void unitTests(Action<UnitTestOptions> action) {
        action.execute(unitTests);
    }

    @Override
    public void unitTests(Function1<? super UnitTestOptions, Unit> action) {
        action.invoke(unitTests);
    }

    /**
     * Configures unit test options.
     *
     * @since 1.2.0
     */
    public void unitTests(Closure closure) {
        ConfigureUtil.configure(closure, unitTests);
    }

    /**
     * Configures unit test options.
     *
     * @since 1.2.0
     */
    @Override
    @NonNull
    public UnitTestOptions getUnitTests() {
        return unitTests;
    }

    /** Name of the results directory. */
    @Nullable
    public String getResultsDir() {
        return resultsDir;
    }

    public void setResultsDir(@Nullable String resultsDir) {
        this.resultsDir = resultsDir;
    }

    /** Name of the reports directory. */
    @Nullable
    public String getReportDir() {
        return reportDir;
    }

    public void setReportDir(@Nullable String reportDir) {
        this.reportDir = reportDir;
    }

    /**
     * Disables animations during instrumented tests you run from the cammand line.
     *
     * <p>If you set this property to {@code true}, running instrumented tests with Gradle from the
     * command line executes {@code am instrument} with the {@code --no-window-animation} flag.
     * By default, this property is set to {@code false}.</p>
     *
     * <p>This property does not affect tests that you run using Android Studio. To learn more about
     * running tests from the command line, see
     * <a href="https://d.android.com/studio/test/command-line.html">Test from the Command Line</a>.
     * </p>
     */
    public boolean getAnimationsDisabled() {
        return animationsDisabled;
    }

    public void setAnimationsDisabled(boolean animationsDisabled) {
        this.animationsDisabled = animationsDisabled;
    }

    /**
     * Specifies whether to use on-device test orchestration.
     *
     * <p>If you want to <a
     * href="https://developer.android.com/training/testing/junit-runner.html#using-android-test-orchestrator">use
     * Android Test Orchestrator</a>, you need to specify <code>"ANDROID_TEST_ORCHESTRATOR"</code>,
     * as shown below. By default, this property is set to <code>"HOST"</code>, which disables
     * on-device orchestration.
     *
     * <pre>
     * android {
     *   testOptions {
     *     execution 'ANDROID_TEST_ORCHESTRATOR'
     *   }
     * }
     * </pre>
     *
     * @since 3.0.0
     */
    @NonNull
    public String getExecution() {
        return Verify.verifyNotNull(
                EXECUTION_CONVERTER.reverse().convert(execution),
                "No string representation for enum.");
    }

    @NonNull
    public Execution getExecutionEnum() {
        return execution;
    }

    public void setExecution(@NonNull String execution) {
        this.execution =
                Preconditions.checkNotNull(
                        EXECUTION_CONVERTER.convert(execution),
                        "The value of `execution` cannot be null.");
    }

    /** Options for controlling unit tests execution. */
    public static class UnitTestOptions implements com.android.build.api.dsl.UnitTestOptions {
        // Used by testTasks.all below, DSL docs generator can't handle diamond operator.
        private final DomainObjectSet<Test> testTasks;

        private boolean returnDefaultValues;
        private boolean includeAndroidResources;

        @Inject
        public UnitTestOptions(@NonNull DslScope dslScope) {
            testTasks = dslScope.getObjectFactory().domainObjectSet(Test.class);
        }

        public boolean isReturnDefaultValues() {
            return getReturnDefaultValues();
        }

        @Override
        public boolean getReturnDefaultValues() {
            return returnDefaultValues;
        }

        @Override
        public void setReturnDefaultValues(boolean returnDefaultValues) {
            this.returnDefaultValues = returnDefaultValues;
        }

        public boolean isIncludeAndroidResources() {
            return getIncludeAndroidResources();
        }

        @Override
        public boolean getIncludeAndroidResources() {
            return includeAndroidResources;
        }

        @Override
        public void setIncludeAndroidResources(boolean includeAndroidResources) {
            this.includeAndroidResources = includeAndroidResources;
        }

        public void all(final Closure<Test> configClosure) {
            testTasks.all(testTask -> ConfigureUtil.configure(configClosure, testTask));
        }

        @Override
        public void all(@NonNull Function1<? super Test, Unit> configAction) {
            testTasks.all(configAction::invoke);
        }

        /**
         * Configures a given test task. The configuration closures that were passed to {@link
         * #all(Closure)} will be applied to it.
         *
         * <p>Not meant to be called from build scripts. The reason it exists is that tasks are
         * created after the build scripts are evaluated, so users have to "register" their
         * configuration closures first and we can only apply them later.
         *
         * @since 1.2.0
         */
        public void applyConfiguration(@NonNull Test task) {
            this.testTasks.add(task);
        }
    }
}
