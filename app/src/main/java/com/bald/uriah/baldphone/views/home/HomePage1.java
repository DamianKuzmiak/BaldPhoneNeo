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

package com.bald.uriah.baldphone.views.home;

import static com.bald.uriah.baldphone.utils.AccessibilityUtils.isAccessibilityServiceEnabled;
import static com.bald.uriah.baldphone.utils.AccessibilityUtils.showAccessibilityServiceDialog;
import static com.bald.uriah.baldphone.utils.D.WHATSAPP_PACKAGE_NAME;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Telephony;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewTreeLifecycleOwner;

import app.baldphone.neo.calls.recent.RecentCallsActivity;
import app.baldphone.neo.notifications.NotificationRepository;

import com.bald.uriah.baldphone.R;
import com.bald.uriah.baldphone.activities.AppsActivity;
import com.bald.uriah.baldphone.activities.DialerActivity;
import com.bald.uriah.baldphone.activities.HomeScreenActivity;
import com.bald.uriah.baldphone.activities.Page1EditorActivity;
import com.bald.uriah.baldphone.activities.SOSActivity;
import com.bald.uriah.baldphone.activities.contacts.ContactsActivity;
import com.bald.uriah.baldphone.databases.apps.App;
import com.bald.uriah.baldphone.databases.apps.AppsDatabase;
import com.bald.uriah.baldphone.databases.apps.AppsDatabaseHelper;
import com.bald.uriah.baldphone.databases.calls.CallLogsHelper;
import com.bald.uriah.baldphone.services.DeviceLockService;
import com.bald.uriah.baldphone.utils.BDB;
import com.bald.uriah.baldphone.utils.BDialog;
import com.bald.uriah.baldphone.utils.BPrefs;
import com.bald.uriah.baldphone.utils.BaldToast;
import com.bald.uriah.baldphone.utils.D;
import com.bald.uriah.baldphone.utils.S;
import com.bald.uriah.baldphone.views.FirstPageAppIcon;

import java.util.Map;
import java.util.Set;

public class HomePage1 extends HomeView {
    public static final String TAG = HomePage1.class.getSimpleName();
    private final NotificationRepository repo = NotificationRepository.getInstance();
    private static final ComponentName WHATSAPP_COMPONENT_NAME =
            new ComponentName(WHATSAPP_PACKAGE_NAME, D.WHATSAPP_LAUNCH_ACTIVITY);

    private Map<App, FirstPageAppIcon> viewsToApps;
    private FirstPageAppIcon bt_assistant,
            bt_camera,
            bt_contacts,
            bt_dialer,
            bt_emergency,
            bt_lock_screen,
            bt_messages,
            bt_recent,
            bt_whatsapp;
    private SharedPreferences sharedPreferences;

    public HomePage1(@NonNull Context context) {
        super(
                (context instanceof HomeScreenActivity) ? (HomeScreenActivity) context : null,
                (Activity) context);
        sharedPreferences = BPrefs.get(activity);
    }

    @SuppressWarnings("unused")
    public HomePage1(@NonNull Context context, AttributeSet attributeSet) {
        this(context);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container) {
        View view = inflater.inflate(R.layout.fragment_home_page1, container, false);
        viewsToApps = new ArrayMap<>();

        initViews(view);

        setupOnClickListeners();
        return view;
    }

    private void initViews(View rootView) {
        bt_assistant = rootView.findViewById(R.id.bt_assistant);
        bt_camera = rootView.findViewById(R.id.bt_camera);
        bt_contacts = rootView.findViewById(R.id.bt_contacts);
        bt_dialer = rootView.findViewById(R.id.bt_dialer);
        bt_emergency = rootView.findViewById(R.id.bt_emergency);
        bt_lock_screen = rootView.findViewById(R.id.bt_lock_screen);
        bt_messages = rootView.findViewById(R.id.bt_messages);
        bt_recent = rootView.findViewById(R.id.bt_recent);
        bt_whatsapp = rootView.findViewById(R.id.bt_whatsapp);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        LifecycleOwner owner = ViewTreeLifecycleOwner.get(this);
        if (owner != null) {
            repo.getPackages().observe(owner, this::refreshBadges);
        } else {
            Log.e(TAG, "LifecycleOwner is null. Cannot observe LiveData.");
        };
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    private Intent getCameraIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        ResolveInfo resolveInfo =
                activity.getPackageManager()
                        .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);

        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            Log.e(TAG, "No camera app found to handle IMAGE_CAPTURE action.");
            BaldToast.error(this.getContext());
            return null;
        }

        ActivityInfo activityInfo = resolveInfo.activityInfo;
        ComponentName name =
                new ComponentName(activityInfo.applicationInfo.packageName, activityInfo.name);
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .setComponent(name);
    }

    private void setupOnClickListeners() {
        sharedPreferences = BPrefs.get(activity);
        setupButton(
                BPrefs.CUSTOM_RECENTS_KEY,
                bt_recent,
                v -> homeScreen.startActivity(new Intent(homeScreen, RecentCallsActivity.class)));
        setupButton(
                BPrefs.CUSTOM_DIALER_KEY,
                bt_dialer,
                v -> homeScreen.startActivity(new Intent(homeScreen, DialerActivity.class)));
        setupButton(
                BPrefs.CUSTOM_CONTACTS_KEY,
                bt_contacts,
                v -> homeScreen.startActivity(new Intent(homeScreen, ContactsActivity.class)));
        setupButton(
                BPrefs.CUSTOM_APP_KEY,
                bt_whatsapp,
                v -> {
                    if (S.isPackageInstalled(homeScreen, WHATSAPP_PACKAGE_NAME))
                        S.startComponentName(homeScreen, WHATSAPP_COMPONENT_NAME);
                    else
                        try {
                            homeScreen.startActivity(
                                    new Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(
                                                    "market://details?id="
                                                            + WHATSAPP_PACKAGE_NAME)));
                        } catch (android.content.ActivityNotFoundException e) {
                            homeScreen.startActivity(
                                    new Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(
                                                    "https://play.google.com/store/apps/details?id="
                                                            + WHATSAPP_PACKAGE_NAME)));
                        }
                });
        setupButton(
                BPrefs.CUSTOM_ASSISTANT_KEY,
                bt_assistant,
                v -> {
                    try {
                        homeScreen.startActivity(
                                new Intent(Intent.ACTION_VOICE_COMMAND)
                                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception e) {
                        BaldToast.from(homeScreen)
                                .setType(BaldToast.TYPE_ERROR)
                                .setText(R.string.your_phone_doesnt_have_assistant_installed)
                                .show();
                    }
                });
        setupButton(
                BPrefs.CUSTOM_MESSAGES_KEY,
                bt_messages,
                v -> {
                    try {
                        final ResolveInfo resolveInfo =
                                homeScreen
                                        .getPackageManager()
                                        .queryIntentActivities(
                                                new Intent("android.intent.action.MAIN", null)
                                                        .setPackage(
                                                                Telephony.Sms.getDefaultSmsPackage(
                                                                        homeScreen)),
                                                0)
                                        .iterator()
                                        .next();
                        S.startComponentName(
                                homeScreen,
                                new ComponentName(
                                        resolveInfo.activityInfo.packageName,
                                        resolveInfo.activityInfo.name));

                    } catch (Exception e) {
                        BaldToast.from(homeScreen)
                                .setType(BaldToast.TYPE_ERROR)
                                .setText(R.string.an_error_has_occurred)
                                .show();
                    }
                });
        setupButton(
                BPrefs.CUSTOM_EMERGENCY_KEY,
                bt_emergency,
                v -> homeScreen.startActivity(new Intent(homeScreen, SOSActivity.class)));
        setupButton(
                BPrefs.CUSTOM_CAMERA_KEY,
                bt_camera,
                v -> homeScreen.startActivity(getCameraIntent()));
        setupButton(
                BPrefs.CUSTOM_VIDEOS_KEY,
                bt_lock_screen,
                v -> {
                    // Support for API levels below 28 is not currently implemented or planned
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                        BaldToast.from(homeScreen)
                                .setType(BaldToast.TYPE_DEFAULT)
                                .setText(R.string.info_lock_screen_min_sdk)
                                .show();
                        return;
                    }

                    if (!isAccessibilityServiceEnabled(homeScreen, DeviceLockService.class)) {
                        showAccessibilityServiceDialog(homeScreen);
                        return;
                    }

                    DeviceLockService service = DeviceLockService.getInstance();
                    if (service != null) {
                        boolean locked = service.lockScreen();
                        if (!locked) {
                            BaldToast.from(homeScreen)
                                    .setType(BaldToast.TYPE_ERROR)
                                    .setText("Action failed")
                                    .show();
                        }
                    } else {
                        BaldToast.from(homeScreen)
                                .setType(BaldToast.TYPE_ERROR)
                                .setText("Accessibility Service is not available")
                                .show();
                    }
                });
    }

    private void setupButton(
            String bPrefsKey, FirstPageAppIcon bt, View.OnClickListener onClickListener) {
        final App app;
        if (sharedPreferences.contains(bPrefsKey)) {
            app =
                    AppsDatabase.getInstance(homeScreen)
                            .appsDatabaseDao()
                            .findByFlattenComponentName(
                                    sharedPreferences.getString(bPrefsKey, null));
            if (app == null) sharedPreferences.edit().remove(bPrefsKey).apply();
        } else app = null;

        if (homeScreen != null) {
            if (app == null) {
                bt.setOnClickListener(onClickListener);
            } else {
                bt.setText(app.getLabel());
                AppsDatabaseHelper.loadPic(app, bt.imageView);
                bt.setOnClickListener(
                        v ->
                                S.startComponentName(
                                        homeScreen,
                                        ComponentName.unflattenFromString(
                                                app.getFlattenComponentName())));
                viewsToApps.put(app, bt);
            }
        } else { // This is for Page1EditorActivity context
            final Page1EditorActivity page1EditorActivity = (Page1EditorActivity) activity;
            final CharSequence initialAppName;

            initialAppName = bt.getText();

            final BDB bdb =
                    BDB.from(activity)
                            .setTitle(R.string.custom_app)
                            .setSubText(R.string.custom_app_subtext)
                            .addFlag(BDialog.FLAG_OK | BDialog.FLAG_CANCEL)
                            .setOptions(initialAppName, activity.getText(R.string.custom))
                            .setOptionsStartingIndex(
                                    () -> sharedPreferences.contains(bPrefsKey) ? 1 : 0)
                            .setPositiveButtonListener(
                                    params -> {
                                        if (params[0].equals(0)) {
                                            sharedPreferences.edit().remove(bPrefsKey).apply();
                                        } else {
                                            activity.startActivityForResult(
                                                    new Intent(activity, AppsActivity.class)
                                                            .putExtra(
                                                                    AppsActivity.CHOOSE_MODE,
                                                                    bPrefsKey),
                                                    AppsActivity.REQUEST_SELECT_CUSTOM_APP);
                                        }
                                        return true;
                                    });

            bt.setOnClickListener(
                    v ->
                            bdb.show()
                                    .setOnDismissListener(
                                            dialog -> {
                                                if (page1EditorActivity.baldPrefsUtils.hasChanged(
                                                        page1EditorActivity)) {
                                                    page1EditorActivity.recreate();
                                                }
                                            }));

            if (app != null) {
                bt.setText(app.getLabel());
                AppsDatabaseHelper.loadPic(app, bt.imageView);
                viewsToApps.put(app, bt);
            }
        }
    }

    private void refreshBadges(Set<String> packagesSet) {
        Context viewContext = getContext(); // Use the view's context if available

        if (bt_whatsapp != null && !viewsToApps.containsValue(bt_whatsapp)) {
            bt_whatsapp.setBadgeVisibility(packagesSet.contains(WHATSAPP_PACKAGE_NAME));
        }

        if (bt_recent != null && !viewsToApps.containsValue(bt_recent)) {
            if (viewContext != null && viewContext.getContentResolver() != null) {
                bt_recent.setBadgeVisibility(
                        !CallLogsHelper.isAllReadSafe(
                                viewContext.getContentResolver()));
            } else if (viewContext != null && viewContext.getContentResolver()
                    != null) { // Fallback to receiver's context
                bt_recent.setBadgeVisibility(
                        !CallLogsHelper.isAllReadSafe(viewContext.getContentResolver()));
            } else {
                bt_recent.setBadgeVisibility(
                        false); // Fallback: hide badge if context is unavailable
            }
        }

        if (bt_messages != null && !viewsToApps.containsValue(bt_messages)) {
            String defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(viewContext);
            if (defaultSmsPackage != null) {
                bt_messages.setBadgeVisibility(packagesSet.contains(defaultSmsPackage));
            } else {
                bt_messages.setBadgeVisibility(false); // No default SMS app, hide badge
            }
        }

        for (Map.Entry<App, FirstPageAppIcon> app : viewsToApps.entrySet()) {
            if (app == null) continue;

            FirstPageAppIcon icon = app.getValue();
            if (icon != null) {
                String flatComponentName = app.getKey().getFlattenComponentName();
                if (flatComponentName != null) {
                    ComponentName cn =
                            ComponentName.unflattenFromString(flatComponentName);
                    if (cn != null) {
                        icon.setBadgeVisibility(
                                packagesSet.contains(cn.getPackageName()));
                    } else {
                        icon.setBadgeVisibility(
                                false); // Invalid component or no package name
                    }
                } else {
                    icon.setBadgeVisibility(false); // No component name in app data
                }
            }
        }
    }
}
