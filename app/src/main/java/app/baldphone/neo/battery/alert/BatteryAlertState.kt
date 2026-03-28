package app.baldphone.neo.battery.alert

import android.content.Context
import android.content.SharedPreferences

import androidx.core.content.edit

/**
 * Manages the persistent internal state of the battery alert system.
 *
 *  State is persisted to SharedPreferences rather than in-memory variables because Android
 *  can kill the background processes. This ensures the cooldown time
 *  ([app.baldphone.neo.battery.alert.BatteryAlertPolicy.REPEAT_INTERVAL_MINUTES]) and dismissal
 *  state survive process death, preventing duplicate alerts within the same cooldown cycle.
 */
object BatteryAlertState {
    private const val STATE_PREFS_NAME = "battery_alert_internal_state"
    private const val KEY_LAST_EVAL_MS = "last_alert_eval_ms"
    private const val KEY_IS_DISMISSED = "is_alert_dismissed"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(STATE_PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Gets the timestamp (ms) of the last alert evaluation.
     */
    fun getLastEvalTimeMs(context: Context): Long = getPrefs(context).getLong(KEY_LAST_EVAL_MS, 0L)

    /**
     * Records the timestamp of a successful alert evaluation.
     */
    fun updateLastEvalTimeMs(
        context: Context,
        timeMs: Long,
    ) {
        getPrefs(context).edit { putLong(KEY_LAST_EVAL_MS, timeMs) }
    }

    /**
     * Checks if the user has dismissed the battery alert for the current cycle.
     */
    fun isAlertDismissed(context: Context): Boolean = getPrefs(context).getBoolean(KEY_IS_DISMISSED, false)

    /**
     * Updates the dismissal state of the alert.
     */
    fun setAlertDismissed(
        context: Context,
        dismissed: Boolean,
    ) {
        getPrefs(context).edit { putBoolean(KEY_IS_DISMISSED, dismissed) }
    }

    /**
     * Fully resets the internal state.
     */
    fun clear(context: Context) {
        getPrefs(context).edit { clear() }
    }
}
