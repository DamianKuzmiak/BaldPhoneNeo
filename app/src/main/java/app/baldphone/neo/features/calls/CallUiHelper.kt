package app.baldphone.neo.features.calls

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan

import androidx.core.content.ContextCompat

import java.util.Collections

import app.baldphone.neo.data.Prefs
import app.baldphone.neo.features.calls.data.CallIntentFactory
import app.baldphone.neo.ui.dialogs.BaldDialog
import app.baldphone.neo.ui.dialogs.BaldSnackbar
import app.baldphone.neo.utils.PhoneNumberUtils
import app.baldphone.neo.utils.getDeviceRegion

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.utils.BDB
import com.bald.uriah.baldphone.utils.BDialog

/**
 * Handles the UI flow for making a phone call, including:
 * - Call confirmation dialogs
 * - Dual SIM selection dialogs
 * - Starting the call intent
 * - Showing error snackbars if no app is found
 */
object CallUiHelper {
    /**
     * Immediately initiates a call, bypassing confirmation prompts and SIM selection dialogs.
     * Primarily used by the emergency SOS feature.
     */
    fun callDirectly(context: Context, number: CharSequence) {
        call(context, number, directly = true)
    }

    /**
     * Initiates a phone call to the specified number with appropriate UI prompts.
     *
     * @param context The context used to display dialogs and start activities.
     * @param number The phone number to call.
     * @param name The name of the contact (optional).
     * @param directly If true, bypasses Dual SIM selection.
     * @param skipPrompt If true, bypasses the call confirmation dialog.
     */
    @JvmOverloads
    fun call(
        context: Context,
        number: CharSequence,
        name: CharSequence? = null,
        directly: Boolean = false,
        skipPrompt: Boolean = false
    ) {
        if (!skipPrompt && Prefs.shouldConfirmCalls) {
            showCallConfirmationDialog(context, number, name, directly)
        } else {
            performCallFlow(context, number, directly)
        }
    }

    private fun showCallConfirmationDialog(
        context: Context,
        number: CharSequence,
        name: CharSequence?,
        directly: Boolean
    ) {
        val region = context.getDeviceRegion()
        val formattedNumber = PhoneNumberUtils.formatForDisplay(number as String, region)
        val message =
            if (name != null) {
                SpannableStringBuilder().apply {
                    val start = length
                    append(name)
                    setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    append("\n")
                    append(formattedNumber)
                }
            } else {
                formattedNumber
            }

        BaldDialog
            .Builder(context)
            .setIcon(R.drawable.phone_on_button)
            .setIconTintRes(R.color.green)
            .setTitle(R.string.call_dialog_title)
            .setMessage(message)
            .setPositiveButton(R.string.call_dialog_confirm) {
                performCallFlow(context, number, directly)
            }.setNegativeButton(R.string.no)
            .show()
    }

    private fun performCallFlow(context: Context, number: CharSequence, directly: Boolean) {
        if (!directly && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && Prefs.isDualSimActive) {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val list = sm.activeSubscriptionInfoList?.let { Collections.unmodifiableList(it) } ?: emptyList()
                if (list.size > 1) {
                    val names = list.map { it.displayName ?: "" }.toTypedArray()
                    BDB
                        .from(context)
                        .addFlag(BDialog.FLAG_OK or BDialog.FLAG_CANCEL)
                        .setTitle(R.string.choose_sim)
                        .setSubText(R.string.choose_sim_subtext)
                        .setOptions(*names)
                        .setPositiveButtonListener {
                            startCall(context, number, list[it[0] as Int])
                            true
                        }.show()
                    return
                }
            }
        }

        startCall(context, number, null)
    }

    private fun startCall(context: Context, number: CharSequence, subscriptionInfo: SubscriptionInfo?) {
        val intent = CallIntentFactory.createCallIntent(context, number, subscriptionInfo)

        runCatching {
            context.startActivity(intent)
        }.recoverCatching {
            val fallbackUri = Uri.fromParts("tel", number.toString(), null)
            context.startActivity(Intent(Intent.ACTION_DIAL).setData(fallbackUri))
        }.onFailure {
            BaldSnackbar.show(context, R.string.no_app_was_found, BaldSnackbar.TYPE_ERROR)
        }
    }
}
