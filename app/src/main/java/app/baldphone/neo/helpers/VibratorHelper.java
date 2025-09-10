package app.baldphone.neo.helpers;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * This class provides a simplified interface to the Android {@link Vibrator} service. Implemented
 * as a singleton to ensure a single point of control for vibrations.
 */
public class VibratorHelper {

    private static final String TAG = "VibratorHelper";
    private static volatile VibratorHelper sInstance;

    private final Vibrator vibratorService;
    private final boolean hasVibratorHardware;

    private VibratorHelper(Context context) {
        this.vibratorService = getVibratorService(context);

        if (this.vibratorService == null) {
            Log.e(TAG, "Vibrator service could not be retrieved.");
            this.hasVibratorHardware = false;
        } else {
            this.hasVibratorHardware = this.vibratorService.hasVibrator();
            if (!this.hasVibratorHardware) {
                Log.w(TAG, "Device does not have vibrator hardware.");
            }
        }
    }

    /**
     * Initializes the VibratorHelper singleton. Should be called once, preferably in the
     * Application's onCreate method.
     *
     * @param context Application context is preferred.
     */
    public static void init(@NonNull Context context) {
        if (sInstance == null) {
            synchronized (VibratorHelper.class) {
                if (sInstance == null) {
                    sInstance = new VibratorHelper(context);
                }
            }
        }
    }

    /**
     * Performs a one-shot vibration for the specified duration. Call VibratorHelper.init(context)
     * once before using this method.
     *
     * @param durationMs Duration of the vibration in milliseconds. Must be positive.
     */
    public static void vibrate(long durationMs) {
        VibratorHelper instance = getInstance();
        if (!instance.canVibrateInternal()) {
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect =
                        VibrationEffect.createOneShot(
                                durationMs, VibrationEffect.DEFAULT_AMPLITUDE);
                instance.vibratorService.vibrate(effect);
            } else {
                instance.vibratorService.vibrate(durationMs);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during vibration", e);
        }
    }

    /**
     * Cancels any ongoing vibration. Call VibratorHelper.init(context) once before using this
     * method.
     */
    public static void cancel() {
        VibratorHelper instance = getInstance();
        if (instance.vibratorService != null) {
            try {
                instance.vibratorService.cancel();
            } catch (Exception e) {
                Log.e(TAG, "Error cancelling vibration", e);
            }
        }
    }

    private static VibratorHelper getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException(
                    "VibratorHelper not initialized. Call VibratorHelper.init(context)");
        }
        return sInstance;
    }

    private Vibrator getVibratorService(Context context) {
        Context appContext = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager =
                    (VibratorManager) appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vibratorManager != null ? vibratorManager.getDefaultVibrator() : null;
        } else {
            return (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    private boolean canVibrateInternal() {
        return this.vibratorService != null && this.hasVibratorHardware;
    }
}
