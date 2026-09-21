package app.baldphone.neo.features.contacts.ui.details

import android.app.Application

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
import app.baldphone.neo.features.contacts.ContactPinManager
import app.baldphone.neo.features.contacts.data.ContactRepository
import app.baldphone.neo.utils.getDeviceRegion

/**
 * ViewModel for displaying and managing a single contact's information.
 */
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

                val application = getApplication<Application>()
                val fields =
                    withContext(Dispatchers.Default) {
                        contact.toUiFields(application.resources, application.getDeviceRegion())
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
                    val phoneNumbers = contact.phones.map { it.value }
                    val calls = callLogProvider.getCallHistory(contact.lookupUri, phoneNumbers)
                    val callUiModels =
                        withContext(Dispatchers.Default) {
                            calls.toCallUiModels(application)
                        }
                    _uiState.update { it.copy(callHistory = callUiModels) }
                }
            }
    }

    fun toggleFavorite() {
        val key = lookupKey ?: return
        val newValue = !_uiState.value.isFavorite
        viewModelScope.launch {
            contactRepository
                .toggleFavorite(key)
                .onSuccess {
                    _events.trySend(ContactDetailsResult.FavoriteUpdated(newValue))
                }.onFailure {
                    _events.trySend(ContactDetailsResult.ContactUpdateFailed)
                }
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
            contactRepository
                .deleteContact(key)
                .onSuccess {
                    _events.trySend(ContactDetailsResult.ContactDeleted)
                }.onFailure {
                    isDeleting = false
                    _events.trySend(ContactDetailsResult.ContactDeleteFailed)
                }
        }
    }
}
