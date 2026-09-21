package app.baldphone.neo.features.contacts.data.source

import android.database.Cursor
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils

import app.baldphone.neo.features.contacts.model.Address
import app.baldphone.neo.features.contacts.model.Email
import app.baldphone.neo.features.contacts.model.Phone
import app.baldphone.neo.features.contacts.model.SimpleContact
import app.baldphone.neo.utils.messaging.SignalHandler
import app.baldphone.neo.utils.messaging.WhatsAppHandler
import app.baldphone.neo.utils.toNormalizedLowercase

/**
 * Maps contacts-provider cursor rows into domain models.
 */
internal object ContactCursorMapper {
    val PHONE_PROJECTION =
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

    val DATA_PROJECTION =
        arrayOf(
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
            ContactsContract.RawContacts.ACCOUNT_TYPE
        )

    /** Sort order matching [PHONE_PROJECTION], grouping a contact's rows together. */
    const val PHONE_SORT_ORDER =
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC, " +
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} ASC"

    /**
     * Reads the current row of a [PHONE_PROJECTION] cursor as a [SimpleContact].
     *
     * @param unknownName Fallback used when the provider has no display name for the row.
     */
    fun readSimpleContact(
        cursor: Cursor,
        indices: PhoneIndices,
        unknownName: String
    ): SimpleContact {
        val rawNumber = cursor.getString(indices.number) ?: ""
        val displayName = cursor.getString(indices.name) ?: unknownName

        return SimpleContact(
            id = cursor.getLong(indices.id),
            lookupKey = cursor.getString(indices.lookup).orEmpty(),
            name = displayName,
            phoneNumber = rawNumber,
            normalizedNumber =
                cursor.getString(indices.normalizedNumber)
                    ?: PhoneNumberUtils.normalizeNumber(rawNumber),
            normalizedName = displayName.toNormalizedLowercase(),
            photoUri = cursor.getString(indices.photo),
            photoThumbnailUri = cursor.getString(indices.photoThumbnail),
            isPrimary = cursor.getInt(indices.primary) == 1,
            isStarred = cursor.getInt(indices.starred) == 1,
            phoneType = cursor.getInt(indices.type),
            phoneLabel = cursor.getString(indices.label)
        )
    }

    /**
     * Walks every row of a [DATA_PROJECTION] cursor for one contact and groups the rows by mime type.
     *
     * Phone rows are deduplicated across accounts on the way out, see [PhoneDeduplicator].
     */
    fun readContactData(cursor: Cursor): ContactData {
        val phones = mutableListOf<PhoneCandidate>()
        val emails = mutableListOf<Email>()
        val addresses = mutableListOf<Address>()
        val whatsapp = mutableSetOf<String>()
        val signal = mutableSetOf<String>()
        var note: String? = null
        var givenName: String? = null
        var familyName: String? = null

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

        return ContactData(
            addresses = addresses,
            emails = emails,
            familyName = familyName,
            givenName = givenName,
            note = note,
            phones = PhoneDeduplicator.deduplicate(phones),
            signalNumbers = signal.toList(),
            whatsappNumbers = whatsapp.toList()
        )
    }

    /** Per-row details of a contact read from the Data table. */
    data class ContactData(
        val note: String?,
        val phones: List<Phone>,
        val emails: List<Email>,
        val addresses: List<Address>,
        val whatsappNumbers: List<String>,
        val signalNumbers: List<String>,
        val givenName: String?,
        val familyName: String?
    )

    /** Column indices for a cursor opened with [PHONE_PROJECTION]. */
    class PhoneIndices(
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
}
