package app.baldphone.neo.features.notifications

import android.app.PendingIntent
import android.graphics.drawable.Icon

data class NotificationItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: CharSequence?,
    val text: CharSequence?,
    val timeStamp: Long,
    val contentIntent: PendingIntent?,
    val isClearable: Boolean,
    val smallIcon: Icon?, // For API >= 23
    val smallIconResId: Int, // For API < 23
    val largeIcon: Icon? // For API >= 23
)
