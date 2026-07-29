/*
 * Copyright 2019 Uriah Shaul Mandel
 * Copyright 2026 Zenolabs
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

import android.app.KeyguardManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.Nullable;

/**
 * Base class for screens that are shown by an alarm or a reminder, i.e. screens that must
 * appear on top of the lock screen and wake the device up on their own.
 */
public abstract class TimedBaldActivity extends BaldActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Window window = getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        showWhenLockedAndTurnScreenOn();

        new Handler(Looper.getMainLooper()).postDelayed(
                () -> window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON), screenTimeout());
    }

    /**
     * Shows this activity on top of the lock screen and turns the display on.
     * <p>
     * {@code FLAG_SHOW_WHEN_LOCKED} and {@code FLAG_TURN_SCREEN_ON} were deprecated in API 27
     * and are unreliable on recent Android releases, which is why the modern per-activity API
     * is preferred whenever it is available.
     * <p>
     * The keyguard is only dismissed when it is not secure, which preserves the behaviour of
     * the legacy {@code FLAG_DISMISS_KEYGUARD}: a user with a PIN, pattern or password still
     * has to unlock the device, and is never prompted to do so just to silence an alarm.
     */
    private void showWhenLockedAndTurnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);

            final KeyguardManager keyguardManager = getSystemService(KeyguardManager.class);
            if (keyguardManager != null && !keyguardManager.isKeyguardSecure()) {
                keyguardManager.requestDismissKeyguard(this, null);
            }
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    protected abstract int screenTimeout();
}
