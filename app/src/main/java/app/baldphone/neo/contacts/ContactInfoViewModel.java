package app.baldphone.neo.contacts;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import app.baldphone.neo.utils.SingleLiveEvent;

import com.bald.uriah.baldphone.databases.calls.Call;
import com.bald.uriah.baldphone.databases.calls.CallLogsHelper;
import com.bald.uriah.baldphone.databases.home_screen_pins.HomeScreenPinHelper;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** ViewModel for displaying and managing a single contact's information. */
public class ContactInfoViewModel extends AndroidViewModel {
    private static final String TAG = "ContactInfoViewModel";

    private final ExecutorService executor;
    private final ContentResolver contentResolver;

    private final MutableLiveData<Contact> contactInfo = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isFavorite = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isPinned = new MutableLiveData<>(false);
    private final MutableLiveData<List<Call>> callHistory =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> contactChanged = new MutableLiveData<>(false);
    private final SingleLiveEvent<Void> contactNotFound = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> contactDeleted = new SingleLiveEvent<>();

    private volatile String contactLookupKey;
    private ContentObserver contactObserver;

    public ContactInfoViewModel(@NonNull Application application) {
        super(application);
        this.contentResolver = application.getContentResolver();
        this.executor =
                Executors.newSingleThreadExecutor(
                        r -> {
                            Thread t = new Thread(r, "ContactInfoWorker");
                            t.setDaemon(true);
                            return t;
                        });
    }

    public LiveData<Contact> contactInfoLiveData() {
        return contactInfo;
    }

    public LiveData<Boolean> isFavoriteLiveData() {
        return isFavorite;
    }

    public LiveData<Boolean> isPinnedLiveData() {
        return isPinned;
    }

    public LiveData<List<Call>> callHistoryLiveData() {
        return callHistory;
    }

    public LiveData<Boolean> contactChangedLiveData() {
        return contactChanged;
    }

    public LiveData<Void> contactNotFoundEvent() {
        return contactNotFound;
    }

    public LiveData<Void> contactDeletedEvent() {
        return contactDeleted;
    }

    public void loadContact(@NonNull String lookupKey) {
        if (lookupKey.isEmpty()) return;
        Log.d(TAG, "Loading contact: " + lookupKey);
        this.contactLookupKey = lookupKey;
        executeBackground(() -> loadContactInternal(lookupKey));
    }

    public void reloadContact() {
        Log.d(TAG, "Reloading contact: " + contactLookupKey);
        if (contactLookupKey != null)
            executeBackground(() -> loadContactInternal(contactLookupKey));
    }

    private void loadContactInternal(@NonNull String lookupKey) {
        try {
            Contact contact = Contact.fromLookupKey(lookupKey, contentResolver);

            if (contact == null) {
                String refreshedKey =
                        ContactsUtils.resolveLatestLookupKey(contentResolver, lookupKey);
                if (refreshedKey != null && !refreshedKey.equals(lookupKey))
                    contact = Contact.fromLookupKey(refreshedKey, contentResolver);
                if (contact != null) {
                    contactLookupKey = contact.getLookupKey();
                }
            } else {
                contactLookupKey = contact.getLookupKey();
            }

            if (contact == null) {
                Log.w(TAG, "Contact not found: " + lookupKey);
                contactNotFound.call();
                return;
            }

            registerContactObserver();

            contactInfo.postValue(contact);
            isFavorite.postValue(contact.isStarred());
            isPinned.postValue(HomeScreenPinHelper.isPinned(getApplication(), contactLookupKey));
            callHistory.postValue(
                    List.copyOf(CallLogsHelper.getForSpecificContact(contentResolver, contact)));
        } catch (Exception e) {
            Log.e(TAG, "Error loading contact: " + lookupKey, e);
            contactNotFound.call();
        }
    }

    public void toggleFavorite() {
        if (contactLookupKey == null) return;
        executeBackground(
                () -> {
                    try {
                        boolean newState = Boolean.FALSE.equals(isFavorite.getValue());
                        ContentValues values = new ContentValues();
                        values.put(ContactsContract.Contacts.STARRED, newState ? 1 : 0);

                        int updated =
                                contentResolver.update(
                                        ContactsContract.Contacts.CONTENT_URI,
                                        values,
                                        ContactsContract.Contacts.LOOKUP_KEY + " = ?",
                                        new String[] {contactLookupKey});

                        if (updated > 0) {
                            isFavorite.postValue(newState);
                            markAsChanged();
                        } else {
                            Log.w(TAG, "toggleFavorite: no rows updated");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "toggleFavorite error", e);
                    }
                });
    }

    public void toggleHomeScreenPin() {
        if (contactLookupKey == null) return;
        executeBackground(
                () -> {
                    try {
                        boolean newPinned = Boolean.FALSE.equals(isPinned.getValue());
                        if (newPinned) {
                            HomeScreenPinHelper.pinContact(getApplication(), contactLookupKey);
                        } else {
                            HomeScreenPinHelper.removeContact(getApplication(), contactLookupKey);
                        }
                        isPinned.postValue(newPinned);
                        markAsChanged();
                    } catch (Exception e) {
                        Log.e(TAG, "toggleHomeScreenPin error", e);
                    }
                });
    }

    public void deleteContact() {
        if (contactLookupKey == null) return;
        executeBackground(
                () -> {
                    try {
                        Uri lookupUri =
                                Uri.withAppendedPath(
                                        ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                                        contactLookupKey);
                        int deleted = contentResolver.delete(lookupUri, null, null);
                        if (deleted > 0) {
                            contactLookupKey = null;
                            contactDeleted.call();
                            markAsChanged();
                        } else {
                            Log.w(TAG, "deleteContact: no rows deleted");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "deleteContact error", e);
                    }
                });
    }

    public void markAsChanged() {
        contactChanged.postValue(true);
    }

    private void executeBackground(Runnable task) {
        if (!executor.isShutdown()) executor.execute(task);
    }

    private void registerContactObserver() {
        unregisterContactObserver();

        if (contactLookupKey == null) return;

        contactObserver =
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        Log.v(TAG, "Contact data changed! Reloading.");
                        reloadContact();
                    }
                };

        Uri contactUri =
                Uri.withAppendedPath(
                        ContactsContract.Contacts.CONTENT_LOOKUP_URI, contactLookupKey);
        contentResolver.registerContentObserver(contactUri, true, contactObserver);
        Log.d(TAG, "Registered ContentObserver for URI: " + contactUri);
    }

    private void unregisterContactObserver() {
        if (contactObserver != null) {
            try {
                contentResolver.unregisterContentObserver(contactObserver);
            } catch (Exception ignored) {
            }
            contactObserver = null;
            Log.v(TAG, "Unregistered ContentObserver.");
        }
    }

    @Override
    protected void onCleared() {
        unregisterContactObserver();
        try {
            executor.shutdownNow();
        } catch (Exception e) {
            Log.e(TAG, "Error shutting down executor", e);
        }
        super.onCleared();
    }
}
