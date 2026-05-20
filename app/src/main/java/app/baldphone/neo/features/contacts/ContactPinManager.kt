package app.baldphone.neo.features.contacts

import android.content.Context

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.bald.uriah.baldphone.databases.home_screen_pins.HomeScreenPinHelper

/**
 * Manages pinning contacts to the BaldPhone home screen.
 */
class ContactPinManager(
    private val context: Context
) {
    /**
     * Toggles the pinned status of a contact. Returns true if the operation succeeded.
     */
    suspend fun togglePin(lookupKey: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val isCurrentlyPinned = HomeScreenPinHelper.isPinned(context, lookupKey)
                if (isCurrentlyPinned) {
                    HomeScreenPinHelper.removeContact(context, lookupKey)
                } else {
                    HomeScreenPinHelper.pinContact(context, lookupKey)
                }
                true
            }.getOrDefault(false)
        }

    /**
     * Checks if a contact is currently pinned to the home screen.
     */
    fun isPinned(lookupKey: String): Boolean = HomeScreenPinHelper.isPinned(context, lookupKey)
}
