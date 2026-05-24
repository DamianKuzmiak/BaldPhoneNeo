package app.baldphone.neo.features.contacts.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

import java.util.LinkedHashMap

import app.baldphone.neo.features.contacts.Contact
import app.baldphone.neo.features.contacts.ContactItemType
import app.baldphone.neo.features.contacts.ContactSearcher
import app.baldphone.neo.features.contacts.SimpleContact

/**
 * Singleton contact repository that orchestrates data flow, caching, and reactivity.
 *
 * Delegates raw ContentResolver operations to [ContactsDataSource] and owns the reactive [contacts]
 * StateFlow fed by a ContentObserver.
 */
class ContactRepository private constructor(
    private val context: Context
) {
    private var isObserverRegistered = false
    private val dataSource = ContactsDataSource(context)
    private val contactSearcher = ContactSearcher(ContactSearcher.buildCharToDigitMap(context))

    private val _contacts = MutableStateFlow<List<SimpleContact>?>(null)
    private val _allPhoneContacts = MutableStateFlow<List<SimpleContact>?>(null)
    private var activeRefreshJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshDebounceJob: Job? = null

    // Paging state
    private val contactsPage = MutableStateFlow(1)
    private val dialerPage = MutableStateFlow(1)
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var lastContactsEmittedSize = 0
    private var lastDialerEmittedSize = 0

    /** Emits whenever contacts database changes and a reload has completed. */
    val refreshEvents: Flow<Unit> = refreshTrigger

    /** Deduplicated contacts (one best phone per contact). Used by ContactsActivity. */
    val contacts: StateFlow<List<SimpleContact>?> = _contacts.asStateFlow()

    /**
     * ContentObserver on Contacts URI. Fires on any external insert/update/delete.
     * Debounced to avoid cascading reloads from batch operations (e.g., account sync).
     */
    private val contactsObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                Log.d(TAG, "contactsObserver: External change detected, scheduling refresh...")
                refreshDebounceJob?.cancel()
                refreshDebounceJob =
                    scope.launch {
                        delay(OBSERVER_DEBOUNCE_MS)
                        Log.d(TAG, "contactsObserver: Debounce finished, triggering refresh")
                        refresh(forceFullScan = true)
                    }
            }
        }

    init {
        tryRegisterObserver()
    }

    private fun tryRegisterObserver() {
        if (isObserverRegistered) return
        if (dataSource.hasContactsPermission()) {
            try {
                context.contentResolver.registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI,
                    true,
                    contactsObserver
                )
                isObserverRegistered = true
                Log.i(TAG, "ContentObserver registered successfully")
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to register ContentObserver even with permission check pass", e)
            }
        } else {
            Log.d(TAG, "Skipping ContentObserver registration: No READ_CONTACTS permission")
        }
    }

    fun clear() {
        if (isObserverRegistered) {
            context.contentResolver.unregisterContentObserver(contactsObserver)
            isObserverRegistered = false
        }
        scope.cancel()
    }

    suspend fun refresh(forceFullScan: Boolean = false) {
        tryRegisterObserver()
        coroutineScope {
            activeRefreshJob?.cancelAndJoin()
            activeRefreshJob = coroutineContext.job

            val startTime = System.currentTimeMillis()
            Log.d(TAG, "refresh: Starting reload (forceFullScan=$forceFullScan)...")

            // Skip full scan if we already have cached data and this is not a forced refresh (e.g. ContentObserver),
            if (!forceFullScan && !_allPhoneContacts.value.isNullOrEmpty()) {
                Log.d(TAG, "refresh: Cache already populated, skipping full scan")
                refreshTrigger.tryEmit(Unit)
                return@coroutineScope
            }

            var fullScanRequired = true

            // Step 1: Fast initial fetch (first PAGE_SIZE)
            if (_allPhoneContacts.value.isNullOrEmpty()) {
                val firstPageRows = dataSource.fetchPhoneContacts(limit = PAGE_SIZE)
                val deduplicatedPage = deduplicateContacts(firstPageRows)
                _contacts.value = deduplicatedPage
                _allPhoneContacts.value = firstPageRows

                // If we got fewer than PAGE_SIZE rows, we've already reached the end
                if (firstPageRows.size < PAGE_SIZE) {
                    fullScanRequired = false
                }

                Log.i(
                    TAG,
                    "refresh: Initial page load complete (count: ${firstPageRows.size}) in ${System.currentTimeMillis() - startTime}ms"
                )
            }

            if (!fullScanRequired) {
                Log.d(TAG, "refresh: Full scan skipped (already at EOF)")
                refreshTrigger.tryEmit(Unit)
                return@coroutineScope
            }

            // Step 2: Full background fetch
            val allRows = dataSource.fetchPhoneContacts(limit = -1)
            val deduplicated = deduplicateContacts(allRows)

            if (deduplicated != _contacts.value) {
                _contacts.value = deduplicated
                Log.i(TAG, "refresh: Contacts collection UPDATED (count: ${deduplicated.size})")
            } else {
                Log.d(TAG, "refresh: Contacts collection UNCHANGED")
            }

            if (allRows != _allPhoneContacts.value) {
                _allPhoneContacts.value = allRows
                Log.i(TAG, "refresh: All phone contacts UPDATED (count: ${allRows.size})")
            }

            refreshTrigger.tryEmit(Unit)
            Log.d(
                TAG,
                "refresh: Completed full load in ${System.currentTimeMillis() - startTime}ms"
            )
        }
    }

    fun observeContacts(
        queryFlow: Flow<String>,
        favoritesOnlyFlow: Flow<Boolean>
    ): Flow<List<ContactItemType>?> = observeInternal(queryFlow, favoritesOnlyFlow, isDialer = false)

    fun observeDialer(queryFlow: Flow<String>): Flow<List<ContactItemType>?> =
        observeInternal(queryFlow, flowOf(false), isDialer = true)

    private fun observeInternal(
        queryFlow: Flow<String>,
        favoritesOnlyFlow: Flow<Boolean>,
        isDialer: Boolean
    ): Flow<List<ContactItemType>?> {
        val pageFlow = if (!isDialer) contactsPage else dialerPage

        val processedQuery =
            queryFlow
                .debounce { if (it.isEmpty()) 0L else 250L }
                .distinctUntilChanged()
                .onEach { pageFlow.value = 1 }

        val processedFavorites =
            favoritesOnlyFlow
                .distinctUntilChanged()
                .onEach { pageFlow.value = 1 }

        val sourceFlow = if (isDialer) _allPhoneContacts else _contacts

        return combine(
            processedQuery,
            processedFavorites,
            pageFlow,
            sourceFlow
        ) { query, favoritesOnly, page, sourceList ->
            if (sourceList == null) {
                Log.v(
                    TAG,
                    "observeInternal[isDialer=$isDialer]: Cache is null, returning null (loading)"
                )
                return@combine null
            }

            val startTime = System.currentTimeMillis()
            Log.v(
                TAG,
                "observeInternal[isDialer=$isDialer]: Triggered by [query=\"$query\", fav=$favoritesOnly, page=$page, cached items=${sourceList.size}]"
            )

            val limit = page * PAGE_SIZE

            // Apply in-memory Logic (T9, Phone Substring, Favorites, Grouping)
            // Passing the paging limit down to avoid processing all 1000s of contacts if just showing the first page
            val processed =
                withContext(Dispatchers.Default) {
                    contactSearcher.searchContacts(
                        allContacts = sourceList,
                        query = query,
                        isFavorites = if (!isDialer) favoritesOnly else false,
                        enableT9 = isDialer,
                        showAllWhenEmpty = !isDialer,
                        itemLimit = limit
                    )
                }

            Log.d(
                TAG,
                "observeInternal[isDialer=$isDialer]: Emitting ${processed.size} items (processed in ${System.currentTimeMillis() - startTime}ms)"
            )
            if (isDialer) {
                lastDialerEmittedSize = processed.size
            } else {
                lastContactsEmittedSize = processed.size
            }
            processed
        }.flowOn(Dispatchers.Default)
            .onStart {
                if (sourceFlow.value == null) {
                    Log.v(
                        TAG,
                        "observeInternal[isDialer=$isDialer]: Emitting initial null (loading)"
                    )
                    emit(null)
                }
            }.distinctUntilChanged()
    }

    fun loadNextContactsPage() {
        val currentLimit = contactsPage.value * PAGE_SIZE
        if (lastContactsEmittedSize >= currentLimit) {
            contactsPage.value++
            Log.d(TAG, "loadNextContactsPage: New Page: ${contactsPage.value}")
        } else {
            Log.d(TAG, "loadNextContactsPage: Blocked (reached EOF)")
        }
    }

    fun loadNextDialerPage() {
        val currentLimit = dialerPage.value * PAGE_SIZE
        if (lastDialerEmittedSize >= currentLimit) {
            dialerPage.value++
            Log.d(TAG, "loadNextDialerPage: New Page: ${dialerPage.value}")
        } else {
            Log.d(TAG, "loadNextDialerPage: Blocked (reached EOF)")
        }
    }

    /**
     * One-shot fetch of a single contact. Automatically handles stale lookup keys.
     */
    suspend fun getContact(lookupKey: String): Contact? {
        Log.d(TAG, "getContact: $lookupKey")

        dataSource.queryContact(lookupKey)?.let { return it }

        val freshKey = dataSource.resolveLatestLookupKey(lookupKey)
        if (freshKey != null && freshKey != lookupKey) {
            return dataSource.queryContact(freshKey)
        }

        return null
    }

    // temporary for Java
    @Deprecated("Java interop only")
    fun getRawContactId(contactId: Long): Long = runBlocking { dataSource.getRawContactId(contactId) }

    // temporary for Java
    @Deprecated("Java interop only")
    fun getContactByLookupKeyJava(key: String): Contact? = runBlocking { dataSource.queryContact(key) }

    @Deprecated("Java interop only")
    fun getContactByIdJava(id: String): Contact? = runBlocking { dataSource.queryContactById(id) }

    @Deprecated("Java interop only")
    fun resolvePhoneNumberJava(lookupKey: String): String? =
        runBlocking { dataSource.resolvePhoneNumber(lookupKey) }

    suspend fun deleteContact(lookupKey: String): Boolean = dataSource.deleteContact(lookupKey)

    suspend fun toggleFavorite(lookupKey: String): Boolean {
        val current =
            _contacts.value?.find { it.lookupKey == lookupKey }?.isStarred
                ?: dataSource.queryContact(lookupKey)?.isStarred
                ?: return false
        return updateFavorite(lookupKey, !current)
    }

    suspend fun updateFavorite(
        lookupKey: String,
        starred: Boolean
    ): Boolean = dataSource.updateFavorite(lookupKey, starred)

    /**
     * Keeps only the best phone number per contact for the Contacts list.
     * Priority: isPrimary > TYPE_MOBILE > first encountered.
     */
    private suspend fun deduplicateContacts(rows: List<SimpleContact>): List<SimpleContact> =
        withContext(Dispatchers.Default) {
            val result = LinkedHashMap<Long, SimpleContact>(rows.size)
            for (candidate in rows) {
                val existing = result[candidate.id]
                if (existing == null || isBetter(candidate, existing)) {
                    result[candidate.id] = candidate
                }
            }
            result.values.toList()
        }

    private fun isBetter(
        candidate: SimpleContact,
        current: SimpleContact
    ): Boolean {
        if (candidate.isPrimary && !current.isPrimary) return true
        if (!current.isPrimary &&
            candidate.phoneType == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE &&
            current.phoneType != ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
        ) {
            return true
        }
        return false
    }

    companion object {
        private const val TAG = "ContactRepository"
        private const val OBSERVER_DEBOUNCE_MS = 300L
        internal const val PAGE_SIZE = 50

        @Volatile
        private var INSTANCE: ContactRepository? = null

        fun getInstance(context: Context): ContactRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ContactRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
