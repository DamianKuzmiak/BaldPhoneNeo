package app.baldphone.neo.launcher.apps.sync

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.os.UserManager
import android.util.Log

import kotlin.collections.emptyList

import app.baldphone.neo.launcher.apps.data.AppsRepository
import app.baldphone.neo.launcher.apps.getSerialNumberForUser
import app.baldphone.neo.launcher.apps.getUserForSerialNumber

import com.bald.uriah.baldphone.BuildConfig

/**
 * A callback that monitors system package changes (install, uninstall, updates, suspensions)
 * using the [LauncherApps] API.
 * Each callback targets only the affected package(s) and delegates to [AppsRepository].
 */
object LauncherAppsReceiver : LauncherApps.Callback() {
    private const val TAG = "LauncherAppsReceiver"

    private lateinit var applicationContext: Context

    /**
     * Initializes the callback singleton and caches the safe Application Context.
     * Registers the callback to the system LauncherApps service.
     */
    fun init(context: Context) {
        applicationContext = context.applicationContext
        val launcherApps =
            applicationContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        if (launcherApps != null) {
            launcherApps.registerCallback(this)
            Log.d(TAG, "Registered LauncherAppsReceiver")
        }
    }

    override fun onPackageAdded(packageName: String, user: UserHandle) {
        Log.d(TAG, "onPackageAdded: $packageName for user $user")
        val userId = applicationContext.getSerialNumberForUser(user)
        AppsRepository.syncPackage(packageName, userId)
    }

    override fun onPackageChanged(packageName: String, user: UserHandle) {
        Log.d(TAG, "onPackageChanged: $packageName for user $user")
        val userId = applicationContext.getSerialNumberForUser(user)
        AppsRepository.syncPackage(packageName, userId)
    }

    override fun onPackageRemoved(packageName: String, user: UserHandle) {
        Log.d(TAG, "onPackageRemoved: $packageName for user $user")
        val userId = applicationContext.getSerialNumberForUser(user)
        AppsRepository.removePackage(packageName, userId)
    }

    override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
        Log.d(TAG, "onPackagesAvailable: ${packageNames.joinToString()}, replacing=$replacing for user $user")
        val userId = applicationContext.getSerialNumberForUser(user)
        packageNames.forEach { AppsRepository.syncPackage(it, userId) }
    }

    override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
        Log.d(TAG, "onPackagesUnavailable: ${packageNames.joinToString()}, replacing=$replacing for user $user")
        if (!replacing) {
            val userId = applicationContext.getSerialNumberForUser(user)
            packageNames.forEach { AppsRepository.removePackage(it, userId) }
        }
    }

    override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) {
        Log.d(TAG, "onPackagesSuspended: ${packageNames.joinToString()} for user $user")
        val userId = applicationContext.getSerialNumberForUser(user)
        syncPackages(packageNames, userId)
    }

    override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) {
        Log.d(TAG, "onPackagesUnsuspended: ${packageNames.joinToString()} for user $user")
        val userId = applicationContext.getSerialNumberForUser(user)
        syncPackages(packageNames, userId)
    }

    private fun syncPackages(packageNames: Array<out String>, userId: Long) {
        packageNames.forEach { AppsRepository.syncPackage(it, userId) }
    }

    /**
     * Discovers all launchable activities on the device, except for the app itself.
     */
    fun getDiscoverableApps(context: Context): List<LauncherActivityInfo> {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        if (launcherApps == null || userManager == null) return emptyList()

        return try {
            val profiles = userManager.userProfiles
            profiles.flatMap { profile ->
                launcherApps
                    .getActivityList(null, profile)
                    .filter { it.componentName.packageName != BuildConfig.APPLICATION_ID }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying LauncherApps", e)
            emptyList()
        }
    }

    /**
     * Returns a list of launchable activities for a specific package and user handle.
     */
    fun getPackageActivities(
        context: Context,
        packageName: String,
        userId: Long
    ): List<LauncherActivityInfo> {
        if (packageName == BuildConfig.APPLICATION_ID) return emptyList()

        val userHandle = context.getUserForSerialNumber(userId) ?: return emptyList()
        val launcherApps =
            context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return emptyList()
        return try {
            launcherApps.getActivityList(packageName, userHandle)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting activity list for package $packageName", e)
            emptyList()
        }
    }
}

