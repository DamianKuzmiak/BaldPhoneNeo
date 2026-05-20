package app.baldphone.neo.features.calls.model

/**
 * Represents a single call from the system call log.
 *
 * @property phoneNumber The phone number associated with the call.
 * @property duration The duration of the call in seconds.
 * @property dateTime The timestamp of the call in epoch milliseconds.
 * @property callType The type of the call (e.g., [android.provider.CallLog.Calls.INCOMING_TYPE]).
 */
data class Call(
    val phoneNumber: String?,
    val duration: Int,
    val dateTime: Long,
    val callType: Int
)
