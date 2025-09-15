/*
 * Copyright 2019 Uriah Shaul Mandel
 * Copyright 2025 Damian Kuzmiak
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

package com.bald.uriah.baldphone.databases.apps;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.bald.uriah.baldphone.BuildConfig;
import com.bald.uriah.baldphone.R;
import com.bald.uriah.baldphone.activities.AppsActivity;
import com.bald.uriah.baldphone.activities.DialerActivity;
import com.bald.uriah.baldphone.activities.SOSActivity;
import com.bald.uriah.baldphone.activities.alarms.AlarmsActivity;
import com.bald.uriah.baldphone.activities.contacts.ContactsActivity;
import com.bald.uriah.baldphone.activities.media.PhotosActivity;
import com.bald.uriah.baldphone.activities.media.VideosActivity;
import com.bald.uriah.baldphone.activities.pills.PillsActivity;
import com.bald.uriah.baldphone.utils.S;
import com.bumptech.glide.Glide;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import app.baldphone.neo.calls.recent.RecentCallsActivity;

/** Static class of useful methods when using the Apps Database */
public class AppsDatabaseHelper {
    private static final String TAG = AppsDatabaseHelper.class.getSimpleName();

    public static final String PREDEFINED_APP_PREFIX = BuildConfig.APPLICATION_ID + "/";

    private record PredefinedAppInfo(int iconResId, int labelResId) {}

    private static final Map<String, PredefinedAppInfo> PREDEFINED_APP_ICONS_MAP =
            Map.ofEntries(
                    Map.entry(
                            PREDEFINED_APP_PREFIX + RecentCallsActivity.class.getName(),
                            new PredefinedAppInfo(
                                    R.drawable.history_on_background, R.string.recent)),
                    Map.entry(
                            PREDEFINED_APP_PREFIX + ContactsActivity.class.getName(),
                            new PredefinedAppInfo(
                                    R.drawable.human_on_background, R.string.contacts)),
                    Map.entry(
                            PREDEFINED_APP_PREFIX + DialerActivity.class.getName(),
                            new PredefinedAppInfo(R.drawable.phone_on_background, R.string.dialer)),
                    Map.entry(
                            PREDEFINED_APP_PREFIX + PhotosActivity.class.getName(),
                            new PredefinedAppInfo(R.drawable.photo_on_background, R.string.photos)),
                    Map.entry(
                            PREDEFINED_APP_PREFIX + VideosActivity.class.getName(),
                            new PredefinedAppInfo(R.drawable.movie_on_background, R.string.videos)),
                    Map.entry(
                            PREDEFINED_APP_PREFIX + PillsActivity.class.getName(),
                            new PredefinedAppInfo(R.drawable.pill, R.string.pills)),
                    Map.entry(
                            PREDEFINED_APP_PREFIX + AppsActivity.class.getName(),
                            new PredefinedAppInfo(R.drawable.apps_on_background, R.string.apps)),
                    Map.entry(
                            PREDEFINED_APP_PREFIX + AlarmsActivity.class.getName(),
                            new PredefinedAppInfo(R.drawable.clock_on_background, R.string.alarms)),
                    Map.entry(
                            PREDEFINED_APP_PREFIX + SOSActivity.class.getName(),
                            new PredefinedAppInfo(R.drawable.emergency, R.string.sos))
                    // Map.entry(PREDEFINED_APP_PREFIX + Page1EditorActivity.class.getName(), ...
                    );

    /**
     * Updates the Apps Database. This method is designed to be robust and should not throw
     * exceptions. It synchronizes the database with the currently discoverable applications.
     */
    public static void updateDB(@NonNull Context context) {
        long startUpdateDb = System.currentTimeMillis();

        final AppsDatabase appsDatabase = AppsDatabase.getInstance(context);
        final AppsDatabaseDao dao = appsDatabase.appsDatabaseDao();
        final PackageManager packageManager = context.getPackageManager();

        // 1. Get all discoverable apps (external + predefined)
        Set<String> discoverableAppNames = getDiscoverableAppNames(context);

        Log.d(TAG, "Apps DB 2#: " + (System.currentTimeMillis() - startUpdateDb) + "ms");
        // 2. Current DB apps, 12 ms
        List<App> appsInDb = dao.getAll();
        Map<String, App> appsInDbMap =
                appsInDb.stream()
                        .collect(Collectors.toMap(App::getFlattenComponentName, app -> app));

        Log.d(TAG, "Apps DB 3#: " + (System.currentTimeMillis() - startUpdateDb) + "ms");
        // 3. Apps to add
        List<App> appsToAdd =
                discoverableAppNames.stream()
                        .filter(name -> !appsInDbMap.containsKey(name))
                        .map(name -> buildAppFromComponent(name, packageManager, context))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

        if (!appsToAdd.isEmpty()) {
            dao.insertAll(appsToAdd.toArray(new App[0]));
            Log.i(TAG, "Added apps: " + appsToAdd.size());
        }

        Log.d(TAG, "Apps DB 4#: " + (System.currentTimeMillis() - startUpdateDb) + "ms");
        // 4. Apps to delete
        int[] idsToDelete =
                appsInDb.stream()
                        .filter(
                                app ->
                                        !discoverableAppNames.contains(
                                                app.getFlattenComponentName()))
                        .mapToInt(App::getId)
                        .toArray();

        if (idsToDelete.length > 0) {
            dao.deleteByIds(idsToDelete);
            Log.i(TAG, "Deleted apps: " + idsToDelete.length);
        }

        if (appsToAdd.isEmpty() && idsToDelete.length == 0) {
            Log.i(TAG, "Apps DB is already up-to-date.");
        }

        Log.d(TAG, "Apps DB updated in " + (System.currentTimeMillis() - startUpdateDb) + "ms");
    }

    /**
     * Loads the icon of an app into an ImageView. If an error occurs, a default icon is displayed.
     */
    public static void loadPic(App app, ImageView imageView) {
        if (app.getFlattenComponentName().startsWith(PREDEFINED_APP_PREFIX))
            setPredefinedAppIcon(app, imageView);
        else if (app.getIcon() != null && app.getIcon().length > 0) {
            Glide.with(imageView)
                    .load(S.byteArrayToBitmap(app.getIcon()))
                    .error(R.drawable.ic_default_app_icon)
                    .into(imageView);
        } else {
            imageView.setImageResource(R.drawable.ic_default_app_icon);
        }
    }

    private static Set<String> getDiscoverableAppNames(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
        Set<String> componentNames =
                new HashSet<>(resolveInfos.size() + PREDEFINED_APP_ICONS_MAP.size());

        for (ResolveInfo resolveInfo : resolveInfos) {
            ActivityInfo activityInfo = resolveInfo.activityInfo;
            if (!activityInfo.packageName.equals(BuildConfig.APPLICATION_ID)) {
                ComponentName componentName =
                        new ComponentName(activityInfo.packageName, activityInfo.name);
                componentNames.add(componentName.flattenToString());
            }
        }

        componentNames.addAll(PREDEFINED_APP_ICONS_MAP.keySet());
        return componentNames;
    }

    private static App buildAppFromComponent(
            String componentName, PackageManager pm, Context context) {
        App newApp = new App();
        newApp.setFlattenComponentName(componentName);

        if (PREDEFINED_APP_ICONS_MAP.containsKey(componentName)) {
            PredefinedAppInfo info = PREDEFINED_APP_ICONS_MAP.get(componentName);
            if (info != null) {
                newApp.setLabel(context.getString(info.labelResId));
                newApp.setIcon(null); // handled by loadPic()
            }
            return newApp;
        }

        try {
            ComponentName component = ComponentName.unflattenFromString(componentName);
            if (component == null) {
                Log.e(TAG, "Invalid component: " + componentName);
                return null;
            }

            ActivityInfo info = pm.getActivityInfo(component, PackageManager.MATCH_DEFAULT_ONLY);
            newApp.setLabel(String.valueOf(info.loadLabel(pm)));

            Drawable drawable = info.loadIcon(pm);
            byte[] iconData =
                    (drawable instanceof BitmapDrawable)
                            ? S.bitmapToByteArray(((BitmapDrawable) drawable).getBitmap())
                            : S.bitmapToByteArray(S.getBitmapFromDrawable(drawable));
            newApp.setIcon(iconData);

            return newApp;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "App not found: " + componentName, e);
        } catch (Exception e) {
            Log.e(TAG, "Error adding app: " + componentName, e);
        }
        return null;
    }

    /** Sets the icon for the predefined app. */
    private static void setPredefinedAppIcon(@NonNull App app, @NonNull ImageView imageView) {
        PredefinedAppInfo info = PREDEFINED_APP_ICONS_MAP.get(app.getFlattenComponentName());
        if (info != null) {
            imageView.setImageResource(info.iconResId);
        } else {
            Log.e(TAG, "Drawable resource not found for app: " + app.getLabel());
            imageView.setImageResource(R.drawable.ic_default_app_icon);
        }
    }
}
