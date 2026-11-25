// Copyright (c) 2026 Damian Kuźmiak
// SPDX-License-Identifier: Apache-2.0

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
        getNotificationItems(context)
            .map { items -> selectTopNotification(items) }
            // Collapse identical notification emissions to prevent redundant home-screen overlay
            // re-binds caused by fresh NotificationItem instances generated on re-posts.
            .distinctUntilChanged { old, new ->
                if (old == null || new == null) old === new else old.hasSameVisibleContentAs(new)
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

    /** Badge key for missed calls. */
    const val MISSED_CALL_BADGE_KEY = "missed_calls"

    /**
     * Flow of badge keys: package names with active notifications (excluding the current app),
     * plus [MISSED_CALL_BADGE_KEY] when any missed call notification exists.
     */
    fun badgesFlow(context: Context): Flow<Set<String>> =
        notifications
            .map { list ->
                buildSet {
                    list.forEach { sbn ->
                        // This app is not a dialer, so our own notifications never count as missed calls.
                        if (sbn.packageName == BuildConfig.APPLICATION_ID) return@forEach

                        add(sbn.packageName)
                        if (NotificationClassifier.isMissedCall(context, sbn)) add(MISSED_CALL_BADGE_KEY)
                    }
                }
            }.distinctUntilChanged()

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
