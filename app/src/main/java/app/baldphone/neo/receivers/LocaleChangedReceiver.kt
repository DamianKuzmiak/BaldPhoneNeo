package app.baldphone.neo.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

import app.baldphone.neo.launcher.apps.data.AppsRepository

/**
 * Global BroadcastReceiver that listens for system locale/language changes. Any feature can utilize it.
 *
 * 1) Triggers a full apps database sync when a locale change is received.
 */
class LocaleChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_LOCALE_CHANGED) return

        Log.i(TAG, "System locale changed")

        AppsRepository.requestLocaleSync()
    }

    companion object {
        private const val TAG = "LocaleChangedReceiver"
    }
}
