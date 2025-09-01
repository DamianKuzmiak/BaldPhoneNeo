package app.baldphone.neo.notifications;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class NotificationListenerService
        extends android.service.notification.NotificationListenerService {

    private static final String TAG = NotificationListenerService.class.getSimpleName();
    private static final long DEBOUNCE_DELAY_MS = 250;

    private static WeakReference<NotificationListenerService> instance;
    private final NotificationRepository repo = NotificationRepository.getInstance();

    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateRunnable = this::performRepoUpdate;

    /**
     * Cancels a single notification by its key.
     *
     * @param key The key of the notification to cancel, obtained from a {@link
     *     StatusBarNotification}.
     */
    public static void cancel(String key) {
        synchronized (NotificationListenerService.class) {
            var s = instance != null ? instance.get() : null;
            if (s != null) s.cancelSingle(key);
        }
    }

    /** Requests the dismissal of all active notifications from the status bar. */
    public static void cancelAll() {
        synchronized (NotificationListenerService.class) {
            var s = instance != null ? instance.get() : null;
            if (s != null) s.cancelAllActive();
        }
    }

    /**
     * Finds and cancels a single active notification that is identified as a missed call.
     *
     * <p>This method searches through active notifications for one that is categorized as or
     * contains text indicating a missed call and is clearable (i.e., not an ongoing event). If
     * found, it cancels that specific notification.
     */
    public static void clearMissedCalls() {
        synchronized (NotificationListenerService.class) {
            var s = instance != null ? instance.get() : null;
            if (s != null) {
                String key = s.getIdentifiedMissedCallNotifications();
                if (key != null) {
                    s.cancelSingle(key);
                }
            }
        }
    }

    // For diagnostic screen in Settings → “System Components” view.
    public static boolean isServiceAlive() {
        return instance != null && instance.get() != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "onCreate");

        instance = new WeakReference<>(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");

        debounceHandler.removeCallbacks(updateRunnable); // Clean up pending updates
        repo.update(Collections.emptyList()); // Clear data when listener is removed

        if (instance != null) {
            instance.clear();
            instance = null;
        };
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "System binding.");
        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "System unbinding.");
        return super.onUnbind(intent);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "Listener connected.");

        updateRepository();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.i(TAG, "Listener disconnected.");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        Log.d(TAG, "Notification posted: " + sbn.getKey());
        updateRepository();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        Log.d(TAG, "Notification removed: " + sbn.getKey());
        updateRepository();
    }

    private void cancelSingle(String key) {
        try {
            cancelNotification(key);
        } catch (Exception e) {
            Log.w(TAG, "Failed to cancel notification: " + key, e);
        }
    }

    private void cancelAllActive() {
        try {
            cancelAllNotifications();
        } catch (Exception e) {
            Log.w(TAG, "Failed to cancel all notifications", e);
        }
    }

    /**
     * Schedules a debounced update of the notification repository. Any subsequent calls within the
     * DEBOUNCE_DELAY_MS will reset the timer.
     */
    private void updateRepository() {
        debounceHandler.removeCallbacks(updateRunnable);
        debounceHandler.postDelayed(updateRunnable, DEBOUNCE_DELAY_MS);
    }

    /**
     * Performs the actual update of the repository by fetching all active notifications. This
     * method is called by the debouncing handler.
     */
    private void performRepoUpdate() {
        Log.d(TAG, "Performing debounced repository update.");
        try {
            List<StatusBarNotification> list = Arrays.asList(nullSafeActiveNotifications());
            repo.update(list);
        } catch (Exception e) {
            Log.e(TAG, "Failed to update repository safely", e);
        }
    }

    @NonNull
    private StatusBarNotification[] nullSafeActiveNotifications() {
        try {
            StatusBarNotification[] list = getActiveNotifications();
            return (list != null) ? list : new StatusBarNotification[0];
        } catch (SecurityException se) {
            // Listener permission might be missing/disabled by user
            Log.w(TAG, "Permission missing/disabled: " + se.getMessage());
            return new StatusBarNotification[0];
        }
    }

    @Nullable
    private String getIdentifiedMissedCallNotifications() {
        StatusBarNotification[] activeNotifications;
        try {
            activeNotifications = getActiveNotifications();
        } catch (SecurityException se) {
            Log.w(TAG, "Permission missing/disabled: " + se.getMessage());
            return null;
        }

        if (activeNotifications == null || activeNotifications.length == 0) {
            Log.d(TAG, "getIdentifiedMissedCallNotifications: no active notifications!");
            return null;
        }

        for (StatusBarNotification sbn : activeNotifications) {
            if (sbn == null || sbn.getNotification() == null || sbn.getKey() == null) {
                continue;
            }

            final Notification notification = sbn.getNotification();
            final String notificationKey = sbn.getKey();

            if (isMissedCall(notification, notificationKey) && isClearable(notification)) {
                Log.d(TAG, "Found clearable missed call notification: " + notificationKey);
                return notificationKey;
            }
        }
        return null; // No suitable notification found
    }

    private boolean isMissedCall(Notification notification, String key) {
        if (Notification.CATEGORY_MISSED_CALL.equals(notification.category)) {
            return true;
        }

        // Check key for common identifiers
        String lowerCaseKey = key.toLowerCase(Locale.ROOT);
        if (lowerCaseKey.contains("missedcall") || lowerCaseKey.contains("missed")) {
            return true;
        }

        // Fallback to checking notification text
        Bundle extras = notification.extras != null ? notification.extras : Bundle.EMPTY;
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE, "");
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT, "");
        String fullText =
                ((title != null ? title : "") + " " + (text != null ? text : ""))
                        .toLowerCase(Locale.ROOT);

        return fullText.contains("missed call");
    }

    private boolean isClearable(Notification notification) {
        // A notification is clearable if it does NOT have these flags.
        boolean isNoClear = (notification.flags & Notification.FLAG_NO_CLEAR) != 0;
        boolean isOngoing = (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0;

        boolean clearable = !isNoClear && !isOngoing;
        if (!clearable) {
            Log.i(TAG, "Missed call notification not clearable (flags=" + notification.flags + ")");
        }
        return clearable;
    }
}
