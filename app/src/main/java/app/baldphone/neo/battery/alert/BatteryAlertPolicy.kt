package app.baldphone.neo.battery.alert

import android.content.Context

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

import app.baldphone.neo.battery.BatteryRepository
import app.baldphone.neo.battery.BatteryState
import app.baldphone.neo.data.Prefs
import app.baldphone.neo.utils.AppLog

/**
 * Pure logic engine for reading battery state and determining alert actions.
 */
object BatteryAlertPolicy {
    private const val TAG = "BatteryAlertPolicy"

    const val REPEAT_INTERVAL_MINUTES = 30L
    private const val GRACE_PERIOD_MINUTES = 1
    private const val HYSTERESIS = 2

    private var lastPercentage: Int? = null
    private var lastIsCharging: Boolean? = null

    enum class Action { CLEAR, SUPPRESSED, ALERT, UNCHANGED }

    /**
     * Entry point for evaluation triggers (mainly Worker path).
     */
    fun processCheck(context: Context) {
        val state = BatteryRepository.get(context).freshSnapshot()
        processCheck(context, state)
    }

    /**
     * Entry point for reactive updates from BatteryRepository.
     */
    @Synchronized
    fun processCheck(
        context: Context,
        current: BatteryState,
    ) {
        val percentage =
            current.percentage ?: run {
                AppLog.w(TAG, "processCheck: percentage is null, skipping check.")
                return
            }

        val now = System.currentTimeMillis()
        val threshold = Prefs.batteryAlertThreshold

        val action =
            evaluate(
                currentPercentage = percentage,
                currentIsCharging = current.isCharging,
                lastPercentage = lastPercentage,
                lastIsCharging = lastIsCharging,
                threshold = threshold,
                isAlertDismissed = BatteryAlertState.isAlertDismissed(context),
                lastEvalTimeMs = BatteryAlertState.getLastEvalTimeMs(context),
                currentTimeMs = now,
            )

        AppLog.d(TAG, "processCheck: action=$action, level=$percentage%, charging=${current.isCharging}")

        lastPercentage = percentage
        lastIsCharging = current.isCharging

        when (action) {
            Action.ALERT -> {
                BatteryAlertState.updateLastEvalTimeMs(context, now)
                BatteryAlertNotificationManager.showLowBatteryAlert(context)
            }

            Action.CLEAR -> {
                BatteryAlertState.setAlertDismissed(context, false)
                BatteryAlertNotificationManager.dismissLowBatteryAlert(context)
            }

            Action.SUPPRESSED -> {
                BatteryAlertNotificationManager.dismissLowBatteryAlert(context)
            }

            Action.UNCHANGED -> {
                // No-operation
            }
        }
    }

    fun onAlertDismissed(context: Context) {
        AppLog.i(TAG, "User dismissed battery alert")
        BatteryAlertState.setAlertDismissed(context, true)
        BatteryAlertNotificationManager.dismissLowBatteryAlert(context)
    }

    fun reset(context: Context) {
        lastPercentage = null
        lastIsCharging = null
        BatteryAlertState.clear(context)
    }

    private fun evaluate(
        currentPercentage: Int,
        currentIsCharging: Boolean,
        lastPercentage: Int?,
        lastIsCharging: Boolean?,
        threshold: Int,
        isAlertDismissed: Boolean,
        lastEvalTimeMs: Long,
        currentTimeMs: Long,
    ): Action {
        val clearThreshold = threshold + HYSTERESIS
        val lastPct = lastPercentage ?: Int.MAX_VALUE
        val wasChargingBefore = lastIsCharging == true

        // Clear state check
        if (currentIsCharging || currentPercentage > clearThreshold) {
            val wasHealthyBefore = wasChargingBefore || lastPct > clearThreshold
            return if (wasHealthyBefore) Action.UNCHANGED else Action.CLEAR
        }

        // --- 2. BETWEEN THRESHOLD AND CLEAR THRESHOLD ---
        if (currentPercentage > threshold) {
            return Action.UNCHANGED
        }

        // --- 3. ALERT DISMISSED ---
        if (isAlertDismissed) {
            return Action.SUPPRESSED
        }

        // --- 4. THROTTLING / COOLDOWN ---
        val cooldownMs = (REPEAT_INTERVAL_MINUTES - GRACE_PERIOD_MINUTES).minutes.inWholeMilliseconds
        val elapsedMs = currentTimeMs - lastEvalTimeMs

        if (elapsedMs >= cooldownMs || wasChargingBefore) {
            return Action.ALERT
        }

        val minutesLeft = (cooldownMs - elapsedMs).milliseconds.inWholeMinutes
        AppLog.d(TAG, "Throttled: next alert in ~$minutesLeft min")

        return Action.UNCHANGED
    }
}
