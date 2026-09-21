package app.baldphone.neo.features.contacts.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.util.LinkedHashMap

import app.baldphone.neo.features.contacts.data.source.ContactReader
import app.baldphone.neo.features.contacts.model.SimpleContact

/**
 * Owns the cached contact collections.
 *
 * Holds two source-of-truth StateFlows:
 *  - [contacts]: one best phone per contact (contacts list source).
 *  - [allPhoneContacts]: one row per distinct number per contact (dialer source).
 */
class ContactCache(
    private val context: Context,
    private val reader: ContactReader,
    private val scope: CoroutineScope
) {
    private var isObserverRegistered = false
    private var activeRefreshJob: Job? = null
    private var refreshDebounceJob: Job? = null

    private val _contacts = MutableStateFlow<List<SimpleContact>?>(null)
    private val _allPhoneContacts = MutableStateFlow<List<SimpleContact>?>(null)
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Deduplicated contacts sed by ContactsActivity. */
    val contacts: StateFlow<List<SimpleContact>?> = _contacts.asStateFlow()

    /** One row per distinct number per contact, used as the dialer search source. */
    val allPhoneContacts: StateFlow<List<SimpleContact>?> = _allPhoneContacts.asStateFlow()

    /** Emits whenever the contacts database changes and a reload has completed. */
    val refreshEvents: Flow<Unit> = refreshTrigger

    /** Debounced ContentObserver on Contacts URI. */
    private val contactsObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                Log.d(TAG, "contactsObserver: External change detected, scheduling refresh...")
                refreshDebounceJob?.cancel()
                refreshDebounceJob =
                    scope.launch {
                        delay(OBSERVER_DEBOUNCE_MS.milliseconds)
                        Log.d(TAG, "contactsObserver: Debounce finished, triggering refresh")
                        refresh(forceFullScan = true)
                    }
            }
        }

    init {
        tryRegisterObserver()
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
                val firstPageRows = reader.fetchPhoneContacts(limit = PAGE_SIZE)
                _contacts.value = deduplicateContacts(firstPageRows)
                _allPhoneContacts.value = deduplicatePhoneRows(firstPageRows)

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
            val allRows = reader.fetchPhoneContacts(limit = -1)
            val deduplicated = deduplicateContacts(allRows)
            val allPhoneRows = deduplicatePhoneRows(allRows)

            if (deduplicated != _contacts.value) {
                _contacts.value = deduplicated
                Log.i(TAG, "refresh: Contacts collection UPDATED (count: ${deduplicated.size})")
            } else {
                Log.d(TAG, "refresh: Contacts collection UNCHANGED")
            }

            if (allPhoneRows != _allPhoneContacts.value) {
                _allPhoneContacts.value = allPhoneRows
                Log.i(TAG, "refresh: All phone contacts UPDATED (count: ${allPhoneRows.size})")
            }

            refreshTrigger.tryEmit(Unit)
            Log.d(
                TAG,
                "refresh: Completed full load in ${System.currentTimeMillis() - startTime}ms"
            )
        }
    }

    fun clear() {
        if (isObserverRegistered) {
            context.contentResolver.unregisterContentObserver(contactsObserver)
            isObserverRegistered = false
        }
        scope.cancel()
    }

    private fun tryRegisterObserver() {
        if (isObserverRegistered) return
        if (reader.hasReadContactsPermission()) {
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

    /**
     * Deduplication for the dialer search source.
     * Keeps one row per distinct number of a contact, so a person with multiple different numbers
     * still yields multiple entries, as required by the dialer.
     */
    private suspend fun deduplicatePhoneRows(rows: List<SimpleContact>): List<SimpleContact> =
        withContext(Dispatchers.Default) {
            val result = LinkedHashMap<Pair<Long, String>, SimpleContact>(rows.size)

            for (candidate in rows) {
                val key = candidate.id to candidate.numberKey()
                val existing = result[key]

                if (existing == null || isBetter(candidate, existing)) {
                    result[key] = candidate
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

    /** Stable key identifying the same phone number regardless of source/formatting. */
    private fun SimpleContact.numberKey(): String =
        normalizedNumber.takeIf { it.isNotEmpty() }
            ?: phoneNumber.filter { it.isDigit() || it == '+' }.takeIf { it.isNotEmpty() }
            ?: phoneNumber

    companion object {
        private const val TAG = "ContactCache"
        private const val OBSERVER_DEBOUNCE_MS = 300L

        /** Kept in sync with [ContactRepository.PAGE_SIZE]; both describe the same page window. */
        private const val PAGE_SIZE = ContactRepository.PAGE_SIZE
    }
}
