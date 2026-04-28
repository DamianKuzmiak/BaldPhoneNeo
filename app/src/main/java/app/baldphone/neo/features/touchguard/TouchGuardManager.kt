package app.baldphone.neo.features.touchguard

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.MotionEvent
import android.view.Window

import androidx.annotation.RequiresApi
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

import java.lang.ref.WeakReference

import app.baldphone.neo.data.PrefKeys
import app.baldphone.neo.data.Prefs

/**
 * Manages touch-guard components to prevent accidental touches.
 */
class TouchGuardManager private constructor(application: Application) {
    private val touchMonitor = TouchMonitor()
    private var currentActivityRef: WeakReference<Activity>? = null
    private var currentActivity: Activity?
        get() = currentActivityRef?.get()
        set(value) {
            currentActivityRef = value?.let { WeakReference(it) }
        }

    private var isAppInForeground = false
    private var isFeatureEnabled = Prefs.useAccidentalGuard

    private fun evaluateState() {
        val shouldRun = isAppInForeground && isFeatureEnabled
        if (shouldRun) {
            sensorHandler.start()
            currentActivity?.let { wrapActivityIfNeeded(it) }
        } else {
            sensorHandler.stop()
            if (!isFeatureEnabled) touchMonitor.reset()
            overlay.hide()
        }
    }

    private val sensorHandler =
        SensorHandler(application) { isNear ->
            if (!isNear) {
                touchMonitor.resetThreshold()
                overlay.hide()
            }
        }

    private val overlay = TouchOverlayController(application) { touchMonitor.raiseThreshold() }

    private val activityLifecycleCallbacks =
        object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity
                if (isFeatureEnabled) wrapActivityIfNeeded(activity)

                if (overlay.isShowing) {
                    overlay.hide()
                    overlay.show(activity)
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (currentActivity == activity) {
                    overlay.hide()
                    currentActivity = null
                }
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity == activity) {
                    overlay.hide()
                    currentActivity = null
                }
            }

            override fun onActivityCreated(a: Activity, s: Bundle?) {}

            override fun onActivityStarted(a: Activity) {}

            override fun onActivityStopped(a: Activity) {}

            override fun onActivitySaveInstanceState(a: Activity, o: Bundle) {}
        }

    private val lifecycleObserver =
        object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isAppInForeground = true
                isFeatureEnabled = Prefs.useAccidentalGuard
                evaluateState()
            }

            override fun onStop(owner: LifecycleOwner) {
                isAppInForeground = false
                evaluateState()
            }
        }

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PrefKeys.KEY_USE_ACCIDENTAL_GUARD) {
                isFeatureEnabled = Prefs.useAccidentalGuard
                evaluateState()
            }
        }

    internal fun handleTouchEvent(): Boolean {
        if (!isFeatureEnabled) return false

        return when {
            overlay.isShowing -> {
                true
            }

            !sensorHandler.isNear -> {
                touchMonitor.reset()
                false
            }

            touchMonitor.recordTouch() -> {
                currentActivity?.let { overlay.show(it) }
                true
            }

            else -> {
                false
            }
        }
    }

    internal fun isOverlayShowing() = overlay.isShowing

    private fun wrapActivityIfNeeded(activity: Activity) {
        val window = activity.window
        if (window.callback !is TouchGuardWindowCallback) {
            Log.v(TAG, "Wrapping callback for ${activity.javaClass.simpleName}")
            window.callback = TouchGuardWindowCallback(window.callback, this)
        }
    }

    private class TouchGuardWindowCallback(
        private val delegate: Window.Callback,
        private val manager: TouchGuardManager
    ) : Window.Callback by delegate {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            val intercepted =
                if (event.action == MotionEvent.ACTION_DOWN) {
                    manager.handleTouchEvent()
                } else {
                    manager.isOverlayShowing()
                }

            return intercepted || delegate.dispatchTouchEvent(event)
        }

        // Explicitly forward major Java default methods to avoid the warning
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onPointerCaptureChanged(hasCapture: Boolean) {
            delegate.onPointerCaptureChanged(hasCapture)
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>?, menu: Menu?, deviceId: Int) {
            delegate.onProvideKeyboardShortcuts(data, menu, deviceId)
        }
    }

    companion object {
        private const val TAG = "TouchGuardManager"

        @Volatile
        private var instance: TouchGuardManager? = null

        fun init(application: Application) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        val mgr = TouchGuardManager(application)
                        application.registerActivityLifecycleCallbacks(mgr.activityLifecycleCallbacks)
                        ProcessLifecycleOwner.get().lifecycle.addObserver(mgr.lifecycleObserver)

                        // Keep a strong reference to the listener
                        application
                            .getSharedPreferences(PrefKeys.PREFS_NAME, Context.MODE_PRIVATE)
                            .registerOnSharedPreferenceChangeListener(mgr.prefsListener)

                        instance = mgr
                        Log.d(TAG, "TouchGuardManager initialized")
                    }
                }
            }
        }

        fun getInstance(): TouchGuardManager = checkNotNull(instance) { "Not initialized" }
    }
}
