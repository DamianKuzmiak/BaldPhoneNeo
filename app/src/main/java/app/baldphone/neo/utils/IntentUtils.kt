package app.baldphone.neo.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.os.Bundle
import android.util.Log

import androidx.core.net.toUri

import app.baldphone.neo.ui.dialogs.BaldSnackbar

import com.bald.uriah.baldphone.R

private const val TAG = "IntentUtils"

/**
 * Launches a map application to display the given [address].
 */
fun Context.openMap(address: String) {
    val intent = Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(address)}".toUri())
    startActivityWithNewTask(intent)
}

/**
 * Opens a given URL in an appropriate application (usually a web browser).
 */
fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    startActivityWithNewTask(intent)
}

/**
 * Launches the default SMS application to send a message to the given [number].
 */
fun Context.sendMessage(number: String) {
    val intent = Intent(Intent.ACTION_SENDTO, "smsto:${Uri.encode(number)}".toUri())
    startActivityWithNewTask(intent)
}

/**
 * Launches the default email client to email the given [email] address.
 */
fun Context.sendEmail(email: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", email, null))
    startActivityWithNewTask(intent)
}

/**
 * Starts an activity safely by catching [ActivityNotFoundException] and showing an error message.
 *
 * @param intent The [Intent] to be started.
 * @param options Additional options for how the Activity should be started.
 * @param flags Optional flags to add to the intent.
 */
@JvmOverloads
fun Context.startActivitySafe(
    intent: Intent,
    options: Bundle? = null,
    flags: Int? = null
) {
    flags?.let { intent.addFlags(it) }
    try {
        startActivity(intent, options)
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "Activity not found: $intent", e)
        BaldSnackbar.show(this, R.string.no_app_was_found, BaldSnackbar.TYPE_ERROR)
    }
}

/**
 * Starts an activity with [Intent.FLAG_ACTIVITY_NEW_TASK] flag.
 */
@JvmOverloads
fun Context.startActivityWithNewTask(intent: Intent, options: Bundle? = null) {
    startActivitySafe(intent, options, flags = FLAG_ACTIVITY_NEW_TASK)
}
