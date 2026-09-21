package app.baldphone.neo.features.contacts.model

import android.provider.ContactsContract

/**
 * Represents a complete contact entry, including related details such as phone numbers,
 * email addresses, postal information, data from messaging apps, and more.
 */
data class Contact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
    val photoThumbnailUri: String?,
    val isStarred: Boolean,
    val note: String?,
    val phones: List<Phone>,
    val emails: List<Email>,
    val addresses: List<Address>,
    val whatsappNumbers: List<String>,
    val signalNumbers: List<String>,
    val givenName: String? = null,
    val familyName: String? = null
) {
    val lookupUri: android.net.Uri
        get() = ContactsContract.Contacts.getLookupUri(id, lookupKey)

    val mobilePhone: String?
        get() =
            phones
                .firstOrNull {
                    it.type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                }?.value

    val homePhone: String?
        get() =
            phones
                .firstOrNull {
                    it.type == ContactsContract.CommonDataKinds.Phone.TYPE_HOME
                }?.value

    val firstAddress: String?
        get() = addresses.firstOrNull()?.value

    val primaryEmail: String?
        get() = emails.firstOrNull()?.value
}
