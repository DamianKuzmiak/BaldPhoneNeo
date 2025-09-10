package com.bald.uriah.baldphone.views;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.util.Log; // Added for logging

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

// Imports for new helper and config
import app.baldphone.neo.buttons.TouchInputHandler;
import app.baldphone.neo.buttons.ViewTouchHandlerFactory;

import com.bald.uriah.baldphone.utils.BPrefs;
import com.bald.uriah.baldphone.utils.D;
import app.baldphone.neo.helpers.VibratorHelper; // Assuming this is your VibratorHelper

public class BaldImageButton extends AppCompatImageView implements BaldButtonInterface, TouchInputHandler.HapticFeedbackProvider {

    // TAG for logging, consistent with BaldButtonInterface
    // private static final String TAG = BaldButtonInterface.TAG; // Or define locally:
    private static final String TAG = BaldImageButton.class.getSimpleName();

    private TouchInputHandler touchInputHandler;
    private SharedPreferences sharedPreferences;
    private boolean isLongPressHandlingEnabled; // To manage listener behavior
    private boolean vibrationFeedbackGloballyEnabled; // To gate actual vibration

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

        // Initialize isLongPressHandlingEnabled and vibrationFeedbackGloballyEnabled
        this.isLongPressHandlingEnabled = sharedPreferences.getBoolean(
                BPrefs.LONG_PRESSES_KEY, BPrefs.LONG_PRESSES_DEFAULT_VALUE);
        this.vibrationFeedbackGloballyEnabled = sharedPreferences.getBoolean(
                BPrefs.VIBRATION_FEEDBACK_KEY, BPrefs.VIBRATION_FEEDBACK_DEFAULT_VALUE);

        // Use the ViewTouchHandlerFactory to initialize the TouchInputHandler
        this.touchInputHandler = ViewTouchHandlerFactory.initViewTouchHandler(
                this, context, this.sharedPreferences);

        // Set this button as the HapticFeedbackProvider for the TouchInputHandler
        if (this.touchInputHandler != null) {
            this.touchInputHandler.setHapticFeedbackProvider(this);
        }

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
            // Fallback if touchInputHandler is somehow null, though unlikely with current setup
            Log.w(TAG, "TouchInputHandler not initialized, programmatic click might not work as expected.");
            super.performClick();
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

    // --- SharedPreferences Handling ---
    /**
     * Call this method when shared preferences that affect button behavior might have changed.
     * This will update the button's internal state and reconfigure the TouchInputHandler.
     */
    public void onSharedPreferencesChanged() {
        if (this.sharedPreferences == null) {
            // This might happen if called before commonInit, though unlikely.
            this.sharedPreferences = getContext().getSharedPreferences(D.BALD_PREFS, Context.MODE_PRIVATE);
        }

        // Update local flags based on the latest preferences
        this.isLongPressHandlingEnabled = sharedPreferences.getBoolean(
                BPrefs.LONG_PRESSES_KEY, BPrefs.LONG_PRESSES_DEFAULT_VALUE);
        this.vibrationFeedbackGloballyEnabled = sharedPreferences.getBoolean(
                BPrefs.VIBRATION_FEEDBACK_KEY, BPrefs.VIBRATION_FEEDBACK_DEFAULT_VALUE);

        // Notify the ViewTouchHandlerFactory about the preference change
        if (touchInputHandler != null) { // touchInputHandler could be null if commonInit failed
            ViewTouchHandlerFactory.onSharedPreferenceChanged(this, touchInputHandler, sharedPreferences);
        }

        // If the primary OnTouchListener was managed by ViewTouchHandlerFactory,
        // calling ViewTouchHandlerFactory.onSharedPreferenceChanged would have re-evaluated
        // and set it. However, if the user called setOnTouchListener directly AFTER init
        // and IS_LONG_PRESS_HANDLING_ENABLED was true at that point, our setOnTouchListener
        // would re-assert the VTH. If IS_LONG_PRESS_HANDLING_ENABLED changed from true to false,
        // ViewTouchHandlerFactory.onSharedPreferenceChanged -> configureViewTouchInteraction
        // will setOnTouchListener(null). The user's listener (if set via setOnTouchListener)
        // would have been on 'super' and would become active.
        // If it changed from false to true, configureViewTouchInteraction sets VTH.
        // The logic in setOnTouchListener also needs to be robust for calls *after* init.
        // The current implementation of setOnTouchListener re-evaluates based on isLongPressHandlingEnabled.
        // It's important to ensure that if isLongPressHandlingEnabled changes, the correct
        // OnTouchListener (either VTH or a user-set one) is active.
        // The ViewTouchHandlerFactory.onSharedPreferenceChanged already calls configureViewTouchInteraction,
        // which sets the main OnTouchListener on the view.
        // The setOnTouchListener method in this class mostly ensures that if the user
        // tries to set a listener, it respects the isLongPressHandlingEnabled state at that moment.
        // One final check: if isLongPressHandlingEnabled became true, and a user had previously
        // set a custom OnTouchListener (when it was false), configureViewTouchInteraction
        // will correctly override it with VTH. If it became false, configureViewTouchInteraction
        // sets OnTouchListener to null, and the super.setOnTouchListener (if called by user) would be active.

        Log.d(TAG, "onSharedPreferencesChanged: isLongPressHandlingEnabled=" + isLongPressHandlingEnabled +
                ", vibrationFeedbackGloballyEnabled=" + vibrationFeedbackGloballyEnabled);
    }

    // Optional: Expose the internal click listener from TouchInputHandler if needed by other parts of your app
    @Nullable
    public OnClickListener getOnClickListenerFromHandler() {
        if (this.touchInputHandler != null) {
            return this.touchInputHandler.getOnClickListener();
        }
        return null;
    }
}
