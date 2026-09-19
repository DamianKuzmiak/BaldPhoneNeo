package app.baldphone.neo.features.contacts.ui

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log

import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import app.baldphone.neo.R
import app.baldphone.neo.features.contacts.Contact
import app.baldphone.neo.features.contacts.ContactForm
import app.baldphone.neo.features.contacts.data.ContactRepository
import app.baldphone.neo.utils.ImageUtils

class AddContactViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val repository = ContactRepository.getInstance(application)

    private val _uiState = MutableStateFlow(AddContactUiState())
    val uiState: StateFlow<AddContactUiState> = _uiState.asStateFlow()

    private val _errors =
        MutableSharedFlow<Int>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    val errors: SharedFlow<Int> = _errors.asSharedFlow()

    private val _contactDeleted = MutableSharedFlow<Unit>()
    val contactDeleted: SharedFlow<Unit> = _contactDeleted.asSharedFlow()

    /** Lookup key of the contact being edited, or `null` when adding a new contact. */
    private val lookupKey: String? = savedStateHandle[AddContactActivity.CONTACT_LOOKUP_KEY]

    private var initialDraft: ContactForm? = null

    init {
        val prefilledNumber: String? = savedStateHandle[AddContactActivity.CONTACT_NUMBER]

        if (lookupKey != null) {
            loadContact(lookupKey)
        } else {
            val draft =
                if (prefilledNumber != null) {
                    ContactForm.fromPrefilledNumber(prefilledNumber)
                } else {
                    ContactForm.EMPTY
                }
            initialDraft = draft.copy()
            updateDraft(draft)
        }
    }

    fun updateDraft(draft: ContactForm) {
        _uiState.update { it.copy(currentDraft = draft) }
    }

    fun onPhotoPicked(uri: Uri) {
        _uiState.update { it.copy(photoUri = uri) }
    }

    fun clearPhoto() {
        _uiState.update { it.copy(photoUri = null) }
    }

    fun save() {
        val state = _uiState.value
        val draft = state.currentDraft

        if (state.isSaving || draft == null) return

        if (draft.hasName) {
            _uiState.update { it.copy(isSaving = true) }

            viewModelScope.launch {
                val photoBytes = resolvePhotoBytes()
                val contactId = state.contactId
                val isEdit = contactId != null

                val success =
                    if (contactId != null) {
                        repository.updateContact(contactId, draft, photoBytes)
                    } else {
                        repository.createContact(draft, photoBytes)
                    }

                if (success) {
                    _uiState.update { it.copy(saveSuccess = true) }
                } else {
                    _uiState.update { it.copy(isSaving = false) }
                    _errors.emit(
                        if (isEdit) R.string.contact_not_updated else R.string.contact_not_created
                    )
                }
            }
        } else {
            viewModelScope.launch {
                _errors.emit(R.string.contact_must_has_name)
            }
        }
    }

    private fun loadContact(lookupKey: String) {
        viewModelScope.launch {
            val contact = repository.getContact(lookupKey)
            if (contact == null) {
                _errors.emit(R.string.an_error_has_occurred)
                return@launch
            }
            applyContact(contact)
            observeContactChanges(lookupKey)
        }
    }

    private fun applyContact(contact: Contact) {
        val draft =
            ContactForm(
                givenName = contact.givenName ?: contact.name,
                familyName = contact.familyName.orEmpty(),
                preferredPhone = contact.mobilePhone.orEmpty(),
                otherPhone = contact.homePhone.orEmpty(),
                address = contact.firstAddress.orEmpty(),
                email = contact.primaryEmail.orEmpty()
            )

        initialDraft = draft.copy()
        updateDraft(draft)
        val contactPhotoUri = contact.photoUri?.toUri()
        _uiState.update {
            it.copy(
                contactId = contact.id,
                originalPhotoUri = contact.photoUri,
                photoUri = contactPhotoUri
            )
        }
    }

    /**
     * Observes changes to the edited contact in the system ContentProvider.
     */
    @OptIn(FlowPreview::class)
    private fun observeContactChanges(lookupKey: String) {
        val contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
        val resolver = getApplication<Application>().contentResolver

        val changes =
            callbackFlow {
                val observer =
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            trySend(Unit)
                        }
                    }
                try {
                    resolver.registerContentObserver(contactUri, true, observer)
                } catch (e: SecurityException) {
                    Log.e(TAG, "Failed to register contact observer", e)
                    close(e)
                    return@callbackFlow
                }
                awaitClose { resolver.unregisterContentObserver(observer) }
            }.debounce(OBSERVER_DEBOUNCE_MS.milliseconds)

        viewModelScope.launch {
            changes.collect { onContactChanged() }
        }
    }

    /**
     * Re-evaluates the contact after the provider signaled a change.
     */
    private suspend fun onContactChanged() {
        if (_uiState.value.isSaving || initialDraft == null) return

        val key = lookupKey ?: return
        val contact = repository.getContact(key)
        if (contact != null) {
            if (!hasUnsavedChanges()) {
                applyContact(contact)
            }
        } else {
            // The contact was deleted in the system Contacts app.
            _contactDeleted.emit(Unit)
        }
    }

    private suspend fun resolvePhotoBytes(): ByteArray? =
        withContext(Dispatchers.IO) {
            val state = _uiState.value
            val currentUri = state.photoUri

            when {
                // No change compared to original contact photo
                currentUri?.toString() == state.originalPhotoUri -> null

                // Photo was removed (now null)
                currentUri == null -> ByteArray(0)

                // New photo picked - uri is different from original
                else -> ImageUtils.toSquareJpeg(getApplication<Application>().contentResolver, currentUri)
            }
        }

    fun hasUnsavedChanges(): Boolean {
        val state = _uiState.value
        val currentDraft = state.currentDraft ?: return false

        val fieldsChanged = currentDraft.normalized() != initialDraft?.normalized()
        val photoChanged = state.photoUri?.toString() != state.originalPhotoUri

        return fieldsChanged || photoChanged
    }

    data class AddContactUiState(
        val contactId: Long? = null,
        val originalPhotoUri: String? = null,
        val currentDraft: ContactForm? = null,
        val photoUri: Uri? = null,
        val isSaving: Boolean = false,
        val saveSuccess: Boolean = false
    )

    private companion object {
        private const val TAG = "AddContactViewModel"
        private const val OBSERVER_DEBOUNCE_MS = 300L
    }
}
