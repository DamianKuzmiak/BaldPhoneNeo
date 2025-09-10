package app.baldphone.neo.buttons;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import com.bald.uriah.baldphone.R;
import com.bald.uriah.baldphone.utils.BaldToast;


public class TouchInputHandler {

    private static final String TAG = TouchInputHandler.class.getSimpleName();

    private View hostView;
    private final Context context;
    private ViewTouchHandlerConfig config;

    private View.OnClickListener onClickListenerExternal;
    private View.OnLongClickListener onLongClickListenerExternal;
    private HapticFeedbackProvider hapticFeedbackProvider; // New listener for haptics

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isDown;
    private boolean veryLongPressFired;
    private boolean longerPressFiredEarly; // For "longer press" when no very long click listener
    private long downEventTime;

    /**
     * Interface for the host view (e.g., a BaldButton) to provide to TouchInputHandler
     * if it wants to receive callbacks to trigger haptic feedback at appropriate times.
     */
    public interface HapticFeedbackProvider {
        enum HapticType {
            TAP,                // Corresponds to a standard click/tap
            LONGER_PRESS,       // Corresponds to the "longer press"
            VERY_LONG_PRESS     // Corresponds to the "very long press"
        }
        void requestHapticFeedback(HapticType type);
    }

    public TouchInputHandler(
            View hostView,
            Context context,
            ViewTouchHandlerConfig config
    ) {
        this.hostView = hostView;
        this.context = context.getApplicationContext();
        updateConfig(config); // Use updateConfig to set initial config and apply it
    }

    /**
     * Updates the handler's behavior with a new configuration.
     * @param newConfig The new configuration to apply.
     */
    public void updateConfig(ViewTouchHandlerConfig newConfig) {
        this.config = newConfig;
        // If config changes, it might affect ongoing gestures, but usually config is set once.
        // If a gesture is in progress and config changes, behavior might be unpredictable for that one gesture.
        // For simplicity, we don't cancel pending actions here, assuming config updates are not frequent during active touches.
    }

    /**
     * Sets a provider that will be called when haptic feedback should be triggered.
     * @param provider The HapticFeedbackProvider implementation.
     */
    public void setHapticFeedbackProvider(@Nullable HapticFeedbackProvider provider) {
        this.hapticFeedbackProvider = provider;
    }

    private final Runnable longerPressRunnable = // For "longer press" (early fire click)
            () -> {
                if (hostView == null || !isDown || veryLongPressFired || !config.longPressesEnabled) {
                    return;
                }
                // This is the "longer press" action: triggers onClickListener if no onLongClickListener is set.
                if (onClickListenerExternal != null && onLongClickListenerExternal == null) {
                    if (hapticFeedbackProvider != null) {
                        hapticFeedbackProvider.requestHapticFeedback(HapticFeedbackProvider.HapticType.LONGER_PRESS);
                    }

                    // 1. Call performClick() on the host view for accessibility and system behaviors.
                    if (hostView != null && hostView.isEnabled()) { // Good practice to check
                        hostView.performClick();
                    }

                    // 2. Explicitly call the stored external listener.
                    //    This is necessary because hostView's own OnClickListener was likely nulled out
                    //    by ViewTouchHandlerFactory when longPressesEnabled is true.
                    onClickListenerExternal.onClick(hostView);

                    longerPressFiredEarly = true;
                }
            };

    private final Runnable veryLongPressRunnable = // For "very long press"
            () -> {
                if (hostView == null || !isDown || !config.longPressesEnabled) {
                    return;
                }
                // "Very long press" only fires if an OnLongClickListener is set.
                if (onLongClickListenerExternal != null) {
                    if (hapticFeedbackProvider != null) {
                        hapticFeedbackProvider.requestHapticFeedback(HapticFeedbackProvider.HapticType.VERY_LONG_PRESS);
                    }
                    onLongClickListenerExternal.onLongClick(hostView);
                    veryLongPressFired = true;
                }
            };


    public void setOnClickListener(@Nullable View.OnClickListener listener) {
        this.onClickListenerExternal = listener;
    }

    @Nullable
    public View.OnClickListener getOnClickListener() { // Renamed for consistency
        return this.onClickListenerExternal;
    }

    public void setOnLongClickListener(@Nullable View.OnLongClickListener listener) {
        this.onLongClickListenerExternal = listener;
        // If a new long click listener is set, re-evaluate pending short press runnable
        // This is complex as a gesture might be in progress. Simpler to assume this is set before interaction.
    }
    @Nullable
    public View.OnLongClickListener getOnLongClickListener() {
        return this.onLongClickListenerExternal;
    }


    public boolean onTouchEvent(MotionEvent event) {
        if (!hostView.isEnabled()) {
            return false; // View is disabled, do nothing.
        }

        // If longPressesEnabled is false, this handler should not perform its custom logic.
        // It returns false to allow standard Android touch processing.
        if (!config.longPressesEnabled) {
            cancelPendingActions(); // Ensure any stray runnables are cleared if config changed mid-gesture
            return false;
        }

        switch (event.getActionMasked()) { // Use getActionMasked for pointer events
            case MotionEvent.ACTION_DOWN:
                isDown = true;
                veryLongPressFired = false;
                longerPressFiredEarly = false;
                downEventTime = SystemClock.elapsedRealtime();

                handler.removeCallbacks(longerPressRunnable);
                handler.removeCallbacks(veryLongPressRunnable);

                // Schedule "very long press" if a listener is set
                if (onLongClickListenerExternal != null) {
                    handler.postDelayed(veryLongPressRunnable, config.longPressTimeoutMs);
                }
                // Schedule "longer press" (early click) if no "very long press" listener is set, but a click listener is.
                // This will effectively be an "early" click if held for shortPressTimeoutMs.
                else if (onClickListenerExternal != null) {
                    handler.postDelayed(longerPressRunnable, config.shortPressTimeoutMs);
                }
                return true; // Consume the event as we are handling it.

            case MotionEvent.ACTION_MOVE:
                // Basic movement cancellation: if finger moves too far, cancel pending actions.
                // This is a simplified version; a more robust one would use ViewConfiguration.get(context).getScaledTouchSlop().
                // For now, let's assume movement cancellation is handled by the view becoming un-pressed or similar.
                // If a more specific move cancellation is needed, it can be added here.
                // Example:
                // float x = event.getX();
                // float y = event.getY();
                // if (x < 0 || x > hostView.getWidth() || y < 0 || y > hostView.getHeight()) { // Moved out of bounds
                //    if(isDown) cancelPendingActions();
                // }
                break; // Don't consume move by default unless explicitly handled

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!isDown) { // Event received without a preceding ACTION_DOWN
                    return event.getActionMasked() == MotionEvent.ACTION_UP; // Consume UP to prevent issues, but not CANCEL.
                }
                isDown = false; // Mark that finger is up or gesture cancelled

                boolean wasVeryLongPress = veryLongPressFired;
                boolean wasLongerPressEarly = longerPressFiredEarly;

                handler.removeCallbacks(longerPressRunnable);
                handler.removeCallbacks(veryLongPressRunnable);

                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    return true; // Consume cancel
                }

                // If a "very long press" or an "early longer press" already fired, we're done for this gesture.
                if (wasVeryLongPress || wasLongerPressEarly) {
                    return true;
                }

                long pressDuration = SystemClock.elapsedRealtime() - downEventTime;

                // Standard Tap/Click logic:
                // A click occurs if it's not a "very long press" (already handled)
                // and not an "early longer press" (already handled),
                // and the duration is sufficient for a tap.
                if (onClickListenerExternal != null) {
                    // Check if duration qualifies as a tap (e.g., >= system tap timeout but less than timeouts that triggered other actions)
                    // The ViewConfiguration.getTapTimeout() is the duration before which a tap is considered "too short".
                    // A press that is shorter than shortPressTimeoutMs (if longerPressRunnable was posted)
                    // or shorter than longPressTimeoutMs (if veryLongPressRunnable was posted) could be a tap.
                    boolean isTap = pressDuration >= ViewConfiguration.getTapTimeout();
                    if (onLongClickListenerExternal != null) { // If there's a VLP listener...
                        isTap &= (pressDuration < config.longPressTimeoutMs);
                    } else { // If only NLP listener (or just click listener)...
                        isTap &= (pressDuration < config.shortPressTimeoutMs);
                    }


                    if (isTap) {
                        if (hapticFeedbackProvider != null) {
                            hapticFeedbackProvider.requestHapticFeedback(HapticFeedbackProvider.HapticType.TAP);
                        }

                        // Call performClick() on the host view for accessibility and system behaviors.
                        if (hostView != null && hostView.isEnabled()) {
                            hostView.performClick();
                        }

                        if (onClickListenerExternal != null) {
                            onClickListenerExternal.onClick(hostView);
                        }
                    }
                }

                // "Press longer" hint logic
                if (config.showPressLongerHint && !wasVeryLongPress && !wasLongerPressEarly &&
                        (onClickListenerExternal != null || onLongClickListenerExternal != null)) {
                    // Show hint if press was shorter than the "longer press" timeout and also shorter than a system tap.
                    // This implies the user released very quickly.
                    if (pressDuration < config.shortPressTimeoutMs && pressDuration < ViewConfiguration.getTapTimeout()) {
                        BaldToast.from(context)
                                .setText(context.getText(R.string.press_longer))
                                .setType(BaldToast.TYPE_DEFAULT) // Assuming BaldToast types
                                .setLength(-1) // Or a short Toast.LENGTH_SHORT equivalent
                                .build()
                                .show();
                    }
                }
                return true; // Consume ACTION_UP
        }
        return false; // By default, don't consume if not explicitly handled.
    }

    private void cancelPendingActions() {
        handler.removeCallbacks(longerPressRunnable);
        handler.removeCallbacks(veryLongPressRunnable);
        // Reset flags related to an ongoing gesture
        isDown = false;
        veryLongPressFired = false;
        longerPressFiredEarly = false;
    }

    /**
     * Allows programmatic triggering of a click. This will invoke the OnClickListener.
     */
    public void programmaticClickTrigger() {
        if (onClickListenerExternal != null) {
            if (hapticFeedbackProvider != null) {
                // Programmatic clicks usually correspond to a TAP-like interaction
                hapticFeedbackProvider.requestHapticFeedback(HapticFeedbackProvider.HapticType.TAP);
            }
            hostView.performClick(); // For accessibility and sound
            if (onClickListenerExternal != null) {
                onClickListenerExternal.onClick(hostView); // Call the stored listener
            }
        }
    }
}
