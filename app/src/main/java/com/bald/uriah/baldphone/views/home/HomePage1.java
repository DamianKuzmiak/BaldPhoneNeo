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

import static com.bald.uriah.baldphone.databases.apps.AppsDatabaseHelper.PREDEFINED_APP_PREFIX;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.Telephony;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewTreeLifecycleOwner;

import app.baldphone.neo.activities.DialerActivity;
import app.baldphone.neo.data.Prefs;
import app.baldphone.neo.features.calls.ui.RecentCallsActivity;
import app.baldphone.neo.features.contacts.ui.ContactsActivity;
import app.baldphone.neo.features.notifications.data.NotificationRepository;
import app.baldphone.neo.permissions.PermissionManager;
import app.baldphone.neo.permissions.model.SpecialPermission;
import app.baldphone.neo.services.DeviceLock;
import app.baldphone.neo.ui.dialogs.BaldDialog;
import app.baldphone.neo.utils.IntentUtilsKt;
import app.baldphone.neo.utils.messaging.WhatsAppHandler;

import com.bald.uriah.baldphone.R;
import com.bald.uriah.baldphone.activities.AppsActivity;
import com.bald.uriah.baldphone.activities.HomeScreenActivity;
import com.bald.uriah.baldphone.activities.Page1EditorActivity;
import com.bald.uriah.baldphone.activities.SOSActivity;
import com.bald.uriah.baldphone.databases.apps.App;
import com.bald.uriah.baldphone.databases.apps.AppsDatabase;
import com.bald.uriah.baldphone.databases.apps.AppsDatabaseHelper;
import com.bald.uriah.baldphone.utils.BDB;
import com.bald.uriah.baldphone.utils.BDialog;
import com.bald.uriah.baldphone.utils.BPrefs;
import com.bald.uriah.baldphone.utils.BaldToast;
import com.bald.uriah.baldphone.utils.S;
import com.bald.uriah.baldphone.views.FirstPageAppIcon;

import java.util.Map;
import java.util.Set;

public class HomePage1 extends HomeView {
    public static final String TAG = HomePage1.class.getSimpleName();
    private final NotificationRepository repo = NotificationRepository.INSTANCE;
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
    private TextView tv_phone_number;
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
        tv_phone_number = rootView.findViewById(R.id.phone_number);
        updatePhoneNumber();
    }

    private void updatePhoneNumber() {
        if (tv_phone_number == null) return;

        if (!Prefs.INSTANCE.isHomePhoneNumberEnabled()) {
            tv_phone_number.setVisibility(View.GONE);
            return;
        }

        String number = Prefs.INSTANCE.getHomePhoneNumber();
        if (number != null && !number.isEmpty()) {
            tv_phone_number.setText(number);
        } else {
            tv_phone_number.setText(R.string.custom_phone_number);
        }
        tv_phone_number.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updatePhoneNumber();

        LifecycleOwner owner = ViewTreeLifecycleOwner.get(this);
        if (owner != null) {
            repo.getPackages().observe(owner, this::refreshBadges);
            repo.getMissedCalls(activity).observe(owner, missedCalls -> {
                if (bt_recent != null && !viewsToApps.containsValue(bt_recent)) {
                    bt_recent.setBadgeVisibility(!missedCalls.isEmpty());
                }
            });
        } else {
            Log.e(TAG, "LifecycleOwner is null. Cannot observe LiveData.");
        }
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
                v -> IntentUtilsKt.startActivityWithNewTaskClear(homeScreen, new Intent(homeScreen, RecentCallsActivity.class)));
        setupButton(
                BPrefs.CUSTOM_DIALER_KEY,
                bt_dialer,
                v -> IntentUtilsKt.startActivityWithNewTaskClear(homeScreen, new Intent(homeScreen, DialerActivity.class)));
        setupButton(
                BPrefs.CUSTOM_CONTACTS_KEY,
                bt_contacts,
                v -> IntentUtilsKt.startActivityWithNewTaskClear(homeScreen, new Intent(homeScreen, ContactsActivity.class)));
        setupButton(
                BPrefs.CUSTOM_APP_KEY,
                bt_whatsapp,
                v -> {
                    try {
                        WhatsAppHandler.INSTANCE.launch(homeScreen);
                    } catch (Exception e) {
                        BaldToast.error(homeScreen, e.getLocalizedMessage());
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
                v -> {
                    Intent intent = getCameraIntent();
                    if (intent != null) {
                        homeScreen.startActivity(intent);
                    }
                });
        setupButton(
                BPrefs.CUSTOM_VIDEOS_KEY,
                bt_lock_screen,
                v -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        requestDeviceLock();
                    } else {
                        homeScreen.startActivity(new Intent(homeScreen, AppsActivity.class));
                    }
                });
    }

    private void setupButton(
            String bPrefsKey,
            @NonNull FirstPageAppIcon button,
            View.OnClickListener defaultListener) {
        if (homeScreen != null) {
            setupButtonForHomeScreen(bPrefsKey, button, defaultListener);
        } else {
            setupButtonForEditor(bPrefsKey, button);
        }
    }

    private void setupButtonForHomeScreen(
            String bPrefsKey,
            @NonNull FirstPageAppIcon button,
            View.OnClickListener defaultListener) {
        App app = findAppByPreference(bPrefsKey);

        if (app == null) {
            setupDefault(button, defaultListener);
        } else {
            button.setText(app.getLabel());
            AppsDatabaseHelper.loadPic(app, button.imageView);
            button.setOnClickListener(
                    v ->
                            S.startComponentName(
                                    homeScreen,
                                    ComponentName.unflattenFromString(
                                            app.getFlattenComponentName())));
            viewsToApps.put(app, button);
        }
    }

    private void setupButtonForEditor(String bPrefsKey, @NonNull FirstPageAppIcon bt) {
        App app = findAppByPreference(bPrefsKey);

        // This is for Page1EditorActivity context
        final Page1EditorActivity page1EditorActivity = (Page1EditorActivity) activity;
        final CharSequence initialAppName;

        if (bt == bt_lock_screen && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            if (app == null) {
                App appsActivityApp =
                        AppsDatabase.getInstance(activity)
                                .appsDatabaseDao()
                                .findByFlattenComponentName(
                                        PREDEFINED_APP_PREFIX + AppsActivity.class.getName());
                if (appsActivityApp != null) {
                    bt.setText(R.string.apps);
                    AppsDatabaseHelper.loadPic(appsActivityApp, bt.imageView);
                }
            }
            initialAppName = activity.getText(R.string.apps);
        } else {
            initialAppName = bt.getText();
        }

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

    private void setupDefault(@NonNull FirstPageAppIcon bt, OnClickListener onClickListener) {
        if (bt == bt_lock_screen) {
            // The lock screen button has a different behavior on older APIs
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                // On older APIs, this button opens the Apps screen
                App app =
                        AppsDatabase.getInstance(homeScreen)
                                .appsDatabaseDao()
                                .findByFlattenComponentName(
                                        PREDEFINED_APP_PREFIX + AppsActivity.class.getName());
                bt.setText(R.string.apps);
                AppsDatabaseHelper.loadPic(app, bt.imageView);
            }
        }
        bt.setOnClickListener(onClickListener);
    }

    // Helper
    @Nullable
    private App findAppByPreference(String bPrefsKey) {
        if (sharedPreferences.contains(bPrefsKey)) {
            App app =
                    AppsDatabase.getInstance(homeScreen)
                            .appsDatabaseDao()
                            .findByFlattenComponentName(
                                    sharedPreferences.getString(bPrefsKey, null));
            // If app is not found in DB, preference is stale. Remove it.
            if (app == null) {
                sharedPreferences.edit().remove(bPrefsKey).apply();
            }
            return app;
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private void requestDeviceLock() {
        DeviceLock.requestLock(homeScreen, result -> {
            switch (result) {
                case FAILURE:
                    // System reports failure despite the service being technically enabled.
                    // Prompt user to re-enable to fix the internal state.
                    new BaldDialog.Builder(homeScreen)
                        .setTitle(R.string.accessibility_permission_check_title)
                        .setMessage(R.string.accessibility_permission_check_message)
                        .setPositiveButton(R.string.dialog_button_enable, dialog -> {
                            openAccessibilitySettings();
                            return kotlin.Unit.INSTANCE;
                        })
                        .setNegativeButton(R.string.dialog_button_not_now, null)
                        .show();
                    break;
                case ACCESS_DENIED:
                    // Permission missing.
                    PermissionManager.checkOrRequest(
                        homeScreen, SpecialPermission.Accessibility.INSTANCE, result1 -> {}
                    );
                    break;
            }
        });
    }

    private void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            homeScreen.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            BaldToast.error(homeScreen, "Failed to open accessibility settings.");
        }
    }

    private void refreshBadges(Set<String> packagesSet) {
        Context viewContext = getContext(); // Use the view's context if available

        if (bt_whatsapp != null && !viewsToApps.containsValue(bt_whatsapp)) {
            bt_whatsapp.setBadgeVisibility(packagesSet.contains(WhatsAppHandler.WHATSAPP_PACKAGE_NAME));
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
