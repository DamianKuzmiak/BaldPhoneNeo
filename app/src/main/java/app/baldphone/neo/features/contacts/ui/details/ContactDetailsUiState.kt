package app.baldphone.neo.features.contacts.ui.details

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

import app.baldphone.neo.features.contacts.model.Contact

/**
 * Everything [ContactDetailsActivity] needs to render a single contact.
 */
data class ContactUiState(
    val contact: Contact? = null,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val callHistory: List<CallUiModel> = emptyList(),
    val fields: List<ContactFieldUiModel> = emptyList(),
    val isCallLogVisible: Boolean = false
)

/** One-shot outcomes the details screen reacts to: either navigating away or showing an error. */
sealed interface ContactDetailsResult {
    data object ContactDeleted : ContactDetailsResult

    data object ContactNotFound : ContactDetailsResult

    data object ContactDeleteFailed : ContactDetailsResult

    data object ContactUpdateFailed : ContactDetailsResult

    data class FavoriteUpdated(val isFavorite: Boolean) : ContactDetailsResult
}

/** A single row of the contact's call history, pre-formatted for display. */
data class CallUiModel(
    @param:StringRes val callTypeStringRes: Int,
    @param:DrawableRes val callTypeDrawableRes: Int,
    @param:ColorRes val callTypeColorRes: Int,
    val isFirstInDay: Boolean,
    val relativeDate: String?,
    val timeString: String,
    val isDurationVisible: Boolean,
    val durationText: String
)

/** A labeled contact detail (phone, email, address, note, messaging entry) with up to two actions. */
data class ContactFieldUiModel(
    val label: CharSequence,
    val value: CharSequence,
    val isBold: Boolean = false,
    val primaryAction: FieldActionUiModel? = null,
    val secondaryAction: FieldActionUiModel? = null
)

/** A single tappable action attached to a [ContactFieldUiModel]. */
data class FieldActionUiModel(
    val type: FieldActionType,
    @param:DrawableRes val icon: Int,
    @param:StringRes val description: Int,
    @param:ColorRes val tint: Int? = null,
    val data: String
)

enum class FieldActionType {
    CALL,
    SMS,
    WHATSAPP,
    SIGNAL,
    EMAIL,
    MAP
}
