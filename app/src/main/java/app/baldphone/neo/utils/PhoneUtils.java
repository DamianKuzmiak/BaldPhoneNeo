package app.baldphone.neo.utils;

import android.content.Context;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.util.Locale;

public class PhoneUtils {
    private static final String TAG = "PhoneUtils";

    // Lazily-initialized instance for efficiency.
    private static final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    /**
     * Attempts to determine the user's current country region.
     *
     * <p>It checks for the region in the following order:
     *
     * <ol>
     *   <li>Network country ISO from TelephonyManager.
     *   <li>SIM country ISO from TelephonyManager.
     *   <li>Device's primary locale.
     * </ol>
     *
     * @param context The application context.
     * @return A two-letter uppercase country code (ISO 3166-1 alpha-2), e.g., "US".
     */
    @NonNull
    public static String getDeviceRegion(@NonNull Context context) {
        TelephonyManager tm =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            String region = tm.getNetworkCountryIso();
            if (region != null && !region.isEmpty()) {
                return region.toUpperCase(Locale.US);
            }

            // Fallback to SIM country ISO
            region = tm.getSimCountryIso();
            if (region != null && !region.isEmpty()) {
                return region.toUpperCase(Locale.US);
            }
        }

        // 2. Fallback to device locale
        Locale primaryLocale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            primaryLocale = context.getResources().getConfiguration().getLocales().get(0);
        } else {
            //noinspection deprecation
            primaryLocale = context.getResources().getConfiguration().locale;
        }

        if (primaryLocale != null) {
            String country = primaryLocale.getCountry();
            if (!country.isEmpty()) {
                return country.toUpperCase(Locale.US);
            }
        }

        // 3. Final fallback to a default region (e.g., "US")
        return "US";
    }

    /**
     * Formats a phone number to the E.164 standard.
     *
     * <p>E.164 format includes a country code prefixed with '+' and no separators (e.g.,
     * +12125552368).
     *
     * @param number The phone number to format.
     * @param region The two-letter ISO country code (e.g., "US") for parsing context. See {@link
     *     #getDeviceRegion(Context)}.
     * @return The E.164 formatted number, or {@code null} if parsing or validation fails.
     */
    @Nullable
    public static String formatToE164(@NonNull String number, @NonNull String region) {
        try {
            Phonenumber.PhoneNumber parsed = phoneNumberUtil.parse(number, region);
            if (phoneNumberUtil.isValidNumber(parsed)) {
                return phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
            }
        } catch (NumberParseException e) {
            Log.w(TAG, "Could not parse number for region '" + region + "'", e);
        }
        return null;
    }

    /**
     * Formats a phone number for display in a standardized, human-readable international format.
     *
     * <p>This method uses {@code libphonenumber} to format the number into the {@code
     * INTERNATIONAL} format (e.g., "+1 201 555 0123"), replacing hyphens with spaces.
     *
     * @param number The raw phone number string to be formatted.
     * @param region The two-letter ISO country code for parsing context (e.g., "US").
     * @return A formatted, human-readable phone number, or the original if formatting fails.
     */
    @NonNull
    public static String formatForDisplay(@NonNull String number, @NonNull String region) {
        try {
            Phonenumber.PhoneNumber parsed = phoneNumberUtil.parse(number, region);
            if (phoneNumberUtil.isValidNumber(parsed)) {
                String formatted =
                        phoneNumberUtil.format(
                                parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
                return formatted.replace('-', ' ');
            }
        } catch (NumberParseException ignored) {
        }
        return number; // fallback: return original input
    }
}
