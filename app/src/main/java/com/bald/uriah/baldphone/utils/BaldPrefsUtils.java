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

package com.bald.uriah.baldphone.utils;

import android.content.Context;
import android.content.SharedPreferences;

import app.baldphone.neo.data.Prefs;

import java.util.Objects;

import static com.bald.uriah.baldphone.utils.BPrefs.EMERGENCY_BUTTON_VISIBLE_DEFAULT_VALUE;
import static com.bald.uriah.baldphone.utils.BPrefs.EMERGENCY_BUTTON_VISIBLE_KEY;
import static com.bald.uriah.baldphone.utils.BPrefs.LONG_PRESSES_DEFAULT_VALUE;
import static com.bald.uriah.baldphone.utils.BPrefs.LONG_PRESSES_KEY;
import static com.bald.uriah.baldphone.utils.BPrefs.NOTE_VISIBLE_DEFAULT_VALUE;
import static com.bald.uriah.baldphone.utils.BPrefs.NOTE_VISIBLE_KEY;
import static com.bald.uriah.baldphone.utils.BPrefs.TOUCH_NOT_HARD_DEFAULT_VALUE;
import static com.bald.uriah.baldphone.utils.BPrefs.TOUCH_NOT_HARD_KEY;

public class BaldPrefsUtils {
    private final boolean vibrationFeedback, touchNoHard, longPresses, notes, sos;
    private final int statusBar;

    private BaldPrefsUtils(boolean vibrationFeedback, boolean touchNoHard, boolean longPresses, boolean notes, int statusBar, boolean sos) {
        this.vibrationFeedback = vibrationFeedback;
        this.touchNoHard = touchNoHard;
        this.longPresses = longPresses;
        this.notes = notes;
        this.statusBar = statusBar;
        this.sos = sos;
    }

    public static BaldPrefsUtils newInstance(Context context) {
        final SharedPreferences sharedPreferences = context.getSharedPreferences(BPrefs.KEY, Context.MODE_PRIVATE);
        return new BaldPrefsUtils(
                Prefs.isVibrationFeedbackEnabled(),
                sharedPreferences
                        .getBoolean(TOUCH_NOT_HARD_KEY, TOUCH_NOT_HARD_DEFAULT_VALUE),
                sharedPreferences
                        .getBoolean(LONG_PRESSES_KEY, LONG_PRESSES_DEFAULT_VALUE),
                sharedPreferences
                        .getBoolean(NOTE_VISIBLE_KEY, NOTE_VISIBLE_DEFAULT_VALUE),
                Prefs.getStatusBarMode().getValue(),
                sharedPreferences.getBoolean(EMERGENCY_BUTTON_VISIBLE_KEY, EMERGENCY_BUTTON_VISIBLE_DEFAULT_VALUE)
        );
    }

    public boolean hasChanged(Context context) {
        return !equals(newInstance(context));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaldPrefsUtils that = (BaldPrefsUtils) o;
        return vibrationFeedback == that.vibrationFeedback &&
                touchNoHard == that.touchNoHard &&
                longPresses == that.longPresses &&
                notes == that.notes &&
                sos == that.sos &&
                statusBar == that.statusBar;
    }

    @Override
    public int hashCode() {
        return Objects.hash(vibrationFeedback, touchNoHard, longPresses, notes, sos, statusBar);
    }
}
