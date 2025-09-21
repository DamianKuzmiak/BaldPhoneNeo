package app.baldphone.neo.contacts;

import static app.baldphone.neo.utils.SignalUtils.SIGNAL_CONTACT_MIMETYPE;
import static app.baldphone.neo.utils.WhatsappUtils.WHATSAPP_PROFILE_MIMETYPE;

import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.*;

/**
 * Represents a full contact with all related data (phones, emails, etc.). Provides static factory
 * methods for loading contact instances directly from a {@link ContentResolver}.
 */
public final class Contact {

    private static final String SELECTION_DETAILS_BY_CONTACT_ID =
            ContactsContract.Data.CONTACT_ID
                    + " = ? AND "
                    + ContactsContract.Data.MIMETYPE
                    + " IN (?, ?, ?, ?, ?, ?)";

    private final int id;
    @NonNull private final String lookupKey;
    @NonNull private final String name;
    @Nullable private final String photo;
    private final boolean isStarred;
    @Nullable private final String note;
    @NonNull private final List<TaggedData> phones;
    @NonNull private final List<TaggedData> emails;
    @NonNull private final List<TaggedData> addresses;
    @NonNull private final List<String> whatsappNumbers;
    @NonNull private final List<String> signalNumbers;

    private Contact(
            int id,
            @NonNull String lookupKey,
            @NonNull String name,
            @Nullable String photo,
            boolean isStarred,
            @Nullable String note,
            @NonNull List<TaggedData> phones,
            @NonNull List<TaggedData> emails,
            @NonNull List<TaggedData> addresses,
            @NonNull List<String> whatsappNumbers,
            @NonNull List<String> signalNumbers) {

        this.id = id;
        this.lookupKey = lookupKey;
        this.name = name;
        this.photo = photo;
        this.isStarred = isStarred;
        this.note = note;
        this.phones = List.copyOf(phones);
        this.emails = List.copyOf(emails);
        this.addresses = List.copyOf(addresses);
        this.whatsappNumbers = List.copyOf(whatsappNumbers);
        this.signalNumbers = List.copyOf(signalNumbers);
    }

    // -------------------------------------------------------------------------
    // Factory Methods
    // -------------------------------------------------------------------------

    @WorkerThread
    @Nullable
    public static Contact fromId(@NonNull String id, @NonNull ContentResolver resolver) {
        return query(ContactsContract.Contacts._ID + "=?", new String[] {id}, resolver);
    }

    @WorkerThread
    @Nullable
    public static Contact fromLookupKey(@NonNull String key, @NonNull ContentResolver resolver) {
        return query(ContactsContract.Contacts.LOOKUP_KEY + "=?", new String[] {key}, resolver);
    }

    // -------------------------------------------------------------------------
    // Private Loader
    // -------------------------------------------------------------------------

    @WorkerThread
    @Nullable
    private static Contact query(String selection, String[] args, ContentResolver resolver) {
        try (Cursor cursor =
                resolver.query(
                        ContactsContract.Contacts.CONTENT_URI,
                        new String[] {
                            ContactsContract.Contacts._ID,
                            ContactsContract.Contacts.LOOKUP_KEY,
                            ContactsContract.Contacts.DISPLAY_NAME,
                            ContactsContract.Contacts.PHOTO_URI,
                            ContactsContract.Contacts.STARRED
                        },
                        selection,
                        args,
                        null)) {

            if (cursor == null || !cursor.moveToFirst()) return null;

            int id = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
            String lookupKey =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY));
            String name =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
            String photo =
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI));
            boolean favorite =
                    cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED))
                            == 1;

            return loadDetails(id, lookupKey, name, photo, favorite, resolver);
        }
    }

    @WorkerThread
    @NonNull
    private static Contact loadDetails(
            int id,
            @NonNull String lookupKey,
            @NonNull String name,
            @Nullable String photo,
            boolean favorite,
            @NonNull ContentResolver resolver) {

        List<TaggedData> phones = new ArrayList<>();
        List<TaggedData> emails = new ArrayList<>();
        List<TaggedData> addresses = new ArrayList<>();
        Set<String> whatsapp = new HashSet<>();
        Set<String> signal = new HashSet<>();
        String note = null;

        String[] selectionArgs = {
            String.valueOf(id),
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
            WHATSAPP_PROFILE_MIMETYPE,
            SIGNAL_CONTACT_MIMETYPE,
            ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
        };

        try (Cursor data =
                resolver.query(
                        ContactsContract.Data.CONTENT_URI,
                        new String[] {
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.Data.DATA1, // value
                            ContactsContract.Data.DATA2 // type
                        },
                        SELECTION_DETAILS_BY_CONTACT_ID,
                        selectionArgs,
                        null)) {

            if (data != null) {
                final int mimeIdx = data.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE);
                final int valueIdx = data.getColumnIndexOrThrow(ContactsContract.Data.DATA1);
                final int typeIdx = data.getColumnIndexOrThrow(ContactsContract.Data.DATA2);

                while (data.moveToNext()) {
                    String mime = data.getString(mimeIdx);
                    String value = data.getString(valueIdx);
                    int type = data.getInt(typeIdx);
                    if (TextUtils.isEmpty(value)) continue;

                    switch (mime) {
                        case ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
                                phones.add(new TaggedData(type, value));
                        case ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE ->
                                emails.add(new TaggedData(type, value));
                        case ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE ->
                                addresses.add(new TaggedData(type, value));
                        case WHATSAPP_PROFILE_MIMETYPE -> whatsapp.add(value);
                        case SIGNAL_CONTACT_MIMETYPE -> signal.add(value);
                        case ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE ->
                                note = value;
                        default -> {
                            // Ignore
                        }
                    }
                }
            }
        }

        return new Contact(
                id,
                lookupKey,
                name,
                photo,
                favorite,
                note,
                phones,
                emails,
                addresses,
                new ArrayList<>(whatsapp),
                new ArrayList<>(signal));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int getId() {
        return id;
    }

    @NonNull
    public String getLookupKey() {
        return lookupKey;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @Nullable
    public String getPhoto() {
        return photo;
    }

    public boolean isStarred() {
        return isStarred;
    }

    public boolean hasPhoto() {
        return !TextUtils.isEmpty(photo);
    }

    public boolean hasPhone() {
        return !phones.isEmpty();
    }

    public boolean hasMail() {
        return !emails.isEmpty();
    }

    public boolean hasAddress() {
        return !addresses.isEmpty();
    }

    public boolean hasWhatsapp() {
        return !whatsappNumbers.isEmpty();
    }

    public boolean hasSignal() {
        return !signalNumbers.isEmpty();
    }

    public boolean hasNote() {
        return !TextUtils.isEmpty(note);
    }

    @NonNull
    public List<TaggedData> getPhones() {
        return phones;
    }

    @NonNull
    public List<TaggedData> getEmails() {
        return emails;
    }

    @NonNull
    public List<TaggedData> getAddresses() {
        return addresses;
    }

    @NonNull
    public List<String> getWhatsappNumbers() {
        return whatsappNumbers;
    }

    @NonNull
    public List<String> getSignalNumbers() {
        return signalNumbers;
    }

    @Nullable
    public String getNote() {
        return note;
    }

    @Nullable
    public String getMobilePhone() {
        return phones.stream()
                .filter(p -> p.type() == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .map(TaggedData::value)
                .findFirst()
                .orElse(null);
    }

    @Nullable
    public String getHomePhone() {
        return phones.stream()
                .filter(p -> p.type() == ContactsContract.CommonDataKinds.Phone.TYPE_HOME)
                .map(TaggedData::value)
                .findFirst()
                .orElse(null);
    }

    @Nullable
    public String getFirstAddress() {
        return addresses.isEmpty() ? null : addresses.get(0).value();
    }

    @Nullable
    public String getPrimaryEmail() {
        return emails.isEmpty() ? null : emails.get(0).value();
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Contact other)) return false;
        return id == other.id && lookupKey.equals(other.lookupKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, lookupKey);
    }

    @NonNull
    @Override
    public String toString() {
        return name + " (" + id + ")";
    }

    public record TaggedData(int type, @NonNull String value) {}

    public static class ContactNotFoundException extends Exception {
        ContactNotFoundException() {
            super("Contact not found");
        }
    }
}
