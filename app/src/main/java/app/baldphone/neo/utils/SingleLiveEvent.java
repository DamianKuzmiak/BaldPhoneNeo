package app.baldphone.neo.utils;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.util.concurrent.atomic.AtomicBoolean;

/** Simple SingleLiveEvent for one-shot events. */
public class SingleLiveEvent<T> extends MutableLiveData<T> {
    private final AtomicBoolean pending = new AtomicBoolean(false);

    @MainThread
    @Override
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
        // Observe the internal MutableLiveData
        super.observe(
                owner,
                t -> {
                    if (pending.compareAndSet(true, false)) {
                        observer.onChanged(t);
                    }
                });
    }

    @MainThread
    @Override
    public void setValue(@Nullable T t) {
        pending.set(true);
        super.setValue(t);
    }

    /**
     * Convenience for firing an event with no payload.
     */
    @MainThread
    public void call() {
        super.postValue(null);
    }
}
