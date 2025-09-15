package app.baldphone.neo.contacts;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

public final class ContactsUtils {
    private static final String TAG = "ContactsUtils";

    private ContactsUtils() {}

    @RequiresPermission("android.Manifest.permission.READ_CONTACTS")
    @Nullable
    public static String resolveLookupKey(
            @NonNull ContentResolver resolver,
            @Nullable String cachedLookupUri,
            @Nullable String number,
            @Nullable String name) {

        // 1. Cached URI
        if (!TextUtils.isEmpty(cachedLookupUri)) {
            try {
                Uri cached = Uri.parse(cachedLookupUri);
                Uri freshContactUri = ContactsContract.Contacts.lookupContact(resolver, cached);
                String lookupKey = queryLookupKey(resolver, freshContactUri);
                if (lookupKey != null) {
                    return lookupKey;
                }
            } catch (Exception e) {
                Log.d(TAG, "Failed to resolve cached URI", e);
            }
        }

        // 2. Phone number
        if (!TextUtils.isEmpty(number)) {
            String normalized = PhoneNumberUtils.normalizeNumber(number);
            if (!TextUtils.isEmpty(normalized)) {
                Uri filterUri =
                        Uri.withAppendedPath(
                                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                                Uri.encode(normalized));
                String lookupKey = queryLookupKey(resolver, filterUri);
                if (lookupKey != null) {
                    return lookupKey;
                }
            }
        }

        // 3. Name
        if (!TextUtils.isEmpty(name)) {
            Uri filterUri =
                    Uri.withAppendedPath(
                            ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(name));
            String lookupKey = queryLookupKey(resolver, filterUri);
            if (lookupKey != null) {
                return lookupKey;
            }
        }

        Log.w(TAG, "No lookup key found for the given details.");
        return null;
    }

    @Nullable
    private static String queryLookupKey(
            @NonNull ContentResolver resolver, @Nullable Uri contactUri) {
        if (contactUri == null) {
            return null;
        }

        final String[] projection = new String[] {ContactsContract.Contacts.LOOKUP_KEY};

        try (Cursor c = resolver.query(contactUri, projection, null, null, null)) {
            if (c == null || !c.moveToFirst()) {
                return null;
            }
            int columnIndex = c.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY);
            if (columnIndex == -1) {
                Log.w(TAG, "LOOKUP_KEY column not found in cursor for URI: " + contactUri);
                return null;
            }
            return c.getString(columnIndex);
        } catch (Exception e) {
            Log.e(TAG, "Error querying for lookup key with URI: " + contactUri, e);
        }
        return null;
    }
}
