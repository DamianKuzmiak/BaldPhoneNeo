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

    // Theme
    const val THEME_KEY = "theme"

    // Setup Process
    const val KEY_SETUP_COMPLETED = "setup.is_completed" // Default: false
    const val KEY_SETUP_SKIPPED = "setup.is_skipped" // Default: false
    const val KEY_PENDING_DEFAULT_LAUNCHER = "setup.temp.pending_launcher_change" // Default: false
    const val KEY_LAST_SETUP_FRAGMENT = "setup.last_setup_fragment"
    const val DEFAULT_LAST_SETUP_FRAGMENT = 0

    const val KEY_ACCESSIBILITY_LEVEL = "accessibility_level"

    // Legacy BPrefs keys
    const val KEY_TOUCH_NOT_HARD = BPrefs.TOUCH_NOT_HARD_KEY
    const val KEY_LONG_PRESSES_SHORTER = BPrefs.LONG_PRESSES_SHORTER_KEY
    const val KEY_VIBRATION_FEEDBACK = BPrefs.VIBRATION_FEEDBACK_KEY
    const val KEY_LONG_PRESSES = BPrefs.LONG_PRESSES_KEY

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
    const val KEY_STATUS_BAR = BPrefs.STATUS_BAR_KEY
    const val KEY_USE_ACCIDENTAL_GUARD = "USE_ACCIDENTAL_GUARD_KEY"
}
