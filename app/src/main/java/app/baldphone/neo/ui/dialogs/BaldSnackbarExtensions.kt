package app.baldphone.neo.ui.dialogs

import android.app.Activity

import androidx.annotation.StringRes

/**
 * Extension functions for easier access from Activities
 */

fun Activity.showSnackbar(
    message: CharSequence,
    type: BaldSnackbar.Type = BaldSnackbar.TYPE_INFO,
    duration: Int = BaldSnackbar.LENGTH_SHORT
) =
    BaldSnackbar.show(this, message, type, duration)

fun Activity.showSnackbar(
    @StringRes resId: Int,
    type: BaldSnackbar.Type = BaldSnackbar.TYPE_INFO,
    duration: Int = BaldSnackbar.LENGTH_SHORT
) =
    BaldSnackbar.show(this, resId, type, duration)

/**
 * Displays an informational Snackbar with a blue background.
 * Used for neutral updates, tips, or background process statuses that do not require immediate action.
 */
fun Activity.showInfoSnackbar(
    @StringRes resId: Int
) = showSnackbar(resId, BaldSnackbar.TYPE_INFO)

fun Activity.showInfoSnackbar(message: CharSequence) = showSnackbar(message, BaldSnackbar.TYPE_INFO)

/**
 * Displays a success Snackbar with a green background.
 * Used to confirm that a user-initiated action has completed successfully.
 */
fun Activity.showSuccessSnackbar(
    @StringRes resId: Int
) = showSnackbar(resId, BaldSnackbar.TYPE_SUCCESS)

fun Activity.showSuccessSnackbar(message: CharSequence) = showSnackbar(message, BaldSnackbar.TYPE_SUCCESS)

/**
 * Displays an error Snackbar with a red background.
 * Used for critical failures, interrupted actions, or when an operation cannot be completed.
 */
fun Activity.showErrorSnackbar(
    @StringRes resId: Int
) = showSnackbar(resId, BaldSnackbar.TYPE_ERROR)

fun Activity.showErrorSnackbar(message: CharSequence) = showSnackbar(message, BaldSnackbar.TYPE_ERROR)

/**
 * Displays a warning Snackbar with a yellow background.
 * Used for non-critical issues or situations that require the user's attention to prevent potential errors.
 */
fun Activity.showWarningSnackbar(
    @StringRes resId: Int
) = showSnackbar(resId, BaldSnackbar.TYPE_WARNING)

fun Activity.showWarningSnackbar(message: CharSequence) = showSnackbar(message, BaldSnackbar.TYPE_WARNING)
