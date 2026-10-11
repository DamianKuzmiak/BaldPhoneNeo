package app.baldphone.neo.features.calls

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager

import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

import app.baldphone.neo.utils.PhoneNumberUtils
import app.baldphone.neo.utils.hasPermission

/**
 * Telephony and call utility functions
 */
object CallUtils {
    /**
     * Creates a call [Intent], using [Intent.ACTION_CALL] if permitted and non-emergency,
     * or falling back to [Intent.ACTION_DIAL].
     */
    fun createCallIntent(
        context: Context,
        number: CharSequence,
        subscriptionInfo: SubscriptionInfo? = null
    ): Intent {
        val numberStr = number.toString()
        val uri = Uri.fromParts("tel", numberStr, null)

        val hasCallPermission = context.hasPermission(Manifest.permission.CALL_PHONE)
        val isEmergencyNumber = PhoneNumberUtils.isEmergency(context, numberStr)

        val action =
            if (hasCallPermission && !isEmergencyNumber) {
                Intent.ACTION_CALL
            } else {
                Intent.ACTION_DIAL
            }

        val intent = Intent(action, uri)

        if ((subscriptionInfo != null) && hasCallPermission && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            if (context.hasPermission(Manifest.permission.READ_PHONE_STATE)) {
                attachPhoneAccountHandle(context, intent, subscriptionInfo)
            }
        }

        return intent
    }

    /**
     * Returns the list of active SIM subscriptions if the READ_PHONE_STATE permission is granted.
     * Returns an empty list otherwise.
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    @Suppress("MissingPermission")
    fun getActiveSims(context: Context): List<SubscriptionInfo> {
        if (!context.hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            return emptyList()
        }

        val sm = ContextCompat.getSystemService(context, SubscriptionManager::class.java)
        return sm?.activeSubscriptionInfoList.orEmpty()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @Suppress("MissingPermission") // READ_PHONE_STATE is checked before calling this method
    private fun attachPhoneAccountHandle(
        context: Context,
        intent: Intent,
        subscriptionInfo: SubscriptionInfo
    ) {
        val telecomManager = ContextCompat.getSystemService(context, TelecomManager::class.java) ?: return
        val targetSubId = subscriptionInfo.subscriptionId.toString()

        runCatching {
            telecomManager.callCapablePhoneAccounts
                ?.firstOrNull { it.id == targetSubId }
                ?.let { handle ->
                    intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                }
        }
    }
}
