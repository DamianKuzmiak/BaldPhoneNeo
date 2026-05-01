package app.baldphone.neo.features.notifications.data

import android.content.Context
import android.service.notification.StatusBarNotification

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

import app.baldphone.neo.features.notifications.NotificationItem
import app.baldphone.neo.services.NotificationReceiverService

object NotificationRepository {
    private val _notifications = MutableStateFlow<List<StatusBarNotification>>(emptyList())
    val notifications: StateFlow<List<StatusBarNotification>> = _notifications.asStateFlow()

    /**
     * Returns a flow of domain [NotificationItem] objects, mapping happens on [Dispatchers.Default].
     */
    fun getNotificationItems(context: Context): Flow<List<NotificationItem>> =
        notifications
            .map { sbns ->
                NotificationItemMapper.toNotificationItems(context, sbns)
            }.flowOn(Dispatchers.Default)

    // Legacy
    val count: LiveData<Int> =
        notifications
            .map { it.size }
            .asLiveData()

    // Legacy
    val packages: LiveData<Set<String>> =
        notifications
            .map { it.map { sbn -> sbn.packageName }.toSet() }
            .asLiveData()

    // Legacy
    fun getMissedCalls(context: Context): LiveData<List<StatusBarNotification>> =
        notifications
            .map { it.filter { sbn -> NotificationClassifier.isMissedCall(context, sbn) } }
            .asLiveData()

    /**
     * Updates the repository with a new list of active notifications.
     */
    fun update(list: List<StatusBarNotification>) {
        _notifications.value = list.filter { sbn -> NotificationClassifier.shouldShow(sbn) }
    }

    // Helpers to cancel notification(s) via the service.
    fun cancelAll() = NotificationReceiverService.getInstance()?.dismissNotifications()

    fun cancelNotification(key: String) = NotificationReceiverService.getInstance()?.dismissNotification(key)

    fun clearMissedCalls() = NotificationReceiverService.getInstance()?.cancelMissedCalls()
}
