package app.baldphone.neo.features.notifications.data

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.LruCache

import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.os.BundleCompat

import app.baldphone.neo.features.notifications.NotificationItem

/**
 * Mapper for converting [StatusBarNotification] objects into the domain model [NotificationItem].
 */
object NotificationItemMapper {
    /**
     * Cache for application names
     */
    private val appNameCache = LruCache<String, String>(20)

    private const val EXTRA_CONVERSATION_ICON = "android.conversationIcon"

    @WorkerThread
    fun toNotificationItems(context: Context, sbns: List<StatusBarNotification>): List<NotificationItem> {
        return sbns.mapNotNull { sbn ->
            val notification = sbn.notification ?: return@mapNotNull null
            val extras = notification.extras ?: Bundle.EMPTY
            val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)

            NotificationItem(
                key = sbn.key,
                packageName = sbn.packageName,
                appName = getAppNameFromPackage(context, sbn.packageName),
                title = extras.getCharSequence(Notification.EXTRA_TITLE),
                text = extractText(extras, messagingStyle),
                timeStamp = if (notification.`when` != 0L) notification.`when` else sbn.postTime,
                contentIntent = notification.contentIntent,
                isClearable = sbn.isClearable,
                smallIcon = extractSmallIcon(notification),
                largeIcon = extractIconPriority(context, notification, extras, messagingStyle),
                smallIconResId = notification.icon
            )
        }
    }

    private fun extractText(extras: Bundle, messagingStyle: NotificationCompat.MessagingStyle?): CharSequence? =
        extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: messagingStyle?.messages?.lastOrNull()?.text
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)

    private fun extractSmallIcon(n: Notification): Icon? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            n.smallIcon
        } else {
            null
        }

    /**
     * Extracts the most appropriate icon for the notification based on a priority order:
     */
    private fun extractIconPriority(
        context: Context,
        n: Notification,
        extras: Bundle,
        messagingStyle: NotificationCompat.MessagingStyle?
    ): Icon? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null

        // 1. Primary Large Icon (Standard)
        n.getLargeIcon()?.let { return it }

        // 2. Extra Large Icon fallbacks from extras
        BundleCompat.getParcelable(extras, Notification.EXTRA_LARGE_ICON, Icon::class.java)?.let { return it }
        BundleCompat.getParcelable(extras, Notification.EXTRA_LARGE_ICON_BIG, Icon::class.java)?.let { return it }

        // 3. Legacy Bitmap fallback
        @Suppress("DEPRECATION")
        n.largeIcon?.let { return Icon.createWithBitmap(it) }

        // 4. MessagingStyle person icon
        messagingStyle
            ?.messages
            ?.lastOrNull()
            ?.person
            ?.icon
            ?.toIcon(context)
            ?.let { return it }

        // 5. Conversation-specific icon
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BundleCompat.getParcelable(extras, EXTRA_CONVERSATION_ICON, Icon::class.java)?.let { return it }
        }

        return null
    }

    @WorkerThread
    private fun getAppNameFromPackage(context: Context, packageName: String?): String {
        if (packageName == null) return context.getString(android.R.string.unknownName)

        return appNameCache.get(packageName) ?: try {
            val pm = context.packageManager
            val ai =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(packageName, 0)
                }
            pm.getApplicationLabel(ai).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            context.getString(android.R.string.unknownName)
        }.also {
            appNameCache.put(packageName, it)
        }
    }
}
