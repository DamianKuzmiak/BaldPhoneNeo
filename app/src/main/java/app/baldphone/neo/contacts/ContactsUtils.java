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
import androidx.annotation.WorkerThread;

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

    /**
     * Resolve latest lookup key for a contact given an old lookup key.
     *
     * @param contentResolver content resolver to query
     * @param oldLookupKey the previous lookup key (may be stale)
     * @return the fresh lookup key (String) or null if not found / deleted
     */
    @RequiresPermission("android.Manifest.permission.READ_CONTACTS")
    @WorkerThread
    @Nullable
    public static String resolveLatestLookupKey(
            @NonNull ContentResolver contentResolver, @NonNull String oldLookupKey) {
        try {
            Uri lookupUri =
                    Uri.withAppendedPath(
                            ContactsContract.Contacts.CONTENT_LOOKUP_URI, oldLookupKey);

            // The lookupContact method can handle cases where the contact is deleted.
            Uri contactUri = ContactsContract.Contacts.lookupContact(contentResolver, lookupUri);
            if (contactUri == null) {
                Log.i(TAG, "Contact not found for lookup key: " + oldLookupKey);
                return null;
            }

            String freshLookupKey = queryLookupKey(contentResolver, contactUri);
            if (TextUtils.isEmpty(freshLookupKey)) {
                Log.w(TAG, "Fresh lookup key was empty for contact URI: " + contactUri);
                return null;
            }

            return freshLookupKey;

        } catch (SecurityException se) {
            Log.e(TAG, "Missing READ_CONTACTS permission.", se);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "An unexpected error occurred while resolving lookup key.", e);
            return null;
        }
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

    @WorkerThread
    public static int getRawContactId(int contactId, @NonNull ContentResolver resolver) {
        try (final Cursor c =
                resolver.query(
                        ContactsContract.RawContacts.CONTENT_URI,
                        new String[] {ContactsContract.RawContacts._ID},
                        ContactsContract.RawContacts.CONTACT_ID + " = ?",
                        new String[] {String.valueOf(contactId)},
                        null)) {
            if (c != null && c.moveToNext())
                return c.getInt(c.getColumnIndexOrThrow(ContactsContract.RawContacts._ID));
        }
        return -1;
    }
}
