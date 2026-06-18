package app.baldphone.neo.permissions

import android.content.Context
import android.os.Build

import app.baldphone.neo.data.Prefs
import app.baldphone.neo.permissions.model.AppPermission
import app.baldphone.neo.permissions.model.RuntimePermission
import app.baldphone.neo.permissions.model.SpecialPermission

import com.bald.uriah.baldphone.utils.BPrefs

/**
 * Interface for determining if a given [AppPermission] is considered mandatory.
 */
interface PermissionMandatoryPolicy {
    fun isMandatory(context: Context, permission: AppPermission): Boolean
}

/**
 * Implementation of [PermissionMandatoryPolicy] that uses [Prefs] and system settings.
 */
class DefaultPermissionMandatoryPolicy : PermissionMandatoryPolicy {
    override fun isMandatory(context: Context, permission: AppPermission): Boolean =
        when (permission) {
            is RuntimePermission.MediaStorage -> {
                false
            }

            is RuntimePermission.PostNotifications -> {
                false
            }

            is SpecialPermission.Overlay -> {
                // TODO: shall only be required when any alarm is active
                val isAnyAlarmEnabled = false
                isAnyAlarmEnabled
            }

            is SpecialPermission.WriteSettings -> {
                // Minor, required in few settings only
                false
            }

            is SpecialPermission.Accessibility -> {
                isLockScreenEnabled(context)
            }

            else -> {
                true
            }
        }

    private fun isLockScreenEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return !BPrefs.get(context).contains(BPrefs.CUSTOM_VIDEOS_KEY)
    }
}
