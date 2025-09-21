package app.baldphone.neo.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.annotation.NonNull;

public class SignalUtils {
    public static final String SIGNAL_CONTACT_MIMETYPE =
            "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.contact";
    public static final String SIGNAL_PROFILE_URI_PREFIX = "sgnl://signal.me/#p/";

    public static void startSignalConversation(
            @NonNull Context context, @NonNull String phoneNumber) throws Exception {
        if (phoneNumber.isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be null or empty.");
        }

        String region = PhoneUtils.getDeviceRegion(context);
        String formattedNumber = PhoneUtils.formatToE164(phoneNumber, region);

        if (formattedNumber == null) {
            throw new IllegalArgumentException(
                    "Invalid phone number format. Unable to convert to E.164.");
        }

        Uri uri = Uri.parse(SIGNAL_PROFILE_URI_PREFIX + formattedNumber);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PackageManager pm = context.getPackageManager();
        if (intent.resolveActivity(pm) == null) {
            throw new ActivityNotFoundException(
                    "Signal app is not installed or cannot handle chat intent.");
        }

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            throw new ActivityNotFoundException("Unable to start Signal chat activity.");
        } catch (Exception e) {
            throw new Exception(
                    "Unexpected error while starting Signal chat: " + e.getMessage(), e);
        }
    }
}
