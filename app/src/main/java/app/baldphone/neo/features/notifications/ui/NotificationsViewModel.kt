package app.baldphone.neo.features.notifications.ui

import android.app.Application

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

import app.baldphone.neo.features.notifications.NotificationItem
import app.baldphone.neo.features.notifications.data.NotificationRepository

/**
 * ViewModel for [NotificationsActivity].
 */
class NotificationsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NotificationRepository

    val notificationItems: StateFlow<List<NotificationItem>> =
        repository
            .getNotificationItems(getApplication())
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
                initialValue = emptyList()
            )

    /**
     * Dismisses a single notification.
     */
    fun dismiss(item: NotificationItem) {
        repository.cancelNotification(item.key)
    }

    /**
     * Dismiss all clearable notifications.
     */
    fun clearAll() {
        repository.cancelAll()
    }

    companion object {
        private const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}
