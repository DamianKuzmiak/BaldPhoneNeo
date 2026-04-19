package app.baldphone.neo.features.calls.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo

import androidx.core.content.ContextCompat

import app.baldphone.neo.utils.PhoneNumberUtils

object CallIntentFactory {
    /**
     * Creates Intent for making a phone call.
     * Uses ACTION_CALL if permission is granted and it's not an emergency number, otherwise falls back to ACTION_DIAL.
     */
    fun createCallIntent(
        context: Context,
        number: CharSequence,
        subscriptionInfo: SubscriptionInfo? = null
    ): Intent {
        val uri = Uri.fromParts("tel", number.toString(), null)

        val hasCallPermission =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED

        val isEmergency = PhoneNumberUtils.isEmergency(context, number.toString())

        val intent =
            if (hasCallPermission && !isEmergency) {
                Intent(Intent.ACTION_CALL).setData(uri)
            } else {
                Intent(Intent.ACTION_DIAL).setData(uri)
            }

        if (subscriptionInfo != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasReadPhoneStatePermission =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED

            if (hasCallPermission && hasReadPhoneStatePermission) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                telecomManager.callCapablePhoneAccounts
                    .firstOrNull { it.id.contains(subscriptionInfo.iccId) }
                    ?.let { intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
            }
        }

        return intent
    }
}
