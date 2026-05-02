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

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.splashscreen.SplashScreen;

import app.baldphone.neo.battery.BatteryRepository;
import app.baldphone.neo.battery.ui.BatteryIconView;
import app.baldphone.neo.extensions.ViewExtensions;
import app.baldphone.neo.extensions.SurfaceWorkaroundKt;
import app.baldphone.neo.features.notifications.data.NotificationRepository;
import app.baldphone.neo.features.notifications.ui.NotificationsActivity;
import app.baldphone.neo.flashlight.FlashLightController;
import app.baldphone.neo.flashlight.FlashlightState;
import app.baldphone.neo.permissions.PermissionManager;
import app.baldphone.neo.permissions.PermissionRepository;
import app.baldphone.neo.ui.dialogs.BaldSnackbar;
import app.baldphone.neo.utils.HomeAppUtils;
import app.baldphone.neo.wizard.SetupActivity;

import com.bald.uriah.baldphone.R;
import com.bald.uriah.baldphone.adapters.BaldPagerAdapter;
import com.bald.uriah.baldphone.databases.apps.AppsDatabaseHelper;
import com.bald.uriah.baldphone.utils.BPrefs;
import com.bald.uriah.baldphone.utils.BaldPrefsUtils;
import com.bald.uriah.baldphone.utils.BaldToast;
import com.bald.uriah.baldphone.utils.D;
import com.bald.uriah.baldphone.utils.DropDownRecyclerViewAdapter;
import com.bald.uriah.baldphone.utils.S;
import com.bald.uriah.baldphone.views.BaldImageButton;
import com.bald.uriah.baldphone.views.ViewPagerHolder;
import com.bald.uriah.baldphone.views.home.NotesView;

import java.lang.ref.WeakReference;

public class HomeScreenActivity extends BaldActivity {
    private static final String TAG = HomeScreenActivity.class.getSimpleName();

    public static final int NOTIFICATIONS_ALOT = 5;

    private static final int[]
            SOUND_DRAWABLES = {R.drawable.mute_on_background, R.drawable.vibration_on_background, R.drawable.sound_on_background},
            SOUND_TEXTS = {R.string.mute, R.string.vibrate, R.string.sound};

    private static final int SPEECH_REQUEST_CODE = 7;

    @NonNull
    public final NotesView.RecognizerManager recognizerManager = new NotesView.RecognizerManager();

    public boolean finishedUpdatingApps, launchAppsActivity;
    public BaldPagerAdapter baldPagerAdapter;

    private SharedPreferences sharedPreferences;
    private BaldPrefsUtils baldPrefsUtils;
    private ViewPagerHolder viewPagerHolder;
    private BatteryIconView batteryIconView;

    @Nullable
    private FlashLightController flashlight = null;

    private int notificationCount = 0;
    @ColorInt
    private int decorationColorOnBackground;
    private BaldImageButton notificationsButton, soundButton, flashButton;
    private AudioManager audioManager;

    private final Handler handler = new Handler();

    private NotificationRepository repo;
    private boolean permissionBannerDismissed = false;
    private boolean isResumed = false;
    private boolean isFirstResume = true;

    private final Runnable showPermissionBannerRunnable = () -> {
        final View permissionBanner = findViewById(R.id.permission_banner);
        if (permissionBanner == null) return;
        boolean hasPermission = PermissionRepository.INSTANCE.isMandatoryGranted(this);
        if (hasPermission) {
            permissionBanner.setVisibility(View.GONE);
        } else {
            if (!permissionBannerDismissed) {
                permissionBanner.setVisibility(View.VISIBLE);
            }
        }
    };

    public enum LaunchSource {
        HOME, LAUNCHER, UNKNOWN
    }

    /**
     * "Shakes" the notifications icon when it has more than {@value NOTIFICATIONS_ALOT}
     */
    private final Runnable shakeIt = new Runnable() {
        @Override
        public void run() {
            final Drawable d = notificationsButton.getDrawable();
            if (d instanceof AnimatedVectorDrawable animatedVectorDrawable) {
                animatedVectorDrawable.start();
                final int minusSeconds = Math.min((int) (Math.max((notificationCount - NOTIFICATIONS_ALOT) * 0.5f, 0)), 7);
                handler.postDelayed(this, (long) (10 - minusSeconds) * D.SECOND);
            }
        }
    };

    private void handleNotificationCount(int count) {
        Log.d(TAG, "Notification count: " + count);
        notificationCount = count;
        if (count >= NOTIFICATIONS_ALOT) {
            final Drawable drawable = AppCompatResources.getDrawable(this, R.drawable.notification_alot_on_background);
            final float opacity = Math.min(((count - NOTIFICATIONS_ALOT) / 10.0f), 1.0f);
            drawable.setTint(S.blendColors(decorationColorOnBackground, getResources().getColor(R.color.battery_low), 1 - opacity));
            notificationsButton.setImageDrawable(drawable);
        } else if (count >= 1) {
            notificationsButton.setImageResource(R.drawable.notification_some_on_background);
        } else if (count == 0) {
            notificationsButton.setImageResource(R.drawable.notification_none_on_background);
        } else {
            notificationsButton.setImageResource(R.drawable.error_on_background);
        }

        handler.removeCallbacks(shakeIt);
        handler.postDelayed(shakeIt, 5 * D.SECOND);
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

        if (Prefs.isPendingDefaultLauncherChoice()) {
            Log.v(TAG, "Starting setup with pending default launcher choice");
            Prefs.setPendingDefaultLauncherChoice(false);
            if (HomeAppUtils.isDefaultLauncher(this)) {
                startActivity(new Intent(this, SetupActivity.class));
                finish();
                return;
            }
        }

        if (!Prefs.isSetupComplete() && !Prefs.isSetupSkipped()) {
            if (launchSource == LaunchSource.HOME) {
                // Setup is incomplete but the Home button was pressed, so skip setup.
                Prefs.setSetupSkipped(true);
            } else {
                Log.d(TAG, "Starting setup");
                startActivity(new Intent(this, SetupActivity.class));
                finish();
                return;
            }
        }

        sharedPreferences = BPrefs.get(this);

        new UpdateApps(this).execute(this.getApplicationContext());

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        final TypedValue typedValue = new TypedValue();
        final Resources.Theme theme = getTheme();
        theme.resolveAttribute(R.attr.bald_decoration_on_background, typedValue, true);
        decorationColorOnBackground = typedValue.data;

        setContentView(R.layout.home_screen);
        viewPagerHolder = findViewById(R.id.view_pager_holder);

        final ViewGroup top_bar = findViewById(R.id.top_bar);
        if (getResources().getConfiguration().orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            ViewExtensions.applyTopBarInsets(top_bar);
        }

        soundButton = top_bar.findViewById(R.id.sound);
        batteryIconView = top_bar.findViewById(R.id.battery);
        notificationsButton = top_bar.findViewById(R.id.notifications);
        flashButton = top_bar.findViewById(R.id.flash);

        if (!FlashLightController.Companion.isFlashHardwarePresent(this)) {
            Log.i(TAG, "No flash hardware on this device");
            flashButton.setVisibility(View.GONE);
        } else {
            flashlight = FlashLightController.Companion.getInstance(this);
            flashButton.setOnClickListener(v -> onFlashlightButtonPressed());
            flashlight.getStateLiveData().observe(this, this::handleFlashlightEvent);
        }

        notificationsButton.setOnClickListener((v) -> {
            startActivity(new Intent(this, NotificationsActivity.class));
//            overridePendingTransition(R.anim.slide_in_down, R.anim.nothing);
        });
        soundButton.setOnClickListener(v -> S.showDropDownPopup(this, getWindow().getDecorView().getWidth(), new DropDownRecyclerViewAdapter.DropDownListener() {
            @SuppressLint("InlinedApi")
            @Override
            public void onUpdate(DropDownRecyclerViewAdapter.ViewHolder viewHolder, final int position, PopupWindow popupWindow) {
                viewHolder.pic.setImageResource(SOUND_DRAWABLES[position]);
                viewHolder.text.setText(SOUND_TEXTS[position]);
                viewHolder.itemView.setOnClickListener(v1 -> {
                    try {
                        audioManager.setRingerMode(position);
                        soundButton.setImageResource(SOUND_DRAWABLES[position]);
                    } catch (SecurityException e) {
                        startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                    }
                    popupWindow.dismiss();
                });
            }

            @Override
            public int size() {
                return 3;
            }
        }, soundButton));

        BatteryRepository batteryRepository = BatteryRepository.get(this);
        batteryRepository.getBatteryLiveData().observe(this, batteryState -> {
            batteryIconView.setBatteryState(batteryState);
        });
        batteryIconView.setOnClickListener((v) -> {
            String batteryInfo = batteryIconView.getDetailedContentDescription();
            BaldSnackbar.INSTANCE.show(this, batteryInfo, BaldSnackbar.TYPE_INFO, BaldSnackbar.LENGTH_LONG);
        });

        baldPrefsUtils = BaldPrefsUtils.newInstance(this);
        viewPagerHandler();
        recognizerManager.setHomeScreen(this);

        repo = NotificationRepository.INSTANCE;
        repo.getCount().observe(this, this::handleNotificationCount);

        setupPermissionBanner();

    }

    private void handleFlashlightEvent(FlashlightState event) {
        if (event instanceof FlashlightState.OnOff) {
            boolean isOn = ((FlashlightState.OnOff) event).isOn();
            Log.d(TAG, "Flashlight icon state changed: " + isOn);
            flashButton.setImageResource(
                    isOn
                            ? R.drawable.flashlight_on_background
                            : R.drawable.flashlight_off_on_background);
        } else if (event instanceof FlashlightState.Error) {
            BaldToast.error(this, "Flashlight not available");
        }
    }

    private void setupPermissionBanner() {
        final View permissionBanner = findViewById(R.id.permission_banner);

        permissionBanner.findViewById(R.id.permission_banner_text).setOnClickListener(v ->
            startActivity(new Intent(this, app.baldphone.neo.settings.ui.SettingsActivity.class)
                .setData(Uri.parse("myapp://settings/system/permissions"))));
        permissionBanner.findViewById(R.id.permission_banner_close).setOnClickListener(v -> {
            permissionBanner.setVisibility(View.GONE);
            permissionBannerDismissed = true;
        });
    }

    private void onFlashlightButtonPressed() {
        if (flashlight == null) return;

        PermissionManager.checkOrRequest(this, PermissionManager.CAMERA, result -> {
            if (result == PermissionManager.GRANTED) {
                flashlight.toggle();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(TAG, "onStart");
        if (finishedUpdatingApps)
            updateViewPager(false, false);
    }

    @Override
    protected void onResume() { // remember to change in Page1EditorActivity.java too!
        super.onResume();
        Log.v(TAG, "onResume");

        if (isResumed) {
            Log.d(TAG, "onResume: was already visible!");
        } else {
            // Case: The user just returned from Settings where they might have granted missing permissions.
            if (!permissionBannerDismissed) {
                PermissionRepository.INSTANCE.refresh();
            }
        }
        isResumed = true;


        if (baldPrefsUtils.hasChanged(this)) {
            viewPagerHolder.getViewPager().removeAllViews();//android auto saves fragments, not good for us in this case
            this.recreate();
        }

        soundButton.setImageResource(SOUND_DRAWABLES[audioManager.getRingerMode()]);

        if (isFirstResume) {
            isFirstResume = false;
            final View permissionBanner = findViewById(R.id.permission_banner);
            if (permissionBanner != null) {
                permissionBanner.setVisibility(View.GONE);
            }
            handler.removeCallbacks(showPermissionBannerRunnable);
            handler.postDelayed(showPermissionBannerRunnable, 2000);
        } else {
            showPermissionBannerRunnable.run();
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
        handler.removeCallbacks(showPermissionBannerRunnable);
        handler.removeCallbacks(shakeIt);
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        handler.removeCallbacksAndMessages(null);
        isResumed = false;
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

    static class UpdateApps extends AsyncTask<Context, Void, Void> {
        final WeakReference<HomeScreenActivity> homeScreenWeakReference;

        UpdateApps(HomeScreenActivity homeScreen) {
            super();
            homeScreenWeakReference = new WeakReference<>(homeScreen);
        }

        @Override
        protected Void doInBackground(Context... contexts) {
            try {
                AppsDatabaseHelper.updateDB(contexts[0]);
            } catch (Exception e) {
                BaldToast.from(contexts[0].getApplicationContext()).setType(BaldToast.TYPE_ERROR).setLength(Toast.LENGTH_LONG).setText(e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            HomeScreenActivity homeScreen = homeScreenWeakReference.get();
            if (homeScreen != null && !homeScreen.isFinishing() && !homeScreen.isDestroyed()) {
                homeScreen.updateViewPager(false, false);
                homeScreen.finishedUpdatingApps = true;

                if (homeScreen.launchAppsActivity) {
                    homeScreen.launchAppsActivity = false;
                    Intent intent = new Intent(homeScreen, AppsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    homeScreen.startActivity(intent);
                }
            }
        }
    }
}
