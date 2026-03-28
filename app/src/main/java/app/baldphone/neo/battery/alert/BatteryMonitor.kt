package app.baldphone.neo.battery.alert

import android.content.Context

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager

import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

import java.util.concurrent.TimeUnit

import app.baldphone.neo.battery.BatteryRepository
import app.baldphone.neo.data.Prefs
import app.baldphone.neo.utils.AppLog

/**
 * Manager for battery alert monitoring.
 *
 * Coordinates:
 *   1. Background (via [BatteryCheckWorker])
 *   2. Foreground (via reactive [BatteryRepository.batteryState])
 */
object BatteryMonitor {
    private const val TAG = "BatteryMonitor"
    private const val WORK_NAME = "battery_monitoring_worker"
    private const val MONITOR_DELAY_MS = 3000L

    private var lifecycleObserver: LifecycleObserver? = null

    /**
     * Entry point from the app's onCreate.
     */
    fun initOnAppStart(context: Context) {
        if (Prefs.isBatteryAlertEnabled) {
            startPeriodicWorker(context)
            attachLifecycleObserver(context)
        }
    }

    /**
     * Synchronizes monitoring with user preferences.
     */
    fun syncWithSettings(context: Context) {
        if (Prefs.isBatteryAlertEnabled) {
            startPeriodicWorker(context)
            attachLifecycleObserver(context)
        } else {
            stopPeriodicWorker(context)
            detachLifecycleObserver()
            BatteryAlertPolicy.reset(context)
        }
    }

    private fun attachLifecycleObserver(context: Context) {
        if (lifecycleObserver != null) return
        val observer = LifecycleObserver(context.applicationContext)
        lifecycleObserver = observer
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
    }

    private fun detachLifecycleObserver() {
        lifecycleObserver?.let {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(it)
            lifecycleObserver = null
        }
    }

    /** Worker Management */
    private fun startPeriodicWorker(context: Context) {
        AppLog.d(TAG, "Starting periodic battery monitor worker.")
        val workRequest =
            PeriodicWorkRequestBuilder<BatteryCheckWorker>(
                BatteryAlertPolicy.REPEAT_INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            ).setConstraints(Constraints.Builder().build()).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest,
        )
    }

    private fun stopPeriodicWorker(context: Context) {
        AppLog.d(TAG, "Stopping periodic battery monitor worker.")
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * Internal lifecycle observer that collects battery flow while app is in foreground.
     */
    private class LifecycleObserver(
        private val appContext: Context,
    ) : DefaultLifecycleObserver {
        private var job: Job? = null

        override fun onStart(owner: LifecycleOwner) {
            job =
                owner.lifecycleScope.launch {
                    delay(MONITOR_DELAY_MS)
                    AppLog.d(TAG, "App foregrounded $MONITOR_DELAY_MS ms ago")
                    BatteryRepository.get(appContext).batteryState.collect { state ->
                        BatteryAlertPolicy.processCheck(appContext, state)
                    }
                }
        }

        override fun onStop(owner: LifecycleOwner) {
            AppLog.d(TAG, "App backgrounded")
            job?.cancel()
            job = null
        }
    }
}
