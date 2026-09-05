package app.baldphone.neo.features.notifications.data

import android.app.Notification
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
    private enum class Priority {
        CALL,
        ALARM,
        MESSAGE,
        OTHER
    }

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

    /**
     * Emits the single highest-priority [NotificationItem], or null when no notifications exist.
     */
    fun getTopNotification(context: Context): Flow<NotificationItem?> =
        getNotificationItems(context)
            .map { items ->
                selectTopNotification(items)
            }

    /**
     * Evaluates a list of [NotificationItem]s and returns the single highest-priority notification.
     * Ongoing calls (and clearable notifications) are considered.
     * Returns null if no eligible notifications exist.
     */
    private fun selectTopNotification(items: List<NotificationItem>): NotificationItem? =
        items
            .asSequence()
            .filter { it.isClearable || classifyPriority(it) == Priority.CALL }
            .minWithOrNull(
                compareBy<NotificationItem> { classifyPriority(it).ordinal }
                    .thenByDescending { it.timeStamp }
            )

    private fun classifyPriority(item: NotificationItem): Priority =
        when {
            item.category == Notification.CATEGORY_CALL ||
                item.category == Notification.CATEGORY_MISSED_CALL ||
                NotificationClassifier.isKnownDialer(item.packageName) -> Priority.CALL

            item.category == Notification.CATEGORY_ALARM -> Priority.ALARM

            item.category == Notification.CATEGORY_MESSAGE ||
                item.category == Notification.CATEGORY_SOCIAL -> Priority.MESSAGE

            else -> Priority.OTHER
        }

    /**
     * LiveData wrapper for [getTopNotification] for Java callers.
     */
    fun getTopNotificationLiveData(context: Context): LiveData<NotificationItem?> =
        getTopNotification(context).asLiveData()

    // Legacy
    val packages: LiveData<Set<String>> = notifications.map { it.map { sbn -> sbn.packageName }.toSet() }.asLiveData()

    // Legacy
    fun getMissedCalls(context: Context): LiveData<List<StatusBarNotification>> =
        notifications.map { it.filter { sbn -> NotificationClassifier.isMissedCall(context, sbn) } }.asLiveData()

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
