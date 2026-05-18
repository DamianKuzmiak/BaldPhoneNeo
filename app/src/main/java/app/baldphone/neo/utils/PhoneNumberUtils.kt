package app.baldphone.neo.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log

import androidx.core.content.ContextCompat

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

object PhoneNumberUtils {
    private const val TAG = "PhoneNumberUtils"

    private val phoneNumberUtil = PhoneNumberUtil.getInstance()

    /**
     * Checks if the given phone number is an emergency number.
     */
    fun isEmergency(context: Context, number: String): Boolean {
        val isEmergency =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                tm.isEmergencyNumber(number)
            } else {
                @Suppress("DEPRECATION")
                android.telephony.PhoneNumberUtils.isEmergencyNumber(number)
            }
        Log.v(TAG, "isEmergency: $isEmergency")
        return isEmergency
    }

    /**
     * Retrieves the primary emergency number for the current country/network.
     */
    fun getPrimaryEmergencyNumber(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val emergencyNumbers = telephony.emergencyNumberList
                val primary = emergencyNumbers.values.flatten().firstOrNull()
                if (primary != null) return primary.number
            }
        }

        // Universal fallback
        return "112"
    }

    /**
     * Formats a phone number to the E.164 standard.
     *
     * E.164 format includes a country code prefixed with '+' and no separators (e.g., +12125552368).
     */
    fun formatToE164(number: String, region: String): String? =
        try {
            val parsed = phoneNumberUtil.parse(number, region)
            if (phoneNumberUtil.isValidNumber(parsed)) {
                phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            } else {
                null
            }
        } catch (e: NumberParseException) {
            Log.w(TAG, "Could not parse number for region '$region'", e)
            null
        }

    /**
     * Formats a phone number for display (INTERNATIONAL format).
     * Returns the raw number if parsing fails or if the number is invalid.
     */
    fun formatForDisplay(number: String, region: String): String =
        try {
            val parsed = phoneNumberUtil.parse(number, region)
            if (phoneNumberUtil.isValidNumber(parsed)) {
                phoneNumberUtil
                    .format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
                    .replace('-', ' ')
            } else {
                number
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse number for display: $number", e)
            number
        }

    /**
     * Formats a phone number using AsYouTypeFormatter for the given region.
     * Useful for dialer input display.
     */
    fun formatAsYouType(number: String, region: String): String {
        if (number.isEmpty()) return ""
        val formatter = phoneNumberUtil.getAsYouTypeFormatter(region)
        var formatted = ""
        number.forEach { digit ->
            formatted = formatter.inputDigit(digit)
        }
        return formatted.ifEmpty { number }
    }

    /**
     * Formats a phone number based on its prefix:
     * - INTERNATIONAL format if it starts with "+" or "00".
     * - NATIONAL format otherwise.
     * Returns the raw input if parsing fails.
     */
    fun formatSmartly(rawInput: String, region: String): String {
        try {
            val parsed = phoneNumberUtil.parse(rawInput, region)
            return if (rawInput.startsWith("+") || rawInput.startsWith("00")) {
                phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
            } else {
                phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
            }
        } catch (e: NumberParseException) {
            Log.w(TAG, "Failed to parse phone number '$rawInput': ${e.message}")
            return rawInput
        }
    }
}
