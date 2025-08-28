package app.baldphone.neo.battery;

import android.content.*;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Manages the current battery state and provides LiveData updates to UI and background components.
 */
public final class BatteryStateManager {

    public static final String TAG = "BatteryStateManager";
    // Define a threshold for considering battery low, if not using system's EXTRA_BATTERY_LOW - API
    // 27 and older
    private static final int APP_LOW_BATTERY_THRESHOLD = 15;
    private static volatile BatteryStateManager instance;
    private final Context app;
    private final MutableLiveData<BatteryState> state = new MutableLiveData<>();
    private final BatteryManager batteryManager;

    private BroadcastReceiver batteryReceiver;
    private boolean observing = false;

    private BatteryStateManager(Context context) {
        app = context.getApplicationContext();
        batteryManager = ContextCompat.getSystemService(app, BatteryManager.class);
        state.postValue(readCurrentSnapshot());
    }

    public static BatteryStateManager get(Context ctx) {
        if (instance == null) {
            synchronized (BatteryStateManager.class) {
                if (instance == null) {
                    instance = new BatteryStateManager(ctx);
                }
            }
        }
        return instance;
    }

    public LiveData<BatteryState> live() {
        return state;
    }

    /** Starts listening for battery changes. */
    public void startObserving() {
        if (observing) return;
        observing = true;

        if (batteryReceiver == null) {
            batteryReceiver =
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context c, Intent intent) {
                            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                                final BatteryState newState = parseBatteryIntent(intent);
                                if (!newState.equals(state.getValue())) {
                                    state.postValue(newState);
                                    Log.i(TAG, "Battery state posted: " + newState);
                                }
                            }
                        }
                    };
        }

        app.registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    /** Stops listening to battery changes. */
    public void stopObserving() {
        if (!observing) return;
        observing = false;

        try {
            app.unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Receiver already unregistered or not registered: " + e.getMessage());
        }
    }

    /** Returns the most recent battery state. */
    @NonNull
    public BatteryState currentState() {
        BatteryState current = state.getValue();
        if (current == null) {
            Log.w(TAG, "LiveData value null, reading new snapshot.");
            current = readCurrentSnapshot();
            state.postValue(current);
        }
        return current;
    }

    @NonNull
    private BatteryState readCurrentSnapshot() {
        Intent sticky = app.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return parseBatteryIntent(sticky);
    }

    @NonNull
    private BatteryState parseBatteryIntent(Intent intent) {
        if (intent == null) {
            Log.w(TAG, "Null intent received in parseBatteryIntent.");
            return new BatteryState(
                    BatteryState.UNKNOWN_PERCENTAGE, false, false, false, false, -1L);
        }

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int percentage =
                (level > 0 && scale > 0)
                        ? (int) ((level / (float) scale) * 100f)
                        : BatteryState.UNKNOWN_PERCENTAGE;

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING);
        boolean isFull = (status == BatteryManager.BATTERY_STATUS_FULL);

        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        boolean isPlugged = (plugged > 0);

        boolean isLow;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            isLow = intent.getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false);
        } else {
            isLow = (percentage != -1 && percentage <= APP_LOW_BATTERY_THRESHOLD && !isCharging);
        }

        long chargeTimeRemaining = -1L;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && batteryManager != null) {
            chargeTimeRemaining = batteryManager.computeChargeTimeRemaining();
        }

        return new BatteryState(
                percentage, isCharging, isFull, isPlugged, isLow, chargeTimeRemaining);
    }
}
