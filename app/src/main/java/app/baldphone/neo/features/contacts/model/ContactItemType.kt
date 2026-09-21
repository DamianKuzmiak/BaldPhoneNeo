package app.baldphone.neo.features.contacts.model

/**
 * UI model for the contact list: the header rows, the contact rows and the leading "add" action.
 */
sealed interface ContactItemType {
    data object AddContact : ContactItemType

    data class Header(
        val letter: String
    ) : ContactItemType

    data class ContactItem(
        val contact: SimpleContact
    ) : ContactItemType
}
