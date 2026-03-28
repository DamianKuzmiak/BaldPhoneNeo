package app.baldphone.neo.battery.alert

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

import app.baldphone.neo.core.NotificationChannels
import app.baldphone.neo.receivers.StopBatteryAlertReceiver
import app.baldphone.neo.utils.AppLog

import com.bald.uriah.baldphone.R

/**
 * Helper object to manage battery-related notifications and channels.
 */
object BatteryAlertNotificationManager {
    private const val TAG = "BatteryNotification"
    const val NOTIFICATION_ID = 9001
    const val ACTION_STOP_BATTERY_ALERT =
        "app.baldphone.neo.battery.alert.ACTION_STOP_BATTERY_ALERT"

    /**
     * Shows a persistent notification indicating low battery.
     */
    fun showLowBatteryAlert(context: Context) {
        val appContext = context.applicationContext
        try {
            NotificationManagerCompat
                .from(appContext)
                .notify(NOTIFICATION_ID, createNotification(appContext))
        } catch (e: SecurityException) {
            AppLog.e(TAG, "Cannot post notification: ${e.message}")
        }
    }

    private fun createNotification(context: Context): android.app.Notification {
        val appContext = context.applicationContext
        val stopIntent =
            Intent(appContext, StopBatteryAlertReceiver::class.java).apply {
                action = ACTION_STOP_BATTERY_ALERT
            }

        val stopPendingIntent =
            PendingIntent.getBroadcast(
                appContext,
                NOTIFICATION_ID,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat
            .Builder(appContext, NotificationChannels.BATTERY_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_battery_low)
            .setContentTitle(appContext.getString(R.string.battery_alert_notification_title))
            .setContentText(appContext.getString(R.string.battery_alert_notification_text))
            .setSubText("No czesc, co tam?")
            .setPriority(NotificationCompat.PRIORITY_HIGH) // for API < 26
            .setSound(NotificationChannels.getSoundUri(appContext)) // for API < 26
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .addAction(
                R.drawable.call_received_on_button,
                appContext.getString(R.string.battery_alert_stop),
                stopPendingIntent,
            ).build()
    }

    /**
     * Cancels the low battery notification.
     */
    fun dismissLowBatteryAlert(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }
}
