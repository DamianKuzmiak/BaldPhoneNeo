package com.bald.uriah.baldphone.views;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import app.baldphone.neo.buttons.TouchInputHandler;
import app.baldphone.neo.buttons.ViewTouchHandlerFactory;
import app.baldphone.neo.core.system.HapticManager;

import com.bald.uriah.baldphone.utils.BPrefs;
import com.bald.uriah.baldphone.utils.D;

public class BaldButton extends AppCompatTextView implements BaldButtonInterface, TouchInputHandler.HapticFeedbackProvider {

    private static final String TAG = BaldButton.class.getSimpleName();

    private TouchInputHandler touchInputHandler;
    private SharedPreferences sharedPreferences;
    private boolean isLongPressHandlingEnabled; // To manage listener behavior

    public BaldButton(Context context) {
        super(context);
        init(context);
    }

    public BaldButton(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public BaldButton(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        this.sharedPreferences = context.getSharedPreferences(D.BALD_PREFS, Context.MODE_PRIVATE);

        this.isLongPressHandlingEnabled = sharedPreferences.getBoolean(
                BPrefs.LONG_PRESSES_KEY, BPrefs.LONG_PRESSES_DEFAULT_VALUE);

        this.touchInputHandler = ViewTouchHandlerFactory.initViewTouchHandler(
                this, context, this.sharedPreferences);

        this.touchInputHandler.setHapticFeedbackProvider(this);

        setClickable(true);
        setFocusable(true);
    }

    // --- BaldButtonInterface Implementation ---
    @Override
    public void baldPerformClick() {
        if (touchInputHandler != null) {
            touchInputHandler.programmaticClickTrigger();
        } else {
            // Fallback if touchInputHandler is somehow null
            Log.w(TAG, "TouchInputHandler not initialized, programmatic click might not work as expected.");
            // Check if there's a click listener from the handler, even if VTH is null (unlikely scenario)
            // or directly call super.performClick() if no other listener logic is present.
            OnClickListener handlerListener = getOnClickListenerFromHandler();
            if (handlerListener != null) {
                handlerListener.onClick(this);
            } else {
                super.performClick();
            }
        }
    }

    @Override
    public void vibrate() {
        HapticManager.INSTANCE.vibrate();
    }

    @Override
    public void requestHapticFeedback(TouchInputHandler.HapticFeedbackProvider.HapticType type) {
        Log.d(TAG, "Haptic feedback requested for type: " + type.name());
        this.vibrate();
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener listener) {
        if (touchInputHandler != null) {
            touchInputHandler.setOnClickListener(listener);
        }
        if (!isLongPressHandlingEnabled) {
            super.setOnClickListener(listener);
        } else {
            // If TouchInputHandler is active, ViewTouchHandlerFactory has already called
            // view.setOnClickListener(null) on the raw view.
        }
    }

    @Override
    public void setOnLongClickListener(@Nullable OnLongClickListener listener) {
        if (touchInputHandler != null) {
            touchInputHandler.setOnLongClickListener(listener);
        }
        if (!isLongPressHandlingEnabled) {
            super.setOnLongClickListener(listener);
        } else {
            // If TouchInputHandler is active, ViewTouchHandlerFactory has already called
            // view.setOnLongClickListener(null) on the raw view.
        }
    }

    @Override
    public void setOnTouchListener(OnTouchListener listener) {
        if (isLongPressHandlingEnabled && touchInputHandler != null) {
            super.setOnTouchListener((v, event) -> touchInputHandler.onTouchEvent(event));
            Log.w(TAG, "setOnTouchListener: Custom long press handling is active. TouchInputHandler remains the primary touch listener.");
        } else {
            super.setOnTouchListener(listener);
        }
    }

    @Nullable
    public OnClickListener getOnClickListenerFromHandler() {
        if (this.touchInputHandler != null) {
            return this.touchInputHandler.getOnClickListener();
        }
        return null;
    }

    @Nullable
    public OnLongClickListener getOnLongClickListenerFromHandler() {
        if (this.touchInputHandler != null) {
            return this.touchInputHandler.getOnLongClickListener();
        }
        return null;
    }
}
