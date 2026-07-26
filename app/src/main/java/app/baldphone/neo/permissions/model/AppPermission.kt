package app.baldphone.neo.permissions.model

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

sealed class AppPermission(
    @get:StringRes val titleRes: Int,
    @get:StringRes val messageRes: Int,
    @get:DrawableRes val iconRes: Int
) {
    /**
     * Checks if all permissions associated with this object are granted.
     */
    abstract fun isGranted(context: Context): Boolean

    /**
     * Checks if this permission is declared in the manifest and relevant for the current device.
     */
    abstract fun isDeclared(packageInfo: PackageInfo): Boolean

    /**
     * Returns an [Intent] that leads to the settings screen where the user can grant or manage this permission.
     */
    abstract fun settingsIntent(context: Context): Intent?

    protected fun createIntent(
        context: Context,
        action: String,
        isPackageUri: Boolean = true
    ): Intent =
        Intent(action).apply {
            if (isPackageUri) {
                data = Uri.fromParts("package", context.packageName, null)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }

    companion object {
        fun all(): List<AppPermission> =
            listOf(
                RuntimePermission.Camera,
                RuntimePermission.MediaStorage,
                RuntimePermission.Phone, // CallPhone and ReadPhoneState
                RuntimePermission.PostNotifications,
                RuntimePermission.ReadCallLog,
                RuntimePermission.ReadWriteContacts,
                SpecialPermission.Accessibility,
                SpecialPermission.AccessNotificationPolicy,
                SpecialPermission.NotificationListener,
                SpecialPermission.Overlay,
                SpecialPermission.WriteSettings
            )
    }
}
