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

import android.app.Notification;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import app.baldphone.neo.notifications.NotificationListenerService;
import app.baldphone.neo.notifications.NotificationRepository;

import com.bald.uriah.baldphone.R;
import com.bald.uriah.baldphone.adapters.NotificationRecyclerViewAdapter;
import com.bald.uriah.baldphone.utils.BDB;
import com.bald.uriah.baldphone.utils.BDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationsActivity extends BaldActivity {
    private static final String TAG = NotificationsActivity.class.getSimpleName();
    private final Map<String, String> appNameCache = new HashMap<>();
    private final NotificationRepository repo = NotificationRepository.getInstance();
    public Bundle[] activeNotifications;
    public RecyclerView recyclerView;
    private NotificationRecyclerViewAdapter notificationRecyclerViewAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate");
        if (!notificationListenerGranted(this)) {
            Log.e(TAG, "Notification listener permission not granted");
            Toast.makeText(this, "Permission not granted!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_notifications);
        findViewById(R.id.clear_all_notifications)
                .setOnClickListener(
                        v -> {
                            if (notificationRecyclerViewAdapter != null)
                                NotificationListenerService.cancelAll();
                            finish();
                        });
        recyclerView = findViewById(R.id.recycler_view);
        final DividerItemDecoration dividerItemDecoration =
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        dividerItemDecoration.setDrawable(getDrawable(R.drawable.ll_divider));
        recyclerView.addItemDecoration(dividerItemDecoration);
        recyclerView.setItemViewCacheSize(10);

        if (!Settings.Secure.getString(this.getContentResolver(), "enabled_notification_listeners")
                .contains(getApplicationContext().getPackageName())) {
            BDB.from(this)
                    .setTitle(R.string.enable_notification_access)
                    .setSubText(R.string.enable_notification_access_subtext)
                    .addFlag(BDialog.FLAG_OK | BDialog.FLAG_CANCEL)
                    .setPositiveButtonListener(
                            params -> {
                                getApplicationContext()
                                        .startActivity(
                                                new Intent(
                                                        "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                                return true;
                            })
                    .setNegativeButtonListener(params -> true)
                    .show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(TAG, "onStart");

        repo.getNotifications().observe(this, this::processNotifications);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.v(TAG, "onStop");
    }

    @Override
    public void finish() {
        super.finish();
        //        overridePendingTransition(R.anim.nothing, R.anim.slide_out_up);
    }

    void processNotifications(@NonNull List<StatusBarNotification> notifications) {
        Log.d(TAG, "processNotifications: " + notifications.size());
        List<Bundle> bundles =
                parseNotifications(notifications.toArray(new StatusBarNotification[0]));
        refreshNotifications(bundles);
    }

    private void refreshNotifications(List<Bundle> notifications) {

        // Convert the List<Bundle> to Bundle[]
        if (notifications == null || notifications.isEmpty()) {
            activeNotifications = new Bundle[0];
        } else {
            activeNotifications = notifications.toArray(new Bundle[0]);
        }

        // Update or initialize the RecyclerView adapter
        if (notificationRecyclerViewAdapter == null) {
            notificationRecyclerViewAdapter =
                    new NotificationRecyclerViewAdapter(
                            NotificationsActivity.this, activeNotifications);
            recyclerView.setAdapter(notificationRecyclerViewAdapter);
        } else {
            notificationRecyclerViewAdapter.changeNotifications(activeNotifications);
        }
    }

    public List<Bundle> parseNotifications(StatusBarNotification[] sbns) {
        if (sbns == null) return Collections.emptyList();

        List<Bundle> list = new ArrayList<>(sbns.length);
        for (StatusBarNotification sbn : sbns) {
            if (sbn == null || sbn.getNotification() == null) continue;
            Notification n = sbn.getNotification();
            Bundle b = new Bundle();
            Bundle extras = n.extras != null ? n.extras : Bundle.EMPTY;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                b.putParcelable("small_icon", n.getSmallIcon());
                b.putParcelable("large_icon", n.getLargeIcon());
            } else {
                b.putInt("small_icon", n.icon);
            }
            b.putCharSequence("title", extras.getCharSequence(Notification.EXTRA_TITLE));
            b.putCharSequence("text", extras.getCharSequence(Notification.EXTRA_TEXT));
            b.putCharSequence("packageName", sbn.getPackageName());
            b.putString("key", sbn.getKey());
            b.putLong("time_stamp", n.when);
            b.putCharSequence("app_name", getAppNameFromPackage(sbn.getPackageName()));
            b.putParcelable("clear_intent", n.deleteIntent);
            b.putParcelable("content_intent", n.contentIntent);
            b.putBoolean("clearable", (n.flags & Notification.FLAG_NO_CLEAR) == 0);

            list.add(b);
        }

        return list;
    }

    private String getAppNameFromPackage(String packageName) {
        if (packageName == null) return "(unknown)";
        String cached = appNameCache.get(packageName);
        if (cached != null) return cached;

        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            String name = pm.getApplicationLabel(ai).toString();
            appNameCache.put(packageName, name);
            return name;
        } catch (PackageManager.NameNotFoundException e) {
            return "(unknown)";
        }
    }

    @Override
    protected int requiredPermissions() {
        return PERMISSION_NONE;
    }
}
