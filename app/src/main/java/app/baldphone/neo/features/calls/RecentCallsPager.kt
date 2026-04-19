package app.baldphone.neo.features.calls

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.util.Log

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import app.baldphone.neo.features.calls.data.CallLogProvider
import app.baldphone.neo.features.calls.model.CallListEntry
import app.baldphone.neo.utils.isSameDayAs
import app.baldphone.neo.utils.toRelativeDateString

class RecentCallsPager(
    context: Context,
    private val scope: CoroutineScope
) {
    private val contentResolver = context.contentResolver
    private val callLogProvider = CallLogProvider(contentResolver)

    private val _entries = MutableStateFlow<List<CallListEntry>?>(null)
    val entries = _entries.asStateFlow()

    private val masterList = ArrayList<CallListEntry>()
    private var pagingCursor: Long? = null
    private var isLastPage = false
    private var fetchJob: Job? = null

    // Debounce job for ContentObserver
    private var refreshDebounceJob: Job? = null
    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                refreshDebounceJob?.cancel()
                refreshDebounceJob =
                    scope.launch {
                        delay(CONTENT_OBSERVER_DEBOUNCE_MS)
                        refresh()
                    }
            }
        }

    init {
        contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
    }

    /** Called when the ViewModel is cleared. */
    fun onCleared() {
        runCatching {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    /** Resets the call log pagination and reloads the first page of entries. */
    fun refresh() {
        fetchJob?.cancel()
        pagingCursor = null
        isLastPage = false
        masterList.clear()
        // Only trigger "full loading" if we don't have current entries
        if (_entries.value.isNullOrEmpty()) {
            _entries.value = null
        }
        loadNextPage()
    }

    fun loadNextPage() {
        if (isLastPage || fetchJob?.isActive == true) return

        Log.d(TAG, ">>> loadNextPage: STARTING fetch...")
        fetchJob =
            scope.launch {
                try {
                    val page = callLogProvider.loadPage(olderThan = pagingCursor)

                    if (page.items.isEmpty()) {
                        isLastPage = true
                        if (pagingCursor == null) {
                            _entries.value = emptyList()
                        }
                        Log.d(TAG, "<<< loadNextPage: FINISHED (No more items)")
                        return@launch
                    }
                    Log.d(TAG, "<<< loadNextPage: Fetched ${page.items.size} items")
                    pagingCursor = page.nextBeforeDate
                    isLastPage = (pagingCursor == null)

                    val lastItemIndex = masterList.indexOfLast { it is CallListEntry.Item }
                    val lastItem = if (lastItemIndex >= 0) masterList[lastItemIndex] as CallListEntry.Item else null
                    val lastItemDate = lastItem?.date
                    val now = System.currentTimeMillis()

                    val itemsToAdd =
                        withContext(Dispatchers.Default) {
                            val itemsToProcess = page.items
                            processNewItemsWithHeaders(itemsToProcess, lastItemDate, now)
                        }

                    masterList.addAll(itemsToAdd)
                    _entries.value = ArrayList(masterList)

                    Log.d(TAG, "<<< loadNextPage: Emit entries: ${masterList.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "<<< loadNextPage: FAILED with error", e)
                }
            }
    }

    private fun processNewItemsWithHeaders(
        newItems: List<CallListEntry.Item>,
        lastItemDate: Long?,
        now: Long
    ): List<CallListEntry> {
        // Sparse call history can often double the list size due to date headers. Use a 2x factor to avoid resizing.
        val result = ArrayList<CallListEntry>(newItems.size * 2)
        var lastDate = lastItemDate

        for (item in newItems) {
            if (lastDate == null || !item.date.isSameDayAs(lastDate)) {
                result.add(CallListEntry.Header(item.date.toRelativeDateString(now)))
            }
            result.add(item)
            lastDate = item.date
        }

        return result
    }

    companion object {
        private const val TAG = "RecentCallsPager"
        private const val CONTENT_OBSERVER_DEBOUNCE_MS = 300L
    }
}

/** Result of resolving a contact from call log details. */
sealed class ContactLookupResult {
    data class Found(val lookupKey: String) : ContactLookupResult()

    data class Unknown(val number: String?, val name: String?) : ContactLookupResult()
}
