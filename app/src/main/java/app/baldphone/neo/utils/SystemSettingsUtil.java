// SPDX-License-Identifier: Apache-2.0
// © Copyright 2025 Damian Kuzmiak

package app.baldphone.neo.utils;

import android.content.Context;
import android.text.format.DateFormat;
import android.util.Log;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;

public class SystemSettingsUtil {
    private static final String TAG = SystemSettingsUtil.class.getSimpleName();
    public static final String D_MMM_YYYY = "d MMM yyyy";

    private SystemSettingsUtil() {}

    /**
     * Retrieves the user's preferred date pattern from system settings.
     *
     * <p>If the system pattern cannot be determined from {@link java.text.SimpleDateFormat},
     * defaults to "d MMM yyyy" (e.g., "1 Jan 2025").
     *
     * @return A non-null string representing the date pattern for formatting.
     */
    @NonNull
    public static String getSystemDatePattern(@NonNull Context context) {
        try {
            java.text.DateFormat deviceDateFormat =
                    DateFormat.getDateFormat(context.getApplicationContext());
            if (deviceDateFormat instanceof SimpleDateFormat simpleDateFormat) {
                return simpleDateFormat.toPattern();
            } else {
                Log.w(TAG, "Using fallback date pattern " + D_MMM_YYYY);
                return D_MMM_YYYY; // Example: "26 Aug 2025"
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving system date pattern. Using default + " + D_MMM_YYYY, e);
            return D_MMM_YYYY;
        }
    }
}
