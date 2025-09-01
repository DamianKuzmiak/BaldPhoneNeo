package app.baldphone.neo.notifications;

import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.*;
import java.util.stream.Collectors;

public final class NotificationRepository {
    private static final NotificationRepository INSTANCE = new NotificationRepository();

    private final MutableLiveData<List<StatusBarNotification>> notifications =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> count = new MutableLiveData<>(0);
    private final MutableLiveData<Set<String>> packages =
            new MutableLiveData<>(Collections.emptySet());

    private NotificationRepository() {}

    public static NotificationRepository getInstance() {
        return INSTANCE;
    }

    /**
     * Updates the repository with a new list of active notifications.
     * This method updates the LiveData for the full notification list, the total count,
     * and the set of unique package names from which the notifications originated.
     *
     * @param list The complete list of current {@link StatusBarNotification}s.
     */
    public void update(List<StatusBarNotification> list) {
        Log.d("NotificationRepository", "update: " + list);
        notifications.postValue(list);
        count.postValue(list.size());
        packages.postValue(
                list.stream()
                        .map(StatusBarNotification::getPackageName)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    public LiveData<List<StatusBarNotification>> getNotifications() {
        return notifications;
    }

    public LiveData<Integer> getCount() {
        return count;
    }

    public LiveData<Set<String>> getPackages() {
        return packages;
    }

    public void cancelNotification(String key) {
        NotificationListenerService.cancel(key);
    }

    public void cancelAll() {
        NotificationListenerService.cancelAll();
    }
}
