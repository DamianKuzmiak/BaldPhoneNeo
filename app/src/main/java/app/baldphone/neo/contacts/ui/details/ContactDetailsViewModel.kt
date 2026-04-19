package app.baldphone.neo.contacts.ui.details

import android.app.Application
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

import app.baldphone.neo.contacts.Contact
import app.baldphone.neo.contacts.ContactActionsUseCase
import app.baldphone.neo.contacts.data.ContactRepositoryImpl
import app.baldphone.neo.data.Prefs
import app.baldphone.neo.features.calls.data.CallsRepository
import app.baldphone.neo.utils.PhoneNumberUtils
import app.baldphone.neo.utils.getDeviceRegion
import app.baldphone.neo.utils.messaging.WhatsAppHandler

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.databases.calls.Call

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** ViewModel for displaying and managing a single contact's information. */
class ContactDetailsViewModel(application: Application) : AndroidViewModel(application) {

    private val contactProvider = ContactRepositoryImpl.getInstance(application)
    private val callsRepository = CallsRepository(application)
    private val contactActionsUseCase = ContactActionsUseCase(application, contactProvider)

    private val _uiState =
        MutableStateFlow(ContactUiState(isCallLogVisible = Prefs.isCallLogVisible))
    val uiState: StateFlow<ContactUiState> = _uiState.asStateFlow()

    private val _events = Channel<ContactDetailsResult>(Channel.BUFFERED)
    val events: Flow<ContactDetailsResult> = _events.receiveAsFlow()

    private var lookupKey: String? = null
    private var contactJob: Job? = null
    private var isDeleting = false

    fun loadContact(key: String) {
        if (key.isEmpty()) return
        lookupKey = key

        _uiState.update { it.copy(isLoading = true) }
        contactJob?.cancel()
        contactJob = viewModelScope.launch {
            val contact = contactProvider.getContact(key)

            if (contact == null) {
                if (isDeleting) return@launch
                val freshKey = contactProvider.resolveLatestLookupKey(key)
                if (freshKey != null && freshKey != key) {
                    loadContact(freshKey)
                    return@launch
                }
                _events.trySend(ContactDetailsResult.ContactNotFound)
            } else {
                _uiState.update { state ->
                    state.copy(
                        contact = contact,
                        isFavorite = contact.isStarred,
                        isPinned = contactActionsUseCase.isPinned(contact.lookupKey),
                        callHistory = callsRepository.getCallHistory(contact),
                        fields = mapContactToUiModels(contact),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun toggleFavorite() {
        val key = lookupKey ?: return
        val current = _uiState.value.isFavorite
        viewModelScope.launch {
            if (contactActionsUseCase.toggleFavorite(key, current)) {
                _uiState.update { it.copy(contactChanged = true) }
                loadContact(key) // Refresh data
            }
        }
    }

    fun toggleHomeScreenPin() {
        val key = lookupKey ?: return
        viewModelScope.launch {
            val currentPinned = _uiState.value.isPinned
            if (contactActionsUseCase.toggleHomeScreenPin(key, currentPinned)) {
                _uiState.update { it.copy(isPinned = !currentPinned, contactChanged = true) }
                loadContact(key) // Refresh data
            }
        }
    }

    fun toggleCallLogVisibility() {
        val newValue = !_uiState.value.isCallLogVisible
        Prefs.isCallLogVisible = newValue
        _uiState.update { it.copy(isCallLogVisible = newValue) }
    }

    fun deleteContact() {
        val key = lookupKey ?: return
        isDeleting = true
        viewModelScope.launch {
            if (contactActionsUseCase.deleteContact(key)) {
                _events.trySend(ContactDetailsResult.ContactDeleted)
            } else {
                isDeleting = false
            }
        }
    }

    private fun mapContactToUiModels(contact: Contact): List<ContactFieldUiModel> {
        val fields = mutableListOf<ContactFieldUiModel>()
        val resources = getApplication<Application>().resources
        val region = getApplication<Application>().getDeviceRegion()

        contact.phones.forEachIndexed { index, phone ->
            fields.add(
                ContactFieldUiModel(
                    label = phone.getLabel(resources),
                    value = PhoneNumberUtils.formatForDisplay(phone.value, region),
                    isBold = index == 0,
                    primaryAction = FieldActionUiModel(
                        type = FieldActionType.SMS,
                        icon = R.drawable.message_on_button,
                        description = R.string.message,
                        tint = R.color.blue,
                        data = phone.value
                    ),
                    secondaryAction = FieldActionUiModel(
                        type = FieldActionType.CALL,
                        icon = R.drawable.phone_on_button,
                        description = R.string.call,
                        tint = R.color.green,
                        data = phone.value
                    )
                )
            )
        }

        contact.whatsappNumbers.forEach { jid ->
            val display = WhatsAppHandler.getPhoneNumberFromJid(jid) ?: jid
            fields.add(
                ContactFieldUiModel(
                    label = resources.getString(R.string.whatsapp),
                    value = display,
                    primaryAction = FieldActionUiModel(
                        type = FieldActionType.WHATSAPP,
                        icon = R.drawable.ic_whatsapp_call_lime,
                        description = R.string.open,
                        data = jid
                    )
                )
            )
        }

        contact.signalNumbers.forEach { number ->
            fields.add(
                ContactFieldUiModel(
                    label = "Signal", value = number, primaryAction = FieldActionUiModel(
                        type = FieldActionType.SIGNAL,
                        icon = R.drawable.ic_signal_azure,
                        description = R.string.open,
                        data = number
                    )
                )
            )
        }

        contact.emails.forEach { email ->
            fields.add(
                ContactFieldUiModel(
                    label = resources.getString(R.string.mail),
                    value = email.value,
                    primaryAction = FieldActionUiModel(
                        type = FieldActionType.EMAIL,
                        icon = R.drawable.mail_on_button,
                        description = R.string.send,
                        data = email.value
                    )
                )
            )
        }

        contact.addresses.forEach { address ->
            fields.add(
                ContactFieldUiModel(
                    label = "${resources.getString(R.string.address)}${address.getLabel(resources)}",
                    value = address.value,
                    primaryAction = FieldActionUiModel(
                        type = FieldActionType.MAP,
                        icon = R.drawable.location_on_button,
                        description = R.string.location,
                        data = address.value
                    )
                )
            )
        }

        contact.note?.takeIf { it.isNotEmpty() }?.let { note ->
            fields.add(
                ContactFieldUiModel(
                    label = resources.getString(R.string.note), value = note
                )
            )
        }

        return fields
    }
}

sealed class ContactDetailsResult {
    object ContactDeleted : ContactDetailsResult()
    object ContactNotFound : ContactDetailsResult()
}

data class ContactUiState(
    val contact: Contact? = null,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val callHistory: List<Call> = emptyList(),
    val fields: List<ContactFieldUiModel> = emptyList(),
    val isCallLogVisible: Boolean = false,
    val contactChanged: Boolean = false,
    val isLoading: Boolean = true
)

data class ContactFieldUiModel(
    val label: CharSequence,
    val value: CharSequence,
    val isBold: Boolean = false,
    val primaryAction: FieldActionUiModel? = null,
    val secondaryAction: FieldActionUiModel? = null
)

data class FieldActionUiModel(
    val type: FieldActionType,
    @param:DrawableRes val icon: Int,
    @param:StringRes val description: Int,
    @param:ColorRes val tint: Int? = null,
    val data: String
)

enum class FieldActionType {
    CALL, SMS, WHATSAPP, SIGNAL, EMAIL, MAP
}
