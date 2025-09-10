package com.bald.uriah.baldphone.views;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.util.Log; // Added for logging

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView; // Extends AppCompatTextView

// Imports for new helper and config
import app.baldphone.neo.buttons.TouchInputHandler;
import app.baldphone.neo.buttons.ViewTouchHandlerFactory; // Correct path

import com.bald.uriah.baldphone.utils.BPrefs;
import com.bald.uriah.baldphone.utils.D;
import app.baldphone.neo.helpers.VibratorHelper; // Assuming this is your VibratorHelper

public class BaldButton extends AppCompatTextView implements BaldButtonInterface, TouchInputHandler.HapticFeedbackProvider {

    // TAG for logging
    private static final String TAG = BaldButton.class.getSimpleName();

    private TouchInputHandler touchInputHandler;
    private SharedPreferences sharedPreferences;
    private boolean isLongPressHandlingEnabled; // To manage listener behavior
    private boolean vibrationFeedbackGloballyEnabled; // To gate actual vibration

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

        // Initialize isLongPressHandlingEnabled and vibrationFeedbackGloballyEnabled
        this.isLongPressHandlingEnabled = sharedPreferences.getBoolean(
                BPrefs.LONG_PRESSES_KEY, BPrefs.LONG_PRESSES_DEFAULT_VALUE);
        this.vibrationFeedbackGloballyEnabled = sharedPreferences.getBoolean(
                BPrefs.VIBRATION_FEEDBACK_KEY, BPrefs.VIBRATION_FEEDBACK_DEFAULT_VALUE);

        // Use the ViewTouchHandlerFactory to initialize the TouchInputHandler
        this.touchInputHandler = ViewTouchHandlerFactory.initViewTouchHandler(
                this, context, this.sharedPreferences);

        // Set this button as the HapticFeedbackProvider for the TouchInputHandler
        this.touchInputHandler.setHapticFeedbackProvider(this);

        // Standard view setup
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
        // This method is called by the HapticFeedbackProvider implementation.
        // It checks the global vibration setting.
        if (this.vibrationFeedbackGloballyEnabled) {
            VibratorHelper.vibrate(D.vibetime); // Or VibratorHelper.performSubtleVibration(getContext());
        }
    }

    // --- TouchInputHandler.HapticFeedbackProvider Implementation ---
    @Override
    public void requestHapticFeedback(TouchInputHandler.HapticFeedbackProvider.HapticType type) {
        // Potentially use different vibration patterns based on HapticType in the future.
        // For now, all types trigger the same generic vibration.
        Log.d(TAG, "Haptic feedback requested for type: " + type.name());
        this.vibrate(); // Calls the button's own vibrate method
    }

    // --- Listener Setters ---
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

    // --- SharedPreferences Handling ---
    /**
     * Call this method when shared preferences that affect button behavior might have changed.
     * This will update the button's internal state and reconfigure the TouchInputHandler.
     */
    public void onSharedPreferencesChanged() { // Renamed from onSharedPreferenceChanged for clarity if it's a custom call point
        if (this.sharedPreferences == null) {
            this.sharedPreferences = getContext().getSharedPreferences(D.BALD_PREFS, Context.MODE_PRIVATE);
        }

        this.isLongPressHandlingEnabled = sharedPreferences.getBoolean(
                BPrefs.LONG_PRESSES_KEY, BPrefs.LONG_PRESSES_DEFAULT_VALUE);
        this.vibrationFeedbackGloballyEnabled = sharedPreferences.getBoolean(
                BPrefs.VIBRATION_FEEDBACK_KEY, BPrefs.VIBRATION_FEEDBACK_DEFAULT_VALUE);

        if (touchInputHandler != null) {
            ViewTouchHandlerFactory.onSharedPreferenceChanged(this, touchInputHandler, sharedPreferences);
        }
        Log.d(TAG, "onSharedPreferencesChanged: isLongPressHandlingEnabled=" + isLongPressHandlingEnabled +
                ", vibrationFeedbackGloballyEnabled=" + vibrationFeedbackGloballyEnabled);
    }

    // Optional: Expose the internal click listener from TouchInputHandler
    @Nullable
    public OnClickListener getOnClickListenerFromHandler() {
        if (this.touchInputHandler != null) {
            return this.touchInputHandler.getOnClickListener();
        }
        return null;
    }
    // Optional: Expose the internal long click listener from TouchInputHandler
    @Nullable
    public OnLongClickListener getOnLongClickListenerFromHandler() {
        if (this.touchInputHandler != null) {
            return this.touchInputHandler.getOnLongClickListener();
        }
        return null;
    }
}
