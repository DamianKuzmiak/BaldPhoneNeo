package app.baldphone.neo.permissions.model

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log

import androidx.core.content.ContextCompat

import com.bald.uriah.baldphone.R

/**
 * Class representing runtime permissions that need to be requested from the user.
 */
sealed class RuntimePermission(
    titleRes: Int,
    messageRes: Int,
    iconRes: Int,
    val permissions: Array<String>
) : AppPermission(titleRes, messageRes, iconRes) {
    override fun settingsIntent(context: Context): Intent =
        createIntent(context, Settings.ACTION_APPLICATION_DETAILS_SETTINGS)

    override fun isGranted(context: Context): Boolean =
        permissions.all {
            val hasPermission = ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            Log.v("RuntimePermission", "isGranted: $it -> $hasPermission")
            hasPermission
        }

    override fun isDeclared(packageInfo: PackageInfo): Boolean {
        val requested = packageInfo.requestedPermissions ?: return false
        return permissions.any { it in requested }
    }

    // -----------------------------------------------------------------------------------------------------------------

    data object CallPhone : RuntimePermission(
        titleRes = R.string.permission_call_phone_title,
        messageRes = R.string.permission_call_phone_description,
        iconRes = R.drawable.phone_on_button,
        permissions = arrayOf(Manifest.permission.CALL_PHONE)
    )

    data object Camera : RuntimePermission(
        titleRes = R.string.permission_camera_title,
        messageRes = R.string.permission_camera_description,
        iconRes = R.drawable.ic_photo_camera,
        permissions = arrayOf(Manifest.permission.CAMERA)
    )

    data object MediaStorage : RuntimePermission(
        titleRes = R.string.permission_read_media_title,
        messageRes = R.string.permission_read_media_description,
        iconRes = R.drawable.ic_photo_library,
        permissions =
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
                }

                else -> {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
    )

    /**
     * Combines [CallPhone] and [ReadPhoneState] permissions into one Android permission group.
     * Used in [app.baldphone.neo.permissions.ui.PermissionsActivity]
     */
    data object Phone : RuntimePermission(
        titleRes = R.string.permission_phone_title,
        messageRes = R.string.permission_phone_description,
        iconRes = R.drawable.phone_on_button,
        permissions = arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
    )

    data object PostNotifications : RuntimePermission(
        titleRes = R.string.permission_post_notifications_title,
        messageRes = R.string.permission_post_notifications_description,
        iconRes = R.drawable.ic_mark_chat_unread,
        permissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyArray()
            }
    ) {
        override fun isGranted(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                super.isGranted(context)
            } else {
                true
            }
    }

    /**
     * Android 9+
     */
    data object ReadCallLog : RuntimePermission(
        titleRes = R.string.permission_read_call_log_title,
        messageRes = R.string.permission_read_call_log_description,
        iconRes = R.drawable.ic_call_log,
        permissions = arrayOf(Manifest.permission.READ_CALL_LOG)
    )

    data object ReadPhoneState : RuntimePermission(
        titleRes = R.string.permission_read_phone_state_title,
        messageRes = R.string.permission_read_phone_state_description,
        iconRes = R.drawable.phone_on_button,
        permissions = arrayOf(Manifest.permission.READ_PHONE_STATE)
    )

    /**
     * TBD: shall we split it?
     */
    data object ReadWriteContacts : RuntimePermission(
        titleRes = R.string.permission_contacts_title,
        messageRes = R.string.permission_contacts_description,
        iconRes = R.drawable.ic_contacts,
        permissions = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
    )
}
