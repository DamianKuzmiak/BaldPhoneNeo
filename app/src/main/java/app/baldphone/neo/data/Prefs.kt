package app.baldphone.neo.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

import androidx.core.content.edit

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

import com.bald.uriah.baldphone.utils.BPrefs

object Prefs {
    private const val TAG = "Prefs"
    private lateinit var prefs: SharedPreferences

    @JvmStatic
    fun init(context: Context) {
        val applicationContext = context.applicationContext
        prefs = applicationContext.getSharedPreferences(PrefKeys.PREFS_NAME, Context.MODE_PRIVATE)

        val version = prefs.getInt(PrefKeys.PREFS_VERSION_KEY, 0)
        if (version < PrefKeys.CURRENT_PREFS_VERSION) {
            prefs.edit { putInt(PrefKeys.PREFS_VERSION_KEY, PrefKeys.CURRENT_PREFS_VERSION) }
        }
    }

    // Appearance and UI related preferences.

    /**
     * Determines how the status bar is displayed: Hidden, Only Home Screen or Everywhere
     */
    @JvmStatic
    var statusBarMode by enumPref(
        PrefKeys.KEY_STATUS_BAR,
        StatusBarMode.EVERYWHERE,
        StatusBarMode::fromValue,
        StatusBarMode::value,
    )

    /**
     * Theme used by the application.
     */
    @JvmStatic
    var theme: Theme by enumPref(PrefKeys.THEME_KEY, Theme.SYSTEM, Theme::fromValue, Theme::value)

    // Accessibility related preferences.

    /**
     * Determines the level of accessibility support provided by the app.
     */
    @JvmStatic
    var accessibilityLevel: AccessibilityLevel
        get() {
            if (prefs.contains(PrefKeys.KEY_ACCESSIBILITY_LEVEL)) {
                return AccessibilityLevel.fromValue(
                    prefs.getInt(
                        PrefKeys.KEY_ACCESSIBILITY_LEVEL,
                        0,
                    ),
                )
            }
            // Fallback to legacy boolean flags
            return when {
                prefs.getBoolean(PrefKeys.KEY_TOUCH_NOT_HARD, true) -> AccessibilityLevel.BASIC

                prefs.getBoolean(
                    PrefKeys.KEY_LONG_PRESSES_SHORTER,
                    false,
                ) -> AccessibilityLevel.ENHANCED

                else -> AccessibilityLevel.FULL
            }
        }
        set(level) {
            prefs.edit {
                putInt(PrefKeys.KEY_ACCESSIBILITY_LEVEL, level.value)
                // Still used by BPrefs and legacy code
                val isNotBasic = level != AccessibilityLevel.BASIC
                putBoolean(PrefKeys.KEY_VIBRATION_FEEDBACK, isNotBasic)
                putBoolean(PrefKeys.KEY_LONG_PRESSES, isNotBasic)
                putBoolean(PrefKeys.KEY_LONG_PRESSES_SHORTER, level == AccessibilityLevel.ENHANCED)
                putBoolean(PrefKeys.KEY_TOUCH_NOT_HARD, level == AccessibilityLevel.BASIC)
            }
        }

    /**
     * Controls whether the app provides haptic feedback on back button press.
     */
    @get:JvmStatic
    var isVibrationFeedbackEnabled: Boolean by booleanPref(
        PrefKeys.KEY_VIBRATION_FEEDBACK,
        BPrefs.VIBRATION_FEEDBACK_DEFAULT_VALUE,
    )

    /**
     * Protects against accidental touches by using the proximity sensor.
     */
    @JvmStatic
    var useAccidentalGuard: Boolean by booleanPref(
        PrefKeys.KEY_USE_ACCIDENTAL_GUARD,
        true,
    )

    // Communication (Calls & Dialer) related preferences.

    /**
     * Controls whether audible feedback (DTMF tones) is played when interacting with the dialer.
     */
    var areDialerSoundsEnabled: Boolean by booleanPref(
        PrefKeys.KEY_DIALER_SOUNDS,
        PrefKeys.DEFAULT_DIALER_SOUNDS,
    )

    /**
     * Controls whether call logs are expanded by default
     * in the [app.baldphone.neo.features.contacts.ui.ContactDetailsActivity].
     */
    var isCallLogVisible: Boolean by booleanPref(
        PrefKeys.KEY_CALL_LOG_VISIBLE,
        false,
    )

    /**
     * If true, the dialog for choosing a SIM will be shown when calling.
     */
    var isDualSimActive: Boolean by booleanPref(
        PrefKeys.KEY_DUAL_SIM_MODE,
        PrefKeys.DEFAULT_DUAL_SIM_MODE,
    )

    // Helper functions for the delegate
    private fun booleanPref(
        key: String,
        default: Boolean,
    ) = PreferenceDelegate(
        key,
        default,
        SharedPreferences::getBoolean,
        SharedPreferences.Editor::putBoolean,
    )

    private fun <T> enumPref(
        key: String,
        default: T,
        fromInt: (Int) -> T,
        toInt: (T) -> Int,
    ) = PreferenceDelegate(
        key,
        default,
        getter = { p, k, d -> fromInt(p.getInt(k, toInt(d))) },
        setter = { e, k, v -> e.putInt(k, toInt(v)) },
    )

    private class PreferenceDelegate<T>(
        private val key: String,
        private val defaultValue: T,
        private val getter: (SharedPreferences, String, T) -> T,
        private val setter: (SharedPreferences.Editor, String, T) -> SharedPreferences.Editor,
    ) : ReadWriteProperty<Prefs, T> {
        override fun getValue(
            thisRef: Prefs,
            property: KProperty<*>,
        ): T =
            getter(thisRef.prefs, key, defaultValue).also {
                Log.v(TAG, "Read $key: $it")
            }

        override fun setValue(
            thisRef: Prefs,
            property: KProperty<*>,
            value: T,
        ) {
            Log.v(TAG, "Setting $key to $value")
            thisRef.prefs.edit { setter(this, key, value) }
        }
    }
}
