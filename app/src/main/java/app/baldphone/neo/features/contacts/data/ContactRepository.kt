package app.baldphone.neo.features.contacts.data

import android.content.Context
import android.util.Log

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

import app.baldphone.neo.features.contacts.ContactForm
import app.baldphone.neo.features.contacts.ContactSearcher
import app.baldphone.neo.features.contacts.data.source.ContactReader
import app.baldphone.neo.features.contacts.data.source.ContactWriter
import app.baldphone.neo.features.contacts.model.Contact
import app.baldphone.neo.features.contacts.model.ContactItemType
import app.baldphone.neo.features.contacts.model.SimpleContact

class ContactRepository private constructor(
    context: Context
) {
    private val reader = ContactReader(context)
    private val writer = ContactWriter(context)
    private val searcher = ContactSearcher(ContactSearcher.buildCharToDigitMap(context))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val cache = ContactCache(context, reader, scope)

    private val contactsStream =
        PagedContactStream(
            source = cache.contacts,
            searcher = searcher,
            enableT9 = false,
            showAllWhenEmpty = true,
            supportsFavorites = true,
            tag = "ContactsStream"
        )

    private val dialerStream =
        PagedContactStream(
            source = cache.allPhoneContacts,
            searcher = searcher,
            enableT9 = true,
            showAllWhenEmpty = false,
            supportsFavorites = false,
            tag = "DialerStream"
        )

    /** Emits whenever contacts database changes and a reload has completed. */
    val refreshEvents: Flow<Unit> = cache.refreshEvents

    /** Deduplicated contacts, including contacts without phone numbers. Used by ContactsActivity. */
    val contacts: StateFlow<List<SimpleContact>?> = cache.contacts

    fun clear() = cache.clear()

    suspend fun refresh(forceFullScan: Boolean = false) = cache.refresh(forceFullScan)

    fun observeContacts(
        queryFlow: Flow<String>,
        favoritesOnlyFlow: Flow<Boolean>
    ): Flow<List<ContactItemType>?> = contactsStream.observe(queryFlow, favoritesOnlyFlow)

    fun observeDialer(queryFlow: Flow<String>): Flow<List<ContactItemType>?> =
        dialerStream.observe(queryFlow, flowOf(false))

    fun loadNextContactsPage() = contactsStream.loadNextPage()

    fun loadNextDialerPage() = dialerStream.loadNextPage()

    /** One-shot fetch of a single contact. Automatically handles stale lookup keys. */
    suspend fun getContact(lookupKey: String): Contact? {
        Log.d(TAG, "getContact: $lookupKey")

        reader.queryContact(lookupKey)?.let { return it }

        val freshKey = reader.resolveLatestLookupKey(lookupKey)
        if (freshKey != null && freshKey != lookupKey) {
            return reader.queryContact(freshKey)
        }

        return null
    }

    /**
     * Creates a new contact from [draft]. Refresh for contact list is handled by the cache observer.
     * Succeeds only when the provider returns a valid new id; any failure cause is propagated.
     */
    suspend fun createContact(draft: ContactForm): Result<Unit> =
        writer.insertContact(draft).mapCatching { newId ->
            check(newId > 0L) { "createContact: provider returned invalid id $newId" }
        }

    /**
     * Updates the existing contact identified by [contactId] with the values in [draft].
     * Refresh for contact details is handled by the cache observer.
     */
    suspend fun updateContact(contactId: Long, draft: ContactForm): Result<Unit> {
        val rawContactId = reader.getRawContactId(contactId)
        if (rawContactId <= 0L) {
            return Result.failure(IllegalStateException("updateContact: no raw contact for id $contactId"))
        }

        return writer.updateContact(rawContactId, draft).requireChanged("updateContact")
    }

    /** Resolves the best available phone number for [lookupKey], or null when the contact has none. */
    suspend fun resolvePhoneNumber(lookupKey: String): String? = reader.resolvePhoneNumber(lookupKey)

    @Deprecated("Java interop only")
    fun resolvePhoneNumberJava(lookupKey: String): String? =
        runBlocking { reader.resolvePhoneNumber(lookupKey) }

    suspend fun deleteContact(lookupKey: String): Result<Unit> =
        writer.deleteContact(lookupKey).requireChanged("deleteContact")

    suspend fun toggleFavorite(lookupKey: String): Result<Unit> {
        val current =
            contacts.value?.find { it.lookupKey == lookupKey }?.isStarred
                ?: reader.queryContact(lookupKey)?.isStarred
                ?: return Result.failure(IllegalStateException("toggleFavorite: contact not found"))
        return updateFavorite(lookupKey, !current)
    }

    suspend fun updateFavorite(
        lookupKey: String,
        starred: Boolean
    ): Result<Unit> = writer.updateFavorite(lookupKey, starred).requireChanged("updateFavorite")

    /**
     * Normalizes a writer result: a successful call that changed no rows is treated as a failure,
     * so callers get a single "did it work" contract instead of a `Result<Boolean>`.
     */
    private fun Result<Boolean>.requireChanged(op: String): Result<Unit> =
        mapCatching { changed ->
            check(changed) { "$op: provider reported no rows changed" }
        }

    companion object {
        private const val TAG = "ContactRepository"
        internal const val PAGE_SIZE = 50

        @Volatile
        private var INSTANCE: ContactRepository? = null

        fun getInstance(context: Context): ContactRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ContactRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
