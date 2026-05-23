package com.bald.uriah.baldphone.views;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import app.baldphone.neo.buttons.TouchInputHandler;
import app.baldphone.neo.buttons.ViewTouchHandlerFactory;
import app.baldphone.neo.core.system.HapticManager;

import com.bald.uriah.baldphone.utils.BPrefs;
import com.bald.uriah.baldphone.utils.D;

public class BaldImageButton extends AppCompatImageView implements BaldButtonInterface, TouchInputHandler.HapticFeedbackProvider {

    private static final String TAG = BaldImageButton.class.getSimpleName();

    private TouchInputHandler touchInputHandler;
    private SharedPreferences sharedPreferences;
    private boolean isLongPressHandlingEnabled;

    public BaldImageButton(Context context) {
        super(context);
        commonInit(context);
    }

    public BaldImageButton(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        commonInit(context);
    }

    public BaldImageButton(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        commonInit(context);
    }

    private void commonInit(Context context) {
        this.sharedPreferences = context.getSharedPreferences(D.BALD_PREFS, Context.MODE_PRIVATE);

        this.isLongPressHandlingEnabled = sharedPreferences.getBoolean(
                BPrefs.LONG_PRESSES_KEY, BPrefs.LONG_PRESSES_DEFAULT_VALUE);

        this.touchInputHandler = ViewTouchHandlerFactory.initViewTouchHandler(
                this, context, this.sharedPreferences);

        this.touchInputHandler.setHapticFeedbackProvider(this);

        // Standard view setup
        setClickable(true);
        setFocusable(true);
    }

    @Override
    public void baldPerformClick() {
        if (touchInputHandler != null) {
            touchInputHandler.programmaticClickTrigger();
        } else {
            Log.w(TAG, "TouchInputHandler not initialized, programmatic click might not work as expected.");
            super.performClick();
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
        // If TouchInputHandler's custom logic is disabled (isLongPressHandlingEnabled is false),
        // the click events are standard, so set the listener on super for it to work.
        // ViewTouchHandlerFactory ensures that if long press handling is disabled,
        // the view's OnTouchListener is null, allowing standard clicks.
        if (!isLongPressHandlingEnabled) {
            super.setOnClickListener(listener);
        } else {
            // If TouchInputHandler is active, ViewTouchHandlerFactory has already called
            // view.setOnClickListener(null) on the raw view during configureViewTouchInteraction.
            // So, we should not call super.setOnClickListener() here.
        }
    }

    @Override
    public void setOnLongClickListener(@Nullable OnLongClickListener listener) {
        if (touchInputHandler != null) {
            touchInputHandler.setOnLongClickListener(listener);
        }
        // Similar logic to setOnClickListener
        if (!isLongPressHandlingEnabled) {
            super.setOnLongClickListener(listener);
        } else {
            // If TouchInputHandler is active, ViewTouchHandlerFactory has already called
            // view.setOnLongClickListener(null) on the raw view.
        }
    }

    /**
     * Sets the touch listener for this view.
     * Note: If custom long press handling via {@link TouchInputHandler} is enabled
     * (based on {@link BPrefs#LONG_PRESSES_KEY}), the {@link TouchInputHandler}
     * is set as the primary touch listener. Setting another listener via this method
     * while custom handling is active will override the {@link TouchInputHandler}'s control
     * and is generally not recommended as it will break the custom press behaviors.
     */
    @Override
    public void setOnTouchListener(OnTouchListener listener) {
        // If long press handling by TouchInputHandler is active, ViewTouchHandlerFactory
        // has already set TouchInputHandler as the primary touch listener.
        // Re-assert TouchInputHandler if long presses are enabled to ensure it stays in control,
        // or set the external listener if long presses are disabled.
        if (isLongPressHandlingEnabled && touchInputHandler != null) {
            // Re-assert that TouchInputHandler is the touch listener.
            // This handles cases where user might try to set a different OnTouchListener
            // after initial setup when long presses are enabled.
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

    @Override
    public CharSequence getAccessibilityClassName() {
        return android.widget.ImageButton.class.getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(android.widget.ImageButton.class.getName());
    }
}
