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
    val email: String,
    val photo: ByteArray? = null
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContactForm

        if (givenName != other.givenName) return false
        if (familyName != other.familyName) return false
        if (preferredPhone != other.preferredPhone) return false
        if (otherPhone != other.otherPhone) return false
        if (address != other.address) return false
        if (email != other.email) return false
        if (photo != null) {
            if (other.photo == null) return false
            if (!photo.contentEquals(other.photo)) return false
        } else if (other.photo != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = givenName.hashCode()
        result = 31 * result + familyName.hashCode()
        result = 31 * result + preferredPhone.hashCode()
        result = 31 * result + otherPhone.hashCode()
        result = 31 * result + address.hashCode()
        result = 31 * result + email.hashCode()
        result = 31 * result + (photo?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        val EMPTY =
            ContactForm(
                givenName = "",
                familyName = "",
                preferredPhone = "",
                otherPhone = "",
                address = "",
                email = "",
                photo = null
            )

        fun fromPrefilledNumber(number: String) = EMPTY.copy(preferredPhone = number)
    }
}
