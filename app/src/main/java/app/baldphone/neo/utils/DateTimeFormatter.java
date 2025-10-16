package app.baldphone.neo.utils;

import android.text.format.DateUtils;

import androidx.annotation.NonNull;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class DateTimeFormatter {

    /**
     * Formats a timestamp into a locale-aware time string. If the timestamp is within the last 6
     * hours, this method appends a relative time offset (e.g., "15 seconds ago", "1 minute ago", "5
     * hours ago"). Otherwise, it returns only the formatted time.
     *
     * <p>Examples:
     *
     * <ul>
     *   <li>"15:30, 15 seconds ago"
     *   <li>"15:30, 1 minute ago"
     *   <li>"10:31 AM, 5 hours ago"
     *   <li>"8:31 AM"
     * </ul>
     *
     * @param millisSinceEpoch The timestamp in milliseconds since epoch.
     * @return A formatted, locale-aware time string.
     */
    @NonNull
    public static String formatTimeWithRelativeOffset(long millisSinceEpoch) {
        Date eventDate = new Date(millisSinceEpoch);

        DateFormat timeFormatter =
                DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault());
        String formattedTime = timeFormatter.format(eventDate);

        long nowMillis = System.currentTimeMillis();
        long differenceMillis = nowMillis - millisSinceEpoch;

        if (differenceMillis >= 0 && differenceMillis < TimeUnit.HOURS.toMillis(6)) {
            CharSequence relativeTimeSpan;
            if (differenceMillis < DateUtils.MINUTE_IN_MILLIS) {
                // For times less than a minute ago, show "X seconds ago" (localized)
                relativeTimeSpan =
                        DateUtils.getRelativeTimeSpanString(
                                millisSinceEpoch, nowMillis, DateUtils.SECOND_IN_MILLIS);
            } else {
                // For times between 1 minute and 6 hours ago, show "X minutes/hours ago"
                relativeTimeSpan =
                        DateUtils.getRelativeTimeSpanString(
                                millisSinceEpoch, nowMillis, DateUtils.MINUTE_IN_MILLIS);
            }
            return formattedTime + ", " + relativeTimeSpan;
        } else {
            return formattedTime;
        }
    }

    /**
     * Formats a given timestamp into a human-readable, relative date string
     * (e.g., "Today", "Yesterday", "October 21 2025").
     *
     * @param dateMillis The timestamp in milliseconds to format.
     * @return A string representing the relative date.
     */
    public static String toRelativeDateString(long dateMillis) {
        CharSequence relativeTime =
                DateUtils.getRelativeTimeSpanString(
                        dateMillis,
                        System.currentTimeMillis(),
                        DateUtils.DAY_IN_MILLIS,
                        DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR);
        return relativeTime.toString();
    }

    /**
     * Checks if two timestamps (in milliseconds since epoch) fall on the same calendar day.
     *
     * @param millis1 The first timestamp in milliseconds since epoch.
     * @param millis2 The second timestamp in milliseconds since epoch.
     * @return {@code true} if both timestamps are on the same day, {@code false} otherwise.
     */
    public static boolean isSameDay(long millis1, long millis2) {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTimeInMillis(millis1);
        cal2.setTimeInMillis(millis2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
}
