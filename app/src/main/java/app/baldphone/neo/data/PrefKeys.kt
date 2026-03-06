package app.baldphone.neo.data

import com.bald.uriah.baldphone.utils.BPrefs

/**
 * A centralized object for storing all SharedPreferences keys and their default values.
 */
object PrefKeys {
    // General Prefs
    const val PREFS_NAME = "baldPrefs"
    const val PREFS_VERSION_KEY = "prefs_version"
    const val CURRENT_PREFS_VERSION = 1

    // Theme / UI
    const val THEME_KEY = "theme"
    const val KEY_LOCALE = "locale"

    const val KEY_ACCESSIBILITY_LEVEL = "accessibility_level"

    // Haptic feedback
    const val KEY_VIBRATION_FEEDBACK = BPrefs.VIBRATION_FEEDBACK_KEY

    // Dialer
    const val KEY_CALL_CONFIRMATION = "CALL_CONFIRMATION_KEY"
    const val KEY_DIALER_SOUNDS = "DIALER_SOUNDS_KEY"
    const val DEFAULT_DIALER_SOUNDS = true

    const val KEY_DUAL_SIM_MODE = "DUAL_SIM_KEY"
    const val DEFAULT_DUAL_SIM_MODE = false

    // Contact Settings
    const val KEY_CALL_LOG_VISIBLE = "contact_call_log_visible"
    const val KEY_COMBINE_DUPLICATE_CALLS = "combine_duplicate_calls"

    // System / UI
    const val KEY_STATUS_BAR = "status_bar_mode"
    const val KEY_USE_ACCIDENTAL_GUARD = "USE_ACCIDENTAL_GUARD_KEY"

    // AssistTouch press timing
    const val SHORT_PRESS_DURATION_MS_KEY: String = "short_press_duration_ms"
    const val LONG_PRESS_DURATION_MS_KEY: String = "long_press_duration_ms"
    const val SHOW_PRESS_LONGER_HINT_KEY: String = "show_press_longer_hint"
}
