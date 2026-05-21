package app.baldphone.neo.features.contacts

import android.content.Context

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import app.baldphone.neo.features.contacts.data.ContactRepository

import com.bald.uriah.baldphone.databases.home_screen_pins.HomeScreenPinHelper

/**
 * Encapsulates actions that can be performed on a contact.
 */
class ContactActionsUseCase(
    private val context: Context, private val repository: ContactRepository
) {
    /**
     * Toggles the favorite (starred) status of a contact.
     */
    suspend fun toggleFavorite(lookupKey: String, isCurrentlyFavorite: Boolean): Boolean {
        return repository.updateFavorite(lookupKey, !isCurrentlyFavorite)
    }

    /**
     * Toggles whether the contact is pinned to the home screen.
     */
    suspend fun toggleHomeScreenPin(lookupKey: String, isCurrentlyPinned: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val newPinned = !isCurrentlyPinned
                if (newPinned) {
                    HomeScreenPinHelper.pinContact(context, lookupKey)
                } else {
                    HomeScreenPinHelper.removeContact(context, lookupKey)
                }
                true
            }.getOrDefault(false)
        }

    /**
     * Deletes a contact from the system contacts.
     */
    suspend fun deleteContact(lookupKey: String): Boolean {
        return repository.deleteContact(lookupKey)
    }

    /**
     * Checks if a contact is pinned to the home screen.
     */
    fun isPinned(lookupKey: String): Boolean {
        return HomeScreenPinHelper.isPinned(context, lookupKey)
    }
}
