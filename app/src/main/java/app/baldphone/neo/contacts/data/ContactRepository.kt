package app.baldphone.neo.contacts.data

import app.baldphone.neo.contacts.Contact
import app.baldphone.neo.contacts.SimpleContact

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface defining the operations for managing and retrieving contacts.
 */
interface ContactRepository {
    /**
     * Observable flow of contacts.
     * null = not yet loaded or currently loading for the first time
     * emptyList = loaded but no contacts found
     */
    val contacts: StateFlow<List<SimpleContact>?>

    /**
     * One-shot fetch of all contacts.
     */
    suspend fun getAllContacts(): List<SimpleContact>

    /**
     * Triggers a refresh of the contacts from the system database.
     */
    suspend fun refresh()

    /**
     * Resolves the raw contact ID for a given contact ID.
     */
    fun getRawContactId(contactId: Long): Long

    /**
     * Java interop: Provides a blocking query for a contact by lookup key.
     */
    fun getContactByLookupKeyJava(key: String): Contact?

    /**
     * Java interop: Provides a blocking query for a contact by ID.
     */
    fun getContactByIdJava(id: String): Contact?

    /**
     * Provides a one-shot query for a single contact.
     */
    suspend fun getContact(lookupKey: String): Contact?

    /**
     * Deletes a contact from the system contacts.
     */
    suspend fun deleteContact(lookupKey: String): Boolean

    /**
     * Updates the favorite status of a contact.
     */
    suspend fun updateFavorite(lookupKey: String, starred: Boolean): Boolean

    /**
     * Resolves a fresh lookup key for a contact given its previous (possibly stale) lookup key.
     */
    suspend fun resolveLatestLookupKey(oldLookupKey: String): String?
}
