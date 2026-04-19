package app.baldphone.neo.contacts.data

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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Pure request-response wrapper for ContentResolver. Does not own any state or caching.
 */
class ContactsDataSource(
    private val context: Context
) {
    private val resolver: ContentResolver = context.contentResolver

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

    @Deprecated("Java interop only")
    fun resolvePhoneNumberBlocking(lookupKey: String): String? =
        runBlocking {
            resolvePhoneNumber(lookupKey)
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

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "ContactsDataSource"
    }
}
