package app.baldphone.neo.features.calls.data

import android.Manifest.permission.READ_CALL_LOG
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.provider.CallLog
import android.provider.ContactsContract

import androidx.core.content.ContextCompat.checkSelfPermission

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import app.baldphone.neo.contacts.Contact
import app.baldphone.neo.features.calls.model.Call

class CallsRepository(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    /**
     * Retrieves the call history for a specific contact.
     */
    suspend fun getCallHistory(contact: Contact): List<Call> =
        withContext(Dispatchers.IO) {
            if (checkSelfPermission(context, READ_CALL_LOG) != PERMISSION_GRANTED) {
                return@withContext emptyList()
            }

            val contactUri =
                ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey) ?: return@withContext emptyList()

            val projection =
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.DATE,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.CACHED_LOOKUP_URI
                )

            val calls = mutableListOf<Call>()
            resolver
                .query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    "${CallLog.Calls.CACHED_LOOKUP_URI} = ?",
                    arrayOf(contactUri.toString()),
                    "${CallLog.Calls.DATE} DESC"
                )?.use { cursor ->
                    val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                    val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                    val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                    val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)

                    while (cursor.moveToNext()) {
                        calls.add(
                            Call(
                                phoneNumber = cursor.getString(numberIndex),
                                duration = cursor.getInt(durationIndex),
                                dateTime = cursor.getLong(dateIndex),
                                callType = cursor.getInt(typeIndex)
                            )
                        )
                    }
                }
            calls
        }
}
