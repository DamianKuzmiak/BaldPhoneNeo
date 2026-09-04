package app.baldphone.neo.permissions.model

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager

import app.baldphone.neo.services.DeviceLock
import app.baldphone.neo.services.NotificationReceiverService

import com.bald.uriah.baldphone.R

sealed class SpecialPermission(
    titleRes: Int,
    messageRes: Int,
    iconRes: Int
) : AppPermission(titleRes, messageRes, iconRes) {
    final override fun isGranted(context: Context): Boolean {
        val granted = checkIsGranted(context)
        Log.v("SpecialPermission", "isGranted: ${this.javaClass.simpleName} -> $granted")
        return granted
    }

    protected abstract fun checkIsGranted(context: Context): Boolean

    // -----------------------------------------------------------------------------------------------------------------

    data object Overlay : SpecialPermission(
        titleRes = R.string.permission_overlay_title,
        messageRes = R.string.permission_overlay_description,
        iconRes = R.drawable.ic_alarm_smart_wake
    ) {
        override fun checkIsGranted(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }

        override fun isDeclared(packageInfo: PackageInfo): Boolean =
            packageInfo.requestedPermissions?.contains(Manifest.permission.SYSTEM_ALERT_WINDOW) == true

        override fun settingsIntent(context: Context): Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                createIntent(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            } else {
                null
            }
    }

    data object WriteSettings : SpecialPermission(
        titleRes = R.string.permission_write_settings_title,
        messageRes = R.string.permission_write_settings_description,
        iconRes = R.drawable.ic_custom_typography
    ) {
        override fun checkIsGranted(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.System.canWrite(context)
            } else {
                true
            }

        override fun isDeclared(packageInfo: PackageInfo): Boolean =
            packageInfo.requestedPermissions?.contains(Manifest.permission.WRITE_SETTINGS) == true

        override fun settingsIntent(context: Context): Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                createIntent(context, Settings.ACTION_MANAGE_WRITE_SETTINGS)
            } else {
                null
            }
    }

    data object NotificationListener : SpecialPermission(
        titleRes = R.string.permission_system_notifications_title,
        messageRes = R.string.permission_system_notifications_description,
        iconRes = R.drawable.ic_notifications_active
    ) {
        override fun isDeclared(packageInfo: PackageInfo): Boolean =
            packageInfo.services?.any { it.permission == Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE } ==
                true

        override fun checkIsGranted(context: Context): Boolean {
            val serviceName = NotificationReceiverService::class.java.name
            return isNotificationServiceEnabled(context, serviceName)
        }

        @SuppressLint("InlinedApi")
        override fun settingsIntent(context: Context): Intent =
            createIntent(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, isPackageUri = false)
    }

    data object Accessibility : SpecialPermission(
        titleRes = R.string.permission_accessibility_service_title,
        messageRes = R.string.accessibility_service_description,
        iconRes = R.drawable.ic_settings_accessibility
    ) {
        override fun isDeclared(packageInfo: PackageInfo): Boolean {
            val hasService =
                packageInfo.services?.any { it.permission == Manifest.permission.BIND_ACCESSIBILITY_SERVICE } == true
            // TODO: Move isLockScreenFeatureEnabled to a proper PreferenceManager
            val isLockScreenFeatureEnabled = true
            return hasService && isLockScreenFeatureEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        }

        override fun checkIsGranted(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isAccessibilityServiceEnabled(context, DeviceLock.LockService::class.java.name)
            } else {
                // For older versions, we treat it as granted so it's not flagged as missing in [PermissionRepository].
                true
            }

        override fun settingsIntent(context: Context): Intent =
            createIntent(context, Settings.ACTION_ACCESSIBILITY_SETTINGS, isPackageUri = false)
    }

    data object AccessNotificationPolicy : SpecialPermission(
        titleRes = R.string.permission_dnd_access_title,
        messageRes = R.string.permission_dnd_access_description,
        iconRes = R.drawable.ic_do_not_disturb
    ) {
        override fun checkIsGranted(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.isNotificationPolicyAccessGranted == true
            } else {
                true
            }

        @SuppressLint("InlinedApi")
        override fun isDeclared(packageInfo: PackageInfo): Boolean =
            packageInfo.requestedPermissions?.contains(Manifest.permission.ACCESS_NOTIFICATION_POLICY) == true

        override fun settingsIntent(context: Context): Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                createIntent(context, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, isPackageUri = false)
            } else {
                null
            }
    }

    // -----------------------------------------------------------------------------------------------------------------

    companion object {
        /** Robust check for notification listener permission status. */
        fun isNotificationServiceEnabled(
            context: Context,
            serviceName: String
        ): Boolean {
            val componentName = ComponentName(context, serviceName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                return notificationManager?.isNotificationListenerAccessGranted(componentName)
                    ?: false
            }

            // Fallback for older versions
            return Settings.Secure
                .getString(context.contentResolver, "enabled_notification_listeners")
                ?.splitToSequence(':')
                ?.any { it.equals(componentName.flattenToString(), ignoreCase = true) }
                ?: false
        }

        /** Checks if an accessibility service is enabled by its class name. */
        fun isAccessibilityServiceEnabled(
            context: Context,
            serviceName: String
        ): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)

            return enabledServices.any { info ->
                info.resolveInfo.serviceInfo.packageName == context.packageName &&
                    info.resolveInfo.serviceInfo.name == serviceName
            }
        }
    }
}
