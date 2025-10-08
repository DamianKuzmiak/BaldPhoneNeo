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
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;

import com.bald.uriah.baldphone.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class BaldToast {

    public static final int LENGTH_SEC = -1;

    public static final int TYPE_DEFAULT = 0;
    public static final int TYPE_ERROR = 1;
    public static final int TYPE_INFORMATIVE = 2;

    public static final int POSITION_CENTER = Gravity.CENTER;
    public static final int POSITION_BOTTOM = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;

    private static Toast currentToast;

    private final Context context;
    private int type = TYPE_DEFAULT;
    private CharSequence text;
    private boolean big = false;
    private @ToastDuration int duration = Toast.LENGTH_LONG;
    private @ToastPosition int position = POSITION_CENTER;

    private Toast toast;
    private boolean built;
    private Handler handler;
    private Runnable cancelRunnable;

    private BaldToast(@NonNull Context context) {
        this.context = new ContextThemeWrapper(context.getApplicationContext(), R.style.bald_light);
    }

    // Factory methods
    public static BaldToast from(@NonNull Context context) {
        return new BaldToast(context);
    }

    public static void error(@NonNull Context context) {
        from(context).setText(R.string.an_error_has_occurred).setType(TYPE_ERROR).show();
    }

    public static void simple(@NonNull Context context, CharSequence text) {
        from(context).setText(text).setType(TYPE_DEFAULT).show();
    }

    public static void simple(@NonNull Context context, @StringRes int resId) {
        from(context).setText(context.getText(resId)).setType(TYPE_DEFAULT).show();
    }

    public static void longer(@NonNull Context context) {
        from(context)
                .setText(R.string.press_longer)
                .setType(TYPE_DEFAULT)
                .setLength(LENGTH_SEC)
                .show();
    }

    public static void simpleBottom(@NonNull Context context, CharSequence text) {
        from(context).setText(text).setType(TYPE_DEFAULT).setPosition(POSITION_BOTTOM).show();
    }

    public static void simpleBottom(@NonNull Context context, @StringRes int resId) {
        from(context).setText(resId).setType(TYPE_DEFAULT).setPosition(POSITION_BOTTOM).show();
    }

    public BaldToast setType(@ToastType int type) {
        this.type = type;
        return this;
    }

    public BaldToast setText(CharSequence text) {
        this.text = text;
        return this;
    }

    public BaldToast setText(@StringRes int resString) {
        this.text = context.getString(resString);
        return this;
    }

    public BaldToast setLength(@ToastDuration int duration) {
        this.duration = duration;
        return this;
    }

    public BaldToast setBig(boolean big) {
        this.big = big;
        return this;
    }

    public BaldToast setPosition(@ToastPosition int gravity) {
        this.position = gravity;
        return this;
    }

    public void show() {
        cancelActiveToast(); // cancel old toast first

        if (!built) build();
        toast.show();

        currentToast = toast; // track this as the active toast

        if (duration == LENGTH_SEC) {
            if (handler == null) handler = new Handler(Looper.getMainLooper());
            if (cancelRunnable == null) cancelRunnable = () -> toast.cancel();

            handler.removeCallbacks(cancelRunnable);
            handler.postDelayed(cancelRunnable, 1000); // 1 second
        }
    }

    public void cancel() {
        if (handler != null && cancelRunnable != null) handler.removeCallbacks(cancelRunnable);
        if (toast != null) toast.cancel();
    }

    private void cancelActiveToast() {
        if (currentToast != null) {
            currentToast.cancel();
            currentToast = null;
        }
    }

    public BaldToast build() {
        View toastView = LayoutInflater.from(context).inflate(R.layout.toast_layout, null);
        TextView textView = (TextView) toastView;

        if (big) textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);

        // Determine resources based on type
        @DrawableRes int backgroundRes;
        @ColorRes int colorRes;
        switch (type) {
            case TYPE_ERROR:
                backgroundRes = R.drawable.toast_error_background;
                colorRes = R.color.toast_foreground_error;
                break;
            case TYPE_INFORMATIVE:
                backgroundRes = R.drawable.toast_informative_background;
                colorRes = R.color.toast_foreground_informative;
                break;
            default: // TYPE_DEFAULT
                backgroundRes = R.drawable.toast_default_background;
                colorRes = R.color.toast_foreground_default;
                break;
        }

        textView.setBackgroundResource(backgroundRes);
        textView.setTextColor(ContextCompat.getColor(context, colorRes));
        textView.setText(text);

        toast = new Toast(context);
        toast.setView(toastView);
        toast.setDuration(duration == LENGTH_SEC ? Toast.LENGTH_SHORT : duration);
        toast.setGravity(position, 0, position == POSITION_BOTTOM ? 120 : 0);
        built = true;

        return this;
    }

    @IntDef({TYPE_DEFAULT, TYPE_ERROR, TYPE_INFORMATIVE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ToastType {}

    @IntDef({Toast.LENGTH_SHORT, Toast.LENGTH_LONG, BaldToast.LENGTH_SEC})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ToastDuration {}

    @IntDef({Gravity.CENTER, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ToastPosition {}
}
