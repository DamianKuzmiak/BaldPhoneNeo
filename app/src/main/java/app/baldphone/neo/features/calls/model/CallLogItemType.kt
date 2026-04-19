package app.baldphone.neo.features.calls.model

import android.provider.CallLog.Calls

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

import com.bald.uriah.baldphone.R

/**
 * Maps system call types to specific UI resources (drawable, string, and color) for display in the call log.
 */
enum class CallLogItemType(
    @DrawableRes val drawableRes: Int,
    @StringRes val stringRes: Int,
    @ColorRes val colorRes: Int
) {
    INCOMING(R.drawable.call_received_on_button, R.string.received, R.color.received),
    MISSED(R.drawable.call_missed_on_button, R.string.missed, R.color.missed),
    OUTGOING(R.drawable.call_made_on_button, R.string.outgoing, R.color.outgoing),
    VOICEMAIL(R.drawable.voicemail_on_button, R.string.voice_mail, R.color.other),
    BLOCKED(R.drawable.blocked_on_button, R.string.blocked, R.color.other),
    UNKNOWN(R.drawable.error_on_background, R.string.empty, R.color.other);

    companion object {
        /**
         * Converts a system call type constant from [Calls] to a [CallLogItemType].
         *
         * @param systemCallType The call type from [Calls].
         * @return The corresponding [CallLogItemType]
         */
        @JvmStatic
        fun fromSystemType(systemCallType: Int): CallLogItemType =
            when (systemCallType) {
                Calls.INCOMING_TYPE, Calls.ANSWERED_EXTERNALLY_TYPE -> INCOMING
                Calls.MISSED_TYPE, Calls.REJECTED_TYPE -> MISSED
                Calls.OUTGOING_TYPE -> OUTGOING
                Calls.VOICEMAIL_TYPE -> VOICEMAIL
                Calls.BLOCKED_TYPE -> BLOCKED
                else -> UNKNOWN
            }
    }
}
