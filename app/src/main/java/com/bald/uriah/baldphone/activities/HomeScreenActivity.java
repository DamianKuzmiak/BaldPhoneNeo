/*
 * Copyright 2019 Uriah Shaul Mandel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bald.uriah.baldphone.activities;

import static app.baldphone.neo.utils.IntentUtilsKt.startActivitySafe;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.splashscreen.SplashScreen;

import app.baldphone.neo.extensions.SurfaceWorkaroundKt;
import app.baldphone.neo.extensions.ViewExtensions;
import app.baldphone.neo.features.notifications.ui.NotificationsActivity;
import app.baldphone.neo.launcher.apps.data.AppsRepository;
import app.baldphone.neo.launcher.ui.BatteryIconView;
import app.baldphone.neo.launcher.ui.FlashlightButton;
import app.baldphone.neo.launcher.ui.NotificationsButton;
import app.baldphone.neo.launcher.ui.SoundButton;
import app.baldphone.neo.permissions.PermissionManager;
import app.baldphone.neo.ui.dialogs.BaldSnackbar;
import app.baldphone.neo.utils.HomeAppUtils;

import com.bald.uriah.baldphone.R;
import com.bald.uriah.baldphone.adapters.BaldPagerAdapter;
import com.bald.uriah.baldphone.utils.BPrefs;
import com.bald.uriah.baldphone.utils.BaldPrefsUtils;
import com.bald.uriah.baldphone.utils.BaldToast;
import com.bald.uriah.baldphone.utils.D;
import com.bald.uriah.baldphone.utils.S;
import com.bald.uriah.baldphone.views.ViewPagerHolder;
import com.bald.uriah.baldphone.views.home.NotesView;

public class HomeScreenActivity extends BaldActivity {
    private static final String TAG = HomeScreenActivity.class.getSimpleName();

    private static final int SPEECH_REQUEST_CODE = 7;

    @NonNull
    public final NotesView.RecognizerManager recognizerManager = new NotesView.RecognizerManager();

    public BaldPagerAdapter baldPagerAdapter;

    private SharedPreferences sharedPreferences;
    private BaldPrefsUtils baldPrefsUtils;
    private ViewPagerHolder viewPagerHolder;

    private final Handler handler = new Handler();

    public enum LaunchSource {
        HOME, LAUNCHER, UNKNOWN
    }

    private final Runnable surfaceCheckRunnable = () ->
        SurfaceWorkaroundKt.ensureValidSurface(this);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");

        final LaunchSource launchSource = detectLaunchSource(getIntent());
        Log.d(TAG, "launchSource: " + launchSource);

        sharedPreferences = BPrefs.get(this);
        if (!sharedPreferences.getBoolean(BPrefs.AFTER_TUTORIAL_KEY, false) && !testing) {
            startActivity(new Intent(this, TutorialActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.home_screen);
        viewPagerHolder = findViewById(R.id.view_pager_holder);

        View topBar = findViewById(R.id.top_bar);
        if (getResources().getConfiguration().orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            ViewExtensions.applyTopBarInsets(topBar);
        }
        setupTopBarButtons();

        baldPrefsUtils = BaldPrefsUtils.newInstance(this);
        viewPagerHandler();
        recognizerManager.setHomeScreen(this);

        HomeAppUtils.cleanupFakeLauncher(this);

        AppsRepository.getPinnedAppsLiveData().observe(this, pinnedApps -> {
            if (!isFinishing() && !isDestroyed()) {
                updateViewPager(false, false);
            }
        });
    }

    private void setupTopBarButtons() {
        BatteryIconView battery = findViewById(R.id.battery);
        FlashlightButton flash = findViewById(R.id.flash);
        SoundButton sound = findViewById(R.id.sound);
        NotificationsButton notifications = findViewById(R.id.notifications);

        battery.observeBatteryState(this);
        battery.setOnClickListener(v -> {
            String batteryInfo = battery.getDetailedContentDescription();
            BaldSnackbar.INSTANCE.show(this, batteryInfo, BaldSnackbar.TYPE_INFO, BaldSnackbar.LENGTH_LONG);
        });

        flash.bind(this, onGranted -> {
            requestFlashlightPermission(onGranted);
            return kotlin.Unit.INSTANCE;
        });

        sound.bind(this);

        notifications.bind(this);
        notifications.setOnClickListener(v -> {
            Intent intent = new Intent(this, NotificationsActivity.class);
            startActivitySafe(this, intent);
        });
    }

    private void requestFlashlightPermission(@NonNull Runnable onGranted) {
        PermissionManager.checkOrRequest(this, PermissionManager.CAMERA, result -> {
            if (result == PermissionManager.GRANTED) {
                onGranted.run();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(TAG, "onStart");
    }

    @Override
    protected void onResume() { // remember to change in Page1EditorActivity.java too!
        super.onResume();
        Log.v(TAG, "onResume");

        if (baldPrefsUtils.hasChanged(this)) {
            viewPagerHolder.getViewPager().removeAllViews();//android auto saves fragments, not good for us in this case
            this.recreate();
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        getWindow().getDecorView().removeCallbacks(surfaceCheckRunnable);
        getWindow().getDecorView().postDelayed(surfaceCheckRunnable, 500);
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        handler.removeCallbacksAndMessages(null);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        recognizerManager.setHomeScreen(null);
        super.onDestroy();
    }

    /**
     * Starts the view pager - being called only in {@link #onCreate(Bundle)}
     */
    private void viewPagerHandler() {
        baldPagerAdapter = new BaldPagerAdapter(this);
        viewPagerHolder.setViewPagerAdapter(baldPagerAdapter);
        viewPagerHolder.setCurrentItem(baldPagerAdapter.startingPage);
    }

    /**
     * Updates {@link HomeScreenActivity#baldPagerAdapter} apps
     * Sets the page to {@link BaldPagerAdapter#startingPage}
     */
    private void updateViewPager(boolean animate, boolean resetToHome) {
        baldPagerAdapter.obtainAppList();
        if (resetToHome)
            viewPagerHolder.getViewPager().setCurrentItem(baldPagerAdapter.startingPage, animate);
        viewPagerHolder.onDataChanged();
    }

    @Override
    public void startActivity(Intent intent, @Nullable Bundle options) {
        try {
            super.startActivity(intent, options);
        } catch (Exception e) {
            Log.e(TAG, S.str(e.getMessage()));
            e.printStackTrace();
            BaldToast.error(this);
        }
    }

    public void displaySpeechRecognizer() {
        try {
            startActivityForResult(
                    new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            ),
                    SPEECH_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, S.str(e.getMessage()));
            e.printStackTrace();
            BaldToast.error(this);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            final String spokenText = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).get(0);
            recognizerManager.onSpeechRecognizerResult(spokenText);
        }
    }

    @Override
    public void onBackPressed() {
        Log.v(TAG, "onBackPressed");
        if (vibrator != null)
            vibrator.vibrate(D.vibetime);

        if (viewPagerHolder.getViewPager().getCurrentItem() != baldPagerAdapter.startingPage) {
            viewPagerHolder.setCurrentItem(baldPagerAdapter.startingPage);
            // updateViewPager();
        } else {
            if (!HomeAppUtils.isDefaultLauncher(this)) {
                super.onBackPressed();
            }
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // update the activity's intent
        final LaunchSource launchSource = detectLaunchSource(intent);
        Log.d(TAG, "onNewIntent: launchSource=" + launchSource);
        if (launchSource == LaunchSource.HOME) {
            updateViewPager(true, true);
        }
    }

    /**
     * Detects how the app was launched based on the provided intent.
     */
    @NonNull
    private LaunchSource detectLaunchSource(@Nullable Intent intent) {
        if (intent == null) {
            return LaunchSource.UNKNOWN;
        }

        // A Home button press
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            return LaunchSource.HOME;
        }

        // Launching by an app icon
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            return LaunchSource.LAUNCHER;
        }

        return LaunchSource.UNKNOWN;
    }
}
