package app.baldphone.neo.features.contacts.data

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
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
import app.baldphone.neo.features.contacts.ContactForm
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
            if (!hasReadContactsPermission()) return@withContext emptyList()

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
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC, " +
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} ASC"

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
                                        cursor.getString(indices.normalizedNumber)
                                            ?: PhoneNumberUtils.normalizeNumber(
                                                cursor.getString(indices.number) ?: ""
                                            ),
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
            queryContactInternal("${ContactsContract.Contacts.LOOKUP_KEY}=?", arrayOf(lookupKey))
        }

    /**
     * Resolves the raw contact ID for a given contact ID.
     */
    suspend fun getRawContactId(contactId: Long): Long =
        guardedIo(default = -1L, errorTag = "getRawContactId: $contactId") {
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

    /**
     * Deletes a contact by lookup key.
     */
    suspend fun deleteContact(lookupKey: String): Boolean =
        guardedIo(default = false, errorTag = "deleteContact: $lookupKey") {
            val lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
            resolver.delete(lookupUri, null, null) > 0
        }

    /**
     * Updates the favorite (starred) status of a contact.
     */
    suspend fun updateFavorite(lookupKey: String, starred: Boolean): Boolean =
        guardedIo(default = false, errorTag = "updateFavorite: $lookupKey") {
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
        }

    /**
     * Resolves a fresh lookup key for a contact given its previous (possibly stale) lookup key.
     */
    suspend fun resolveLatestLookupKey(oldLookupKey: String): String? =
        guardedIo(default = null, errorTag = "resolveLatestLookupKey: $oldLookupKey") {
            val lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, oldLookupKey)
            val contactUri = ContactsContract.Contacts.lookupContact(resolver, lookupUri)
            if (contactUri == null) {
                Log.i(TAG, "Contact not found for lookup key: $oldLookupKey")
                null
            } else {
                queryLookupKey(contactUri)
            }
        }

    /**
     * Resolves the best available phone number for the contact identified by [lookupKey].
     */
    @RequiresPermission(Manifest.permission.READ_CONTACTS)
    suspend fun resolvePhoneNumber(lookupKey: String): String? =
        guardedIo(default = null, errorTag = "resolvePhoneNumber: $lookupKey") {
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

    /**
     * Resolves a contact lookup key from a cached URI, phone number, or display name.
     */
    suspend fun resolveLookupKey(
        cachedLookupUri: String?,
        number: String?,
        name: String?
    ): String? =
        guardedIo(default = null, errorTag = "resolveLookupKey") {
            // Tried in order of confidence: cached URI, then phone number, then display name.
            resolveFromCachedUri(cachedLookupUri)
                ?: resolveFromNumber(number)
                ?: resolveFromName(name)
                ?: run {
                    Log.w(TAG, "No lookup key found for the given details.")
                    null
                }
        }

    private fun resolveFromCachedUri(cachedLookupUri: String?): String? {
        if (cachedLookupUri.isNullOrEmpty()) return null
        return runCatching {
            val freshUri =
                ContactsContract.Contacts.lookupContact(resolver, cachedLookupUri.toUri())
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
        val filterUri =
            Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(name))
        return queryLookupKey(filterUri)
    }

    fun hasReadContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    // ---- Writes ----

    /**
     * Inserts a brand-new contact and returns its raw contact id, or -1 on failure.
     * The photo (if any) is written separately by the repository once the raw id is known.
     */
    suspend fun insertContact(draft: ContactForm): Long =
        guardedIo(default = -1L, errorTag = "insertContact failed") {
            val ops = ArrayList<ContentProviderOperation>()
            ops +=
                ContentProviderOperation
                    .newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.DIRTY, 0)
                    .build()
            ops += draft.buildDataInserts(rawContactBackReference = 0)

            val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            val rawContactUri = results.firstOrNull()?.uri ?: return@guardedIo -1L
            ContentUris.parseId(rawContactUri)
        }

    /**
     * Rewrites the editable fields of the raw contact [rawContactId] from [draft].
     *
     * Scoping every delete/insert to RAW_CONTACT_ID (rather than the aggregated CONTACT_ID) is what
     * keeps data owned by other raw contacts of the same aggregate, e.g. WhatsApp, Signal, SIM, or a
     * secondary account, untouched. Within this one raw contact we mirror the "delete then insert"
     * strategy the platform requires for multi-row data (name/phones/emails/addresses). Returns true
     * on success.
     */
    suspend fun updateContact(contactId: Long, rawContactId: Long, draft: ContactForm): Boolean =
        guardedIo(default = false, errorTag = "updateContact failed for $contactId (raw $rawContactId)") {
            val ops = ArrayList<ContentProviderOperation>()
            val rawSelection =
                "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"

            // Clear then re-insert the rows this editor owns, scoped to the editable raw contact.
            for (mimeType in REWRITABLE_MIME_TYPES) {
                ops +=
                    ContentProviderOperation
                        .newDelete(ContactsContract.Data.CONTENT_URI)
                        .withSelection(rawSelection, arrayOf(rawContactId.toString(), mimeType))
                        .build()
            }
            ops += draft.buildDataInserts(rawContactId = rawContactId)

            resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        }

    /**
     * Writes [jpegBytes] as the display photo of the raw contact, or deletes the existing photo
     * when [jpegBytes] is null.
     */
    suspend fun writeContactPhoto(rawContactId: Long, jpegBytes: ByteArray?): Boolean =
        guardedIo(default = false, errorTag = "writeContactPhoto failed for $rawContactId") {
            if (jpegBytes == null) {
                resolver.delete(
                    ContactsContract.Data.CONTENT_URI,
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(
                        rawContactId.toString(),
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                    )
                )
                return@guardedIo true
            }

            val rawContactUri = ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawContactId)
            val displayPhotoUri =
                Uri.withAppendedPath(
                    rawContactUri,
                    ContactsContract.RawContacts.DisplayPhoto.CONTENT_DIRECTORY
                )
            resolver.openAssetFileDescriptor(displayPhotoUri, "rw")?.use { fd ->
                fd.createOutputStream().use { it.write(jpegBytes) }
            }
            true
        }

    // ---- Private helpers ----

    /**
     * Runs [block] on [Dispatchers.IO] behind the contacts permission check, returning [default]
     * when the permission is missing or [block] throws.
     */
    private suspend fun <T> guardedIo(
        default: T,
        errorTag: String,
        block: () -> T
    ): T =
        withContext(Dispatchers.IO) {
            if (!hasReadContactsPermission()) return@withContext default
            runCatching(block)
                .onFailure { Log.e(TAG, errorTag, it) }
                .getOrDefault(default)
        }

    private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

    /**
     * Builds the data-row insert operations shared by insert and update. Exactly one of
     * [rawContactBackReference] (for a batch that also inserts the raw contact) or [rawContactId]
     * (for an existing raw contact) must be provided.
     */
    private fun ContactForm.buildDataInserts(
        rawContactBackReference: Int? = null,
        rawContactId: Long? = null
    ): List<ContentProviderOperation> {
        fun newDataInsert(): ContentProviderOperation.Builder =
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).apply {
                when {
                    rawContactBackReference != null -> {
                        withValueBackReference(
                            ContactsContract.Data.RAW_CONTACT_ID,
                            rawContactBackReference
                        )
                    }

                    rawContactId != null -> {
                        withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    }

                    else -> {
                        error("Either a back reference or a raw contact id must be provided")
                    }
                }
            }

        // A data row that carries a single value plus a type (phone/email/postal share this shape).
        fun singleRowInsert(
            mimeType: String,
            valueColumn: String,
            value: String,
            typeColumn: String,
            typeValue: Int
        ): ContentProviderOperation =
            newDataInsert()
                .withValue(ContactsContract.Data.MIMETYPE, mimeType)
                .withValue(valueColumn, value)
                .withValue(typeColumn, typeValue)
                .build()

        val ops = ArrayList<ContentProviderOperation>()

        // Structured name. On insert this is the contact's only name row; on update the caller has
        // already deleted the previous name row for this raw contact (StructuredName is part of
        // REWRITABLE_MIME_TYPES), so re-inserting keeps a single, up-to-date name row.
        ops +=
            newDataInsert()
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                ).withValue(
                    ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                    givenName.nullIfBlank()
                ).withValue(
                    ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
                    familyName.nullIfBlank()
                ).build()

        preferredPhone.nullIfBlank()?.let { number ->
            ops +=
                singleRowInsert(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    number,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                )
        }

        otherPhone.nullIfBlank()?.let { number ->
            ops +=
                singleRowInsert(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    number,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_HOME
                )
        }

        email.nullIfBlank()?.let { addr ->
            ops +=
                singleRowInsert(
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                    addr,
                    ContactsContract.CommonDataKinds.Email.TYPE,
                    ContactsContract.CommonDataKinds.Email.TYPE_HOME
                )
        }

        address.nullIfBlank()?.let { postal ->
            ops +=
                singleRowInsert(
                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
                    postal,
                    ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
                    ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME
                )
        }

        return ops
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
    ): Contact? {
        return resolver
            .query(ContactsContract.Contacts.CONTENT_URI, CONTACT_PROJECTION, selection, args, null)
            ?.use { cursor ->
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
                    name =
                        cursor.getString(nameIdx)
                            ?: context.getString(android.R.string.unknownName),
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
    }

    /**
     * Reads all rows from the Data table for [contactId] and groups them into a [ContactData].
     */
    private fun loadContactDetails(contactId: Long): ContactData {
        val phones = mutableListOf<PhoneCandidate>()
        val emails = mutableListOf<Email>()
        val addresses = mutableListOf<Address>()
        val whatsapp = mutableSetOf<String>()
        val signal = mutableSetOf<String>()
        var note: String? = null
        var givenName: String? = null
        var familyName: String? = null

        resolver
            .query(
                ContactsContract.Data.CONTENT_URI,
                DATA_PROJECTION,
                "${ContactsContract.Data.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
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
                            val normalizedIdx =
                                cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
                            val accountTypeIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)

                            val normalizedNumber =
                                normalizedIdx.takeIf { it != -1 }?.let { cursor.getString(it) }
                                    ?: PhoneNumberUtils.normalizeNumber(data1).takeIf { it.isNotEmpty() }
                            val accountType = accountTypeIdx.takeIf { it != -1 }?.let { cursor.getString(it) }

                            phones +=
                                PhoneCandidate(
                                    phone =
                                        Phone(
                                            value = data1, // number
                                            type = data2,
                                            label = data3,
                                            normalizedNumber = normalizedNumber
                                        ),
                                    accountType = accountType
                                )
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

                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                            givenName = cursor.getString(data2Idx)
                            familyName = data3
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

        return ContactData(
            addresses = addresses,
            emails = emails,
            familyName = familyName,
            givenName = givenName,
            note = note,
            phones = deduplicatePhones(phones),
            signalNumbers = signal.toList(),
            whatsappNumbers = whatsapp.toList()
        )
    }

    /**
     * Deduplicates phone numbers across different accounts.
     *
     * The same number can appear multiple times, e.g. once from the SIM card and once from a
     * synced account, or across two different accounts. When that happens we keep the "richest"
     * source rather than a raw SIM entry.

     */
    private fun deduplicatePhones(candidates: List<PhoneCandidate>): List<Phone> {
        if (candidates.isEmpty()) return emptyList()

        val bestCandidates = LinkedHashMap<String, PhoneCandidate>()

        for (candidate in candidates) {
            val key = candidate.normalizedNumberKey()
            val existing = bestCandidates[key]

            if (existing == null || isBetterAccount(candidate, existing)) {
                bestCandidates[key] = candidate
            }
        }

        return bestCandidates.values.map { it.phone }
    }

    private fun isBetterAccount(candidate: PhoneCandidate, existing: PhoneCandidate): Boolean =
        if (candidate.sourceRank != existing.sourceRank) {
            candidate.sourceRank > existing.sourceRank
        } else {
            candidate.phone.type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE &&
                existing.phone.type != ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
        }

    /**
     * Per-row details of a contact read from the Data table.
     */
    private data class ContactData(
        val note: String?,
        val phones: List<Phone>,
        val emails: List<Email>,
        val addresses: List<Address>,
        val whatsappNumbers: List<String>,
        val signalNumbers: List<String>,
        val givenName: String?,
        val familyName: String?
    )

    private data class PhoneCandidate(
        val phone: Phone,
        val accountType: String?
    ) {
        val isSim: Boolean
            get() = accountType?.contains(SIM_ACCOUNT_TYPE_SUFFIX, ignoreCase = true) == true

        val sourceRank: Int
            get() = if (isSim) SOURCE_RANK_SIM else SOURCE_RANK_ACCOUNT

        /** To detect the same number across accounts. */
        fun normalizedNumberKey(): String =
            phone.normalizedNumber?.takeIf { it.isNotEmpty() } ?: phone.value
    }

    private class PhoneIndices(
        cursor: Cursor
    ) {
        val id = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val lookup = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
        val name = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val number = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val normalizedNumber = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
        val photo = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
        val photoThumbnail = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
        val primary = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY)
        val starred = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.STARRED)
        val type = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
        val label = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)
    }

    companion object {
        private const val TAG = "ContactsDataSource"

        // SIM account types are vendor-specific (e.g. "vnd.sec.contact.sim", "com.android.contacts.sim")...
        private const val SIM_ACCOUNT_TYPE_SUFFIX = ".sim"

        // Source ranking used when deduplicating a number across sources. Higher wins.
        private const val SOURCE_RANK_SIM = 0
        private const val SOURCE_RANK_ACCOUNT = 1

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
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.IS_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.LABEL,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.STARRED,
                ContactsContract.CommonDataKinds.Phone.TYPE
            )

        private val DATA_PROJECTION =
            arrayOf(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA2,
                ContactsContract.Data.DATA3,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                ContactsContract.RawContacts.ACCOUNT_TYPE
            )

        /**
         * Data mime types that the add/edit screen fully owns and rewrites (delete + insert) on
         * every update, scoped to the editable raw contact. StructuredName is included so the name
         * row is rewritten rather than duplicated.
         */
        private val REWRITABLE_MIME_TYPES =
            arrayOf(
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE
            )
    }
}
