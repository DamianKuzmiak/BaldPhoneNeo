package app.baldphone.neo.utils

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build

import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.core.text.HtmlCompat

import java.io.File

import app.baldphone.neo.Constants.FILE_PROVIDER_AUTHORITY
import app.baldphone.neo.ui.dialogs.BaldSnackbar

import com.bald.uriah.baldphone.R

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
            it.hasPrimaryClip() && it.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
        }?.primaryClip
        ?.getItemAt(0)
        ?.text

/**
 * Converts a [File] object to a [Uri] in a way that is compatible with different Android versions.
 *
 * @param file The [File] to convert.
 * @return A [Uri] representing the file, suitable for the device's Android version.
 */
fun Context.getUriForFile(file: File): Uri =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        FileProvider.getUriForFile(
            this,
            FILE_PROVIDER_AUTHORITY,
            file
        )
    } else {
        Uri.fromFile(file)
    }

/**
 * Returns a [CharSequence] formatted as HTML from a string resource.
 * Newlines in the resource are converted to `<br>` tags.
 */
fun Context.getHtmlString(
    @StringRes resId: Int,
    vararg formatArgs: Any?
): CharSequence {
    val rawString = getString(resId, *formatArgs)
    val htmlWithBreaks = rawString.replace("\n", "<br>")
    return HtmlCompat.fromHtml(htmlWithBreaks, HtmlCompat.FROM_HTML_MODE_LEGACY)
}
