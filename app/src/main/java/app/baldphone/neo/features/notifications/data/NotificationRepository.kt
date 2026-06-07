package app.baldphone.neo.features.notifications.data

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

import app.baldphone.neo.BuildConfig
import app.baldphone.neo.features.notifications.NotificationItem
import app.baldphone.neo.services.NotificationReceiverService

object NotificationRepository {
    private enum class Priority {
        CALL,
        ALARM,
        MESSAGE,
        THIS_APP,
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
        getNotificationItems(context).map { items ->
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
            .map { it to classifyPriority(it) }
            .filter { (item, priority) ->
                item.isClearable || priority == Priority.CALL || priority == Priority.ALARM ||
                    priority == Priority.THIS_APP
            }.minWithOrNull(
                compareBy<Pair<NotificationItem, Priority>> {
                    it.second.ordinal
                }.thenByDescending { it.first.timeStamp }
            )?.first

    private fun classifyPriority(item: NotificationItem): Priority {
        if (item.packageName == BuildConfig.APPLICATION_ID) return Priority.THIS_APP

        return when (item.category) {
            Notification.CATEGORY_CALL, Notification.CATEGORY_MISSED_CALL -> Priority.CALL
            Notification.CATEGORY_ALARM -> Priority.ALARM
            Notification.CATEGORY_MESSAGE, Notification.CATEGORY_SOCIAL -> Priority.MESSAGE
            else -> if (NotificationClassifier.isKnownDialer(item.packageName)) Priority.CALL else Priority.OTHER
        }
    }

    /**
     * Returns a flow of all active package names, excluding the current app.
     */
    val activeNotificationPackages: Flow<Set<String>> =
        notifications
            .map { list ->
                list
                    .asSequence()
                    .map { sbn -> sbn.packageName }
                    .filter { it != BuildConfig.APPLICATION_ID }
                    .toSet()
            }.distinctUntilChanged()

    /**
     * Flow of missed call notifications. Requires a context.
     */
    fun getMissedCallNotificationsFlow(context: Context): Flow<List<StatusBarNotification>> =
        notifications
            .map { it.filter { sbn -> NotificationClassifier.isMissedCall(context, sbn) } }
            .distinctUntilChanged()

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
