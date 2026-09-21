package app.baldphone.neo.features.contacts.model

/**
 * The typed detail rows attached to a [Contact]: phone numbers, e-mails and postal addresses.
 * Each type mirrors a `ContactsContract.CommonDataKinds` row, keeping the provider's `type`/`label` pair.
 */

data class Address(
    val type: Int,
    val value: String,
    val label: String? = null
)

data class Email(
    val type: Int,
    val value: String,
    val label: String? = null
)

data class Phone(
    val type: Int,
    val value: String,
    val label: String? = null,
    val normalizedNumber: String? = null
)
