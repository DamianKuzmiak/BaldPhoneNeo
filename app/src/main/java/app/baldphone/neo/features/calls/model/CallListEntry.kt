package app.baldphone.neo.features.calls.model

import android.content.Context

import com.bald.uriah.baldphone.R

/**
 * Represents a single entry in the recent calls list.
 *
 * This sealed interface unifies different types of call log entries
 * (e.g., actual calls, date headers) so they can be displayed in a single RecyclerView.
 */
sealed interface CallListEntry {
    /**
     * A date-group header displayed above a section of call items.
     *
     * @property text The header label (e.g. "Today", "Yesterday", "March 24 2026").
     */
    data class Header(val text: String) : CallListEntry

    /**
     * A single call log entry.
     *
     * @property id Unique identifier from the system call log.
     * @property name Cached display name of the contact, or `null` if unknown.
     * @property number Raw phone number. May be `null` for private/unknown callers.
     * @property type System call type constant (e.g. [android.provider.CallLog.Calls.INCOMING_TYPE]).
     * @property date Timestamp of the call in epoch milliseconds.
     * @property cachedPhotoUri URI string for the cached contact photo, or `null`.
     * @property cachedLookupUri Cached lookup URI for the contact, or `null`.
     * @property isNew `true` if this is an unread missed call.
     * @property cachedFormattedNumber Cached formatted phone number, or `null`.
     * @property groupCount Number of consecutive identical calls aggregated into this entry.
     */
    data class Item(
        val id: Long,
        val name: String?,
        val number: String?,
        val type: Int,
        val date: Long,
        val cachedPhotoUri: String?,
        val cachedLookupUri: String?,
        val isNew: Boolean,
        val cachedFormattedNumber: String?,
        val groupCount: Int = 1
    ) : CallListEntry {
        /**
         * Returns a user-visible display name for this call entry,
         * preferring name -> formatted number -> raw number -> "Private Number".
         */
        fun displayName(context: Context): CharSequence =
            when {
                !name.isNullOrEmpty() -> name
                !cachedFormattedNumber.isNullOrEmpty() -> cachedFormattedNumber
                !number.isNullOrEmpty() -> number
                else -> context.getString(R.string.private_number)
            }
    }
}
