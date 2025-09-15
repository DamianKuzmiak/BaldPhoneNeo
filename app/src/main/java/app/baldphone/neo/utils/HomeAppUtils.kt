package app.baldphone.neo.utils

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log

object HomeAppUtils {

    private val TAG = HomeAppUtils::class.java.simpleName

    /**
     * Opens the system settings screen where the user can manage the default Home app.
     *
     * - Prefer ACTION_HOME_SETTINGS (Android provides a dedicated page).
     * - If not available, try ACTION_MANAGE_DEFAULT_APPS_SETTINGS (API 24+).
     * - Final fallback: generic Settings.
     */
    fun openHomeAppSettings(context: Context) {
        val homeSettingsIntent =
            Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(homeSettingsIntent)
            return
        } catch (_: Exception) {
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val manageDefaultsIntent =
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(manageDefaultsIntent)
                return
            } catch (_: Exception) {
            }
        }

        val genericSettingsIntent =
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(genericSettingsIntent)
        } catch (e: Exception) {
            Log.w(TAG, "No settings screen available.", e)
        }
    }

    /**
     * Checks if the application is the current default launcher.
     */
    @JvmStatic
    fun isDefaultLauncher(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
            Log.e(TAG, "RoleManager not available! Falling back to legacy check.")
        }

        val packageManager = context.packageManager
        val thisPackageName = context.packageName
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        val resolveInfo =
            packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolveInfo?.activityInfo?.packageName == thisPackageName) {
            Log.d(TAG, "Our app is default launcher (system-resolved)")
            return true
        }

        Log.w(TAG, "Cannot determine if our app is default launcher, returning false.")
        return false
    }
}
