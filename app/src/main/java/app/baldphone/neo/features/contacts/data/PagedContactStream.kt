package app.baldphone.neo.features.contacts.data

import android.util.Log

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

import java.util.concurrent.atomic.AtomicInteger

import app.baldphone.neo.features.contacts.ContactSearcher
import app.baldphone.neo.features.contacts.model.ContactItemType
import app.baldphone.neo.features.contacts.model.SimpleContact

/**
 * A single paged, searchable view over one contact source.
 *
 * Each consumer (the Contacts list, the dialer) gets its own instance with its own page state,
 * search configuration, and emitted-size counter.
 *
 * @param source The backing collection to search over.
 * @param searcher Shared search/grouping engine.
 * @param enableT9 Whether numeric queries also match names via T9 (dialer only).
 * @param showAllWhenEmpty Whether an empty query shows all contacts (list) or nothing (dialer).
 * @param supportsFavorites Whether the favorites filter applies (list only).
 * @param tag Log tag identifying this stream.
 */
class PagedContactStream(
    private val source: StateFlow<List<SimpleContact>?>,
    private val searcher: ContactSearcher,
    private val enableT9: Boolean,
    private val showAllWhenEmpty: Boolean,
    private val supportsFavorites: Boolean,
    private val tag: String
) {
    private val page = MutableStateFlow(1)
    private val lastEmittedSize = AtomicInteger(0)

    /**
     * Emits null while the source is still loading.
     */
    fun observe(
        queryFlow: Flow<String>,
        favoritesOnlyFlow: Flow<Boolean>
    ): Flow<List<ContactItemType>?> {
        val processedQuery =
            queryFlow
                .debounce { if (it.isEmpty()) 0L else 250L }
                .distinctUntilChanged()
                .onEach { page.value = 1 }

        val processedFavorites =
            favoritesOnlyFlow
                .distinctUntilChanged()
                .onEach { page.value = 1 }

        return combine(
            processedQuery,
            processedFavorites,
            page,
            source
        ) { query, favoritesOnly, currentPage, sourceList ->
            if (sourceList == null) {
                Log.v(tag, "observe: Cache is null, returning null (loading)")
                return@combine null
            }

            val startTime = System.currentTimeMillis()
            Log.v(
                tag,
                "observe: Triggered by [query=\"$query\", fav=$favoritesOnly, page=$currentPage, cached items=${sourceList.size}]"
            )

            val limit = currentPage * ContactRepository.PAGE_SIZE

            // Apply in-memory logic (T9, phone substring, favorites, grouping).
            // Passing the paging limit down avoids processing all 1000s of contacts when only the
            // first page is visible.
            val processed =
                withContext(Dispatchers.Default) {
                    searcher.searchContacts(
                        allContacts = sourceList,
                        query = query,
                        isFavorites = supportsFavorites && favoritesOnly,
                        enableT9 = enableT9,
                        showAllWhenEmpty = showAllWhenEmpty,
                        itemLimit = limit
                    )
                }

            Log.d(
                tag,
                "observe: Emitting ${processed.size} items (processed in ${System.currentTimeMillis() - startTime}ms)"
            )
            lastEmittedSize.set(processed.size)
            processed
        }.flowOn(Dispatchers.Default)
            .onStart {
                if (source.value == null) {
                    Log.v(tag, "observe: Emitting initial null (loading)")
                    emit(null)
                }
            }.distinctUntilChanged()
    }

    fun loadNextPage() {
        val currentLimit = page.value * ContactRepository.PAGE_SIZE
        if (lastEmittedSize.get() >= currentLimit) {
            val newPage = page.value + 1
            page.value = newPage
            Log.d(tag, "loadNextPage: New page: $newPage")
        } else {
            Log.d(tag, "loadNextPage: Reached EOF")
        }
    }
}
