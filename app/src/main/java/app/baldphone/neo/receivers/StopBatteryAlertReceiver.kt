package app.baldphone.neo.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import app.baldphone.neo.battery.alert.BatteryAlertNotificationManager
import app.baldphone.neo.battery.alert.BatteryAlertPolicy

/**
 * BroadcastReceiver that handles the "Stop" action from the battery alert notification.
 */
class StopBatteryAlertReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        if (intent?.action != BatteryAlertNotificationManager.ACTION_STOP_BATTERY_ALERT) return
        BatteryAlertPolicy.onAlertDismissed(context)
    }
}
