package app.baldphone.neo.battery

import android.content.Context

import androidx.annotation.IntRange

import java.util.concurrent.TimeUnit

import com.bald.uriah.baldphone.R

/**
 * Data class representing the battery state.
 *
 * @property percentage Battery percentage (0–100). Null if unavailable.
 * @property isCharging True if the device is actively charging.
 * @property isFull True if the battery is fully charged (can be false even 100% level).
 * @property isPlugged True if the device is plugged into a power source.
 * @property isLow True if the system is in a low battery state.
 * @property minutesUntilCharged Estimated minutes until fully charged. -1 if unknown.
 */
data class BatteryState(
    @get:IntRange(from = 0, to = 100) val percentage: Int? = null,
    val isCharging: Boolean = false,
    val isFull: Boolean = false,
    val isPlugged: Boolean = false,
    val isLow: Boolean = false,
    val minutesUntilCharged: Long = -1L
) {
    /**
     * Generates a user-friendly localized string describing the battery status.
     * This logic is used for accessibility (content descriptions) and toasts.
     */
    fun formatInfo(context: Context): String {
        val level = percentage ?: return context.getString(R.string.battery_unavailable)

        if (isFull) {
            return context.getString(R.string.battery_full)
        }

        if (isCharging) {
            if (minutesUntilCharged <= 0) {
                return context.getString(R.string.battery_charging, level)
            }

            val hoursToFull = TimeUnit.MINUTES.toHours(minutesUntilCharged)
            val minutesPart = minutesUntilCharged % 60

            val timeFormatted =
                when {
                    hoursToFull > 0 -> {
                        context.getString(R.string.battery_time_h_m_tts, hoursToFull, minutesPart)
                    }

                    minutesPart > 0 -> {
                        context.getString(R.string.battery_time_m_tts, minutesPart)
                    }

                    else -> {
                        return context.getString(R.string.battery_charging, level)
                    }
                }
            return context.getString(R.string.battery_charging_with_time, level, timeFormatted)
        }

        return context.getString(R.string.battery_remaining, level)
    }

    /**
     * Generates a simple, concise description of the battery status suitable for TalkBack focuses.
     */
    fun formatSimpleInfo(context: Context): String {
        val level = percentage ?: return context.getString(R.string.battery_unavailable)

        if (isFull) {
            return context.getString(R.string.battery_full)
        }

        if (isCharging) {
            return context.getString(R.string.battery_charging, level)
        }

        return context.getString(R.string.battery_remaining, level)
    }
}
