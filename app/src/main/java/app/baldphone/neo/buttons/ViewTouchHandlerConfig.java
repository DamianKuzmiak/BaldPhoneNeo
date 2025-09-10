package app.baldphone.neo.buttons;

/**
 * Configuration for {@link TouchInputHandler}. This class encapsulates settings that control the
 * behavior of touch interactions handled by {@link TouchInputHandler}. These settings are typically
 * derived from SharedPreferences and passed to the handler.
 */
public class ViewTouchHandlerConfig {

    /**
     * If true, {@link TouchInputHandler} actively manages touch events to detect custom press
     * durations (e.g., "longer press", "very long press") based on the configured timeouts. If
     * false, {@link TouchInputHandler} disables its custom touch detection logic, and the view's
     * touch interactions will behave like standard Android views (handling normal clicks and long
     * clicks as defined by the system and any directly set listeners).
     */
    public final boolean longPressesEnabled;

    /**
     * The minimum duration (in milliseconds) a touch must be held to be considered a "longer
     * press". This timeout is relevant when {@code longPressesEnabled} is true and is used:
     * <li>As the threshold below which a "Press longer" hint may be shown (if {@code
     *     showPressLongerHint} is true).
     * <li>If no {@link android.view.View.OnLongClickListener} is set on the view, a press held for
     *     at least this duration (but less than {@code longPressTimeoutMs}) may trigger an "early"
     *     invocation of the {@link android.view.View.OnClickListener}.
     */
    public final int shortPressTimeoutMs; // Represents "longer press" timeout

    /**
     * The minimum duration (in milliseconds) a touch must be held for the {@link TouchInputHandler}
     * to recognize it as a "very long press". This is only effective if {@code longPressesEnabled}
     * is true AND an {@link android.view.View.OnLongClickListener} is set on the view; the listener
     * will be invoked after this timeout. If no {@link android.view.View.OnLongClickListener} is
     * set, this timeout has no effect within {@link TouchInputHandler}.
     */
    public final int longPressTimeoutMs; // Represents "very long press" timeout

    /**
     * If true (and {@code longPressesEnabled} is true), {@link TouchInputHandler} will request to
     * show a visual hint (e.g., a Toast) prompting the user to "Press longer" if a touch event is
     * released too quickly (typically, if the duration is less than {@code shortPressTimeoutMs} and
     * also less than the system's tap timeout). This is only relevant if there's an {@link
     * android.view.View.OnClickListener} or {@link android.view.View.OnLongClickListener} set.
     */
    public final boolean showPressLongerHint;

    // hapticsEnabledInHandler field is removed as per new requirements.
    // Haptic feedback will be managed via a listener mechanism in TouchInputHandler.

    public ViewTouchHandlerConfig(
            boolean longPressesEnabled,
            int shortPressTimeoutMs, // "longer press" timeout
            int longPressTimeoutMs, // "very long press" timeout
            boolean showPressLongerHint) {
        this.longPressesEnabled = longPressesEnabled;
        this.shortPressTimeoutMs = shortPressTimeoutMs;
        this.longPressTimeoutMs = longPressTimeoutMs;
        this.showPressLongerHint = showPressLongerHint;
    }
}
