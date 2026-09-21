package app.baldphone.neo.features.contacts.model

/**
 * Simplified version of a contact, used for lists and searches.
 */
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
