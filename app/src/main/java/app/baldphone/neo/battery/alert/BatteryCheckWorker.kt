package app.baldphone.neo.battery.alert

import android.content.Context

import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

import app.baldphone.neo.helpers.AppForegroundState
import app.baldphone.neo.utils.AppLog

/**
 * Worker that triggers the battery monitoring check.
 */
class BatteryCheckWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        if (AppForegroundState.isForeground) {
            AppLog.d("BatteryCheckWorker", "Worker skipped: app is in foreground.")
            return Result.success()
        }

        AppLog.d("BatteryCheckWorker", "Worker triggered.")
        BatteryAlertPolicy.processCheck(applicationContext)
        return Result.success()
    }
}
