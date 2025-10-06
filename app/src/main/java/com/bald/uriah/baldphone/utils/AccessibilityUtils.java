/* SPDX-License-Identifier: Apache-2.0 */
/*
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

package com.bald.uriah.baldphone.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.bald.uriah.baldphone.R;

public class AccessibilityUtils {
    private static final String TAG = AccessibilityUtils.class.getSimpleName();

    /**
     * Checks if a specific Accessibility Service is enabled in the system settings.
     *
     * @param context The application context.
     * @param serviceClass The Class of the Accessibility Service to check.
     * @return true if the service is enabled, false otherwise.
     */
    public static boolean isAccessibilityServiceEnabled(
            @NonNull final Context context, @NonNull final Class<?> serviceClass) {

        String enabledServices =
                Settings.Secure.getString(
                        context.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        Log.v(TAG, "Currently enabled accessibility services: " + enabledServices);

        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }

        ComponentName cn = new ComponentName(context, serviceClass);
        String fullName = cn.flattenToString(); // com.example.pckg/com.example.pckg.ExampleService
        Log.d(TAG, "Bald accessibility service full name: " + fullName);

        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        colonSplitter.setString(enabledServices);
        while (colonSplitter.hasNext()) {
            if (colonSplitter.next().equalsIgnoreCase(fullName)) {
                return true;
            }
        }
        Log.i(TAG, "Bald accessibility service not found in enabled services.");
        return false;
    }

    /**
     * Displays a dialog prompting the user to enable the accessibility service for the application.
     *
     * @param context The context from which the dialog should be displayed. Must not be null.
     */
    public static void showAccessibilityServiceDialog(@NonNull final Context context) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_accessibility_permission, null);

        AlertDialog dialog = new AlertDialog.Builder(context).setView(dialogView).create();

        Button btnEnable = dialogView.findViewById(R.id.btnEnable);
        Button btnNotNow = dialogView.findViewById(R.id.btnNotNow);

        btnEnable.setOnClickListener(
                v -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    dialog.dismiss();
                });

        btnNotNow.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
