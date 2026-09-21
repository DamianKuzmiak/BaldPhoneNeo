package app.baldphone.neo.features.contacts.data.source

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.Log

import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

import app.baldphone.neo.features.contacts.model.Contact
import app.baldphone.neo.features.contacts.model.SimpleContact

/**
 * Read-only access to the contacts' provider. Cursor rows are turned into models by [ContactCursorMapper].
 */
class ContactReader(
    private val context: Context
) {
    private val resolver: ContentResolver get() = context.contentResolver

    fun hasReadContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Queries phone rows. Used by the repository to build deduplicated contacts and dialer search contacts.
     *
     * @param limit Maximum items to return. Use -1 for no limit.
     */
    suspend fun fetchPhoneContacts(limit: Int = -1): List<SimpleContact> =
        withContext(Dispatchers.IO) {
            if (!hasReadContactsPermission()) return@withContext emptyList()

            val baseUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val targetUri =
                if (limit > 0) {
                    baseUri.buildUpon().appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, limit.toString()).build()
                } else {
                    baseUri
                }

            Log.d(TAG, "fetchPhoneContacts: limit=$limit")
            val startTime = System.currentTimeMillis()

            resolver
                .query(
                    targetUri,
                    ContactCursorMapper.PHONE_PROJECTION,
                    null,
                    null,
                    ContactCursorMapper.PHONE_SORT_ORDER
                )?.use { cursor ->
                    val indices = ContactCursorMapper.PhoneIndices(cursor)
                    val unknownName = context.getString(android.R.string.unknownName)

                    buildList {
                        while (cursor.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            add(ContactCursorMapper.readSimpleContact(cursor, indices, unknownName))

                            // Manual safety break.
                            // We use size >= limit because if limit is 10 and we just added
                            // the 10th item, we should stop immediately.
                            if (limit > 0 && size >= limit) break
                        }
                    }
                }.also {
                    Log.d(
                        TAG,
                        "fetchPhoneContacts: Completed in ${System.currentTimeMillis() - startTime}ms"
                    )
                } ?: emptyList()
        }

    /** Loads full contact details for a single contact by lookup key. */
    suspend fun queryContact(lookupKey: String): Contact? =
        withContext(Dispatchers.IO) {
            queryContactInternal("${ContactsContract.Contacts.LOOKUP_KEY}=?", arrayOf(lookupKey))
        }

    /** Resolves the raw contact ID for a given contact ID. */
    suspend fun getRawContactId(contactId: Long): Long =
        guarded(default = -1L, error = "getRawContactId: $contactId") { resolver ->
            resolver
                .query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    arrayOf(ContactsContract.RawContacts._ID),
                    "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                    arrayOf(contactId.toString()),
                    null
                )?.use { c ->
                    if (c.moveToNext()) {
                        c.getLong(c.getColumnIndexOrThrow(ContactsContract.RawContacts._ID))
                    } else {
                        null
                    }
                } ?: -1L
        }

    /** Resolves a fresh lookup key for a contact given its previous (possibly stale) lookup key. */
    suspend fun resolveLatestLookupKey(oldLookupKey: String): String? =
        guarded(default = null, error = "resolveLatestLookupKey: $oldLookupKey") { resolver ->
            val lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, oldLookupKey)
            val contactUri = ContactsContract.Contacts.lookupContact(resolver, lookupUri)
            if (contactUri == null) {
                Log.i(TAG, "Contact not found for lookup key: $oldLookupKey")
                null
            } else {
                queryLookupKey(contactUri)
            }
        }

    /** Resolves the best available phone number for the contact identified by [lookupKey]. */
    @RequiresPermission(Manifest.permission.READ_CONTACTS)
    suspend fun resolvePhoneNumber(lookupKey: String): String? =
        guarded(default = null, error = "resolvePhoneNumber: $lookupKey") { resolver ->
            resolver
                .query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY} = ?",
                    arrayOf(lookupKey),
                    "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC, " +
                        "${ContactsContract.CommonDataKinds.Phone.TYPE} ASC"
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }

    /** Resolves a contact lookup key from a cached URI, phone number, or display name. */
    suspend fun resolveLookupKey(
        cachedLookupUri: String?,
        number: String?,
        name: String?
    ): String? =
        guarded(default = null, error = "resolveLookupKey") {
            // Tried in order of confidence: cached URI, then phone number, then display name.
            resolveFromCachedUri(cachedLookupUri) ?: resolveFromNumber(number) ?: resolveFromName(name) ?: run {
                Log.w(TAG, "No lookup key found for the given details.")
                null
            }
        }

    private fun resolveFromCachedUri(cachedLookupUri: String?): String? {
        if (cachedLookupUri.isNullOrEmpty()) return null
        return runCatching {
            val freshUri = ContactsContract.Contacts.lookupContact(resolver, cachedLookupUri.toUri())
            queryLookupKey(freshUri)
        }.getOrNull()
    }

    private fun resolveFromNumber(number: String?): String? {
        if (number.isNullOrEmpty()) return null
        val normalized = PhoneNumberUtils.normalizeNumber(number)
        if (normalized.isNullOrEmpty()) return null
        val filterUri =
            Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(normalized)
            )
        return queryLookupKey(filterUri)
    }

    private fun resolveFromName(name: String?): String? {
        if (name.isNullOrEmpty()) return null
        val filterUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(name))
        return queryLookupKey(filterUri)
    }

    private fun queryLookupKey(contactUri: Uri?): String? {
        contactUri ?: return null
        return resolver
            .query(
                contactUri,
                arrayOf(ContactsContract.Contacts.LOOKUP_KEY),
                null,
                null,
                null
            )?.use { c ->
                val col = c.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                if (col != -1 && c.moveToFirst()) {
                    c.getString(col)
                } else {
                    Log.w(TAG, "No lookup key found for: $contactUri")
                    null
                }
            }
    }

    private fun queryContactInternal(
        selection: String,
        args: Array<String>
    ): Contact? =
        resolver
            .query(
                ContactsContract.Contacts.CONTENT_URI,
                CONTACT_PROJECTION,
                selection,
                args,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null

                val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                val keyIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
                val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val photoIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI)
                val photoThumbIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
                val starIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)

                val id = cursor.getLong(idIdx)
                val details = loadContactDetails(id)

                Contact(
                    id = id,
                    lookupKey = cursor.getString(keyIdx),
                    name = cursor.getString(nameIdx) ?: context.getString(android.R.string.unknownName),
                    photoUri = cursor.getString(photoIdx),
                    photoThumbnailUri = cursor.getString(photoThumbIdx),
                    isStarred = cursor.getInt(starIdx) == 1,
                    note = details.note,
                    phones = details.phones,
                    emails = details.emails,
                    addresses = details.addresses,
                    whatsappNumbers = details.whatsappNumbers,
                    signalNumbers = details.signalNumbers,
                    givenName = details.givenName,
                    familyName = details.familyName
                )
            }

    /**
     * Reads all rows from the Data table for [contactId] and groups them into a [ContactCursorMapper.ContactData].
     */
    private fun loadContactDetails(contactId: Long): ContactCursorMapper.ContactData =
        resolver
            .query(
                ContactsContract.Data.CONTENT_URI,
                ContactCursorMapper.DATA_PROJECTION,
                "${ContactsContract.Data.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )?.use { cursor ->
                ContactCursorMapper.readContactData(cursor)
            } ?: EMPTY_CONTACT_DATA

    /**
     * Runs [block] behind the [Manifest.permission.READ_CONTACTS] gate,
     * returning [default] when the permission is missing or the block throws.
     */
    private suspend fun <T> guarded(default: T, error: String, block: (ContentResolver) -> T): T =
        withContext(Dispatchers.IO) {
            if (!hasReadContactsPermission()) return@withContext default
            runCatching { block(resolver) }.onFailure { Log.e(TAG, error, it) }.getOrDefault(default)
        }

    companion object {
        private const val TAG = "ContactReader"

        private val CONTACT_PROJECTION =
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.STARRED
            )

        private val EMPTY_CONTACT_DATA =
            ContactCursorMapper.ContactData(
                note = null,
                phones = emptyList(),
                emails = emptyList(),
                addresses = emptyList(),
                whatsappNumbers = emptyList(),
                signalNumbers = emptyList(),
                givenName = null,
                familyName = null
            )
    }
}
