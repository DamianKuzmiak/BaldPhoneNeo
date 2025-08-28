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

package com.bald.uriah.baldphone.views;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.baldphone.neo.battery.BatteryState;

import com.bald.uriah.baldphone.R;

import java.util.concurrent.TimeUnit;

public class BatteryView extends BaldImageButton {

    private String accessibilityText;

    public BatteryView(Context context) {
        super(context);
        init();
    }

    public BatteryView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BatteryView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        accessibilityText = getContext().getString(R.string.battery_unavailable_tts);
    }

    /**
     * Updates the battery icon and content description based on the provided battery state.
     *
     * @param batteryState The current state of the battery, including percentage and charging
     *     status. Cannot be null.
     */
    public void updateBatteryState(@NonNull BatteryState batteryState) {
        @DrawableRes
        int batteryDrawable = getBatteryDrawable(batteryState.percentage, batteryState.isCharging);
        setImageResource(batteryDrawable);

        accessibilityText = buildBatteryInfoString(getContext(), batteryState);
        setContentDescription(accessibilityText);
    }

    /**
     * Retrieves the string representation of the current battery information. This string is
     * suitable for accessibility services (e.g., TalkBack) and provides a human-readable
     * description of the battery status, including percentage and charging state.
     *
     * @return A non-null string describing the current battery information. If the battery state is
     *     unavailable, it returns a default string indicating this.
     */
    @NonNull
    public String getAccessibilityText() {
        return accessibilityText;
    }

    @DrawableRes
    private int getBatteryDrawable(int percentage, boolean isCharging) {
        if (percentage < 0 || percentage > 100) {
            return R.drawable.battery_unknown;
        }

        if (isCharging) {
            if (percentage < 20) return R.drawable.battery_20_charging;
            if (percentage < 30) return R.drawable.battery_30_charging;
            if (percentage < 50) return R.drawable.battery_50_charging;
            if (percentage < 60) return R.drawable.battery_60_charging;
            if (percentage < 80) return R.drawable.battery_80_charging;
            if (percentage < 90) return R.drawable.battery_90_charging;
            if (percentage < 100) return R.drawable.battery_100_charging;
            return R.drawable.battery_full_charging;
        } else {
            if (percentage < 2) return R.drawable.battery_empty;
            if (percentage < 11) return R.drawable.battery_01;
            if (percentage < 21) return R.drawable.battery_10;
            if (percentage < 31) return R.drawable.battery_20;
            if (percentage < 40) return R.drawable.battery_30;
            if (percentage < 50) return R.drawable.battery_40;
            if (percentage < 59) return R.drawable.battery_50;
            if (percentage < 69) return R.drawable.battery_60;
            if (percentage < 78) return R.drawable.battery_70;
            if (percentage < 88) return R.drawable.battery_80;
            if (percentage < 97) return R.drawable.battery_90;
            return R.drawable.battery_full;
        }
    }

    /**
     * Generates a user-friendly string describing the currentState battery state, suitable for
     * toasts and Text-to-Speech, using string resources for localization.
     */
    private static String buildBatteryInfoString(
            @NonNull Context context, @NonNull BatteryState batteryState) {

        /*
         * 1) "Battery status currently unavailable."
         * 2) "Battery is full, 100 percent."
         * 3) "{percentage} percent, charging."
         * 4) "{percentage} percent, charging. ({hours} hours and ){minutes} minutes until full."
         * 5) "{percentage} percent battery remaining."
         */

        int percentage = batteryState.percentage;
        if (percentage == BatteryState.UNKNOWN_PERCENTAGE) {
            return context.getString(R.string.battery_unavailable_tts);
        }

        if (batteryState.isFull) {
            return context.getString(R.string.battery_full_tts);
        }

        if (batteryState.isCharging) {
            long minutesToFull = batteryState.getMinutesToFullCharge();
            if (minutesToFull <= 0) {
                return context.getString(R.string.battery_charging_tts, percentage);
            }

            long hoursToFull = TimeUnit.MINUTES.toHours(minutesToFull);
            long minutesPart = minutesToFull % 60;

            String timeFormatted;
            if (hoursToFull > 0) {
                timeFormatted =
                        context.getString(R.string.battery_time_h_m_tts, hoursToFull, minutesPart);
            } else if (minutesPart > 0) {
                timeFormatted = context.getString(R.string.battery_time_m_tts, minutesPart);
            } else { // Time is 0, not yet full, e.g. still calculating or very slow charge
                return context.getString(R.string.battery_charging_tts, percentage);
            }
            return context.getString(
                    R.string.battery_charging_with_time_tts, percentage, timeFormatted);
        }

        return context.getString(R.string.battery_remaining_tts, percentage);
    }
}
