package app.baldphone.neo.features.calls.data

import android.content.ContentResolver
import android.os.Build
import android.provider.BaseColumns
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import app.baldphone.neo.features.calls.data.CallLogProvider.Companion.DEFAULT_PAGE_SIZE
import app.baldphone.neo.features.calls.model.CallListEntry

/**
 * Reads paginated call log entries from the system [CallLog] content provider.
 *
 * Each call to [loadPage] returns a [Page] containing up to [DEFAULT_PAGE_SIZE] items
 * plus a cursor key for fetching the next page.
 */
class CallLogProvider(private val resolver: ContentResolver) {
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
    }
}

/** Returns the string at [index], or `null` if the index is invalid or the column is null. */
private fun android.database.Cursor.stringOrNull(index: Int): String? =
    if (index >= 0 && !isNull(index)) getString(index) else null
