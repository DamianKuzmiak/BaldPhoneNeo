package app.baldphone.neo.utils

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

import androidx.core.content.getSystemService
import androidx.core.net.toUri

import app.baldphone.neo.ui.dialogs.BaldSnackbar

import com.bald.uriah.baldphone.R

private const val TAG = "ContextUtils"

/**
 * Copies a given text to the system clipboard.
 *
 * Displays a confirmation snackbar on Android 12 and older, as newer versions provide a system confirmation.
 *
 * @param label A user-visible label for the clip data.
 * @param text The actual text to be copied to the clipboard.
 */
fun Context.copyToClipboard(label: CharSequence, text: CharSequence) {
    getSystemService<ClipboardManager>()?.run {
        setPrimaryClip(ClipData.newPlainText(label, text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            BaldSnackbar.show(this@copyToClipboard, R.string.copied_to_clipboard, BaldSnackbar.TYPE_SUCCESS)
        }
    }
}

/**
 * Retrieves text from the system clipboard.
 *
 * @return The text content of the primary clip, or null if the clipboard is empty or does not
 * contain text.
 */
fun Context.getTextFromClipboard(): CharSequence? =
    getSystemService<ClipboardManager>()
        ?.takeIf {
            it.hasPrimaryClip() &&
                it.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
        }?.primaryClip
        ?.getItemAt(0)
        ?.text

/**
 * Attempts to open a given URL in an appropriate application (usually a web browser).
 *
 * If no application is found on the system that can handle the URL, it displays an error toast
 * to the user.
 *
 * @param url The URL string to be opened.
 */
fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "Cannot open URL: ${e.message}")
        BaldSnackbar.show(this, "No app found to open URL", BaldSnackbar.TYPE_ERROR)
    }
}
