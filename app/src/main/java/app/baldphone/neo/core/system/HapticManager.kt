package app.baldphone.neo.core.system

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View

import app.baldphone.neo.data.Prefs

/**
 * Manages haptic feedback throughout the application.
 */
object HapticManager {
    private const val TAG = "HapticManager"

    private var isInitialized = false
    private var hasVibratorHardware: Boolean = false
    private var vibratorService: Vibrator? = null

    private val touchAttributes: VibrationAttributes? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            VibrationAttributes
                .Builder()
                .setUsage(VibrationAttributes.USAGE_TOUCH)
                .build()
        } else {
            null
        }
    }

    /**
     * [HapticFeedbackConstants.CONFIRM] is API 30+
     * [HapticFeedbackConstants.VIRTUAL_KEY] on older platforms
     */
    private val DEFAULT_CONFIRM_CONSTANT: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }

    @JvmStatic
    fun init(context: Context) {
        if (isInitialized) return

        val appContext = context.applicationContext
        vibratorService = VibratorProvider.get(appContext)?.also { service ->
            hasVibratorHardware = service.hasVibrator()
            if (!hasVibratorHardware) {
                Log.w(TAG, "Device reports no vibrator hardware.")
            }
        } ?: run {
            Log.e(TAG, "Failed to retrieve Vibrator service.")
            null
        }

        isInitialized = true
    }

    /**
     * Triggers a standard haptic feedback tap using a View, preferred way.
     * Honors OS handles system settings and user-facing "Touch feedback" toggles.
     */
    @JvmStatic
    @JvmOverloads
    fun performViewHaptic(
        view: View,
        feedbackConstant: Int = DEFAULT_CONFIRM_CONSTANT
    ): Boolean {
        if (!Prefs.isVibrationFeedbackEnabled) return false
        return view.performHapticFeedback(feedbackConstant)
    }

    /**
     * Triggers a quick 'click' vibration directly.
     */
    @JvmStatic
    fun feedbackTap() {
        if (!Prefs.isVibrationFeedbackEnabled || !canVibrateInternal()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            playEffect(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            vibrate(Prefs.vibrationDuration.toLong())
        }
    }

    private fun vibrate(durationMs: Long) {
        if (!Prefs.isVibrationFeedbackEnabled || !canVibrateInternal()) return
        val service = vibratorService ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                playEffect(resolveOneShotEffect(service, durationMs))
            } else {
                @Suppress("DEPRECATION")
                service.vibrate(durationMs)
            }
            Log.d(TAG, "Vibrated for ${durationMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform simple vibration", e)
        }
    }

    /** Executes a vibration effect using the best available API. */
    private fun playEffect(effect: VibrationEffect) {
        val service = vibratorService ?: return
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    touchAttributes?.let { service.vibrate(effect, it) } ?: service.vibrate(effect)
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    service.vibrate(effect)
                }

                else -> {
                    Log.d(TAG, "playEffect skipped: API < 26")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during vibration execution", e)
        }
    }

    /** Picks the best vibration effect (predefined or custom) supported by the device. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun resolveOneShotEffect(service: Vibrator, durationMs: Long): VibrationEffect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isEffectClickSupported(service)) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        }

    private fun isEffectClickSupported(service: Vibrator): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val support = service.areEffectsSupported(VibrationEffect.EFFECT_CLICK)
            return support.isNotEmpty() && support[0] == Vibrator.VIBRATION_EFFECT_SUPPORT_YES
        }
        return true
    }

    private fun canVibrateInternal(): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "HapticManager accessed before initialization!")
            return false
        }
        return vibratorService != null && hasVibratorHardware
    }
}
