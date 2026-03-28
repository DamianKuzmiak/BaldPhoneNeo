package app.baldphone.neo.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log

import androidx.core.app.NotificationCompat
import androidx.core.net.toUri

import com.bald.uriah.baldphone.R

/**
 * Global notification channel initialization.
 */
object NotificationChannels {
    private const val TAG = "NotificationChannels"

    const val BATTERY_ALERT_CHANNEL_ID = "battery_alert_channel_v1"

    /**
     * Initializes all notification channels.
     * Should be called from Application.onCreate().
     */
    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Battery Alert Channel
            val name = context.getString(R.string.battery_alert_channel_name)
            val descriptionText = context.getString(R.string.battery_alert_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val audioAttributes =
                AudioAttributes
                    .Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()

            val channel =
                NotificationChannel(BATTERY_ALERT_CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setSound(getSoundUri(context), audioAttributes)
                    enableVibration(false)
                    setShowBadge(false)
                }

            try {
                nm.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create notification channel", e)
            }
        }
    }

    /**
     * Common method to get the custom alert sound URI.
     */
    fun getSoundUri(
        context: Context
    ): Uri = "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.low_battery_alert}".toUri()

    /**
     * Deletes old notification channels. DEBUG ONLY
     */
    fun deleteNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notificationChannels.forEach { channel ->
                Log.d(TAG, "Deleting notification channel: ${channel.id}")
                nm.deleteNotificationChannel(channel.id)
            }
        }
    }
}
