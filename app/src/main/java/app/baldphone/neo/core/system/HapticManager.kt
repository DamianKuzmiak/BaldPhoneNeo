package app.baldphone.neo.core.system

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

import app.baldphone.neo.data.Prefs

/**
 * Interface to the Android [android.os.Vibrator] service.
 */
object HapticManager {
    private const val TAG = "HapticManager"

    private var vibratorService: Vibrator? = null
    private var hasVibratorHardware: Boolean = false
    private var isInitialized = false

    /**
     * Attributes for haptic feedback (usage touch).
     * This helps the system decide if the vibration should be allowed based on user settings.
     */
    private val hapticAttributes: AudioAttributes by lazy {
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    private val touchAttributes: VibrationAttributes by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            VibrationAttributes
                .Builder()
                .setUsage(VibrationAttributes.USAGE_TOUCH)
                .build()
        } else {
            error("API 30 required")
        }
    }

    fun init(context: Context) {
        if (isInitialized) return

        // No synchronization needed: called strictly during app initialization on the Main Thread.
        val appContext = context.applicationContext
        vibratorService = getVibratorService(appContext)

        vibratorService?.let { service ->
            hasVibratorHardware = service.hasVibrator()
            if (!hasVibratorHardware) {
                Log.w(TAG, "Device does not have vibrator hardware.")
            }
        } ?: run {
            Log.e(TAG, "Vibrator service could not be retrieved.")
        }

        isInitialized = true
    }

    /**
     * Performs a one-shot vibration for the specified duration.
     *
     * @param durationMs Duration of the vibration in milliseconds.
     * @param ignoreSettings If true, bypasses [app.baldphone.neo.data.Prefs.isVibrationFeedbackEnabled].
     */
    @JvmStatic
    @JvmOverloads
    fun vibrate(durationMs: Long, ignoreSettings: Boolean = false) {
        if ((!ignoreSettings && !Prefs.isVibrationFeedbackEnabled) || !canVibrateInternal()) return
        val service = vibratorService ?: return
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    val effect = VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                    service.vibrate(effect, touchAttributes)
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    val effect = VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                    @Suppress("DEPRECATION")
                    service.vibrate(effect, hapticAttributes)
                }

                else -> {
                    @Suppress("DEPRECATION")
                    service.vibrate(durationMs, hapticAttributes)
                }
            }
            Log.d(TAG, "Vibrate for ${durationMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error during vibration", e)
        }
    }

    /**
     * Performs a standard haptic feedback vibration using [Prefs.vibrationDuration].
     * Respects [Prefs.isVibrationFeedbackEnabled].
     */
    fun vibrate() {
        vibrate(Prefs.vibrationDuration.toLong(), ignoreSettings = false)
    }

    /**
     * Performs a predefined haptic effect (API 29+).
     * Falls back to standard vibration on older devices.
     */
    fun performClick() {
        if (!Prefs.isVibrationFeedbackEnabled) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibratorService?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            vibrate()
        }
    }

    private fun getVibratorService(appContext: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun canVibrateInternal(): Boolean {
        val ready = vibratorService != null && hasVibratorHardware
        if (!ready && !isInitialized) {
            Log.e(TAG, "HapticManager accessed before init!")
        }
        return ready
    }
}
