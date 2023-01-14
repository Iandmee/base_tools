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
package com.android.tools.lint.checks

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.detector.api.Detector

class UnsafeIntentLaunchDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = UnsafeIntentLaunchDetector()

    fun testDocumentationExampleUnparceledIntentLaunchFromExportedComponents() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.content.Intent;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                        startActivity(intent);
                    }
                }
            """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Service;

                public class TestService extends Service {
                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                        stopService(intent);
                    }
                }
            """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.content.BroadcastReceiver;
                import android.content.Context;
                import android.content.Intent;

                public class TestReceiver extends BroadcastReceiver {

                    @Override
                    public void onReceive(Context context, Intent intent) {
                        context.sendOrderedBroadcast(intent, "qwerty");
                    }
                }
                """
            ).indented(),
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="true" />
                        <service android:name=".TestService" android:exported="true" />
                        <receiver android:name=".TestReceiver" android:exported="true" />
                    </application>
                </manifest>
                """
            ).indented(),
            *stubs
        ).issues(UnsafeIntentLaunchDetector.ISSUE).run().expect(
            """
            src/test/pkg/TestActivity.java:10: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    Intent intent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestActivity.java:11: The unsafe intent is launched here.
                    startActivity(intent);
                    ~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestReceiver.java:10: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestReceiver. You could either make the component test.pkg.TestReceiver protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                public void onReceive(Context context, Intent intent) {
                                                       ~~~~~~~~~~~~~
                src/test/pkg/TestReceiver.java:11: The unsafe intent is launched here.
                    context.sendOrderedBroadcast(intent, "qwerty");
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestService.java:9: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestService. You could either make the component test.pkg.TestService protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    Intent intent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestService.java:10: The unsafe intent is launched here.
                    stopService(intent);
                    ~~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """
        )
    }

    /**
     * Activity that does not declare export attribute but with intentFilter is considered exported.
     */
    fun testUnparceledIntentLaunchFromActivityWithFilter() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = getIntent().getExtras().getParcelable(Intent.EXTRA_INTENT);
                        startActivity(intent, savedInstanceState);
                    }
                }
                """
            ).indented(),
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
            ).indented(),
            *stubs
        ).issues(UnsafeIntentLaunchDetector.ISSUE).run().expect(
            """
            src/test/pkg/TestActivity.java:10: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    Intent intent = getIntent().getExtras().getParcelable(Intent.EXTRA_INTENT);
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestActivity.java:11: The unsafe intent is launched here.
                    startActivity(intent, savedInstanceState);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    /**
     * Exported activity with unknown permission is considered protected.
     */
    fun testUnparceledIntentLaunchFromExportedActivityWithPermission() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = getIntent().getExtras().getParcelable(Intent.EXTRA_INTENT);
                        startActivity(intent);
                    }
                }
                """
            ).indented(),
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity"
                              android:exported="true"
                              android:permission="test.pkg.permission">
                        </activity>
                    </application>
                </manifest>
                """
            ).indented(),
            *stubs
        ).issues(UnsafeIntentLaunchDetector.ISSUE).run().expectClean()
    }

    /**
     * Exported activity with known system-defined permission is NOT considered protected.
     */
    fun testUnparceledIntentLaunchFromExportedActivityWithNormalPermission() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                        startActivity(intent);
                    }
                }
                """
            ).indented(),
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity"
                              android:exported="true"
                              android:permission="android.permission.AUTHENTICATE_ACCOUNTS">
                        </activity>
                    </application>
                </manifest>
                """
            ).indented(),
            *stubs
        ).issues(UnsafeIntentLaunchDetector.ISSUE).run().expect(
            """
            src/test/pkg/TestActivity.java:10: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    Intent intent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestActivity.java:11: The unsafe intent is launched here.
                    startActivity(intent);
                    ~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testUnparceledIntentLaunchFromNotExportedComponent() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                        startActivity(intent);
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Service;

                public class TestService extends Service {
                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                        stopService(intent);
                    }
                }
            """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.content.BroadcastReceiver;
                import android.content.Context;
                import android.content.Intent;

                public class TestReceiver extends BroadcastReceiver {

                    @Override
                    public void onReceive(Context context, Intent intent) {
                        context.sendOrderedBroadcast(intent, "qwerty");
                    }
                }
                """
            ).indented(),
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="false" />
                        <service android:name=".TestService" android:exported="false" />
                        <receiver android:name=".TestReceiver" android:exported="false" />
                    </application>
                </manifest>
                """
            ).indented(),
            *stubs
        ).issues(UnsafeIntentLaunchDetector.ISSUE).run().expectClean()
    }

    /**
     * Activity that does not declare export attribute and without intentFilter is considered not exported.
     */
    fun testUnparcelIntentLaunchFromActivityWithoutFilter() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = getIntent().getExtras().getParcelable(Intent.EXTRA_INTENT);
                        startActivity(intent);
                    }
                }
                """
            ).indented(),
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity">
                        </activity>
                    </application>
                </manifest>
                """
            ).indented(),
            *stubs
        ).issues(UnsafeIntentLaunchDetector.ISSUE).run().expectClean()
    }

    fun testUnparcelIntentNotLaunched() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                        // sanitize method returns a different instance. So the intent started is not the unsafe one retrieved above.
                        intent = sanitize(intent);
                        startActivity(intent);
                    }

                    Intent sanitize(Intent intent) {
                        return new Intent();
                    }
                }
            """
            ).indented(),
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="true" />
                    </application>
                </manifest>
                """
            ).indented(),
            *stubs
        ).issues(UnsafeIntentLaunchDetector.ISSUE).run().expectClean()
    }

    fun testUnparcelIntentLaunchedAfterReturnedFromAnotherMethod() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                        // The misnamed method sanitize still returns the original intent retrieved above. So it is still unsafe.
                        intent = sanitize2(intent);
                        startActivity(intent);
                    }

                    Intent sanitize(Intent intent) {
                        intent.setAction("someAction");
                        return intent;
                    }

                    Intent sanitize2(Intent intent) {
                        return sanitize(intent);
                    }
                }
            """
            ).indented(),
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="true" />
                    </application>
                </manifest>
                """
            ).indented(),
            *stubs
        ).issues(UnsafeIntentLaunchDetector.ISSUE).run().expect(
            """
            src/test/pkg/TestActivity.java:10: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    Intent intent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestActivity.java:13: The unsafe intent is launched here.
                    startActivity(intent);
                    ~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testUnparceledIntentLaunchFromActivity() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;
                import android.content.ServiceConnection;

                public class TestActivity extends Activity {
                    private ServiceConnection mServiceConnection = new ServiceConnection();

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        // test getExtras method.
                        Intent intent = getIntent().getExtras().getParcelable(Intent.EXTRA_INTENT);
                        startActivityIfNeeded(intent, 0);

                        Intent intent2 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                        Intent intent3 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                        Intent intent4 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                        Intent intent5 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                        navigateUpTo(intent2);
                        createPendingIntent(0, getIntent(), 0);
                        startActivityFromChild(null, getIntent(), 0);
                        startIntentSenderForResult(null, intent3, 0, 0, 0, 0);
                        startIntentSenderFromChild(null, intent4, 0, 0, 0, 0, 0);
                        startNextMatchingActivity(intent5);
                    }

                    @Override
                    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
                        startActivityForResult("test", data, 0, null);
                    }

                    @Override
                    protected void onNewIntent(Intent intent) {
                        sendStickyBroadcast(intent);
                    }

                    @Override
                    public void onActivityReenter(int resultCode, Intent data) {
                        setResult(0, data);
                    }
                }
            """
            ).indented(),
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="true" />
                    </application>
                </manifest>
                """
            ).indented(),
            *stubs
        ).issues(UnsafeIntentLaunchDetector.ISSUE).run().expect(
            """
            src/test/pkg/TestActivity.java:13: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    Intent intent = getIntent().getExtras().getParcelable(Intent.EXTRA_INTENT);
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestActivity.java:14: The unsafe intent is launched here.
                    startActivityIfNeeded(intent, 0);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestActivity.java:16: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    Intent intent2 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestActivity.java:20: The unsafe intent is launched here.
                    navigateUpTo(intent2);
                    ~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestActivity.java:17: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    Intent intent3 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestActivity.java:23: The unsafe intent is launched here.
                    startIntentSenderForResult(null, intent3, 0, 0, 0, 0);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestActivity.java:18: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    Intent intent4 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestActivity.java:24: The unsafe intent is launched here.
                    startIntentSenderFromChild(null, intent4, 0, 0, 0, 0, 0);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestActivity.java:19: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    Intent intent5 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestActivity.java:25: The unsafe intent is launched here.
                    startNextMatchingActivity(intent5);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestActivity.java:22: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    startActivityFromChild(null, getIntent(), 0);
                                                 ~~~~~~~~~~~
                src/test/pkg/TestActivity.java:22: The unsafe intent is launched here.
                    startActivityFromChild(null, getIntent(), 0);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestActivity.java:29: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                protected void onActivityResult(int requestCode, int resultCode, Intent data) {
                                                                                 ~~~~~~~~~~~
                src/test/pkg/TestActivity.java:30: The unsafe intent is launched here.
                    startActivityForResult("test", data, 0, null);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestActivity.java:34: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                protected void onNewIntent(Intent intent) {
                                           ~~~~~~~~~~~~~
                src/test/pkg/TestActivity.java:35: The unsafe intent is launched here.
                    sendStickyBroadcast(intent);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestActivity.java:39: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                public void onActivityReenter(int resultCode, Intent data) {
                                                              ~~~~~~~~~~~
                src/test/pkg/TestActivity.java:40: The unsafe intent is launched here.
                    setResult(0, data);
                    ~~~~~~~~~~~~~~~~~~
            0 errors, 9 warnings
            """
        )
    }

    fun testUnparceledIntentLaunchFromService() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.content.ServiceConnection;
                import android.app.PendingIntent;
                import android.app.Service;

                public class TestService extends Service {
                    private ServiceConnection mServiceConnection = new ServiceConnection();

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        Intent intent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                        Intent intent2 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                        Intent intent3 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                        Intent intent4 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                        Intent intent5 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                        bindService(intent, mServiceConnection, 0);
                        PendingIntent.getActivity(this, 0, intent2, 0);
                        PendingIntent.getBroadcast(this, 0, intent3, 0);
                        PendingIntent.getForegroundService(this, 0, intent4, 0);
                        PendingIntent.getService(this, 0, intent5, 0);
                    }

                    @Override
                    public abstract IBinder onBind(Intent intent){
                        sendBroadcast(intent);
                    }

                    @Override
                    public boolean onUnbind(Intent intent) {
                        startService(intent);
                        return false;
                    }

                    @Override
                    public void onRebind(Intent intent) {
                        startForegroundService(intent);
                    }

                    @Override
                    public void onStart(Intent intent, int startId) {
                        startIntentSender(null, intent, startId, 0, 0);
                    }

                    @Override
                    public int onStartCommand(Intent intent, int flags, int startId) {
                        removeStickyBroadcast(intent);
                    }
                }
            """
            ).indented(),
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <service android:name=".TestService" android:exported="true" />
                    </application>
                </manifest>
                """
            ).indented(),
            *stubs
        ).issues(UnsafeIntentLaunchDetector.ISSUE).run().expect(
            """
            src/test/pkg/TestService.java:13: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestService. You could either make the component test.pkg.TestService protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    Intent intent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestService.java:18: The unsafe intent is launched here.
                    bindService(intent, mServiceConnection, 0);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestService.java:14: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestService. You could either make the component test.pkg.TestService protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    Intent intent2 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestService.java:19: The unsafe intent is launched here.
                    PendingIntent.getActivity(this, 0, intent2, 0);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestService.java:15: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestService. You could either make the component test.pkg.TestService protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    Intent intent3 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestService.java:20: The unsafe intent is launched here.
                    PendingIntent.getBroadcast(this, 0, intent3, 0);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestService.java:16: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestService. You could either make the component test.pkg.TestService protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    Intent intent4 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestService.java:21: The unsafe intent is launched here.
                    PendingIntent.getForegroundService(this, 0, intent4, 0);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestService.java:17: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestService. You could either make the component test.pkg.TestService protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    Intent intent5 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestService.java:22: The unsafe intent is launched here.
                    PendingIntent.getService(this, 0, intent5, 0);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestService.java:26: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestService. You could either make the component test.pkg.TestService protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                public abstract IBinder onBind(Intent intent){
                                               ~~~~~~~~~~~~~
                src/test/pkg/TestService.java:27: The unsafe intent is launched here.
                    sendBroadcast(intent);
                    ~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestService.java:31: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestService. You could either make the component test.pkg.TestService protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                public boolean onUnbind(Intent intent) {
                                        ~~~~~~~~~~~~~
                src/test/pkg/TestService.java:32: The unsafe intent is launched here.
                    startService(intent);
                    ~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestService.java:37: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestService. You could either make the component test.pkg.TestService protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                public void onRebind(Intent intent) {
                                     ~~~~~~~~~~~~~
                src/test/pkg/TestService.java:38: The unsafe intent is launched here.
                    startForegroundService(intent);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestService.java:42: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestService. You could either make the component test.pkg.TestService protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                public void onStart(Intent intent, int startId) {
                                    ~~~~~~~~~~~~~
                src/test/pkg/TestService.java:43: The unsafe intent is launched here.
                    startIntentSender(null, intent, startId, 0, 0);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestService.java:47: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestService. You could either make the component test.pkg.TestService protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                public int onStartCommand(Intent intent, int flags, int startId) {
                                          ~~~~~~~~~~~~~
                src/test/pkg/TestService.java:48: The unsafe intent is launched here.
                    removeStickyBroadcast(intent);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 10 warnings
            """
        )
    }

    fun testUnparceledIntentLaunchInAnotherMethod() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        Intent intent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                        // startActivity called in a utility method defined in another class
                        IntentUtils.startActivity(this, intent);
                        Intent intent2 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                        // startActivity called in another method defined in the same class
                        startActivityInternal(intent2);
                    }

                    @Override
                    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
                        // startActivity called in a utility method defined in another class 2 levels down in the call hierarchy
                        IntentUtils.startActivityDelegate(this, data);
                    }

                    private void startActivityInternal(Intent intent) {
                        startActivity(intent);
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.app.Activity;

                public class IntentUtils {

                    public static void startActivityDelegate(Activity context, Intent intent) {
                        startActivity(context, intent);
                    }

                    public static void startActivity(Activity context, Intent intent) {
                        context.startActivity(intent);
                    }
                }
                """
            ).indented(),
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="true" />
                    </application>
                </manifest>
                """
            ).indented(),
            *stubs
        ).issues(UnsafeIntentLaunchDetector.ISSUE)/*.testModes(TestMode.DEFAULT)*/.run().expect(
            """
            src/test/pkg/TestActivity.java:11: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    Intent intent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/IntentUtils.java:13: The unsafe intent is launched here.
                    context.startActivity(intent);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestActivity.java:14: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    Intent intent2 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestActivity.java:26: The unsafe intent is launched here.
                    startActivity(intent);
                    ~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestActivity.java:20: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                protected void onActivityResult(int requestCode, int resultCode, Intent data) {
                                                                                 ~~~~~~~~~~~
                src/test/pkg/IntentUtils.java:13: The unsafe intent is launched here.
                    context.startActivity(intent);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """
        )
    }

    fun testRuntimeExportedBroadcastReceiver() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.content.BroadcastReceiver;
                import android.content.Context;
                import android.content.Intent;
                import android.content.IntentFilter;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.activity_main);
                        IntentFilter filter = new IntentFilter("qwerty");
                        TestReceiver receiver = new TestReceiver();
                        registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.content.BroadcastReceiver;
                import android.content.Context;
                import android.content.Intent;

                public class TestReceiver extends BroadcastReceiver {

                    @Override
                    public void onReceive(Context context, Intent intent) {
                        peekService(context, intent);
                    }

                }
                """
            ).indented(),
            *stubs
        ).issues(UnsafeIntentLaunchDetector.ISSUE).run().expect(
            """
            src/test/pkg/TestReceiver.java:10: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestReceiver. You could either make the component test.pkg.TestReceiver protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                public void onReceive(Context context, Intent intent) {
                                                       ~~~~~~~~~~~~~
                src/test/pkg/TestReceiver.java:11: The unsafe intent is launched here.
                    peekService(context, intent);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testRuntimeNotExportedBroadcastReceiver() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.content.BroadcastReceiver;
                import android.content.Context;
                import android.content.Intent;
                import android.content.IntentFilter;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.activity_main);
                        IntentFilter filter = new IntentFilter("qwerty");
                        TestReceiver receiver = new TestReceiver();
                        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.content.BroadcastReceiver;
                import android.content.Context;
                import android.content.Intent;

                public class TestReceiver extends BroadcastReceiver {

                    @Override
                    public void onReceive(Context context, Intent intent) {
                        peekService(context, intent);
                    }

                }
                """
            ).indented(),
            *stubs
        ).issues(UnsafeIntentLaunchDetector.ISSUE).run().expectClean()
    }

    fun testRuntimeBroadcastReceiverRegisteredWithNoFlag() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.content.BroadcastReceiver;
                import android.content.Context;
                import android.content.Intent;
                import android.content.IntentFilter;

                public class TestActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.activity_main);
                        IntentFilter filter = new IntentFilter("qwerty");
                        TestReceiver receiver = new TestReceiver();
                        registerReceiver(receiver, filter);
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.content.BroadcastReceiver;
                import android.content.Context;
                import android.content.Intent;

                public class TestReceiver extends BroadcastReceiver {

                    @Override
                    public void onReceive(Context context, Intent intent) {
                        peekService(context, intent);
                    }

                }
                """
            ).indented(),
            *stubs
        ).issues(UnsafeIntentLaunchDetector.ISSUE).run().expect(
            """
            src/test/pkg/TestReceiver.java:10: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestReceiver. You could either make the component test.pkg.TestReceiver protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                public void onReceive(Context context, Intent intent) {
                                                       ~~~~~~~~~~~~~
                src/test/pkg/TestReceiver.java:11: The unsafe intent is launched here.
                    peekService(context, intent);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testAnonymousBroadcastReceiver() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.BroadcastReceiver;
                import android.content.Context;
                import android.content.Intent;
                import android.app.Activity;

                public class AnonymousBroadcastReceiverTest {
                    private Activity activity;

                    public void onCreate(@NonNull LifecycleOwner lifecycleOwner) {
                        BroadcastReceiver receiver = new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                final int statusCode = intent.getIntExtra("test", 0);
                                if (statusCode == 1) {
                                    context.startActivity(checkNotNull(intent.getParcelableExtra(Intent.EXTRA_INTENT)));
                                } else if (statusCode == 0) {
                                    log("onReceive: Installation successful");
                                }
                            }
                        };

                        activity.registerReceiver(receiver, new IntentFilter("INTENT_ACTION_INSTALL_COMMIT"));
                    }

                    public static <T> T checkNotNull(T reference) {
                        if (reference == null) {
                              throw new NullPointerException();
                        }
                        return reference;
                    }
                }
                """
            ).indented(),
            *stubs
        ).issues(UnsafeIntentLaunchDetector.ISSUE).run().expect(
            """
            src/test/pkg/AnonymousBroadcastReceiverTest.java:17: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component. You could either make the component protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                                context.startActivity(checkNotNull(intent.getParcelableExtra(Intent.EXTRA_INTENT)));
                                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/AnonymousBroadcastReceiverTest.java:17: The unsafe intent is launched here.
                                context.startActivity(checkNotNull(intent.getParcelableExtra(Intent.EXTRA_INTENT)));
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testKotlin() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.Intent
                import android.app.Activity

                class TestActivity: Activity {

                    @Override
                    override fun onCreate(savedInstanceState: Bundle) {
                        val intent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT).let { // ERROR 1
                            sanitize(it)
                        }
                        startActivity(intent)

                        val intent2 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT).let { // OK
                            sanitize2(it)
                        }
                        startActivity(intent2)

                        getIntent().getParcelableExtra(Intent.EXTRA_INTENT).let { // ERROR 2
                            startActivity(sanitize(it))
                        }

                        getIntent().getParcelableExtra(Intent.EXTRA_INTENT).let { // OK
                            startActivity(sanitize2(it))
                        }

                        val intent3 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT).run { // ERROR 3
                            sanitize(this)
                        }
                        startActivity(intent3)

                        val intent4 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT).run { // OK
                            sanitize2(this)
                        }
                        startActivity(intent4)

                        getIntent().getParcelableExtra(Intent.EXTRA_INTENT).run { // ERROR 4
                            startActivity(sanitize(this))
                        }

                        getIntent().getParcelableExtra(Intent.EXTRA_INTENT).run { // OK
                            startActivity(sanitize2(this))
                        }
                    }

                    // fake sanitize
                    private fun sanitize(intent: Intent): Intent {
                        intent.setAction("someAction")
                        return intent
                    }

                    // real sanitize
                    private fun sanitize2(intent: Intent): Intent {
                        return Intent()
                    }
                }
            """
            ).indented(),
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                    <application>
                        <activity android:name=".TestActivity" android:exported="true" />
                    </application>
                </manifest>
                """
            ).indented(),
            *stubs
        ).issues(UnsafeIntentLaunchDetector.ISSUE).run().expect(
            """
            src/test/pkg/TestActivity.kt:10: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    val intent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT).let { // ERROR 1
                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestActivity.kt:13: The unsafe intent is launched here.
                    startActivity(intent)
                    ~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestActivity.kt:20: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    getIntent().getParcelableExtra(Intent.EXTRA_INTENT).let { // ERROR 2
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestActivity.kt:21: The unsafe intent is launched here.
                        startActivity(sanitize(it))
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestActivity.kt:28: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    val intent3 = getIntent().getParcelableExtra(Intent.EXTRA_INTENT).run { // ERROR 3
                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestActivity.kt:31: The unsafe intent is launched here.
                    startActivity(intent3)
                    ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestActivity.kt:38: Warning: This intent could be coming from an untrusted source. It is later launched by an unprotected component test.pkg.TestActivity. You could either make the component test.pkg.TestActivity protected; or sanitize this intent using androidx.core.content.IntentSanitizer. [UnsafeIntentLaunch]
                    getIntent().getParcelableExtra(Intent.EXTRA_INTENT).run { // ERROR 4
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/TestActivity.kt:39: The unsafe intent is launched here.
                        startActivity(sanitize(this))
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 4 warnings
            """
        )
    }

    private val intentStub: TestFile = java(
        """
        package android.content;
        import android.os.Parcelable;
        import android.os.Bundle;

        public class Intent implements Parcelable {
            public static final String EXTRA_INTENT = "extra_intent";
            public static final String ACTION_BATTERY_CHANGED = "android.intent.action.BATTERY_CHANGED";
            public static final String ACTION_BATTERY_LOW = "android.intent.action.BATTERY_LOW";
            public static final String ACTION_BATTERY_OKAY = "android.intent.action.BATTERY_OKAY";
            public Intent getParcelableExtra(String key) { return this;}
            public Bundle getExtras() { return new Bundle(); }
            public Intent setAction(String action) { return this; }
        }
        """
    ).indented()

    private val contextStub: TestFile = java(
        """
        package android.content;
        public class Context {
            public static final int RECEIVER_EXPORTED = 0x2;
            public static final int RECEIVER_NOT_EXPORTED = 0x4;
            public Intent getIntent() { return new Intent();}

            public abstract boolean bindService(@RequiresPermission Intent service,
                    @NonNull ServiceConnection conn, @BindServiceFlags int flags);
            public abstract boolean stopService(Intent service);

            public abstract void sendBroadcast(@RequiresPermission Intent intent);
            public abstract void sendBroadcast(@RequiresPermission Intent intent,
                    @Nullable String receiverPermission);
            public abstract void sendOrderedBroadcast(@RequiresPermission Intent intent, @Nullable String receiverPermission);
            public abstract void sendStickyBroadcast(@RequiresPermission Intent intent);
            public abstract void removeStickyBroadcast(@RequiresPermission Intent intent);

            public abstract Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter);
            public abstract Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags);

            public abstract void startActivity(@RequiresPermission Intent intent);
            public void startActivityForResult(
                @NonNull String who, Intent intent, int requestCode, @Nullable Bundle options) {}
            public abstract ComponentName startService(Intent service);
            public abstract ComponentName startForegroundService(Intent service);
            public abstract void startIntentSender(IntentSender intent, @Nullable Intent fillInIntent,
                        int flagsMask, int flagsValues,  int extraFlags);
        }
        """
    ).indented()

    private val activityStub: TestFile = java(
        """
        package android.app;
        import android.content.Intent;
        import android.content.Context;

        public class Activity extends Context {
            protected void onActivityResult(int requestCode, int resultCode, Intent data) {}
            public boolean navigateUpTo(Intent upIntent) {}
            public boolean navigateUpToFromChild(Activity child, Intent upIntent) {}
            public PendingIntent createPendingResult(int requestCode, Intent data, int flags) {}
            public void startActivityForResult(Intent intent, int requestCode) {}
            public void startActivityFromChild(Activity child, Intent intent, int requestCode) {}
            public void startActivityFromFragment(Fragment fragment, Intent intent, int requestCode) {}
            public boolean startActivityIfNeeded(Intent intent, int requestCode) {}
            public void startIntentSender(IntentSender intent, Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags) {}
            public void startIntentSenderForResult(IntentSender intent, int requestCode,
                    Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags) {}
            public void startIntentSenderFromChild(Activity child, IntentSender intent, int requestCode,
                    Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags) {}
            public boolean startNextMatchingActivity(@RequiresPermission @NonNull Intent intent) {}
            public final void setResult(int resultCode, Intent data) {}
        }
        """
    ).indented()

    private val bundleStub: TestFile = java(
        """
        package android.os;
        import android.content.Intent;

        public class Bundle {
            public Intent getParcelable(String key) { return null; }
        }
        """
    ).indented()

    private val parcelableStub: TestFile = java(
        """
        package android.os;

        public interface Parcelable {}
        """
    ).indented()

    private val broadcastReceiverStub: TestFile = java(
        """
            package android.content;
            import android.os.IBinder;
            public abstract class BroadcastReceiver {
                public abstract void onReceive(Context context, Intent intent);
                public IBinder peekService(Context myContext, Intent service) { return null;}
            }
            """
    ).indented()

    private val intentFilterStub: TestFile = java(
        """
            package android.content;
            public class IntentFilter {
                public IntentFilter() {}
                public IntentFilter(String action) {}
                public IntentFilter(String action, String dataType) {}
                public static IntentFilter create(String action, String dataType) {
                    return null;
                }
                public final void addAction(String action) {}
            }
            """
    ).indented()

    private val intentSenderStub: TestFile = java(
        """
        package android.content;
        public class IntentSender {}
        """
    )

    private val componentNameStub: TestFile = java(
        """
        package android.content;
        public class ComponentName {}
        """
    )

    private val serviceConnectionStub: TestFile = java(
        """
        package android.content;
        public class ServiceConnection {}
        """
    )

    private val serviceStub: TestFile = java(
        """
        package android.app;
        import android.os.IBinder;
        import android.content.Intent;
        import android.content.Context;

        public class Service extends Context {
            public void onCreate() {}
            public abstract IBinder onBind(Intent intent);
            public boolean onUnbind(Intent intent) { return false; }
            public void onRebind(Intent intent) {}
            public void onStart(Intent intent, int startId) {}
            public int onStartCommand(Intent intent, int flags, int startId) {}
        }
        """
    )

    private val iBinderStub: TestFile = java(
        """
        package android.os;
        public interface IBinder{}
        """
    )

    private val pendingIntentStub: TestFile = java(
        """
        package android.app;
        import android.content.Context;
        import android.content.Intent;

        public class PendingIntent {
            public static PendingIntent getActivity(Context context, int requestCode, Intent intent, int flags) { return null; }
            public static PendingIntent getBroadcast(Context context, int requestCode, Intent intent, int flags) { return null; }
            public static PendingIntent getForegroundService(Context context, int requestCode, Intent intent, int flags) { return null; }
            public static PendingIntent getService(Context context, int requestCode, Intent intent, int flags) { return null; }
        }
        """
    )

    private val stubs = arrayOf(
        intentStub, activityStub, bundleStub, parcelableStub, broadcastReceiverStub, contextStub, intentFilterStub,
        intentSenderStub, componentNameStub, serviceConnectionStub, serviceStub, iBinderStub, pendingIntentStub
    )
}