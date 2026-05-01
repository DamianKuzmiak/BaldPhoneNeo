package app.baldphone.neo.utils

import android.content.Context
import android.text.format.DateUtils

import java.time.Instant
import java.time.ZoneId

import com.bald.uriah.baldphone.R

// Utility functions for formatting date and time.

/**
 * Formats a timestamp into a localized string that adapts to its proximity to now and midnight.
 *
 * Examples:
 * - Under 1 minute: "Now"
 * - Under 60 minutes: "5 minutes ago"
 * - Today: "14:20"
 * - Yesterday: "Yesterday, 14:20"
 * - Older: "May 5, 14:20"
 */
fun Long.formatDayAwareTimestamp(context: Context): String {
    val nowMs = System.currentTimeMillis()
    val diffMs = nowMs - this

    val zone = ZoneId.systemDefault()
    val target = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()

    val formattedTime = DateUtils.formatDateTime(context, this, DateUtils.FORMAT_SHOW_TIME)

    return when (target) {
        today -> {
            when {
                diffMs < DateUtils.MINUTE_IN_MILLIS -> {
                    context.getString(R.string.now)
                }

                diffMs < DateUtils.HOUR_IN_MILLIS -> {
                    val minutes = (diffMs / DateUtils.MINUTE_IN_MILLIS).toInt()
                    context.resources.getQuantityString(
                        R.plurals.relative_minutes_ago,
                        minutes,
                        minutes
                    )
                }

                else -> {
                    formattedTime
                }
            }
        }

        // Yesterday
        today.minusDays(1) -> {
            context.getString(
                R.string.text_pair_combined,
                context.getString(R.string.yesterday),
                formattedTime
            )
        }

        // Older
        else -> {
            DateUtils.formatDateTime(
                context,
                this,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
            )
        }
    }
}
