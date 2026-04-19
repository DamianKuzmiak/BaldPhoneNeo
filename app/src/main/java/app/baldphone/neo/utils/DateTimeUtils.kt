package app.baldphone.neo.utils

import android.content.Context
import android.text.format.DateUtils

import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.concurrent.TimeUnit

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

/**
 * Formats a timestamp for list-based layouts like Call Logs or Messaging feeds.
 *
 * Examples:
 * - Under 1 minute: "Now"
 * - 1 to 60 minutes: "5 minutes ago"
 * - 1 to 6 hours: "2 hours ago"
 * - Over 6 hours: "14:20"
 */
fun Long.formatRecentTimestamp(context: Context): String {
    val now = System.currentTimeMillis()
    val relative =
        when (val diff = now - this) {
            in 0 until DateUtils.MINUTE_IN_MILLIS -> {
                context.getString(R.string.now)
            }

            in DateUtils.MINUTE_IN_MILLIS until DateUtils.HOUR_IN_MILLIS -> {
                val minutes = (diff / DateUtils.MINUTE_IN_MILLIS).toInt()
                context.resources.getQuantityString(
                    R.plurals.relative_minutes_ago,
                    minutes,
                    minutes,
                )
            }

            in DateUtils.HOUR_IN_MILLIS until TimeUnit.HOURS.toMillis(6) -> {
                val hours = (diff / DateUtils.HOUR_IN_MILLIS).toInt()
                context.resources.getQuantityString(R.plurals.relative_hours_ago, hours, hours)
            }

            else -> {
                null
            }
        }

    return relative ?: android.text.format.DateFormat
        .getTimeFormat(context)
        .format(Date(this))
}

/**
 * Checks if two timestamps fall on the same calendar day.
 */
fun Long.isSameDayAs(otherMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
    val date1 = Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
    val date2 = Instant.ofEpochMilli(otherMillis).atZone(zoneId).toLocalDate()

    return date1 == date2
}

/**
 * Formats a given timestamp into a relative date string
 * (e.g., "Today", "Yesterday", "October 21 2026").
 */
fun Long.toRelativeDateString(): String = toRelativeDateString(System.currentTimeMillis())

/**
 * Overload that accepts a pre-cached [now] timestamp.
 * Avoids repeated System.currentTimeMillis() calls when used in a batch.
 */
fun Long.toRelativeDateString(now: Long): String =
    DateUtils
        .getRelativeTimeSpanString(
            this,
            now,
            DateUtils.DAY_IN_MILLIS,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR,
        ).toString()
