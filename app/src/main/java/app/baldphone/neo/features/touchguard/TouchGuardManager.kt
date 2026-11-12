// Copyright (c) 2026 Damian Kuźmiak
// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.features.touchguard

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log

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

    /**
     * Called by [TouchGuardWindowCallback] on ACTION_DOWN. Returns true when the touch should
     * be intercepted (overlay shown / guard active).
     */
    fun handleTouchEvent(): Boolean {
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

    /** Called by [TouchGuardWindowCallback] for non-DOWN events while the overlay is up. */
    fun isOverlayShowing() = overlay.isShowing

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

    private fun wrapActivityIfNeeded(activity: Activity) {
        val window = activity.window
        if (window.callback !is TouchGuardWindowCallback) {
            Log.v(TAG, "Wrapping callback for ${activity.javaClass.simpleName}")
            window.callback = TouchGuardWindowCallback(window.callback, this)
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
