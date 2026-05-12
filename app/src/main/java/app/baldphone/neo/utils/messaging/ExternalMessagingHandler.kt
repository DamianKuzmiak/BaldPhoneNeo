package app.baldphone.neo.utils.messaging

import android.app.Activity
import android.content.Context
import android.content.Intent

import androidx.core.net.toUri

import app.baldphone.neo.utils.startActivitySafe

/**
 * Interface for external messaging handlers (e.g., WhatsApp, Signal).
 * For launching the external app and starting chats.
 */
interface ExternalMessagingHandler {
    val packageName: String
    val appName: String

    /**
     * Starts a text chat with the given identifier (e.g., phone number or JID).
     * Throws an Exception if the chat cannot be started.
     */
    @Throws(Exception::class)
    fun startChat(context: Context, identifier: String)

    /**
     * Launches the main activity of the external messaging app.
     * If the app is not found, attempts to open its Play Store page.
     * Throws an Exception if the app cannot be launched or Play Store opened.
     */
    @Throws(Exception::class)
    fun launch(context: Context) {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            context.launchMessagingIntent(launchIntent)
        } else {
            openPlayStore(context)
        }
    }

    /**
     * Opens the app store page for the messaging application.
     * Throws an Exception if neither Play Store nor a web browser can handle the intent.
     */
    @Throws(Exception::class)
    fun openPlayStore(context: Context) {
        val pm = context.packageManager
        val playStoreIntent = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
        playStoreIntent.setPackage("com.android.vending")

        if (playStoreIntent.resolveActivity(pm) != null) {
            context.launchMessagingIntent(playStoreIntent)
            return
        }

        val webIntent = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri())
        if (webIntent.resolveActivity(pm) != null) {
            context.launchMessagingIntent(webIntent)
        } else {
            error("Unable to open $appName in the Play Store or browser.")
        }
    }
}

/**
 * Extension function for safely launching messaging-related intents.
 * Handles Activity/non-Activity context differences and common intent resolution issues.
 */
@Throws(Exception::class)
fun Context.launchMessagingIntent(intent: Intent) {
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    if (intent.resolveActivity(packageManager) == null) {
        error("App not installed or intent cannot be resolved.")
    }

    startActivitySafe(intent)
}
