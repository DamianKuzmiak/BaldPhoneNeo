package app.baldphone.neo.utils

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log

import androidx.core.net.toUri

import app.baldphone.neo.features.share.ShareActivity
import app.baldphone.neo.launcher.apps.data.db.AppEntry
import app.baldphone.neo.launcher.apps.getUserForSerialNumber
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
 * Starts the [ShareActivity] with the provided [intent].
 *
 * @param intent The [Intent] to be shared.
 */
fun Context.share(intent: Intent) {
    startActivity(
        Intent(this, ShareActivity::class.java).putExtra(ShareActivity.EXTRA_SHARE_INTENT, intent)
    )
}

/**
 * Shares a contact as a vCard.
 */
fun Context.shareContact(lookupKey: String?, name: String? = null) {
    if (lookupKey.isNullOrEmpty()) {
        Log.w(TAG, "Cannot share contact: lookupKey is null/empty")
        return
    }
    val vcardUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey)
    val shareIntent =
        Intent(Intent.ACTION_SEND)
            .setType(ContactsContract.Contacts.CONTENT_VCARD_TYPE)
            .putExtra(Intent.EXTRA_STREAM, vcardUri)

    if (!name.isNullOrEmpty()) {
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, name)
    }
    share(shareIntent)
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
 * Opens a contact photo in a full-photo view via any registered system gallery app.
 */
fun Context.viewContactPhoto(photoUri: Uri) {
    val viewIntent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(photoUri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("", photoUri)
        }
    startActivitySafe(
        viewIntent,
        flags = Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS // doubtful
    )
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

/**
 * Starts an activity with [Intent.FLAG_ACTIVITY_NEW_TASK] and [Intent.FLAG_ACTIVITY_CLEAR_TASK] flags.
 */
@JvmOverloads
fun Context.startActivityWithNewTaskClear(intent: Intent, options: Bundle? = null) {
    startActivitySafe(intent, options, flags = FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
}

/**
 * Starts the activity of the given [componentName].
 * If a non-zero [userId] is specified, attempts to launch it as the corresponding user.
 */
@JvmOverloads
fun Context.startComponentName(componentName: ComponentName, userId: Long = 0L) {
    if (userId != 0L) {
        try {
            val user = getUserForSerialNumber(userId)
            if (user != null) {
                val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                launcherApps.startMainActivity(componentName, user, null, null)
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app with LauncherApps for user: $userId", e)
        }
    }
    startActivity(Intent.makeRestartActivityTask(componentName))
}

/**
 * Starts the activity of the given [appEntry].
 */
fun Context.startComponentName(appEntry: AppEntry) {
    startComponentName(appEntry.component, appEntry.userId)
}
