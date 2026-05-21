package app.baldphone.neo.features.contacts.data

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log

import androidx.core.content.ContextCompat

import app.baldphone.neo.features.contacts.Address
import app.baldphone.neo.features.contacts.Contact
import app.baldphone.neo.features.contacts.Email
import app.baldphone.neo.features.contacts.Phone
import app.baldphone.neo.features.contacts.SimpleContact
import app.baldphone.neo.utils.messaging.SignalHandler
import app.baldphone.neo.utils.messaging.WhatsAppHandler

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ContactRepositoryImpl private constructor(private val context: Context) : ContactRepository {
    private val resolver: ContentResolver = context.contentResolver
    private val _contacts = MutableStateFlow<List<SimpleContact>?>(null)
    private val refreshMutex = Mutex()

    override val contacts: StateFlow<List<SimpleContact>?> = _contacts.asStateFlow()

    // TODO: ContentObserver?
    override suspend fun refresh() {
        refreshMutex.withLock {
            val newContacts = getAllContacts()
            if (newContacts != _contacts.value) {
                _contacts.value = newContacts
                Log.d(TAG, "refresh: Contacts updated (count: ${newContacts.size})")
            }
        }
    }

    // temporary for Java
    @Deprecated("Java interop only")
    override fun getRawContactId(contactId: Long): Long {
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { c ->
            if (c.moveToNext()) return c.getLong(c.getColumnIndexOrThrow(ContactsContract.RawContacts._ID))
        }
        return -1L
    }

    // temporary for Java
    @Deprecated("Java interop only")
    override fun getContactByLookupKeyJava(key: String): Contact? =
        queryContact("${ContactsContract.Contacts.LOOKUP_KEY}=?", arrayOf(key))

    @Deprecated("Java interop only")
    override fun getContactByIdJava(id: String): Contact? =
        queryContact("${ContactsContract.Contacts._ID}=?", arrayOf(id))

    /**
     * Provides a one-shot query for a single contact.
     */
    override suspend fun getContact(lookupKey: String): Contact? = withContext(Dispatchers.IO) {
        if (!hasContactsPermission()) return@withContext null
        Log.d(TAG, "getContact: $lookupKey")
        queryContact("${ContactsContract.Contacts.LOOKUP_KEY}=?", arrayOf(lookupKey))
    }

    /**
     * Retrieves a list of all contacts from the database.
     */
    override suspend fun getAllContacts(): List<SimpleContact> = withContext(Dispatchers.IO) {
        if (!hasContactsPermission()) return@withContext emptyList()

        val result = LinkedHashMap<Long, SimpleContact>()

        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            PHONE_PROJECTION,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.SORT_KEY_PRIMARY} ASC"
        )?.use { cursor ->
            val idIdx =
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val lookupIdx =
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
            val nameIdx =
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx =
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIdx =
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            val primaryIdx =
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY)
            val starredIdx =
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.STARRED)
            val typeIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
            val labelIdx =
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val number = cursor.getString(numberIdx) ?: continue

                val candidate = SimpleContact(
                    id = id,
                    lookupKey = cursor.getString(lookupIdx).orEmpty(),
                    name = cursor.getString(nameIdx) ?:  android.R.string.unknownName.toString(),
                    phoneNumber = number,
                    photoUri = cursor.getString(photoIdx),
                    isPrimary = cursor.getInt(primaryIdx) == 1,
                    isStarred = cursor.getInt(starredIdx) == 1,
                    phoneType = cursor.getInt(typeIdx),
                    phoneLabel = cursor.getString(labelIdx)
                )

                // deduplication
                val existing = result[id]
                if (existing == null || isBetter(candidate, existing)) {
                    result[id] = candidate
                }

            }
        }
        result.values.toList()
    }

    private fun isBetter(candidate: SimpleContact, current: SimpleContact): Boolean {
        if (candidate.isPrimary && !current.isPrimary) return true
        if (!current.isPrimary
            && candidate.phoneType == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
            && current.phoneType != ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
        ) return true

        return false
    }

    override suspend fun deleteContact(lookupKey: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasContactsPermission()) return@withContext false
        runCatching {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                lookupKey,
            )
            resolver.delete(lookupUri, null, null) > 0
        }
            .onFailure { Log.e("ContactProvider", "deleteContact: $lookupKey", it) }
            .getOrDefault(false)
    }

    /**
     * @param starred The desired new state of the contact's favorite status.
     */
    override suspend fun updateFavorite(lookupKey: String, starred: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (!hasContactsPermission()) return@withContext false
            runCatching {
                val values = ContentValues().apply {
                    put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0)
                }
                resolver.update(
                    ContactsContract.Contacts.CONTENT_URI,
                    values,
                    "${ContactsContract.Contacts.LOOKUP_KEY} = ?",
                    arrayOf(lookupKey),
                ) > 0
            }
                .onFailure { Log.e("ContactProvider", "updateFavorite: $lookupKey", it) }
                .getOrDefault(false)
        }

    /**
     * Resolves a fresh lookup key for a contact given its previous (possibly stale) lookup key.
     * Returns the fresh key, or null if the contact is not found or was deleted.
     */
    override suspend fun resolveLatestLookupKey(oldLookupKey: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val lookupUri =
                    Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, oldLookupKey)
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

    private fun queryLookupKey(contactUri: Uri?): String? {
        contactUri ?: return null
        return resolver.query(
            contactUri,
            arrayOf(ContactsContract.Contacts.LOOKUP_KEY),
            null, null, null,
        )?.use { c ->
            val col = c.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
            if (col != -1 && c.moveToFirst()) c.getString(col) else {
                Log.w(TAG, "No lookup key found for the given details: $contactUri")
                null
            }
        }
    }

    private fun queryContact(selection: String, args: Array<String>): Contact? {
        return resolver.query(
            ContactsContract.Contacts.CONTENT_URI, CONTACT_PROJECTION, selection, args, null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null

            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val keyIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val nameIdx =
                cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI)
            val starIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)

            loadContactDetails(
                id = cursor.getLong(idIdx),
                key = cursor.getString(keyIdx),
                name = cursor.getString(nameIdx) ?: android.R.string.unknownName.toString(),
                photoUri = cursor.getString(photoIdx),
                starred = cursor.getInt(starIdx) == 1
            )
        }
    }

    private fun loadContactDetails(
        id: Long, key: String, name: String, photoUri: String?, starred: Boolean
    ): Contact {
        val phones = mutableListOf<Phone>()
        val emails = mutableListOf<Email>()
        val addresses = mutableListOf<Address>()
        val whatsapp = mutableSetOf<String>()
        val signal = mutableSetOf<String>()
        var note: String? = null

        resolver.query(
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
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
                        phones += Phone(data2, data1, data3)

                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE ->
                        emails += Email(data2, data1, data3)

                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE ->
                        addresses += Address(data2, data1, data3)

                    ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> note = data1

                    WhatsAppHandler.WHATSAPP_PROFILE_MIMETYPE -> whatsapp += data1
                    SignalHandler.SIGNAL_CONTACT_MIMETYPE -> signal += data1
                }
            }
        }

        return Contact(
            id,
            key,
            name,
            photoUri,
            starred,
            note,
            phones,
            emails,
            addresses,
            whatsapp.toList(),
            signal.toList()
        )
    }

    private fun hasContactsPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    private fun hasWriteContactsPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.WRITE_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "ContactRepository"

        @Volatile
        private var INSTANCE: ContactRepository? = null

        fun getInstance(context: Context): ContactRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ContactRepositoryImpl(context.applicationContext).also { INSTANCE = it }
            }
        }

        private val CONTACT_PROJECTION = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Contacts.STARRED,
        )

        private val PHONE_PROJECTION = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.IS_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.STARRED,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL
        )

        private val DATA_PROJECTION = arrayOf(
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3
        )
    }
}
