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

import static android.os.Build.VERSION_CODES;

import static app.baldphone.neo.utils.IntentUtilsKt.startActivityWithNewTaskClear;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build.VERSION;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;

import app.baldphone.neo.features.gallery.MediaActivity;

import com.bald.uriah.baldphone.R;
import com.bald.uriah.baldphone.activities.AppsActivity;
import com.bald.uriah.baldphone.activities.HomeScreenActivity;
import com.bald.uriah.baldphone.activities.SettingsActivity;
import com.bald.uriah.baldphone.activities.alarms.AlarmsActivity;
import com.bald.uriah.baldphone.activities.pills.PillsActivity;
import com.bald.uriah.baldphone.utils.BaldToast;
import com.bald.uriah.baldphone.utils.DropDownRecyclerViewAdapter;
import com.bald.uriah.baldphone.utils.S;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomePage2 extends HomeView {
    public static final String TAG = HomePage2.class.getSimpleName();
    private View view;
    private ImageView iv_internet, iv_maps;
    private TextView tv_internet, tv_maps;
    private View bt_settings, bt_internet, bt_maps, bt_photo;
    private View bt_videos, bt_pills, bt_apps, bt_alarms; // Added fields
    private PackageManager packageManager;

    public HomePage2(@NonNull HomeScreenActivity homeScreen) {
        super(homeScreen, homeScreen);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container) {
        view = inflater.inflate(R.layout.fragment_home_page2, container, false);
        packageManager = homeScreen.getPackageManager();
        attachXml();
        genOnLongClickListeners();
        return view;
    }

    private void attachXml() {
        bt_alarms = view.findViewById(R.id.bt_alarms);
        bt_apps = view.findViewById(R.id.bt_apps);
        bt_internet = view.findViewById(R.id.bt_internet);
        bt_maps = view.findViewById(R.id.bt_maps);
        bt_photo = view.findViewById(R.id.bt_photo);
        bt_pills = view.findViewById(R.id.bt_pills);
        bt_settings = view.findViewById(R.id.bt_settings);
        bt_videos = view.findViewById(R.id.bt_videos);

        iv_internet = view.findViewById(R.id.iv_internet);
        iv_maps = view.findViewById(R.id.iv_maps);

        tv_internet = view.findViewById(R.id.tv_internet);
        tv_maps = view.findViewById(R.id.tv_maps);
    }

    private void genOnLongClickListeners() {
        bt_settings.setOnClickListener(
                v -> homeScreen.startActivity(new Intent(getContext(), SettingsActivity.class)));

        clickListenerForAbstractOpener(
                Uri.parse("http://www.google.com"), bt_internet, iv_internet, tv_internet);
        clickListenerForAbstractOpener(Uri.parse("geo:0,0"), bt_maps, iv_maps, tv_maps);

        if (bt_photo != null) {
            bt_photo.setOnClickListener(
                    v -> startActivityWithNewTaskClear(getContext(), new Intent(getContext(), MediaActivity.class)
                            .putExtra(MediaActivity.EXTRA_MODE, MediaActivity.MODE_PHOTOS_ONLY)));
        }

        if (bt_videos != null) {
            bt_videos.setOnClickListener(
                    v -> startActivityWithNewTaskClear(getContext(), new Intent(getContext(), MediaActivity.class)
                            .putExtra(MediaActivity.EXTRA_MODE, MediaActivity.MODE_VIDEOS_ONLY)));
        }

        if (bt_pills != null) {
            bt_pills.setOnClickListener(
                    v -> homeScreen.startActivity(new Intent(getContext(), PillsActivity.class)));
        }

        if (bt_apps != null) {
            bt_apps.setOnClickListener(
                    v -> {
                        if (!homeScreen.finishedUpdatingApps) {
                            homeScreen.launchAppsActivity = true;
                        } else {
                            homeScreen.startActivity(new Intent(getContext(), AppsActivity.class));
                        }
                    });
        }

        if (bt_alarms != null) {
            bt_alarms.setOnClickListener(
                    v -> homeScreen.startActivity(new Intent(homeScreen, AlarmsActivity.class)));
        }
    }

    private void clickListenerForAbstractOpener(
            @NonNull final Uri uri,
            @NonNull final View bt,
            @NonNull final ImageView iv,
            @NonNull final TextView tv) {
        final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        final List<ResolveInfo> activitiesWithDuplicates =
                packageManager.queryIntentActivities(
                        intent,
                        VERSION.SDK_INT >= VERSION_CODES.M
                                ? PackageManager.MATCH_ALL
                                : PackageManager.MATCH_DEFAULT_ONLY);

        // Remove duplicates
        final List<ResolveInfo> resolveInfos = new ArrayList<>();
        final Set<String> seenPackages = new HashSet<>();
        for (final ResolveInfo resolveInfo : activitiesWithDuplicates) {
            final String packageName = resolveInfo.activityInfo.applicationInfo.packageName;
            if (seenPackages.add(packageName)) { // Set.add() returns true if the element was new
                resolveInfos.add(resolveInfo);
            }
        }

        if (resolveInfos.size() > 1) {
            bt.setOnClickListener(v -> S.showDropDownPopup(
                    homeScreen,
                    getWidth(),
                    new DropDownRecyclerViewAdapter.DropDownListener() {
                        @Override
                        public void onUpdate(
                                DropDownRecyclerViewAdapter.ViewHolder viewHolder,
                                int position,
                                PopupWindow popupWindow) {
                            final ResolveInfo resolveInfo = resolveInfos.get(position);
                            setupAppDropdownItem(viewHolder, resolveInfo, popupWindow);
                        }

                        @Override
                        public int size() {
                            return resolveInfos.size();
                        }

                    },
                    bt));
        } else if (resolveInfos.size() == 1) {
            final ResolveInfo resolveInfo = resolveInfos.get(0);
            if (S.isValidContextForGlide(iv.getContext())) {
                Glide.with(iv).load(resolveInfo.loadIcon(packageManager)).into(iv);
            }
            tv.setText(resolveInfo.loadLabel(packageManager));
            bt.setOnClickListener(v1 ->
                    homeScreen.startActivity(
                            packageManager.getLaunchIntentForPackage(
                                    resolveInfo.activityInfo.applicationInfo.packageName)));
        } else {
            bt.setOnClickListener(this::showErrorMessage);
        }
    }

    private void setupAppDropdownItem(
            DropDownRecyclerViewAdapter.ViewHolder viewHolder,
            final ResolveInfo resolveInfo,
            final PopupWindow popupWindow) {
        if (S.isValidContextForGlide(viewHolder.pic.getContext())) {
            Glide.with(viewHolder.pic)
                    .load(resolveInfo.loadIcon(packageManager))
                    .into(viewHolder.pic);
        }
        viewHolder.text.setText(resolveInfo.loadLabel(packageManager));
        viewHolder.itemView.setOnClickListener(
                v1 -> {
                    homeScreen.startActivity(
                            packageManager
                                    .getLaunchIntentForPackage(
                                            resolveInfo
                                                    .activityInfo
                                                    .applicationInfo
                                                    .packageName));
                    popupWindow.dismiss();
                });
    }

    private void showErrorMessage(View v) {
        BaldToast.from(v.getContext())
                .setType(BaldToast.TYPE_ERROR)
                .setText(R.string.no_app_was_found)
                .show();
    }
}
