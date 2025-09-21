package app.baldphone.neo.utils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public final class WhatsappUtils {

    public static final String WHATSAPP_PACKAGE_NAME = "com.whatsapp";
    public static final String WHATSAPP_CONVERSATION_ACTIVITY = "com.whatsapp.Conversation";
    public static final String WHATSAPP_LAUNCH_ACTIVITY = "com.whatsapp.Main";
    public static final String WHATSAPP_JID_SUFFIX = "@s.whatsapp.net";
    public static final String WHATSAPP_PROFILE_MIMETYPE =
            "vnd.android.cursor.item/vnd.com.whatsapp.profile";
    public static final ComponentName WHATSAPP_COMPONENT_NAME =
            new ComponentName(WHATSAPP_PACKAGE_NAME, WHATSAPP_LAUNCH_ACTIVITY);

    private static final String TAG = WhatsappUtils.class.getSimpleName();
    public static final String WHATSAPP_SEND_API_URL = "https://api.whatsapp.com/send?phone=";

    private WhatsappUtils() {}

    @Nullable
    public static String buildWhatsappJid(@Nullable String number) {
        if (TextUtils.isEmpty(number)) return null;

        String stripped = PhoneNumberUtils.stripSeparators(number).replaceAll("[^0-9]", "");
        return stripped.isEmpty() ? null : stripped + WHATSAPP_JID_SUFFIX;
    }

    /**
     * Extracts the phone number from a WhatsApp JID and formats it for display.
     * This is the reverse of {@link #buildWhatsappJid(String)}.
     * For example, "1234567890@s.whatsapp.net" becomes a formatted number like "+1 (234) 567-890".
     *
     * @param jid The WhatsApp JID string.
     * @return The formatted phone number, or null if the input is not a valid JID.
     */
    @Nullable
    public static String getPhoneNumberFromJid(@Nullable String jid) {
        if (jid == null || !jid.endsWith(WHATSAPP_JID_SUFFIX)) {
            return null;
        }
        String phoneNumber = jid.substring(0, jid.length() - WHATSAPP_JID_SUFFIX.length());
        if (TextUtils.isEmpty(phoneNumber)) {
            return phoneNumber;
        }

        // Prepending '+' treats it as an international number for formatting.
        String internationalNumber = "+" + phoneNumber;

        // The formatNumber method correctly handles numbers with a '+' prefix,
        // displaying them in a standard international format.
        // The country ISO is ignored when the number starts with '+', so we can pass null.
        return PhoneNumberUtils.formatNumber(internationalNumber, null);
    }


    public static void startWhatsAppChat(@NonNull Context context, @NonNull String jid) {
        Intent intent = new Intent()
                .setComponent(new ComponentName(WHATSAPP_PACKAGE_NAME, WHATSAPP_CONVERSATION_ACTIVITY))
                .putExtra("jid", jid);

        launchWhatsAppIntent(context, intent, "Unable to start WhatsApp chat");
    }

    public static void startWhatsAppVoiceCall(@NonNull Context context, @NonNull String jid) {
        // There is no public API for initiating a WhatsApp voice call directly with a JID.
        // The most stable approach is to open the chat screen, where the user can
        // easily initiate the call themselves.
        Log.i(TAG, "Opening WhatsApp chat for user to initiate voice call with JID: " + jid);
        startWhatsAppChat(context, jid);
    }

    public static void openPhoneCallAppLink(@NonNull Context context, @NonNull Long dataId) {
        Intent intent = new Intent(Intent.ACTION_VIEW);

        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        intent.setDataAndType(Uri.parse("content://com.android.contacts/data/" + dataId),
                "vnd.android.cursor.item/vnd.com.whatsapp.voip.call");

        intent.setPackage("com.whatsapp");
        Log.d(TAG, "openPhoneCallAppLink: intent=" + intent);
        Log.d(TAG, "openPhoneCallAppLink: package=" + context.getPackageName());
        Log.d(TAG, "openPhoneCallAppLink: data=" + intent.getData());

        if (intent.resolveActivity(context.getPackageManager()) != null) {
            Log.d(TAG, "openPhoneCallAppLink: starting intent");
            context.startActivity(intent);
        } else {
            throw new IllegalStateException("No app resolved to open whatsapp link.");
        }
    }

    public static void openPlayStoreForWhatsApp(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();

        Intent playStoreIntent =
                new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=" + WHATSAPP_PACKAGE_NAME));
        playStoreIntent.setPackage("com.android.vending");

        if (resolveAndStart(context, pm, playStoreIntent)) return;

        Intent webIntent =
                new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                                "https://play.google.com/store/apps/details?id="
                                        + WHATSAPP_PACKAGE_NAME));

        if (!resolveAndStart(context, pm, webIntent)) {
            throw new IllegalStateException(
                    "Unable to open WhatsApp in the Play Store or browser.");
        }
    }

    // ---------- Internal Helpers ----------

    private static void launchWhatsAppIntent(Context context, Intent intent, String errorMessage) {
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        PackageManager pm = context.getPackageManager();
        if (intent.resolveActivity(pm) == null) {
            throw new IllegalStateException(
                    "WhatsApp not installed or activity cannot be resolved.");
        }

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "WhatsApp activity not found", e);
            throw new IllegalStateException("WhatsApp not found or incompatible version.", e);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while starting WhatsApp", e);
            throw new SecurityException("Permission denied while starting WhatsApp.", e);
        } catch (Exception e) {
            Log.e(TAG, errorMessage, e);
            throw new IllegalStateException(errorMessage, e);
        }
    }

    private static boolean resolveAndStart(Context context, PackageManager pm, Intent intent) {
        if (intent.resolveActivity(pm) != null) {
            if (!(context instanceof android.app.Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            return true;
        }
        return false;
    }
}
