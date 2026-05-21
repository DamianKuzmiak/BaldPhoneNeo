package app.baldphone.neo.viewmodels

import android.app.Application

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import app.baldphone.neo.features.contacts.ContactItemType
import app.baldphone.neo.features.contacts.ContactSearcher
import app.baldphone.neo.features.contacts.data.ContactRepositoryImpl
import app.baldphone.neo.utils.PhoneNumberUtils
import app.baldphone.neo.utils.getDeviceRegion

/**
 * ViewModel for the DialerActivity:
 * - Manages the dialer number state (add/remove digits)
 * - Formats phone numbers using AsYouTypeFormatter
 * - Provides formatted number as StateFlow for UI observation
 * - Handles contact searching via ContactSearcher
 */
class DialerViewModel(application: Application) : AndroidViewModel(application) {

    // Raw number input (unformatted)
    private val _rawNumber = MutableStateFlow("")
    val rawNumber: StateFlow<String> = _rawNumber.asStateFlow()

    // Formatted number for display
    private val _formattedNumber = MutableStateFlow("")
    val formattedNumber: StateFlow<String> = _formattedNumber.asStateFlow()

    private val deviceRegion: String = application.getDeviceRegion()

    private val contactProvider = ContactRepositoryImpl.getInstance(application)
    private val contactSearcher = ContactSearcher(application)

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<ContactItemType>> = contactProvider.contacts.flatMapLatest { contacts ->
        contactSearcher.searchContactsFlow(
            allContacts = contacts ?: emptyList(),
            searchQueryFlow = rawNumber,
            enableT9 = true,
            showAllWhenEmpty = false
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            contactProvider.refresh()
        }
    }

    /** Add a digit to the number. */
    fun addDigit(digit: Char) {
        setNumber(_rawNumber.value + digit)
    }

    /** Remove the last digit from the number. */
    fun removeLastDigit() {
        if (_rawNumber.value.isEmpty()) return
        setNumber(_rawNumber.value.dropLast(1))
    }

    /** Clear all digits. */
    fun clearNumber() {
        _rawNumber.value = ""
        _formattedNumber.value = ""
    }

    /** Set the number directly */
    fun setNumber(number: String) {
        _rawNumber.value = number
        _formattedNumber.value = PhoneNumberUtils.formatAsYouType(number, deviceRegion)
    }
}
