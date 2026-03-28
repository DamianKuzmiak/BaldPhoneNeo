package app.baldphone.neo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup

import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat.getInsetsController
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.util.DebugLogger
import net.danlew.android.joda.JodaTimeAndroid

import app.baldphone.neo.battery.alert.BatteryMonitor
import app.baldphone.neo.core.NotificationChannels
import app.baldphone.neo.core.system.HapticManager
import app.baldphone.neo.crashes.CrashHandler
import app.baldphone.neo.data.Prefs
import app.baldphone.neo.data.StatusBarMode
import app.baldphone.neo.extensions.apply
import app.baldphone.neo.extensions.applyEdgeToEdgeInsets
import app.baldphone.neo.extensions.isSystem
import app.baldphone.neo.features.touchguard.TouchGuardManager
import app.baldphone.neo.helpers.AppForegroundState
import app.baldphone.neo.utils.MediaStoreThumbnailFetcher

import com.bald.uriah.baldphone.BuildConfig
import com.bald.uriah.baldphone.activities.HomeScreenActivity
import com.bald.uriah.baldphone.databases.alarms.AlarmScheduler
import com.bald.uriah.baldphone.databases.reminders.ReminderScheduler

class NeoApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.init(this)
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

        HapticManager.init(this)
        TouchGuardManager.init(this)

        NotificationChannels.init(this)
        BatteryMonitor.initOnAppStart(this) // WorkManager
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppForegroundState)

        registerActivityLifecycleCallbacks(globalActivityLifecycleListener)
    }

    private val globalActivityLifecycleListener =
        object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is LauncherProxyActivity) return

                val rootView = activity.findViewById<ViewGroup>(android.R.id.content)

                if (activity is ComponentActivity) {
                    activity.enableEdgeToEdge()
                    rootView?.applyEdgeToEdgeInsets()
                }

                if (activity is AppCompatActivity) {
                    setupStatusBar(activity)
                }

                if (activity is ComponentActivity) {
                    activity.window.decorView.post {
                        if (activity.isDestroyed || activity.isFinishing) return@post

                        activity.onBackPressedDispatcher.addCallback(activity) {
                            if (Prefs.isVibrationFeedbackEnabled) {
                                HapticManager.vibrate()
                            }
                            isEnabled = false
                            activity.onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    }
                }
            }

            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {}

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {}

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        }

    override val workManagerConfiguration: Configuration
        get() {
            Log.d(TAG, "workManagerConfiguration called")
            return Configuration.Builder().setMinimumLoggingLevel(Log.INFO).build()
        }

    private fun setupStatusBar(activity: AppCompatActivity) {
        val window = activity.window

        // Note: Be aware of "android:enforceNavigationBarContrast" = true

        /*
                window.apply {
                    navigationBarColor = Color.TRANSPARENT
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        isNavigationBarContrastEnforced = false
                    }
                }
         */

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
