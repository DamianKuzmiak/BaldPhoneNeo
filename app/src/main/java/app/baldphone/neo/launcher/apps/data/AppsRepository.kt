package app.baldphone.neo.launcher.apps.data

import android.content.Context
import android.util.Log

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

import app.baldphone.neo.launcher.apps.data.db.AppDatabase
import app.baldphone.neo.launcher.apps.data.db.AppEntry
import app.baldphone.neo.launcher.apps.data.db.AppEntryDao
import app.baldphone.neo.launcher.apps.sync.AppIconStorage
import app.baldphone.neo.launcher.apps.sync.AppSync

/**
 * Central repository for application data. The single source of truth for all app-related database operations.
 *
 * Refactored to strictly handle Database operations and in-memory caching.
 * Delegates synchronization orchestration to [AppSync].
 */
object AppsRepository {
    private const val TAG = "AppsRepository"

    /**
     * A combined flow of all apps (DB + Predefined).
     */
    val allAppsFlow: Flow<List<AppEntry>> by lazy {
        combine(dao.observeAll(), PredefinedApps.pinnedApps) { dbApps, _ ->
            val predefined = PredefinedApps.getApps(applicationContext)
            (dbApps + predefined).sortedBy { it.label.lowercase() }
        }
    }

    /** LiveData for Java */
    @JvmStatic
    val allAppsLiveData: LiveData<List<AppEntry>> by lazy { allAppsFlow.asLiveData() }

    /**
     * Pinned apps only.
     */
    val pinnedAppsFlow: Flow<List<AppEntry>> by lazy {
        allAppsFlow.map { apps -> apps.filter { it.isPinned } }
    }

    /** LiveData for Java */
    @JvmStatic
    val pinnedAppsLiveData: LiveData<List<AppEntry>> by lazy { pinnedAppsFlow.asLiveData() }

    internal val dao: AppEntryDao by lazy {
        AppDatabase.getInstance(applicationContext).appEntryDao()
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var applicationContext: Context
    private lateinit var appSync: AppSync

    private val appCache = MutableStateFlow<Map<String, AppEntry>>(emptyMap())

    // =========================================================================

    fun init(context: Context) {
        applicationContext = context.applicationContext
        this.appSync = AppSync(applicationContext, dao)

        PredefinedApps.init(applicationContext)

        appScope.launch {
            allAppsFlow.collect { apps ->
                val newCache = apps.associateBy { "${it.packageName}/${it.className}#${it.userId}" }
                appCache.value = newCache
                Log.d(TAG, "Memory cache rebuilt with ${apps.size} apps")
            }
        }

        // Trigger initial app database sync.
        appScope.launch {
            requestInitialSync()
        }

        Log.d(TAG, "Initialized AppsRepository")
    }

    fun requestInitialSync() {
        appSync.requestSync(debounceMs = 0)
    }

    fun requestForceSync() {
        appSync.requestSync(force = true, debounceMs = 0)
    }

    fun requestLocaleSync() {
        appSync.requestSync(force = true, skipIcons = true)
    }

    fun syncPackage(packageName: String, userId: Long) {
        appSync.syncPackage(packageName, userId)
    }

    fun removePackage(packageName: String, userId: Long) {
        appSync.removePackage(packageName, userId)
    }

    /**
     * Deletes all apps from the database and clears the icon cache.
     */
    fun clearAll() {
        appScope.launch {
            dao.deleteAll()
            AppIconStorage.clearIconCache(applicationContext)
            Log.i(TAG, "All apps cleared from database and icon cache")
        }
    }

    /**
     * Returns an app by its component name from the in-memory cache.
     */
    @JvmStatic
    @JvmOverloads
    fun findByComponentName(componentName: String, userId: Long = 0L): AppEntry? {
        val key = "$componentName#$userId"
        return appCache.value[key].also {
            if (it == null) Log.d(TAG, "App not found in cache: $componentName")
        }
    }

    /**
     * Returns all cached pinned apps.
     */
    @JvmStatic
    fun getAllPinnedFromCache(): List<AppEntry> =
        appCache.value.values
            .filter { it.isPinned }
            .sortedBy { it.label.lowercase() }

    /**
     * Updates the pinned state of an app.
     */
    @JvmStatic
    @JvmOverloads
    fun updatePinnedJava(componentName: String, userId: Long = 0L, pinned: Boolean) {
        if (PredefinedApps.isPredefined(componentName)) {
            PredefinedApps.setPinned(applicationContext, componentName, pinned)
            return
        }
        val packageName = componentName.substringBefore('/')
        val className = componentName.substringAfter('/')
        appScope.launch {
            dao.updatePinned(packageName, className, userId, pinned)
        }
    }
}
