package app.baldphone.neo.features.calls

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SubscriptionInfo
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup

import androidx.annotation.RequiresApi
import androidx.core.text.bold
import androidx.core.text.buildSpannedString

import app.baldphone.neo.R
import app.baldphone.neo.data.Prefs
import app.baldphone.neo.ui.dialogs.BaldDialog
import app.baldphone.neo.ui.dialogs.BaldSnackbar
import app.baldphone.neo.utils.PhoneNumberUtils
import app.baldphone.neo.utils.getDeviceRegion

/**
 * Handles the UI flow for making a phone call, including:
 * - Call confirmation dialogs
 * - Dual SIM selection dialogs
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
        val formattedNumber = PhoneNumberUtils.formatForDisplay(number.toString(), region)
        val message =
            if (name != null) {
                buildSpannedString {
                    bold { append(name) }
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
        if (!directly && Prefs.isDualSimActive && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)) {
            val sims = CallUtils.getActiveSims(context)
            if (sims.size > 1) {
                showSimSelectionDialog(context, number, sims)
                return
            }
        }
        startCall(context, number, null)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private fun showSimSelectionDialog(
        context: Context,
        number: CharSequence,
        sims: List<SubscriptionInfo>
    ) {
        val styledContext = ContextThemeWrapper(context, R.style.DialogRadioButton)
        val radioButtons =
            sims.mapIndexed { index, sim ->
                RadioButton(styledContext).apply {
                    id = View.generateViewId()
                    text = getSimDisplayName(context, sim, index)
                    if (index == 0) isChecked = true
                }
            }

        val radioGroup =
            RadioGroup(context).apply {
                radioButtons.forEach { addView(it) }
            }

        BaldDialog
            .Builder(context)
            .setTitle(R.string.choose_sim_subtext) // using the existing string resource
            .setIcon(R.drawable.sim_on_button)
            .setCustomView(radioGroup)
            .setPositiveButton(android.R.string.ok) {
                val selectedIndex = radioButtons.indexOfFirst { it.id == radioGroup.checkedRadioButtonId }
                (sims.getOrNull(selectedIndex) ?: sims.firstOrNull())?.let { selectedSim ->
                    startCall(context, number, selectedSim)
                }
            }.setNegativeButton(android.R.string.cancel)
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private fun getSimDisplayName(
        context: Context,
        sim: SubscriptionInfo,
        index: Int
    ): CharSequence =
        sim.displayName?.takeIf { it.isNotBlank() }
            ?: sim.carrierName?.takeIf { it.isNotBlank() }
            ?: "${context.getString(R.string.choose_sim)} ${index + 1}"

    private fun startCall(
        context: Context,
        number: CharSequence,
        subscriptionInfo: SubscriptionInfo?
    ) {
        val intent = CallUtils.createCallIntent(context, number, subscriptionInfo)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching {
            context.startActivity(intent)
        }.recoverCatching {
            val fallbackUri = Uri.fromParts("tel", number.toString(), null)
            val fallbackIntent =
                Intent(Intent.ACTION_DIAL, fallbackUri).apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            context.startActivity(fallbackIntent)
        }.onFailure {
            BaldSnackbar.show(context, R.string.no_app_was_found, BaldSnackbar.TYPE_ERROR)
        }
    }
}
