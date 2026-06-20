package app.baldphone.neo

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log

import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat.getInsetsController
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.util.DebugLogger
import net.danlew.android.joda.JodaTimeAndroid

import app.baldphone.neo.data.Prefs
import app.baldphone.neo.data.StatusBarMode
import app.baldphone.neo.extensions.apply
import app.baldphone.neo.extensions.isSystem
import app.baldphone.neo.features.touchguard.TouchGuardManager
import app.baldphone.neo.utils.MediaStoreThumbnailFetcher

import com.bald.uriah.baldphone.BuildConfig
import com.bald.uriah.baldphone.activities.HomeScreenActivity
import com.bald.uriah.baldphone.databases.alarms.AlarmScheduler
import com.bald.uriah.baldphone.databases.reminders.ReminderScheduler
import com.bald.uriah.baldphone.utils.BaldUncaughtExceptionHandler

class NeoApp : Application(), SingletonImageLoader.Factory  {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        Thread.setDefaultUncaughtExceptionHandler(
            BaldUncaughtExceptionHandler(this, Thread.getDefaultUncaughtExceptionHandler())
        )
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Application started")

        Prefs.init(this)

        val theme = Prefs.theme
        if (!theme.isSystem) {
            theme.apply()
        }

        JodaTimeAndroid.init(this)
        CoroutineScope(Dispatchers.IO).launch {
            AlarmScheduler.reStartAlarms(this@NeoApp)
            ReminderScheduler.reStartReminders(this@NeoApp)
        }

        TouchGuardManager.init(this)

        registerActivityLifecycleCallbacks(globalActivityLifecycleListener)
    }

    private val globalActivityLifecycleListener =
        object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is AppCompatActivity) {
                    setupStatusBar(activity)
                }
            }

            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {}

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {}

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        }

    private fun setupStatusBar(activity: AppCompatActivity) {
        val window = activity.window
        val insetsController = getInsetsController(window, window.decorView)

        val statusBarMode = Prefs.statusBarMode
        val shouldShowStatusBar =
            (statusBarMode == StatusBarMode.EVERYWHERE) ||
                (statusBarMode == StatusBarMode.ONLY_HOME && activity is HomeScreenActivity)

        if (shouldShowStatusBar) {
            insetsController.show(statusBars())
        } else {
            insetsController.hide(statusBars())
            insetsController.systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "onLowMemory()")
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizePercent(context, 0.35)
                    .build()
            }.components {
                add(MediaStoreThumbnailFetcher.Factory(context))
            }.apply {
                if (BuildConfig.DEBUG) {
                    logger(DebugLogger(coil3.util.Logger.Level.Verbose))
                }
            }.build()

    companion object {
        private const val TAG = "NeoApp"
    }
}
