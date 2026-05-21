package app.baldphone.neo.viewmodels

import android.app.Application

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import app.baldphone.neo.features.contacts.ContactItemType
import app.baldphone.neo.features.contacts.ContactSearcher
import app.baldphone.neo.features.contacts.SimpleContact
import app.baldphone.neo.features.contacts.data.ContactRepositoryImpl

/**
 * ViewModel for ContactsActivity.
 * Handles contact searching and ensures immediate loading of the contact list.
 */
class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val contactProvider = ContactRepositoryImpl.getInstance(application)
    private val contactSearcher = ContactSearcher(application)
    private val _allContacts = MutableStateFlow<List<SimpleContact>>(emptyList())

    val searchQuery = MutableStateFlow("")
    val isFavoritesOnly = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val contactsFlow: StateFlow<List<ContactItemType>?> = _allContacts.flatMapLatest { contacts ->
        contactSearcher.searchContactsFlow(
            allContacts = contacts,
            searchQueryFlow = searchQuery,
            starredOnlyFlow = isFavoritesOnly,
            showAllWhenEmpty = true
        )
    }.flowOn(Dispatchers.Default).stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null  // null = still loading; emptyList() = loaded but no results
        )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _allContacts.value = this@ContactsViewModel.contactProvider.getAllContacts()
        }
    }

    fun toggleFavorites() {
        isFavoritesOnly.value = !isFavoritesOnly.value
    }
}
