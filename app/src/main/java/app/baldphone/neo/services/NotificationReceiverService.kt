package app.baldphone.neo.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import app.baldphone.neo.features.notifications.data.NotificationClassifier
import app.baldphone.neo.features.notifications.data.NotificationRepository

class NotificationReceiverService : NotificationListenerService() {
    private var updateJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private const val TAG = "NotificationReceiverSrv"
        private const val DEBOUNCE_DELAY_MS = 250L

        @Volatile
        private var instance: NotificationReceiverService? = null

        fun isServiceAlive() = instance != null

        fun getInstance() = instance
    }

    private val repo = NotificationRepository

//    override fun onStartCommand(
//        intent: Intent?,
//        flags: Int,
//        startId: Int,
//    ): Int {
//        Log.v(TAG, "onStartCommand: intent=$intent, flags=$flags, startId=$startId")
//        return super.onStartCommand(intent, flags, startId)
//    }

    override fun onListenerConnected() {
//        super.onListenerConnected()
        Log.i(TAG, "onListenerConnected: Binder established")
        instance = this
        triggerUpdate()
    }

    override fun onListenerDisconnected() {
        Log.i(TAG, "onListenerDisconnected: Binder dropped")
        if (instance == this) instance = null
//        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        updateJob?.cancel()
        serviceScope.cancel()
        repo.update(emptyList())
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        Log.v(TAG, "onNotificationPosted: $sbn")
        triggerUpdate()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        Log.v(TAG, "onNotificationRemoved: $sbn")
        triggerUpdate()
    }

    private fun triggerUpdate() {
        updateJob?.cancel()
        updateJob =
            serviceScope.launch {
                delay(DEBOUNCE_DELAY_MS)
                refreshNotifications()
            }
    }

    private fun refreshNotifications() {
        try {
            val active = activeNotifications?.toList() ?: return
            Log.d(TAG, "refreshNotifications: loaded ${active.size} items")
//            for (item in active) {
//                Log.d(TAG, "refreshNotifications: ${logNotificationDetails(item)}")
//            }
            repo.update(active)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh notifications", e)
        }
    }

    internal fun dismissNotification(key: String) {
        Log.v(TAG, "dismissNotification: $key")
        runCatching { cancelNotification(key) }
            .onFailure { Log.w(TAG, "Failed to cancel: $key") }
    }

    internal fun dismissNotifications() {
        Log.v(TAG, "dismissNotifications")
        runCatching { cancelAllNotifications() }
            .onFailure { Log.w(TAG, "Failed to cancel all notifications") }
    }

    internal fun cancelMissedCalls() {
        runCatching {
            activeNotifications
                ?.filter { NotificationClassifier.isMissedCall(this, it) }
                ?.forEach { dismissNotification(it.key) }
        }.onFailure { e ->
            Log.w(TAG, "Failed to cancel missed calls: ${e.message}")
        }
    }

    fun logNotificationDetails(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val flags = notification.flags
        val packageName = sbn.packageName
        val id = sbn.id

        val parsedFlags = mutableListOf<String>()

        if (flags and Notification.FLAG_AUTO_CANCEL != 0) parsedFlags.add("FLAG_AUTO_CANCEL")
        if (flags and Notification.FLAG_FOREGROUND_SERVICE != 0) parsedFlags.add("FLAG_FOREGROUND_SERVICE")
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) parsedFlags.add("FLAG_GROUP_SUMMARY")
        if (flags and Notification.FLAG_INSISTENT != 0) parsedFlags.add("FLAG_INSISTENT")
        if (flags and Notification.FLAG_LOCAL_ONLY != 0) parsedFlags.add("FLAG_LOCAL_ONLY")
        if (flags and Notification.FLAG_NO_CLEAR != 0) parsedFlags.add("FLAG_NO_CLEAR")
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) parsedFlags.add("FLAG_ONGOING_EVENT")
        if (flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) parsedFlags.add("FLAG_ONLY_ALERT_ONCE")
        if (flags and Notification.FLAG_BUBBLE != 0) parsedFlags.add("FLAG_BUBBLE")

        Log.d(TAG, "--- Notification Info ---")
        Log.d(TAG, "Package: $packageName | ID: $id")
        Log.d(TAG, "Raw Flags (int): $flags")
        Log.d(
            TAG,
            "Active Flags: ${if (parsedFlags.isEmpty()) "NONE" else parsedFlags.joinToString(", ")}",
        )
        Log.d(TAG, "Category: ${notification.category ?: "N/A"}")
        Log.d(TAG, "-------------------------")
    }
}
