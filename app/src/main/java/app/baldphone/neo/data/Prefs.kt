package app.baldphone.neo.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64

import androidx.core.content.edit

import app.baldphone.neo.launcher.data.HomeSlot

import com.bald.uriah.baldphone.utils.BPrefs

object Prefs {
    private const val XOR_KEY = 0x5A

    private lateinit var storage: SharedPreferences

    fun init(context: Context) {
        val applicationContext = context.applicationContext
        storage = applicationContext.getSharedPreferences(PrefKeys.PREFS_NAME, Context.MODE_PRIVATE)

        // Bind the same store used by the property delegates (see PreferenceDelegate.kt).
        PreferenceDelegate.bind(storage)

        val version = storage.getInt(PrefKeys.PREFS_VERSION_KEY, 0)
        if (version < PrefKeys.CURRENT_PREFS_VERSION) {
            storage.edit { putInt(PrefKeys.PREFS_VERSION_KEY, PrefKeys.CURRENT_PREFS_VERSION) }
        }
    }

    // Appearance and UI related preferences.

    /**
     * Stored language tag (e.g., "pl", "en"). Set empty if the application should follow the system language.
     */
    var locale: String? by stringPref(PrefKeys.KEY_LOCALE, "")

    /**
     * Determines how the status bar is displayed: Hidden, Only Home Screen or Everywhere
     */
    @JvmStatic
    var statusBarMode by enumPref(
        PrefKeys.KEY_STATUS_BAR,
        StatusBarMode.EVERYWHERE,
        StatusBarMode::fromValue,
        StatusBarMode::value
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
            if (storage.contains(PrefKeys.KEY_ACCESSIBILITY_LEVEL)) {
                return AccessibilityLevel.fromValue(
                    storage.getInt(
                        PrefKeys.KEY_ACCESSIBILITY_LEVEL,
                        0
                    )
                )
            }
            // Fallback to legacy boolean flags
            return when {
                storage.getBoolean(
                    BPrefs.TOUCH_NOT_HARD_KEY,
                    !BPrefs.LONG_PRESSES_DEFAULT_VALUE
                ) -> AccessibilityLevel.BASIC

                storage.getBoolean(BPrefs.LONG_PRESSES_SHORTER_KEY, false) -> AccessibilityLevel.ENHANCED

                else -> AccessibilityLevel.FULL
            }
        }
        set(level) {
            storage.edit {
                putInt(PrefKeys.KEY_ACCESSIBILITY_LEVEL, level.value)
                // Still used by BPrefs and legacy code
                val isNotBasic = level != AccessibilityLevel.BASIC
                putBoolean(BPrefs.LONG_PRESSES_KEY, isNotBasic)
                putBoolean(BPrefs.LONG_PRESSES_SHORTER_KEY, level == AccessibilityLevel.ENHANCED)
                putBoolean(BPrefs.TOUCH_NOT_HARD_KEY, level == AccessibilityLevel.BASIC)
            }
        }

    /**
     * Controls whether the app provides haptic feedback on back button press.
     */
    @JvmStatic
    var isVibrationFeedbackEnabled: Boolean by booleanPref(
        PrefKeys.KEY_VIBRATION_FEEDBACK,
        BPrefs.VIBRATION_FEEDBACK_DEFAULT_VALUE
    )

    /**
     * Timeout for short press (longer press) duration in milliseconds.
     */
    @JvmStatic
    var shortPressTimeoutMs: Int by intPref(PrefKeys.SHORT_PRESS_DURATION_MS_KEY, 300)

    /**
     * Timeout for long press duration in milliseconds.
     */
    @JvmStatic
    var longPressTimeoutMs: Int by intPref(PrefKeys.LONG_PRESS_DURATION_MS_KEY, 1000)

    /**
     * Controls whether a hint is shown when a press is released too quickly.
     */
    @JvmStatic
    var showPressLongerHint: Boolean by booleanPref(PrefKeys.SHOW_PRESS_LONGER_HINT_KEY, true)

    /**
     * Protects against accidental touches by using the proximity sensor.
     */
    @JvmStatic
    var useAccidentalGuard: Boolean by booleanPref(
        PrefKeys.KEY_USE_ACCIDENTAL_GUARD,
        true
    )

    // Communication (Calls & Dialer) related preferences.

    /**
     * Controls whether audible feedback (DTMF tones) is played when interacting with the dialer.
     */
    var areDialerSoundsEnabled: Boolean by booleanPref(
        PrefKeys.KEY_DIALER_SOUNDS,
        PrefKeys.DEFAULT_DIALER_SOUNDS
    )

    /**
     * Controls whether call logs are expanded by default
     * in the [app.baldphone.neo.features.contacts.ui.details.ContactDetailsActivity].
     */
    var isCallLogVisible: Boolean by booleanPref(
        PrefKeys.KEY_CALL_LOG_VISIBLE,
        false
    )

    /**
     * Controls whether identical consecutive calls are merged into a single entry in the log.
     */
    var isCombineDuplicateCallsEnabled: Boolean by booleanPref(PrefKeys.KEY_COMBINE_DUPLICATE_CALLS, true)

    /**
     * If true, the dialog for choosing a SIM will be shown when calling.
     */
    var isDualSimActive: Boolean by booleanPref(PrefKeys.KEY_DUAL_SIM_MODE, false)

    /**
     * Whether a confirmation dialog should be shown before making a call.
     */
    var shouldConfirmCalls: Boolean by booleanPref(PrefKeys.KEY_CALL_CONFIRMATION, false)

    // Custom App Mappings (Home Screen)

    /**
     * Returns the component name for the given [slot], or null if not customized.
     */
    fun getCustomApp(slot: HomeSlot): String? = storage.getString(slot.key, null)

    /**
     * Sets or removes the customized app for the given [slot].
     */
    fun setCustomApp(slot: HomeSlot, componentName: String?) {
        storage.edit {
            if (componentName != null) {
                putString(slot.key, componentName)
            } else {
                remove(slot.key)
            }
        }
    }

    /**
     * Returns true if the [slot] has a custom app assigned.
     */
    fun isCustomized(slot: HomeSlot): Boolean = storage.contains(slot.key)

    /**
     * Controls whether the notes page is visible on the home screen.
     */
    @JvmStatic
    var noteVisible: Boolean by booleanPref(PrefKeys.KEY_NOTE_VISIBLE, BPrefs.NOTE_VISIBLE_DEFAULT_VALUE)

    /**
     * Controls whether the user's phone number is displayed in the notification area on the home screen.
     */
    var isHomePhoneNumberEnabled: Boolean by booleanPref(PrefKeys.KEY_HOME_PHONE_ENABLED, true)

    /**
     * User phone number to display on the home page notification area.
     */
    var homePhoneNumber: String? by obfuscatedStringPref(PrefKeys.KEY_HOME_PHONE_NUMBER, null)

    // Helper methods.

    private fun obfuscatedStringPref(key: String, default: String?) =
        obfuscatedStringPref(
            key,
            default,
            onDecode = { stored ->
                // Fallback: if the value looks like a plain text phone number, return as is
                if (stored.startsWith("+") || stored.firstOrNull()?.isDigit() == true) {
                    stored
                } else {
                    stored.deobfuscate()
                }
            },
            onEncode = { it.obfuscate() }
        )

    /** Lightweight obfuscation (XOR + Base64), it only prevents casual reading of stored values. */
    private fun String.obfuscate(): String {
        val xored = map { (it.code xor XOR_KEY).toChar() }.joinToString("")
        return Base64.encodeToString(xored.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    /** Returns the original string unchanged if it cannot be decoded. */
    private fun String.deobfuscate(): String =
        try {
            val decodedBytes = Base64.decode(this, Base64.NO_WRAP)
            val decodedStr = String(decodedBytes, Charsets.UTF_8)
            decodedStr.map { (it.code xor XOR_KEY).toChar() }.joinToString("")
        } catch (_: Exception) {
            this
        }
}
