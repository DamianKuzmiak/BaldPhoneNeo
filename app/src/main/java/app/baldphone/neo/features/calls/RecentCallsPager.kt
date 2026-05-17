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
import app.baldphone.neo.features.calls.model.CallLogItemType
import app.baldphone.neo.utils.isSameDayAs
import app.baldphone.neo.utils.toRelativeDateString

class RecentCallsPager(
    context: Context,
    private val scope: CoroutineScope,
    initialGroupingEnabled: Boolean = true
) {
    private val contentResolver = context.contentResolver
    private val callLogProvider = CallLogProvider(contentResolver)

    private val _entries = MutableStateFlow<List<CallListEntry>?>(null)
    val entries = _entries.asStateFlow()

    private val masterList = ArrayList<CallListEntry>()
    private var pagingCursor: Long? = null
    private var isLastPage = false
    private var fetchJob: Job? = null

    @Volatile
    private var groupingEnabled = initialGroupingEnabled

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

    /** Enables or disables consecutive call grouping. Triggers a full refresh when the value changes. */
    fun setGroupingEnabled(enabled: Boolean) {
        if (groupingEnabled == enabled) return
        groupingEnabled = enabled
        refresh()
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

    /**
     * Fetches and processes the next page of call log entries.
     *
     * This function performs the following steps:
     * 1. Checks if more items are available and if a fetch is already in progress.
     * 2. Loads a page of data from [callLogProvider] using the [pagingCursor].
     * 3. Groups consecutive calls (if enabled) and merges them across page boundaries.
     * 4. Inserts date headers based on the relative timestamp of the calls.
     * 5. Updates the [entries] StateFlow to notify observers of the new data.
     *
     * The processing is performed asynchronously within the [scope].
     */
    fun loadNextPage() {
        if (isLastPage || fetchJob?.isActive == true) return

        Log.d(TAG, ">>> loadNextPage: STARTING fetch...")
        fetchJob =
            scope.launch {
                try {
                    var itemsAddedThisFetch = 0

                    while (itemsAddedThisFetch < MIN_ITEMS_PER_FETCH && !isLastPage) {
                        val page = callLogProvider.loadPage(olderThan = pagingCursor)

                        if (page.items.isEmpty()) {
                            isLastPage = true
                            if (pagingCursor == null) {
                                _entries.value = emptyList()
                            }
                            Log.d(TAG, "<<< loadNextPage: FINISHED (No more items)")
                            break
                        }
                        Log.d(TAG, "<<< loadNextPage: Fetched ${page.items.size} items")
                        pagingCursor = page.nextBeforeDate
                        isLastPage = (pagingCursor == null)

                        val lastItemIndex = masterList.indexOfLast { it is CallListEntry.Item }
                        val lastItem = masterList.getOrNull(lastItemIndex) as? CallListEntry.Item
                        val lastItemDate = lastItem?.date
                        val now = System.currentTimeMillis()

                        val (mergedLastItem, itemsToAdd) =
                            withContext(Dispatchers.Default) {
                                processFetchedPage(page.items, lastItem, lastItemDate, now)
                            }

                        if (mergedLastItem != null && lastItemIndex >= 0) {
                            masterList[lastItemIndex] = mergedLastItem
                        }
                        masterList.addAll(itemsToAdd)
                        itemsAddedThisFetch += itemsToAdd.count { it is CallListEntry.Item }

                        _entries.value = ArrayList(masterList)
                        Log.d(TAG, "<<< loadNextPage: Emit entries: ${masterList.size}, new: $itemsAddedThisFetch")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "<<< loadNextPage: FAILED with error", e)
                }
            }
    }

    private fun processFetchedPage(
        items: List<CallListEntry.Item>,
        lastItem: CallListEntry.Item?,
        lastItemDate: Long?,
        now: Long
    ): Pair<CallListEntry.Item?, List<CallListEntry>> {
        var itemsToProcess = items
        var updatedLastItem: CallListEntry.Item? = null

        if (groupingEnabled) {
            itemsToProcess = groupConsecutive(itemsToProcess)
            if (lastItem != null && itemsToProcess.isNotEmpty()) {
                val firstNew = itemsToProcess[0]
                if (lastItem.isSameGroup(firstNew)) {
                    updatedLastItem =
                        lastItem.copy(
                            groupCount = lastItem.groupCount + firstNew.groupCount,
                            isNew = lastItem.isNew || firstNew.isNew
                        )
                    itemsToProcess = itemsToProcess.subList(1, itemsToProcess.size)
                }
            }
        }

        val newItems = processNewItemsWithHeaders(itemsToProcess, lastItemDate, now)
        return Pair(updatedLastItem, newItems)
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

    /**
     * Merges consecutive items that share the same caller and type into a single entry
     * with an aggregated [CallListEntry.Item.groupCount].
     */
    private fun groupConsecutive(items: List<CallListEntry.Item>): List<CallListEntry.Item> {
        if (items.isEmpty()) return items

        val result = ArrayList<CallListEntry.Item>()
        var current = items[0]
        var count = 1
        var hasNew = current.isNew

        for (i in 1 until items.size) {
            val next = items[i]
            if (next.isSameGroup(current)) {
                count++
                hasNew = hasNew || next.isNew
            } else {
                result += current.copy(groupCount = count, isNew = hasNew)
                current = next
                count = 1
                hasNew = next.isNew
            }
        }
        result += current.copy(groupCount = count, isNew = hasNew)
        return result
    }

    /** Determines if two call log entries should be grouped together. */
    private fun CallListEntry.Item.isSameGroup(other: CallListEntry.Item): Boolean =
        this.number == other.number &&
            CallLogItemType.fromSystemType(this.type) == CallLogItemType.fromSystemType(other.type)

    companion object {
        private const val TAG = "RecentCallsPager"
        private const val CONTENT_OBSERVER_DEBOUNCE_MS = 300L
        private const val MIN_ITEMS_PER_FETCH = 20
    }
}

/** Result of resolving a contact from call log details. */
sealed class ContactLookupResult {
    data class Found(val lookupKey: String) : ContactLookupResult()

    data class Unknown(val number: String?, val name: String?) : ContactLookupResult()
}
