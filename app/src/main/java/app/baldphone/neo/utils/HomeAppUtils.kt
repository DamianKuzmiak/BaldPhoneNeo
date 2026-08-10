package app.baldphone.neo.utils

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.bald.uriah.baldphone.activities.FakeLauncherActivity

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
        }

        val packageManager = context.packageManager
        val thisPackageName = context.packageName
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        val resolveInfo =
            packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolveInfo?.activityInfo?.packageName == thisPackageName) {
            return true
        }

        return false
    }

    /**
     * Requests the user to set the application as the default launcher.
     */
    @JvmStatic
    fun requestDefaultLauncher(context: Context) {
        // Method 1: RoleManager (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                Log.d(TAG, "Attempting RoleManager request")
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                try {
                    if (context is Activity) {
                        // Some devices require startActivityForResult to properly process the request
                        context.startActivityForResult(intent, 1234)
                        return
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "RoleManager failed", e)
                }
            }
        }

        // Method 2: Home Settings (Android 9-)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Log.d(TAG, "Attempting ACTION_HOME_SETTINGS")
                val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.e(TAG, "ACTION_HOME_SETTINGS failed", e)
            }
        }

        // Method 3: Legacy Fake Launcher
        try {
            val packageManager = context.packageManager
            val componentName = ComponentName(context, FakeLauncherActivity::class.java)
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            val homeIntent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(homeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Legacy trick failed", e)
            // Last resort: just open general settings
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Disables the FakeLauncherActivity if it's currently enabled.
     */
    @JvmStatic
    fun cleanupFakeLauncher(context: Context) {
        try {
            val componentName = ComponentName(context, FakeLauncherActivity::class.java)
            val pm = context.packageManager
            if (pm.getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
}
