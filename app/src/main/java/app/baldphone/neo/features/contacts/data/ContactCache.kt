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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.util.LinkedHashMap

import app.baldphone.neo.features.contacts.data.source.ContactReader
import app.baldphone.neo.features.contacts.model.SimpleContact

/**
 * Owns the cached contact collections.
 *
 * Holds two source-of-truth StateFlows:
 *  - [contacts]: every contact, with or without a phone number (contacts list and picker source).
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

    /** Every contact, including contacts without phone numbers. */
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

        // Run the actual reload on the cache's own long-lived [scope] rather than on the caller's coroutine context,
        // to avoid leaking the caller's scope.
        activeRefreshJob?.cancelAndJoin()
        val job =
            scope.launch {
                doRefresh(forceFullScan)
            }
        activeRefreshJob = job
        job.join()
    }

    private suspend fun doRefresh(forceFullScan: Boolean) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "refresh: Starting reload (forceFullScan=$forceFullScan)...")

        // A non-null cache is populated even when the device has no contacts at all.
        if (!forceFullScan && _contacts.value != null) {
            Log.d(TAG, "refresh: Cache already populated, skipping full scan")
            refreshTrigger.tryEmit(Unit)
            return
        }

        var fullScanRequired = true

        // Step 1: Fast initial fetch (first PAGE_SIZE rows from each source).
        if (_contacts.value == null) {
            val firstContacts = reader.fetchAllContacts(limit = PAGE_SIZE)
            val firstPhoneRows = reader.fetchPhoneContacts(limit = PAGE_SIZE)

            _contacts.value = firstContacts
            _allPhoneContacts.value = deduplicatePhoneRows(firstPhoneRows)

            // Both queries must reach EOF for this to be a complete snapshot.
            if (firstContacts.size < PAGE_SIZE && firstPhoneRows.size < PAGE_SIZE) {
                fullScanRequired = false
            }

            Log.i(
                TAG,
                "refresh: Initial page load complete " +
                    "(contacts: ${firstContacts.size}, phone rows: ${firstPhoneRows.size}) " +
                    "in ${System.currentTimeMillis() - startTime}ms"
            )
        }

        if (!fullScanRequired) {
            Log.d(TAG, "refresh: Full scan skipped (already at EOF)")
            refreshTrigger.tryEmit(Unit)
            return
        }

        // Step 2: Full background fetch.
        val allContacts = reader.fetchAllContacts(limit = -1)
        val allPhoneRows = reader.fetchPhoneContacts(limit = -1)
        val dialerContacts = deduplicatePhoneRows(allPhoneRows)

        if (allContacts != _contacts.value) {
            _contacts.value = allContacts
            Log.i(TAG, "refresh: Contacts collection UPDATED (count: ${allContacts.size})")
        } else {
            Log.d(TAG, "refresh: Contacts collection UNCHANGED")
        }

        if (dialerContacts != _allPhoneContacts.value) {
            _allPhoneContacts.value = dialerContacts
            Log.i(TAG, "refresh: All phone contacts UPDATED (count: ${dialerContacts.size})")
        }

        refreshTrigger.tryEmit(Unit)
        Log.d(
            TAG,
            "refresh: Completed full load in ${System.currentTimeMillis() - startTime}ms"
        )
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
        if (!current.isPrimary && candidate.phoneType == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE &&
            current.phoneType != ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
        ) {
            return true
        }
        return false
    }

    /** Stable key identifying the same phone number regardless of source/formatting. */
    private fun SimpleContact.numberKey(): String =
        normalizedNumber.takeIf { it.isNotEmpty() } ?: phoneNumber
            .filter { it.isDigit() || it == '+' }
            .takeIf { it.isNotEmpty() } ?: phoneNumber

    companion object {
        private const val TAG = "ContactCache"
        private const val OBSERVER_DEBOUNCE_MS = 300L

        /** Kept in sync with [ContactRepository.PAGE_SIZE]; both describe the same page window. */
        private const val PAGE_SIZE = ContactRepository.PAGE_SIZE
    }
}
