package app.baldphone.neo.features.notifications

import android.app.ActivityOptions
import android.app.PendingIntent
import android.os.Build
import android.util.Log

private const val TAG = "NotificationExtensions"

/**
 * Safely launches the PendingIntent considering Android 14+ background activity start restrictions.
 * Returns true if launched successfully (without CanceledException), false otherwise.
 */
@JvmOverloads
fun PendingIntent.sendNotificationIntent(onCanceled: (() -> Unit)? = null): Boolean =
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val options =
                ActivityOptions.makeBasic().apply {
                    pendingIntentBackgroundActivityStartMode =
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
            send(options.toBundle())
        } else {
            send()
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "Notification intent was canceled", e)
        onCanceled?.invoke()
        false
    }
