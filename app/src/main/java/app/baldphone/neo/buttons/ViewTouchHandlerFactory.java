package app.baldphone.neo.buttons;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import com.bald.uriah.baldphone.utils.BPrefs;

public class ViewTouchHandlerFactory {

    /**
     * Initializes a {@link TouchInputHandler} for the given view. It reads configurations from
     * SharedPreferences and instantiates the {@link TouchInputHandler}. It also configures the
     * view's primary touch listener based on the preferences.
     *
     * @param view The View instance that will use the touch handler.
     * @param context The Context.
     * @param sharedPreferences The {@link SharedPreferences} instance for this application.
     * @return The initialized {@link TouchInputHandler}.
     */
    public static TouchInputHandler initViewTouchHandler(
            View view, Context context, SharedPreferences sharedPreferences) {
        ViewTouchHandlerConfig config = buildConfigFromPrefs(sharedPreferences);
        TouchInputHandler touchInputHandler = new TouchInputHandler(view, context, config);

        // Configures the view's android.view.View.OnTouchListener. If useHandlerForTouches is true,
        // TouchInputHandler manages touch events. Otherwise, the view's default touch handling is
        // used.
        if (config.longPressesEnabled) {
            view.setOnTouchListener((v, event) -> touchInputHandler.onTouchEvent(event));

            // When TouchInputHandler is active, nullify standard listeners on the view
            // to prevent conflicts. The TouchInputHandler will invoke external listeners itself.
            view.setOnClickListener(null);
            view.setOnLongClickListener(null);
        } else {
            // Revert to default view touch handling. Standard
            // setOnClickListener/setOnLongClickListener
            // calls on the button will now use the superclass implementations directly.
            view.setOnTouchListener(null);
        }

        return touchInputHandler;
    }

    /**
     * Handles changes in {@link SharedPreferences}. It creates a new {@link
     * ViewTouchHandlerConfig}, updates the {@link TouchInputHandler} with this new configuration,
     * and re-configures the view's touch interaction.
     *
     * @param view The View instance.
     * @param touchInputHandler The {@link TouchInputHandler} associated with the view.
     * @param sharedPreferences The {@link SharedPreferences} instance.
     */
    public static void onSharedPreferenceChanged(
            View view, TouchInputHandler touchInputHandler, SharedPreferences sharedPreferences) {
        if (touchInputHandler != null) {
            ViewTouchHandlerConfig newConfig = buildConfigFromPrefs(sharedPreferences);
            touchInputHandler.updateConfig(newConfig);

            // Re-apply touch interaction setup based on the new config's longPressesEnabled state
            if (newConfig.longPressesEnabled) {
                view.setOnTouchListener((v, event) -> touchInputHandler.onTouchEvent(event));
                view.setOnClickListener(null);
                view.setOnLongClickListener(null);
            } else {
                view.setOnTouchListener(null);
            }
        }
    }

    /**
     * Creates a {@link ViewTouchHandlerConfig} instance by reading values from the provided {@link
     * SharedPreferences}.
     *
     * @param prefs The SharedPreferences instance to read from.
     * @return A new {@link ViewTouchHandlerConfig} populated with values from preferences.
     */
    private static ViewTouchHandlerConfig buildConfigFromPrefs(SharedPreferences prefs) {
        boolean longPressesEnabled =
                prefs.getBoolean(BPrefs.LONG_PRESSES_KEY, BPrefs.LONG_PRESSES_DEFAULT_VALUE);

        // For "longer press" timeout
        int shortPressTimeoutMs =
                prefs.getInt(
                        BPrefs.SHORT_PRESS_DURATION_MS_KEY,
                        BPrefs.SHORT_PRESS_DURATION_MS_DEFAULT_VALUE);

        // For "very long press" timeout
        int longPressTimeoutMs =
                prefs.getInt(
                        BPrefs.LONG_PRESS_DURATION_MS_KEY,
                        BPrefs.LONG_PRESS_DURATION_MS_DEFAULT_VALUE);

        boolean showPressLongerHint =
                prefs.getBoolean(
                        BPrefs.SHOW_PRESS_LONGER_HINT_KEY,
                        BPrefs.SHOW_PRESS_LONGER_HINT_DEFAULT_VALUE);

        return new ViewTouchHandlerConfig(
                longPressesEnabled, shortPressTimeoutMs, longPressTimeoutMs, showPressLongerHint);
    }
}
