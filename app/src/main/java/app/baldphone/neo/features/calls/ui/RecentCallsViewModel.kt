package app.baldphone.neo.features.calls.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager

import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

import app.baldphone.neo.contacts.data.ContactsDataSource
import app.baldphone.neo.features.calls.ContactLookupResult
import app.baldphone.neo.features.calls.RecentCallsPager

/**
 * ViewModel for [RecentCallsActivity].
 *
 * Owns all pagination state, call-log loading, and content-observer lifecycle.
 */
class RecentCallsViewModel(application: Application) : AndroidViewModel(application) {
    private val pager = RecentCallsPager(application, viewModelScope)
    private val dataSource by lazy { ContactsDataSource(application) }

    val callEntries = pager.entries

    private val _uiEvents = MutableSharedFlow<RecentCallsEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    private var lookupJob: Job? = null

    override fun onCleared() {
        super.onCleared()
        pager.onCleared()
    }

    /** Refreshes the recent calls list by clearing the current data and reloading from the beginning. */
    fun refresh() {
        pager.refresh()
    }

    /** Called from Activity when scroll position reaches threshold. */
    fun loadNextPage() {
        pager.loadNextPage()
    }

    /**
     * Resolves the lookup key for a call entry and triggers navigation. Prevents multiple simultaneous clicks.
     */
    fun onCallEntryClicked(number: String?, name: String?, lookupUri: String?) {
        if (lookupJob?.isActive == true) return
        lookupJob =
            viewModelScope.launch {
                when (val resolution = resolveContact(number, name, lookupUri)) {
                    is ContactLookupResult.Found -> {
                        _uiEvents.emit(RecentCallsEvent.OpenContact(resolution.lookupKey))
                    }

                    is ContactLookupResult.Unknown -> {
                        _uiEvents.emit(RecentCallsEvent.UnknownCaller(resolution.number, resolution.name))
                    }
                }
            }
    }

    private suspend fun resolveContact(
        number: String?,
        name: String?,
        lookupUri: String?
    ): ContactLookupResult {
        if (ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return ContactLookupResult.Unknown(number, name)
        }

        val lookupKey = dataSource.resolveLookupKey(lookupUri, number, name)
        return if (lookupKey != null) {
            ContactLookupResult.Found(lookupKey)
        } else {
            ContactLookupResult.Unknown(number, name)
        }
    }
}

/** Events used for one-shot UI navigation */
sealed class RecentCallsEvent {
    data class OpenContact(val lookupKey: String) : RecentCallsEvent()

    data class UnknownCaller(val number: String?, val name: String?) : RecentCallsEvent()
}
