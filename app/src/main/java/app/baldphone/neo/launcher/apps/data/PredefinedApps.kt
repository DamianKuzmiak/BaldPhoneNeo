package app.baldphone.neo.launcher.apps.data

import android.content.Context

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

import app.baldphone.neo.activities.DialerActivity
import app.baldphone.neo.features.calls.ui.RecentCallsActivity
import app.baldphone.neo.features.contacts.ui.ContactsActivity
import app.baldphone.neo.features.gallery.MediaActivity
import app.baldphone.neo.launcher.apps.data.db.AppEntry

import com.bald.uriah.baldphone.BuildConfig
import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.activities.AppsActivity
import com.bald.uriah.baldphone.activities.SOSActivity
import com.bald.uriah.baldphone.activities.alarms.AlarmsActivity
import com.bald.uriah.baldphone.activities.pills.PillsActivity

/**
 * Manager for predefined (built-in) application entries within the BaldPhone launcher.
 *
 * Handles the mapping of internal activities (such as Alarms, Contacts, and SOS) to their respective resources
 * (icons and labels), so can be treated as standard [AppEntry] objects alongside system-installed apps.
 *
 * It also manages the persistence of "pinned" status for these predefined components
 * using SharedPreferences and exposes the current pinned state via [StateFlow].
 */
object PredefinedApps {
    private const val PINNED_PREFS = "predefined_pinned_apps"
    private const val PINNED_KEY = "pinned"
    private const val PACKAGE_NAME = BuildConfig.APPLICATION_ID

    /** Matches the activity-alias name in AndroidManifest.xml for the videos media mode. */
    private const val VIDEOS_ALIAS_CLASS = "app.baldphone.neo.features.gallery.VideosMediaAlias"

    private lateinit var applicationContext: Context

    private val _pinnedApps = MutableStateFlow<Set<String>>(emptySet())
    val pinnedApps: Flow<List<AppEntry>> =
        _pinnedApps.map { names ->
            names.mapNotNull { getAppEntry(applicationContext, it) }
        }

    val allAppsFlow: Flow<List<AppEntry>> by lazy {
        _pinnedApps.map { getApps(applicationContext) }
    }

    val allAppsLiveData: LiveData<List<AppEntry>> by lazy {
        allAppsFlow.asLiveData()
    }

    private data class AppInfo(
        val className: String,
        @DrawableRes val iconResId: Int,
        @StringRes val labelResId: Int
    )

    private val APPS =
        listOf(
            AppInfo(AlarmsActivity::class.java.name, R.drawable.clock_on_background, R.string.alarms),
            AppInfo(AppsActivity::class.java.name, R.drawable.apps_on_background, R.string.apps),
            AppInfo(ContactsActivity::class.java.name, R.drawable.human_on_background, R.string.contacts),
            AppInfo(DialerActivity::class.java.name, R.drawable.phone_on_background, R.string.dialer),
            AppInfo(MediaActivity::class.java.name, R.drawable.photo_on_background, R.string.photos),
            AppInfo(PillsActivity::class.java.name, R.drawable.pill, R.string.pills),
            AppInfo(RecentCallsActivity::class.java.name, R.drawable.history_on_background, R.string.recent),
            AppInfo(SOSActivity::class.java.name, R.drawable.emergency, R.string.sos),
            AppInfo(VIDEOS_ALIAS_CLASS, R.drawable.movie_on_background, R.string.videos)
        )

    /** Keyed by the flattened string format "package/class" for fast lookup. */
    private val APPS_MAP = APPS.associateBy { "$PACKAGE_NAME/${it.className}" }

    fun init(context: Context) {
        applicationContext = context.applicationContext
        val prefs = applicationContext.getSharedPreferences(PINNED_PREFS, Context.MODE_PRIVATE)
        _pinnedApps.value = prefs.getStringSet(PINNED_KEY, emptySet()) ?: emptySet()
    }

    /**
     * Returns a list of all predefined applications as [AppEntry] objects.
     */
    fun getApps(context: Context): List<AppEntry> =
        APPS_MAP.map { (flatName, info) ->
            info.toAppEntry(context, _pinnedApps.value.contains(flatName))
        }

    fun getPinnedApps(context: Context): List<AppEntry> =
        _pinnedApps.value.mapNotNull { getAppEntry(context, it) }

    fun getAppEntry(context: Context, componentName: String): AppEntry? =
        APPS_MAP[componentName]?.toAppEntry(context, _pinnedApps.value.contains(componentName))

    @JvmStatic
    fun getAppsActivityEntry(context: Context): AppEntry? {
        val flatName = "$PACKAGE_NAME/${AppsActivity::class.java.name}"
        return getAppEntry(context, flatName)
    }

    fun getIconResId(componentName: String): Int? =
        APPS_MAP[componentName]?.iconResId

    fun isPredefined(componentName: String): Boolean =
        APPS_MAP.containsKey(componentName)

    fun isAppsActivity(componentName: String): Boolean =
        APPS_MAP[componentName]?.className == AppsActivity::class.java.name

    fun setPinned(context: Context, componentName: String, pinned: Boolean) {
        val prefs = context.getSharedPreferences(PINNED_PREFS, Context.MODE_PRIVATE)

        _pinnedApps.update { current ->
            val newSet =
                current.toMutableSet().apply {
                    if (pinned) add(componentName) else remove(componentName)
                }
            prefs.edit { putStringSet(PINNED_KEY, newSet) }
            newSet
        }
    }

    private fun AppInfo.toAppEntry(context: Context, pinned: Boolean): AppEntry =
        AppEntry(
            packageName = PACKAGE_NAME,
            className = className,
            label = context.getString(labelResId),
            isPinned = pinned
        )
}
