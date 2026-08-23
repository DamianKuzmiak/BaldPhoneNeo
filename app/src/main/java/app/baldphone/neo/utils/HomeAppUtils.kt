package app.baldphone.neo.utils

import android.app.Activity
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

        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)

            resolveInfo?.activityInfo?.packageName == context.packageName
        } catch (e: Exception) {
            Log.e(TAG, "Error checking default launcher", e)
            false
        }
    }

    /**
     * Opens the system settings screen where the user can manage the default Home app.
     *
     * 1) Prefer ACTION_HOME_SETTINGS (dedicated page).
     * 2) Try ACTION_MANAGE_DEFAULT_APPS_SETTINGS (API 24+).
     * 3) Fallback to generic Settings.
     */
    @JvmStatic
    fun requestDefaultLauncher(context: Context) {
        val actions = mutableListOf(Settings.ACTION_HOME_SETTINGS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            actions.add(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        }
        actions.add(Settings.ACTION_SETTINGS)

        for (action in actions) {
            val intent = Intent(action)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.d(TAG, "Action $action not available, trying next fallback: ${e.message}")
            }
        }

        Log.e(TAG, "No settings screen available to request default launcher.")
    }
}
