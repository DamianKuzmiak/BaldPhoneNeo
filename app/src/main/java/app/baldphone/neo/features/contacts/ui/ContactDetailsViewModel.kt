package app.baldphone.neo.features.contacts.ui

import android.app.Application
import android.content.res.Resources

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import app.baldphone.neo.data.Prefs
import app.baldphone.neo.features.calls.data.CallLogProvider
import app.baldphone.neo.features.calls.model.Call
import app.baldphone.neo.features.calls.model.CallLogItemType
import app.baldphone.neo.features.contacts.Contact
import app.baldphone.neo.features.contacts.ContactPinManager
import app.baldphone.neo.features.contacts.data.ContactRepository
import app.baldphone.neo.utils.PhoneNumberUtils
import app.baldphone.neo.utils.formatAsElapsedDuration
import app.baldphone.neo.utils.formatTime
import app.baldphone.neo.utils.getDeviceRegion
import app.baldphone.neo.utils.isSameDayAs
import app.baldphone.neo.utils.messaging.WhatsAppHandler
import app.baldphone.neo.utils.toRelativeDateString

import com.bald.uriah.baldphone.R

/** ViewModel for displaying and managing a single contact's information. */
class ContactDetailsViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val contactRepository = ContactRepository.getInstance(application)
    private val callLogProvider = CallLogProvider(application)
    private val pinManager = ContactPinManager(application)

    private val _uiState =
        MutableStateFlow(ContactUiState(isCallLogVisible = Prefs.isCallLogVisible))
    val uiState: StateFlow<ContactUiState> = _uiState.asStateFlow()

    private val _events = Channel<ContactDetailsResult>(Channel.BUFFERED)
    val events: Flow<ContactDetailsResult> = _events.receiveAsFlow()

    private var lookupKey: String? = null
    private var contactJob: Job? = null
    private var isDeleting = false

    init {
        // Listen for changes in the contact database, e.g. contact has been edited in AddContactActivity
        viewModelScope.launch {
            contactRepository.refreshEvents.collect {
                lookupKey?.let { loadContact(it) }
            }
        }
    }

    fun loadContact(key: String) {
        if (key.isEmpty()) return
        lookupKey = key

        contactJob?.cancel()
        contactJob =
            viewModelScope.launch {
                val contact = contactRepository.getContact(key)

                if (contact == null) {
                    if (!isDeleting) {
                        _events.trySend(ContactDetailsResult.ContactNotFound)
                    }
                    return@launch
                }

                val fields =
                    withContext(Dispatchers.Default) {
                        mapContactToUiModels(contact)
                    }

                // Update our internal key in case it was resolved to a fresh one
                lookupKey = contact.lookupKey

                _uiState.update { state ->
                    state.copy(
                        contact = contact,
                        isFavorite = contact.isStarred,
                        isPinned = pinManager.isPinned(contact.lookupKey),
                        fields = fields
                    )
                }

                launch {
                    val calls = callLogProvider.getCallHistory(contact.lookupUri)
                    val callUiModels =
                        withContext(Dispatchers.Default) {
                            mapCallsToUiModels(calls)
                        }
                    _uiState.update { it.copy(callHistory = callUiModels) }
                }
            }
    }

    private fun mapCallsToUiModels(calls: List<Call>): List<CallUiModel> {
        val context = getApplication<Application>()
        return calls.mapIndexed { index, call ->
            val previousCall = calls.getOrNull(index - 1)
            val logType = CallLogItemType.fromSystemType(call.callType)
            val isFirstInDay = (previousCall == null) || !call.dateTime.isSameDayAs(previousCall.dateTime)
            val relativeDate = if (isFirstInDay) call.dateTime.toRelativeDateString() else null
            val timeString = call.dateTime.formatTime(context)

            val isVoiceCall = logType.isVoiceCall
            val isDurationVisible = isVoiceCall && call.duration > 0
            val durationText =
                when {
                    isDurationVisible -> call.duration.toLong().formatAsElapsedDuration()
                    isVoiceCall -> context.getString(R.string.not_available_duration)
                    else -> ""
                }

            CallUiModel(
                callTypeStringRes = logType.stringRes,
                callTypeDrawableRes = logType.drawableRes,
                callTypeColorRes = logType.colorRes,
                isFirstInDay = isFirstInDay,
                relativeDate = relativeDate,
                timeString = timeString,
                isDurationVisible = isDurationVisible,
                durationText = durationText
            )
        }
    }

    fun toggleFavorite() {
        val key = lookupKey ?: return
        viewModelScope.launch {
            contactRepository.toggleFavorite(key)
        }
    }

    fun toggleHomeScreenPin() {
        val key = lookupKey ?: return
        viewModelScope.launch {
            if (pinManager.togglePin(key)) {
                // Home Screen updates do not trigger the contact observer
                _uiState.update { it.copy(isPinned = !it.isPinned) }
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
            if (contactRepository.deleteContact(key)) {
                _events.trySend(ContactDetailsResult.ContactDeleted)
            } else {
                isDeleting = false
            }
        }
    }

    private fun mapContactToUiModels(contact: Contact): List<ContactFieldUiModel> {
        val resources = getApplication<Application>().resources
        val region = getApplication<Application>().getDeviceRegion()
        return contact.toUiFields(resources, region)
    }
}

private fun Contact.toUiFields(
    resources: Resources,
    region: String
): List<ContactFieldUiModel> =
    buildList {
        phones.forEachIndexed { index, phone ->
            add(
                ContactFieldUiModel(
                    label = phone.getLabel(resources),
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
                            address.getLabel(resources)
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

sealed interface ContactDetailsResult {
    data object ContactDeleted : ContactDetailsResult

    data object ContactNotFound : ContactDetailsResult
}

data class ContactUiState(
    val contact: Contact? = null,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val callHistory: List<CallUiModel> = emptyList(),
    val fields: List<ContactFieldUiModel> = emptyList(),
    val isCallLogVisible: Boolean = false
)

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
    CALL,
    SMS,
    WHATSAPP,
    SIGNAL,
    EMAIL,
    MAP
}
