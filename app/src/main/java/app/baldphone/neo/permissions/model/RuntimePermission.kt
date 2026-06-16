package app.baldphone.neo.permissions.model

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

import androidx.core.content.ContextCompat

import com.bald.uriah.baldphone.R

// --- Runtime Permissions ---
sealed class RuntimePermission(
    titleRes: Int = R.string.dialog_title_permission_required,
    messageRes: Int,
    val permissions: Array<String>
) : AppPermission(titleRes, messageRes) {
    override fun isGranted(context: Context): Boolean =
        permissions.all {
            val hasPermission = ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            android.util.Log.d("RuntimePermission", "isGranted: $it -> $hasPermission")
            hasPermission
        }

    data object CallPhone : RuntimePermission(
        messageRes = R.string.dialog_message_permission_phone,
        permissions = arrayOf(Manifest.permission.CALL_PHONE)
    )

    data object ReadWriteContacts : RuntimePermission(
        messageRes = R.string.dialog_message_permission_contacts,
        permissions =
            arrayOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS
            )
    )

    data object ReadCallLog : RuntimePermission(
        messageRes = R.string.dialog_message_permission_call_log,
        permissions = arrayOf(Manifest.permission.READ_CALL_LOG)
    )

    data object MediaStorage : RuntimePermission(
        messageRes = R.string.dialog_message_permission_media,
        permissions =
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                }

                else -> {
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                }
            }
    )

    data object Camera : RuntimePermission(
        messageRes = R.string.camera_subtext,
        permissions = arrayOf(Manifest.permission.CAMERA)
    )
}
