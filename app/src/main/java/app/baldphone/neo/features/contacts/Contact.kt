package app.baldphone.neo.features.contacts

import android.content.res.Resources
import android.provider.ContactsContract

/** Full contact with all related data (phones, emails, etc.) */
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
    val signalNumbers: List<String>
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

/** Simplified version of a contact, used for lists and searches */
data class SimpleContact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String? = null,
    val photoThumbnailUri: String? = null,
    val isStarred: Boolean = false,
    val phoneNumber: String,
    val normalizedNumber: String = "",
    val normalizedName: String = "",
    val isPrimary: Boolean = false,
    val phoneType: Int = 0,
    val phoneLabel: String? = null
)

/** UI Model for contact list */
sealed interface ContactItemType {
    data class Header(
        val letter: String
    ) : ContactItemType

    data class ContactItem(
        val contact: SimpleContact
    ) : ContactItemType
}

/** Helper types for contacts */
interface Labeled {
    val type: Int
    val value: String
    val label: String?

    fun getLabel(res: Resources): CharSequence
}

data class Phone(
    override val type: Int,
    override val value: String,
    override val label: String? = null
) : Labeled {
    override fun getLabel(res: Resources): CharSequence =
        ContactsContract.CommonDataKinds.Phone.getTypeLabel(res, type, label)
}

data class Email(
    override val type: Int,
    override val value: String,
    override val label: String? = null
) : Labeled {
    override fun getLabel(res: Resources): CharSequence =
        ContactsContract.CommonDataKinds.Email.getTypeLabel(res, type, label)
}

data class Address(
    override val type: Int,
    override val value: String,
    override val label: String? = null
) : Labeled {
    override fun getLabel(res: Resources): CharSequence =
        ContactsContract.CommonDataKinds.StructuredPostal.getTypeLabel(res, type, label)
}
