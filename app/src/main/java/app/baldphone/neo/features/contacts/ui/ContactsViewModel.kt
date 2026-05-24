package app.baldphone.neo.features.contacts.ui

import android.app.Application
import android.util.Log

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import app.baldphone.neo.features.contacts.ContactItemType
import app.baldphone.neo.features.contacts.data.ContactRepository

/**
 * ViewModel for [ContactsActivity].
 */
class ContactsViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val contactRepository = ContactRepository.Companion.getInstance(application)

    val searchQuery = MutableStateFlow("")
    val isFavoritesOnly = MutableStateFlow(false)

    val contactsFlow: StateFlow<List<ContactItemType>?> =
        contactRepository
            .observeContacts(searchQuery, isFavoritesOnly)
            .onEach { contacts ->
                Log.d(
                    "ContactsViewModel",
                    "New data received: ${contacts?.size ?: "null"} items"
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Companion.WhileSubscribed(5000),
                initialValue = null
            )

    fun toggleFavorites() {
        isFavoritesOnly.value = !isFavoritesOnly.value
    }

    fun loadNextPage() {
        contactRepository.loadNextContactsPage()
    }

    fun refresh() {
        viewModelScope.launch {
            contactRepository.refresh()
        }
    }
}
