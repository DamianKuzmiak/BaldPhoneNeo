package app.baldphone.neo.launcher.apps.sync

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

import app.baldphone.neo.launcher.apps.data.db.AppEntry
import app.baldphone.neo.launcher.apps.data.db.AppEntryDao
import app.baldphone.neo.launcher.apps.getSerialNumberForUser

/**
 * Handles application scanning, discovery, and diff-synchronization with the Room database.
 */
internal class AppSync(
    context: Context,
    private val dao: AppEntryDao
) {
    private val applicationContext = context.applicationContext
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var syncDebounceJob: Job? = null
    private val debounceMutex = Mutex()
    private val syncMutex = Mutex()

    /**
     * Requests a sync with configurable behavior.
     *
     * @param force If true, forces re-evaluation of all apps regardless of timestamps.
     * @param skipIcons If true, skips icon re-generation (useful for locale changes).
     * @param debounceMs Debounce delay before executing the sync. Use 0 for immediate.
     */
    fun requestSync(
        force: Boolean = false,
        skipIcons: Boolean = false,
        debounceMs: Long = SYNC_DEBOUNCE_MS
    ) {
        launchDebouncedSync(debounceMs) {
            Log.d(TAG, "requestSync triggered (force=$force, skipIcons=$skipIcons)")
            runCatching {
                performSync(applicationContext, force = force, skipIcons = skipIcons)
            }.onFailure { e ->
                Log.e(TAG, "Error during sync", e)
            }
        }
    }

    fun syncPackage(packageName: String, userId: Long) {
        syncScope.launch {
            syncMutex.withLock {
                try {
                    performPackageSync(applicationContext, packageName, userId)
                    Log.i(TAG, "syncPackage: synced $packageName")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync package: $packageName for user $userId", e)
                }
            }
        }
    }

    fun removePackage(packageName: String, userId: Long) {
        syncScope.launch {
            syncMutex.withLock {
                try {
                    performPackageRemove(packageName, userId)
                    Log.i(TAG, "removePackage: removed $packageName")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to remove package: $packageName for user $userId", e)
                }
            }
        }
    }

    // ========================================================================

    /**
     * Core diff-sync.
     */
    private suspend fun performSync(
        context: Context,
        force: Boolean = false,
        skipIcons: Boolean = false
    ) {
        syncMutex.withLock {
            val startTime = System.currentTimeMillis()

            // 1. Discovery
            val start1 = System.currentTimeMillis()
            val discoveryMap =
                LauncherAppsReceiver.getDiscoverableApps(context).associateBy { app ->
                    AppKey(app.componentName.flattenToString(), context.getSerialNumberForUser(app.user))
                }
            Log.v(TAG, "performSync: 1) Discovery: ${System.currentTimeMillis() - start1}ms, size=${discoveryMap.size}")

            // 2. DB Read
            val start2 = System.currentTimeMillis()
            val dbMap = dao.getAll().associateBy { AppKey(it.componentName, it.userId) }
            Log.v(TAG, "performSync: 2) DB read: ${System.currentTimeMillis() - start2}ms, size=${dbMap.size}")

            // 3. Analysis
            val start3 = System.currentTimeMillis()
            val packageInfoCache = getPackageInfoCache(context.packageManager)
            val diffResult =
                analyzeDiff(
                    force = force,
                    skipIcons = skipIcons,
                    discoveryMap = discoveryMap,
                    dbMap = dbMap,
                    packageInfoProvider = { packageInfoCache[it] }
                )
            Log.v(
                TAG,
                "performSync: 3) Analysis: ${System.currentTimeMillis() - start3}ms, " +
                    "+${diffResult.toUpsert.size} -${diffResult.toDelete.size}"
            )

            // 4. DB Write
            val start4 = System.currentTimeMillis()
            if (diffResult.toUpsert.isNotEmpty() || diffResult.toDelete.isNotEmpty()) {
                dao.syncDiff(diffResult.toUpsert, diffResult.toDelete)
            }
            Log.v(TAG, "performSync: 4) DB write: ${System.currentTimeMillis() - start4}ms")

            Log.v(TAG, "performSync: TOTAL: ${System.currentTimeMillis() - startTime}ms")
        }
    }

    private suspend fun analyzeDiff(
        force: Boolean,
        skipIcons: Boolean,
        discoveryMap: Map<AppKey, LauncherActivityInfo>,
        dbMap: Map<AppKey, AppEntry>,
        packageInfoProvider: (String) -> PackageInfo?
    ): DiffResult {
        val toDelete = mutableListOf<AppEntry>()
        val isIconStorageEmpty = AppIconStorage.isCacheEmpty(applicationContext)
        val semaphore = Semaphore(MAX_PARALLEL_UPSERTS)

        // Find new/changed apps to upsert (bounded parallelism for icon I/O)
        val toUpsert =
            discoveryMap.map { (appKey, launcherAppInfo) ->
                syncScope.async {
                    semaphore.withPermit {
                        val packageInfo =
                            packageInfoProvider(launcherAppInfo.componentName.packageName)
                                ?: return@withPermit null
                        val existing = dbMap[appKey]
                        if (existing == null) {
                            buildAppEntry(
                                componentName = appKey.component,
                                activityInfo = launcherAppInfo,
                                packageInfo = packageInfo,
                                userId = appKey.userId
                            )
                        } else {
                            refreshAppMetadata(
                                existing = existing,
                                launcherAppInfo = launcherAppInfo,
                                packageInfo = packageInfo,
                                userId = appKey.userId,
                                componentName = appKey.component,
                                force = force,
                                labelsOnly = skipIcons,
                                isIconStorageEmpty = isIconStorageEmpty
                            )
                        }
                    }
                }
            }.awaitAll().filterNotNull()

        // Find stale apps to delete
        for (entry in dbMap.values) {
            val compositeKey = AppKey(entry.componentName, entry.userId)
            if (compositeKey !in discoveryMap) {
                toDelete.add(entry)
                AppIconStorage.deleteIconFile(applicationContext, entry.componentName, entry.userId)
            }
        }

        return DiffResult(toUpsert = toUpsert, toDelete = toDelete)
    }

    private fun getPackageInfoCache(pm: PackageManager): Map<String, PackageInfo> =
        try {
            @SuppressLint("QueryPermissionsNeeded")
            val packages =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
                } else {
                    pm.getInstalledPackages(0)
                }
            packages.associateBy { it.packageName }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get package info cache", e)
            emptyMap()
        }

    private suspend fun refreshAppMetadata(
        existing: AppEntry,
        launcherAppInfo: LauncherActivityInfo,
        packageInfo: PackageInfo,
        userId: Long,
        componentName: String,
        force: Boolean,
        labelsOnly: Boolean,
        isIconStorageEmpty: Boolean
    ): AppEntry? {
        try {
            val systemInstallTime = packageInfo.firstInstallTime
            val systemUpdateTime = packageInfo.lastUpdateTime

            val needsUpdate =
                force || isIconStorageEmpty || existing.installTime != systemInstallTime ||
                    existing.updateTime != systemUpdateTime ||
                    !AppIconStorage.isIconCached(applicationContext, componentName, userId)

            if (needsUpdate) {
                val shouldSkipIcon =
                    labelsOnly && !isIconStorageEmpty &&
                        AppIconStorage.isIconCached(applicationContext, componentName, userId)
                val shouldReuseLabel = !force && existing.updateTime == systemUpdateTime
                val freshEntry =
                    buildAppEntry(
                        componentName = componentName,
                        activityInfo = launcherAppInfo,
                        packageInfo = packageInfo,
                        skipIcon = shouldSkipIcon,
                        userId = userId,
                        existingLabel = if (shouldReuseLabel) existing.label else null
                    )
                if (freshEntry != null) {
                    return freshEntry.copy(isPinned = existing.isPinned)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking package info for package: ${launcherAppInfo.componentName.packageName}", e)
        }
        return null
    }

    private suspend fun performPackageSync(context: Context, packageName: String, userId: Long) {
        val pm = context.packageManager
        val packageInfo =
            try {
                pm.getPackageInfo(packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }

        if (packageInfo == null) {
            performPackageRemove(packageName, userId)
            return
        }

        val activities = LauncherAppsReceiver.getPackageActivities(context, packageName, userId)
        if (activities.isEmpty()) {
            // If a package exists but has no launcher activities, it should be removed from our DB
            performPackageRemove(packageName, userId)
            return
        }

        val discoveryMap =
            activities.associateBy { info ->
                AppKey(info.componentName.flattenToString(), userId)
            }

        val storedApps = dao.findByPackageName(packageName, userId)
        val dbMap = storedApps.associateBy { AppKey(it.componentName, it.userId) }

        val diffResult =
            analyzeDiff(
                force = false,
                skipIcons = false,
                discoveryMap = discoveryMap,
                dbMap = dbMap,
                packageInfoProvider = { pkg ->
                    try {
                        pm.getPackageInfo(pkg, 0)
                    } catch (_: PackageManager.NameNotFoundException) {
                        null
                    }
                }
            )

        if (diffResult.toUpsert.isNotEmpty() || diffResult.toDelete.isNotEmpty()) {
            dao.syncDiff(diffResult.toUpsert, diffResult.toDelete)
            Log.i(TAG, "Sync '$packageName' (u$userId): +${diffResult.toUpsert.size}, -${diffResult.toDelete.size}")
        } else {
            Log.d(TAG, "Sync '$packageName' (u$userId): No changes.")
        }
    }

    private suspend fun performPackageRemove(packageName: String, userId: Long) {
        val appComponents = dao.findByPackageName(packageName, userId)
        dao.deleteAll(appComponents)
        appComponents.forEach {
            AppIconStorage.deleteIconFile(applicationContext, it.componentName, it.userId)
        }
    }

    private suspend fun buildAppEntry(
        componentName: String,
        activityInfo: LauncherActivityInfo,
        packageInfo: PackageInfo,
        skipIcon: Boolean = false,
        userId: Long,
        existingLabel: String? = null
    ): AppEntry? =
        try {
            val label = existingLabel ?: activityInfo.label.toString()

            if (!skipIcon) {
                val icon =
                    if (activityInfo.user == Process.myUserHandle()) {
                        activityInfo.getIcon(0)
                    } else {
                        activityInfo.getBadgedIcon(0)
                    }
                AppIconStorage.saveIcon(applicationContext, icon, componentName, userId)
            }

            val installTime = packageInfo.firstInstallTime
            val updateTime = packageInfo.lastUpdateTime

            AppEntry(
                packageName = componentName.substringBefore('/'),
                className = componentName.substringAfter('/'),
                userId = userId,
                label = label,
                installTime = installTime,
                updateTime = updateTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error building app entry from LauncherActivityInfo: $componentName", e)
            null
        }

    /**
     * Helper to debounce sync requests.
     */
    private fun launchDebouncedSync(delayMs: Long = SYNC_DEBOUNCE_MS, block: suspend () -> Unit) {
        syncScope.launch {
            debounceMutex.withLock {
                syncDebounceJob?.cancel()
                syncDebounceJob =
                    syncScope.launch {
                        if (delayMs > 0) delay(delayMs)
                        block()
                    }
            }
        }
    }

    private data class DiffResult(
        val toUpsert: List<AppEntry>,
        val toDelete: List<AppEntry>
    )

    companion object {
        private const val TAG = "AppSync"
        private const val SYNC_DEBOUNCE_MS = 500L
        private const val MAX_PARALLEL_UPSERTS = 4
    }
}

private data class AppKey(val component: String, val userId: Long)
