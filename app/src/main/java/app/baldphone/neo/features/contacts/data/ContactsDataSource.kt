package app.baldphone.neo.features.contacts.data

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
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

import app.baldphone.neo.features.contacts.Address
import app.baldphone.neo.features.contacts.Contact
import app.baldphone.neo.features.contacts.Email
import app.baldphone.neo.features.contacts.Phone
import app.baldphone.neo.features.contacts.SimpleContact
import app.baldphone.neo.utils.messaging.SignalHandler
import app.baldphone.neo.utils.messaging.WhatsAppHandler
import app.baldphone.neo.utils.toNormalizedLowercase

/**
 * Pure request-response wrapper for ContentResolver. Does not own any state or caching.
 */
class ContactsDataSource(
    private val context: Context
) {
    private val resolver: ContentResolver = context.contentResolver

    /**
     * Queries phone rows, useful for both initial fast fetch and full background fetch.
     * Used by Repository to build deduplicated contacts and dialer search contacts.
     *
     * @param limit Maximum items to return. Use -1 for no limit.
     */
    suspend fun fetchPhoneContacts(limit: Int = -1): List<SimpleContact> =
        withContext(Dispatchers.IO) {
            if (!hasContactsPermission()) return@withContext emptyList()

            val baseUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val targetUri =
                if (limit > 0) {
                    baseUri
                        .buildUpon()
                        .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, limit.toString())
                        .build()
                } else {
                    baseUri
                }

            val sortOrder =
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC, ${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} ASC"

            Log.d(TAG, "fetchPhoneContacts: limit=$limit")
            val startTime = System.currentTimeMillis()

            resolver
                .query(targetUri, PHONE_PROJECTION, null, null, sortOrder)
                ?.use { cursor ->
                    val indices = PhoneIndices(cursor)
                    val unknownName = context.getString(android.R.string.unknownName)

                    buildList {
                        while (cursor.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            add(
                                SimpleContact(
                                    id = cursor.getLong(indices.id),
                                    lookupKey = cursor.getString(indices.lookup).orEmpty(),
                                    name = cursor.getString(indices.name) ?: unknownName,
                                    phoneNumber = cursor.getString(indices.number) ?: "",
                                    normalizedNumber =
                                        (
                                            cursor.getString(indices.number)
                                                ?: ""
                                        ).replace(Regex("[^0-9]"), ""),
                                    normalizedName =
                                        (
                                            cursor.getString(indices.name)
                                                ?: unknownName
                                        ).toNormalizedLowercase(),
                                    photoUri = cursor.getString(indices.photo),
                                    photoThumbnailUri = cursor.getString(indices.photoThumbnail),
                                    isPrimary = cursor.getInt(indices.primary) == 1,
                                    isStarred = cursor.getInt(indices.starred) == 1,
                                    phoneType = cursor.getInt(indices.type),
                                    phoneLabel = cursor.getString(indices.label)
                                )
                            )

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

    /**
     * Loads full contact details for a single contact by lookup key.
     */
    suspend fun queryContact(lookupKey: String): Contact? =
        withContext(Dispatchers.IO) {
            queryContactInternal(
                "${ContactsContract.Contacts.LOOKUP_KEY}=?",
                arrayOf(lookupKey)
            )
        }

    /**
     * Loads full contact details by contact ID. Java interop.
     */
    suspend fun queryContactById(id: String): Contact? =
        withContext(Dispatchers.IO) {
            queryContactInternal(
                "${ContactsContract.Contacts._ID}=?",
                arrayOf(id)
            )
        }

    /**
     * Resolves the raw contact ID for a given contact ID.
     */
    suspend fun getRawContactId(contactId: Long): Long =
        withContext(Dispatchers.IO) {
            resolver
                .query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    arrayOf(ContactsContract.RawContacts._ID),
                    "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                    arrayOf(contactId.toString()),
                    null
                )?.use { c ->
                    if (c.moveToNext()) {
                        return@withContext c.getLong(c.getColumnIndexOrThrow(ContactsContract.RawContacts._ID))
                    }
                }
            -1L
        }

    /**
     * Deletes a contact by lookup key.
     */
    suspend fun deleteContact(lookupKey: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!hasContactsPermission()) return@withContext false
            runCatching {
                val lookupUri =
                    Uri.withAppendedPath(
                        ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                        lookupKey
                    )
                resolver.delete(lookupUri, null, null) > 0
            }.onFailure { Log.e(TAG, "deleteContact: $lookupKey", it) }
                .getOrDefault(false)
        }

    /**
     * Updates the favorite (starred) status of a contact.
     */
    suspend fun updateFavorite(
        lookupKey: String,
        starred: Boolean
    ): Boolean =
        withContext(Dispatchers.IO) {
            if (!hasContactsPermission()) return@withContext false
            runCatching {
                val values =
                    ContentValues().apply {
                        put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0)
                    }
                resolver.update(
                    ContactsContract.Contacts.CONTENT_URI,
                    values,
                    "${ContactsContract.Contacts.LOOKUP_KEY} = ?",
                    arrayOf(lookupKey)
                ) > 0
            }.onFailure { Log.e(TAG, "updateFavorite: $lookupKey", it) }
                .getOrDefault(false)
        }

    /**
     * Resolves a fresh lookup key for a contact given its previous (possibly stale) lookup key.
     */
    suspend fun resolveLatestLookupKey(oldLookupKey: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val lookupUri =
                    Uri.withAppendedPath(
                        ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                        oldLookupKey
                    )
                val contactUri =
                    ContactsContract.Contacts.lookupContact(resolver, lookupUri) ?: run {
                        Log.i(TAG, "Contact not found for lookup key: $oldLookupKey")
                        return@withContext null
                    }
                queryLookupKey(contactUri)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error resolving lookup key.", e)
                null
            }
        }

    /**
     * Resolves the best available phone number for the contact identified by [lookupKey].
     */
    @RequiresPermission(Manifest.permission.READ_CONTACTS)
    suspend fun resolvePhoneNumber(lookupKey: String): String? =
        withContext(Dispatchers.IO) {
            if (!hasContactsPermission()) return@withContext null
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val selection = "${ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY} = ?"
            val selectionArgs = arrayOf(lookupKey)
            val sortOrder =
                "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC, " +
                    "${ContactsContract.CommonDataKinds.Phone.TYPE} ASC"

            try {
                resolver
                    .query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        sortOrder
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            return@withContext cursor.getString(0)
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving phone number for lookupKey: $lookupKey", e)
            }
            null
        }

    /**
     * Resolves a contact lookup key from a cached URI, phone number, or display name.
     */
    suspend fun resolveLookupKey(
        cachedLookupUri: String?,
        number: String?,
        name: String?
    ): String? =
        withContext(Dispatchers.IO) {
            // 1. Cached URI
            if (!cachedLookupUri.isNullOrEmpty()) {
                runCatching {
                    val cached = cachedLookupUri.toUri()
                    val freshUri = ContactsContract.Contacts.lookupContact(resolver, cached)
                    queryLookupKey(freshUri)
                }.getOrNull()?.let { return@withContext it }
            }

            // 2. Phone number
            if (!number.isNullOrEmpty()) {
                val normalized = PhoneNumberUtils.normalizeNumber(number)
                if (!normalized.isNullOrEmpty()) {
                    val filterUri =
                        Uri.withAppendedPath(
                            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                            Uri.encode(normalized)
                        )
                    queryLookupKey(filterUri)?.let { return@withContext it }
                }
            }

            // 3. Display name
            if (!name.isNullOrEmpty()) {
                val filterUri =
                    Uri.withAppendedPath(
                        ContactsContract.Contacts.CONTENT_FILTER_URI,
                        Uri.encode(name)
                    )
                queryLookupKey(filterUri)?.let { return@withContext it }
            }

            Log.w(TAG, "No lookup key found for the given details.")
            null
        }

    // ---- Private helpers ----

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
    ): Contact? {
        return resolver
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
                val nameIdx =
                    cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val photoIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI)
                val photoThumbIdx =
                    cursor.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
                val starIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)

                loadContactDetails(
                    id = cursor.getLong(idIdx),
                    key = cursor.getString(keyIdx),
                    name =
                        cursor.getString(nameIdx)
                            ?: context.getString(android.R.string.unknownName),
                    photoUri = cursor.getString(photoIdx),
                    photoThumbnailUri = cursor.getString(photoThumbIdx),
                    starred = cursor.getInt(starIdx) == 1
                )
            }
    }

    private fun loadContactDetails(
        id: Long,
        key: String,
        name: String,
        photoUri: String?,
        photoThumbnailUri: String?,
        starred: Boolean
    ): Contact {
        val phones = mutableListOf<Phone>()
        val emails = mutableListOf<Email>()
        val addresses = mutableListOf<Address>()
        val whatsapp = mutableSetOf<String>()
        val signal = mutableSetOf<String>()
        var note: String? = null

        resolver
            .query(
                ContactsContract.Data.CONTENT_URI,
                DATA_PROJECTION,
                "${ContactsContract.Data.CONTACT_ID} = ?",
                arrayOf(id.toString()),
                null
            )?.use { cursor ->
                val mimeIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
                val data1Idx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA1)
                val data2Idx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA2)
                val data3Idx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA3)

                while (cursor.moveToNext()) {
                    val mime = cursor.getString(mimeIdx)
                    val data1 = cursor.getString(data1Idx) ?: continue
                    val data2 = cursor.getInt(data2Idx)
                    val data3 = cursor.getString(data3Idx)

                    when (mime) {
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                            phones += Phone(data2, data1, data3)
                        }

                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                            emails += Email(data2, data1, data3)
                        }

                        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                            addresses += Address(data2, data1, data3)
                        }

                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
                            note = data1
                        }

                        WhatsAppHandler.WHATSAPP_PROFILE_MIMETYPE -> {
                            whatsapp += data1
                        }

                        SignalHandler.SIGNAL_CONTACT_MIMETYPE -> {
                            signal += data1
                        }
                    }
                }
            }

        return Contact(
            id,
            key,
            name,
            photoUri,
            photoThumbnailUri,
            starred,
            note,
            phones,
            emails,
            addresses,
            whatsapp.toList(),
            signal.toList()
        )
    }

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    // Helper to hold indices
    private class PhoneIndices(
        cursor: Cursor
    ) {
        val id = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val lookup = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
        val name = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val number = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val photo = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
        val photoThumbnail =
            cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
        val primary =
            cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY)
        val starred = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.STARRED)
        val type = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
        val label = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)
    }

    companion object {
        private const val TAG = "ContactsDataSource"

        private val CONTACT_PROJECTION =
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.STARRED
            )

        private val PHONE_PROJECTION =
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ContactsContract.CommonDataKinds.Phone.IS_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.STARRED,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL
            )

        private val DATA_PROJECTION =
            arrayOf(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA2,
                ContactsContract.Data.DATA3
            )
    }
}
