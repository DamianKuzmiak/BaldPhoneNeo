package app.baldphone.neo.features.contacts.ui.details

import android.content.res.Resources
import android.provider.ContactsContract

import app.baldphone.neo.R
import app.baldphone.neo.features.contacts.model.Address
import app.baldphone.neo.features.contacts.model.Contact
import app.baldphone.neo.features.contacts.model.Phone
import app.baldphone.neo.utils.PhoneNumberUtils
import app.baldphone.neo.utils.messaging.WhatsAppHandler

/**
 * Flattens a [Contact] into the ordered list of rows shown on the details screen.
 */
internal fun Contact.toUiFields(
    resources: Resources,
    region: String
): List<ContactFieldUiModel> =
    buildList {
        phones.forEachIndexed { index, phone ->
            add(
                ContactFieldUiModel(
                    label = phone.typeLabel(resources),
                    value = PhoneNumberUtils.formatForDisplay(phone.value, region),
                    isBold = index == 0,
                    primaryAction =
                        FieldActionUiModel(
                            type = FieldActionType.SMS,
                            icon = R.drawable.message_on_button,
                            description = R.string.message,
                            tint = R.color.blue,
                            data = phone.value
                        ),
                    secondaryAction =
                        FieldActionUiModel(
                            type = FieldActionType.CALL,
                            icon = R.drawable.phone_on_button,
                            description = R.string.call,
                            tint = R.color.green,
                            data = phone.value
                        )
                )
            )
        }

        whatsappNumbers.forEach { jid ->
            add(
                ContactFieldUiModel(
                    label = resources.getString(R.string.whatsapp),
                    value = WhatsAppHandler.getPhoneNumberFromJid(jid) ?: jid,
                    primaryAction =
                        FieldActionUiModel(
                            type = FieldActionType.WHATSAPP,
                            icon = R.drawable.ic_whatsapp_call_lime,
                            description = R.string.open,
                            data = jid
                        )
                )
            )
        }

        signalNumbers.forEach { number ->
            add(
                ContactFieldUiModel(
                    label = "Signal",
                    value = number,
                    primaryAction =
                        FieldActionUiModel(
                            type = FieldActionType.SIGNAL,
                            icon = R.drawable.ic_signal_azure,
                            description = R.string.open,
                            data = number
                        )
                )
            )
        }

        emails.forEach { email ->
            add(
                ContactFieldUiModel(
                    label = resources.getString(R.string.mail),
                    value = email.value,
                    primaryAction =
                        FieldActionUiModel(
                            type = FieldActionType.EMAIL,
                            icon = R.drawable.mail_on_button,
                            description = R.string.send,
                            data = email.value
                        )
                )
            )
        }

        addresses.forEach { address ->
            add(
                ContactFieldUiModel(
                    label =
                        resources.getString(
                            R.string.text_pair_hyphen,
                            resources.getString(R.string.address),
                            address.typeLabel(resources)
                        ),
                    value = address.value,
                    primaryAction =
                        FieldActionUiModel(
                            type = FieldActionType.MAP,
                            icon = R.drawable.location_on_button,
                            description = R.string.location,
                            data = address.value
                        )
                )
            )
        }

        note?.takeIf { it.isNotEmpty() }?.let { n ->
            add(
                ContactFieldUiModel(
                    label = resources.getString(R.string.note),
                    value = n
                )
            )
        }
    }

private fun Phone.typeLabel(res: Resources): CharSequence =
    ContactsContract.CommonDataKinds.Phone.getTypeLabel(res, type, label)

private fun Address.typeLabel(res: Resources): CharSequence =
    ContactsContract.CommonDataKinds.StructuredPostal.getTypeLabel(res, type, label)
