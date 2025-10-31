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

package com.bald.uriah.baldphone;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Process;
import android.util.Log;

import app.baldphone.neo.crashes.CrashHandler;
import app.baldphone.neo.helpers.VibratorHelper;

import com.bald.uriah.baldphone.databases.alarms.AlarmScheduler;
import com.bald.uriah.baldphone.databases.reminders.ReminderScheduler;
import com.bald.uriah.baldphone.utils.S;

import net.danlew.android.joda.JodaTimeAndroid;

import java.util.List;

public class BaldPhone extends Application {
    private static final String TAG = BaldPhone.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();
        if (!isMainProcess()) {
            return;
        }
        Log.i(TAG, "BaldPhone was started");

        CrashHandler.init(this);
        JodaTimeAndroid.init(this);
        AlarmScheduler.reStartAlarms(this);
        ReminderScheduler.reStartReminders(this);
        //        if (BuildConfig.FLAVOR.equals("baldUpdates")) {
        //            UpdatesActivity.removeUpdatesInfo(this);
        //        }
        VibratorHelper.init(this);
        S.sendVersionInfo(this);
    }

    private boolean isMainProcess() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            return getPackageName().equals(Application.getProcessName());
        }

        ActivityManager activityManager =
                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return true;
        }
        List<ActivityManager.RunningAppProcessInfo> processInfos =
                activityManager.getRunningAppProcesses();
        if (processInfos == null) {
            return true;
        }
        final String mainProcessName = getPackageName();
        final int myPid = Process.myPid();
        for (ActivityManager.RunningAppProcessInfo info : processInfos) {
            if (info.pid == myPid) {
                return mainProcessName.equals(info.processName);
            }
        }
        return false;
    }
}
