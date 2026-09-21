package app.baldphone.neo.features.contacts.data.source

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log

import androidx.core.content.ContextCompat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import app.baldphone.neo.features.contacts.ContactForm

/**
 * Write access to the contacts' provider.
 */
class ContactWriter(context: Context) {
    private val context = context.applicationContext
    private val resolver: ContentResolver get() = context.contentResolver

    /**
     * Inserts a brand-new contact using the local device account.
     * On success the result carries its raw contactId; failures carry the cause.
     */
    suspend fun insertContact(draft: ContactForm): Result<Long> =
        guardedWriteResult(errorTag = "insertContact failed") { resolver ->
            val ops = ArrayList<ContentProviderOperation>()
            val rawContactInsertIndex = ops.size
            ops +=
                ContentProviderOperation
                    .newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .build()
            ops += draft.buildDataInserts(rawContactBackReference = rawContactInsertIndex)

            val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            val rawContactUri =
                results.firstOrNull()?.uri
                    ?: error("insertContact: provider returned no raw contact uri")
            ContentUris.parseId(rawContactUri)
        }

    /**
     * Rewrites fields of the raw contact [rawContactId] from [draft].
     *
     * Within this one raw contact we mirror the "delete then insert" strategy the platform requires
     * for multi-row data (name/phones/emails/addresses). Returns true on success.
     *
     * Note for photo convention: null = leave as is, empty = remove, non-empty = set/replace.
     */
    suspend fun updateContact(rawContactId: Long, draft: ContactForm): Result<Boolean> =
        guardedWriteResult(
            errorTag = "updateContact failed for rawContactId: $rawContactId"
        ) { resolver ->
            val ops = ArrayList<ContentProviderOperation>()
            val rawSelection = "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"

            // Clear then re-insert
            for (mimeType in REWRITABLE_MIME_TYPES) {
                // If the draft's photo is null, we leave the current photo as is.
                if (mimeType == ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE && draft.photo == null) {
                    continue
                }
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

    /** Deletes a contact by lookup key. */
    suspend fun deleteContact(lookupKey: String): Result<Boolean> =
        guardedWriteResult(errorTag = "deleteContact: $lookupKey") { resolver ->
            val lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
            resolver.delete(lookupUri, null, null) > 0
        }

    /** Updates the favorite (starred) status of a contact. */
    suspend fun updateFavorite(lookupKey: String, starred: Boolean): Result<Boolean> =
        guardedWriteResult(errorTag = "updateFavorite: $lookupKey") { resolver ->
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
     * Runs [block] behind the [Manifest.permission.WRITE_CONTACTS] gate and reports the outcome.
     *
     * - missing permission -> [Result.failure] wrapping a [SecurityException]
     * - provider failure   -> [Result.failure] wrapping the thrown exception
     * - success            -> [Result.success] with the block's value
     */
    private suspend fun <T> guardedWriteResult(
        errorTag: String,
        block: (ContentResolver) -> T
    ): Result<T> =
        withContext(Dispatchers.IO) {
            if (!hasWritePermission()) {
                Log.w(TAG, "$errorTag: missing WRITE_CONTACTS permission")
                return@withContext Result.failure(
                    SecurityException("WRITE_CONTACTS permission not granted")
                )
            }
            try {
                Result.success(block(resolver))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, errorTag, throwable)
                Result.failure(throwable)
            }
        }

    private fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Builds the data-row insert operations shared by insert and update.
     * Must be provided exactly one of:
     * [rawContactBackReference] (the index of the raw contact insert earlier in the same batch) or
     * [rawContactId] (for an already-existing raw contact).
     */
    private fun ContactForm.buildDataInserts(
        rawContactBackReference: Int? = null,
        rawContactId: Long? = null
    ): List<ContentProviderOperation> {
        fun newDataInsert(): ContentProviderOperation.Builder =
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).apply {
                when {
                    rawContactBackReference != null -> {
                        withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactBackReference)
                    }

                    rawContactId != null -> {
                        withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    }

                    else -> {
                        error("Either a back reference or a raw contact id must be provided")
                    }
                }
            }

        // A data row that carries a single value plus a type (phone/email/postal).
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

        // Adds a single-value data row (phone/email/postal) only when the raw value isn't blank.
        fun MutableList<ContentProviderOperation>.addIfPresent(
            rawValue: String?,
            mimeType: String,
            valueColumn: String,
            typeColumn: String,
            typeValue: Int
        ) {
            rawValue.nullIfBlank()?.let { value ->
                this += singleRowInsert(mimeType, valueColumn, value, typeColumn, typeValue)
            }
        }

        val ops = ArrayList<ContentProviderOperation>()

        // Structured name:
        // 1) On insert this is the contact's only name row.
        // 2) On update the caller has already deleted the previous name row for this raw contact.
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

        ops.addIfPresent(
            preferredPhone,
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
        )

        ops.addIfPresent(
            otherPhone,
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME
        )

        ops.addIfPresent(
            email,
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Email.ADDRESS,
            ContactsContract.CommonDataKinds.Email.TYPE,
            ContactsContract.CommonDataKinds.Email.TYPE_HOME
        )

        ops.addIfPresent(
            address,
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
            ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
            ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME
        )

        photo?.takeIf { it.isNotEmpty() }?.let { bytes ->
            ops +=
                newDataInsert()
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                    ).withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, bytes)
                    .build()
        }

        return ops
    }

    private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

    companion object {
        private const val TAG = "ContactWriter"

        /**
         * Data mime types that the add/edit screen fully rewrites (delete + insert) on every update.
         * StructuredName is included so the name row is rewritten rather than duplicated.
         */
        private val REWRITABLE_MIME_TYPES =
            arrayOf(
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
            )
    }
}
