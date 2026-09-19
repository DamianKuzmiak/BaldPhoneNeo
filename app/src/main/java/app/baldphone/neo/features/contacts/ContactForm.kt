package app.baldphone.neo.features.contacts

/**
 * Snapshot of the basic contact fields collected by [app.baldphone.neo.features.contacts.ui.AddContactActivity].
 */
data class ContactForm(
    val givenName: String,
    val familyName: String,
    val preferredPhone: String,
    val otherPhone: String,
    val address: String,
    val email: String
) {
    val hasName: Boolean
        get() = givenName.isNotBlank() || familyName.isNotBlank()

    fun normalized(): ContactForm =
        copy(
            givenName = givenName.trim(),
            familyName = familyName.trim(),
            preferredPhone = preferredPhone.trim(),
            otherPhone = otherPhone.trim(),
            address = address.trim(),
            email = email.trim()
        )

    companion object {
        val EMPTY =
            ContactForm(
                givenName = "",
                familyName = "",
                preferredPhone = "",
                otherPhone = "",
                address = "",
                email = ""
            )

        fun fromPrefilledNumber(number: String) = EMPTY.copy(preferredPhone = number)
    }
}
