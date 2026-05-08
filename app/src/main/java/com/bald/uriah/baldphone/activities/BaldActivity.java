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

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Vibrator;
import android.widget.PopupWindow;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bald.uriah.baldphone.utils.BPrefs;
import com.bald.uriah.baldphone.utils.D;
import com.bald.uriah.baldphone.utils.S;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import app.baldphone.neo.data.Prefs;

/**
 * the parent of all of the activitys in this app.
 */
@Deprecated()
public abstract class BaldActivity extends AppCompatActivity {
    protected static final int
            PERMISSION_NONE = 0,
            PERMISSION_READ_PHONE_STATE = 0b100000000000,
            PERMISSION_WRITE_SETTINGS = 0b1,
            PERMISSION_DEFAULT_PHONE_HANDLER = 0b10,
            PERMISSION_READ_CONTACTS = 0b100 | PERMISSION_DEFAULT_PHONE_HANDLER,
            PERMISSION_WRITE_CONTACTS = 0b1000 | PERMISSION_DEFAULT_PHONE_HANDLER,
            PERMISSION_CALL_PHONE = 0b10000 | PERMISSION_DEFAULT_PHONE_HANDLER | PERMISSION_READ_PHONE_STATE,
        PERMISSION_WRITE_EXTERNAL_STORAGE = 0b10000000,
        PERMISSION_SYSTEM_ALERT_WINDOW = 0b1000000000000;

    public boolean testing = false;
    protected Vibrator vibrator;
    private List<WeakReference<Dialog>> dialogsToClose = new ArrayList<>(1);
    private List<WeakReference<PopupWindow>> popupWindowsToClose = new ArrayList<>(1);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SharedPreferences sharedPreferences = BPrefs.get(this);
        testing = sharedPreferences.getBoolean(BPrefs.TEST_KEY, BPrefs.TEST_DEFAULT_VALUE);

        vibrator = Prefs.isVibrationFeedbackEnabled()
                ? (Vibrator) getSystemService(VIBRATOR_SERVICE) : null;
    }

    @Override
    protected void onPause() {
        for (WeakReference<Dialog> dialogWeakReference : dialogsToClose) {
            final Dialog dialog = dialogWeakReference.get();
            if (dialog != null)
                dialog.dismiss();
        }

        for (WeakReference<PopupWindow> windowWeakReference : popupWindowsToClose) {
            final PopupWindow window = windowWeakReference.get();
            if (window != null)
                window.dismiss();
        }

        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (vibrator != null)
            vibrator.vibrate(D.vibetime);
        super.onBackPressed();
    }

    public void autoDismiss(Dialog dialog) {
        if (dialogsToClose.size() > 10)
            dialogsToClose = S.cleanWeakList(dialogsToClose);
        dialogsToClose.add(new WeakReference<>(dialog));
    }

    public void autoDismiss(PopupWindow popupWindow) {
        if (popupWindowsToClose.size() > 10)
            popupWindowsToClose = S.cleanWeakList(popupWindowsToClose);
        popupWindowsToClose.add(new WeakReference<>(popupWindow));
    }

    protected int requiredPermissions() {
        return PERMISSION_NONE;
    }
}
