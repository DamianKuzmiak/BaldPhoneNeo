package app.baldphone.neo.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.bald.uriah.baldphone.utils.BPrefs;
import com.bald.uriah.baldphone.utils.D;

public class BaldButtonConfig {
    private final boolean longPressesEnabled;
    private final boolean vibrationFeedbackEnabled;
    private final int shortPressTimeout;
    private final int longPressTimeout;
    private final boolean showPressLongerHint;

    public BaldButtonConfig(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(D.BALD_PREFS, Context.MODE_PRIVATE);

        longPressesEnabled =
                prefs.getBoolean(BPrefs.LONG_PRESSES_KEY, BPrefs.LONG_PRESSES_DEFAULT_VALUE);
        vibrationFeedbackEnabled =
                prefs.getBoolean(
                        BPrefs.VIBRATION_FEEDBACK_KEY, BPrefs.VIBRATION_FEEDBACK_DEFAULT_VALUE);
        shortPressTimeout =
                prefs.getInt(
                        BPrefs.SHORT_PRESS_DURATION_MS_KEY,
                        BPrefs.SHORT_PRESS_DURATION_MS_DEFAULT_VALUE);
        longPressTimeout =
                prefs.getInt(
                        BPrefs.LONG_PRESS_DURATION_MS_KEY,
                        BPrefs.LONG_PRESS_DURATION_MS_DEFAULT_VALUE);
        showPressLongerHint =
                prefs.getBoolean(
                        BPrefs.SHOW_PRESS_LONGER_HINT_KEY,
                        BPrefs.SHOW_PRESS_LONGER_HINT_DEFAULT_VALUE);
    }

    public boolean isLongPressesEnabled() {
        return longPressesEnabled;
    }

    public boolean isVibrationEnabled() {
        return vibrationFeedbackEnabled;
    }

    public int getShortPressTimeout() {
        return shortPressTimeout;
    }

    public int getLongPressTimeout() {
        return longPressTimeout;
    }

    public boolean isShowPressLongerHint() {
        return showPressLongerHint;
    }
}
