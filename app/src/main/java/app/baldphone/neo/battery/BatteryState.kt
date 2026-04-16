package app.baldphone.neo.battery

import androidx.annotation.IntRange

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
    val minutesUntilCharged: Long = -1L,
)
