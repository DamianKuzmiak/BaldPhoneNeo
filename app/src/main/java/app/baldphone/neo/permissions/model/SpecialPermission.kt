package app.baldphone.neo.permissions.model

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.bald.uriah.baldphone.R

sealed class SpecialPermission(
    titleRes: Int, messageRes: Int,
) : AppPermission(titleRes, messageRes) {

    abstract fun settingsIntent(context: Context): Intent?

    protected fun createPackageIntent(action: String, context: Context): Intent =
        Intent(action, Uri.fromParts("package", context.packageName, null))

    data object Overlay : SpecialPermission(
        titleRes = R.string.dialog_title_permission_overlay,
        messageRes = R.string.dialog_message_permission_overlay,
    ) {
        override fun isGranted(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true

        override fun settingsIntent(context: Context): Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                createPackageIntent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, context)
            } else null
    }

    data object WriteSettings : SpecialPermission(
        titleRes = R.string.dialog_title_permission_write_settings,
        messageRes = R.string.dialog_message_permission_write_settings,
    ) {
        override fun isGranted(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.System.canWrite(context)
            } else true

        override fun settingsIntent(context: Context): Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                createPackageIntent(Settings.ACTION_MANAGE_WRITE_SETTINGS, context)
            } else null
    }

    data object NotificationListener : SpecialPermission(
        titleRes = R.string.dialog_title_permission_notification,
        messageRes = R.string.dialog_message_permission_notification,
    ) {
        override fun isGranted(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ) ?: return false
            return enabled.contains(context.packageName)
        }

        @SuppressLint("InlinedApi")
        override fun settingsIntent(context: Context): Intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
