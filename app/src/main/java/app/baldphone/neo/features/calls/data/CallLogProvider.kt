package app.baldphone.neo.features.calls.data

import android.Manifest.permission.READ_CALL_LOG
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import android.provider.BaseColumns
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log

import androidx.core.content.ContextCompat.checkSelfPermission

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

import app.baldphone.neo.features.calls.data.CallLogProvider.Companion.DEFAULT_PAGE_SIZE
import app.baldphone.neo.features.calls.model.Call
import app.baldphone.neo.features.calls.model.CallListEntry

/**
 * Reads paginated call log entries from the system [CallLog] content provider.
 *
 * Each call to [loadPage] returns a [Page] containing up to [DEFAULT_PAGE_SIZE] items
 * plus a cursor key for fetching the next page.
 */
class CallLogProvider(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    /**
     * Loads a single page of call log entries.
     *
     * @param olderThan Return only calls older than this epoch-millis value (cursor key).
     *                   Pass `null` for the first page.
     * @param limit Maximum number of items to return.
     * @param query Optional search string matched against cached name and number.
     * @param typeFilter Optional system call type constant to filter by.
     * @return A [Page] with the loaded items and an optional key for the next page.
     */
    suspend fun loadPage(
        olderThan: Long? = null,
        limit: Int = DEFAULT_PAGE_SIZE,
        query: String? = null,
        typeFilter: Int? = null
    ): Page =
        withContext(Dispatchers.IO) {
            val selParts = mutableListOf<String>()
            val args = mutableListOf<String>()

            if (olderThan != null) {
                selParts += "${CallLog.Calls.DATE} < ?"
                args += olderThan.toString()
            }

            if (!query.isNullOrEmpty()) {
                val escapedQuery = "%${query.replace("%", "\\%")}%"
                selParts +=
                    "(${CallLog.Calls.CACHED_NAME} LIKE ? ESCAPE '\\' OR ${CallLog.Calls.NUMBER} LIKE ? ESCAPE '\\')"
                args += escapedQuery
                args += escapedQuery
            }

            if (typeFilter != null) {
                selParts += "${CallLog.Calls.TYPE} = ?"
                args += typeFilter.toString()
            }

            val selection = selParts.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
            val selectionArgs = args.takeIf { it.isNotEmpty() }?.toTypedArray()

            // Stable ordering to avoid reshuffling rows with identical DATE.
            val sortOrder = "${CallLog.Calls.DATE} DESC, ${BaseColumns._ID} DESC"

            // Some devices/providers might ignore Bundle-based limits; use URI query parameter.
            val uri =
                CallLog.Calls.CONTENT_URI
                    .buildUpon()
                    .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, limit.toString())
                    .build()

            val items = mutableListOf<CallListEntry.Item>()
            var minDateSeen: Long? = null

            try {
                val cursor =
                    resolver.query(uri, PROJECTION, selection, selectionArgs, sortOrder)
                        ?: run {
                            Log.v(TAG, "Query returned null. Falling back to legacy path.")
                            resolver.query(
                                CallLog.Calls.CONTENT_URI,
                                PROJECTION,
                                selection,
                                selectionArgs,
                                sortOrder
                            )
                        }

                cursor?.use { c ->
                    Log.d(TAG, "Query returned ${c.count} rows")

                    val idxId = c.getColumnIndexOrThrow(BaseColumns._ID)
                    val idxName = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                    val idxNumber = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                    val idxType = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                    val idxDate = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
                    val idxNew = c.getColumnIndexOrThrow(CallLog.Calls.NEW)
                    val idxLookup = c.getColumnIndex(CallLog.Calls.CACHED_LOOKUP_URI)
                    val idxFormatted = c.getColumnIndex(CallLog.Calls.CACHED_FORMATTED_NUMBER)
                    val idxPhoto =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            c.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)
                        } else {
                            -1
                        }

                    while (c.moveToNext() && items.size < limit) {
                        val date = c.getLong(idxDate)
                        val type = c.getInt(idxType)

                        items +=
                            CallListEntry.Item(
                                id = c.getLong(idxId),
                                name = c.stringOrNull(idxName),
                                number = c.stringOrNull(idxNumber),
                                type = type,
                                date = date,
                                cachedPhotoUri = c.stringOrNull(idxPhoto),
                                cachedLookupUri = c.stringOrNull(idxLookup),
                                isNew =
                                    (type == CallLog.Calls.MISSED_TYPE) &&
                                        idxNew >= 0 && !c.isNull(idxNew) && c.getInt(idxNew) == 1,
                                cachedFormattedNumber = c.stringOrNull(idxFormatted)
                            )

                        if (minDateSeen == null || date < minDateSeen) minDateSeen = date
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadPage", e)
            }

            // If fewer than `limit` items fetched, the end of data was reached.
            val nextKey = if (items.size < limit) null else minDateSeen
            Page(items, nextKey)
        }

    /**
     * Retrieves the call history for a specific contact.
     *
     * Queries both by lookupUri and phone numbers to better results, deduplicating and sorting the combined results.
     */
    suspend fun getCallHistory(
        lookupUri: Uri?,
        phoneNumbers: List<String> = emptyList()
    ): List<Call> =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val validNumbers = phoneNumbers.filter { it.isNotBlank() }

            if (lookupUri == null && validNumbers.isEmpty()) return@withContext emptyList()
            if (checkSelfPermission(context, READ_CALL_LOG) != PERMISSION_GRANTED) {
                return@withContext emptyList()
            }

            val deferredResults = mutableListOf<Deferred<List<Call>>>()

            if (lookupUri != null) {
                deferredResults.add(
                    async {
                        queryCallLog(
                            uri = CallLog.Calls.CONTENT_URI,
                            selection = "${CallLog.Calls.CACHED_LOOKUP_URI} = ?",
                            selectionArgs = arrayOf(lookupUri.toString())
                        )
                    }
                )
            }

            // Query by phone numbers using CONTENT_FILTER_URI to handle different formatting
            // better than a standard SQL "IN" clause.
            for (phoneNumber in validNumbers) {
                deferredResults.add(
                    async {
                        val filterUri = Uri.withAppendedPath(CallLog.Calls.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
                        queryCallLog(
                            uri = filterUri,
                            selection = null,
                            selectionArgs = null
                        )
                    }
                )
            }

            val combinedCalls =
                deferredResults
                    .awaitAll()
                    .flatten()
                    .distinctBy { it.dateTime }
                    .sortedByDescending { it.dateTime }

            val totalDuration = System.currentTimeMillis() - startTime
            Log.d(TAG, "getCallHistory took ${totalDuration}ms, found ${combinedCalls.size} calls")

            combinedCalls
        }

    private fun queryCallLog(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): List<Call> {
        val calls = mutableListOf<Call>()
        try {
            resolver
                .query(
                    uri,
                    HISTORY_PROJECTION,
                    selection,
                    selectionArgs,
                    null
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query call history for URI: $uri", e)
        }
        return calls
    }

    /**
     * A page of call log items with an optional cursor key for the next page.
     *
     * @property items The call entries on this page.
     * @property nextBeforeDate Pass as [beforeDate] to [loadPage] for the next page;
     *                          `null` means no more data.
     */
    data class Page(
        val items: List<CallListEntry.Item>,
        val nextBeforeDate: Long?
    )

    companion object {
        const val DEFAULT_PAGE_SIZE = 40
        private const val TAG = "CallLogProvider"

        private val PROJECTION: Array<String> =
            buildList {
                add(BaseColumns._ID)
                add(CallLog.Calls.CACHED_LOOKUP_URI)
                add(CallLog.Calls.CACHED_NAME)
                add(CallLog.Calls.CACHED_FORMATTED_NUMBER)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    add(CallLog.Calls.CACHED_PHOTO_URI)
                }
                add(CallLog.Calls.NUMBER)
                add(CallLog.Calls.TYPE)
                add(CallLog.Calls.DATE)
                add(CallLog.Calls.NEW)
            }.toTypedArray()

        private val HISTORY_PROJECTION =
            arrayOf(
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE
            )
    }
}

/** Returns the string at [index], or `null` if the index is invalid or the column is null. */
private fun android.database.Cursor.stringOrNull(index: Int): String? =
    if (index >= 0 && !isNull(index)) getString(index) else null
