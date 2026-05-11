package app.baldphone.neo.core.system

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Single point for obtaining the system [Vibrator], handling the API 31+ and the legacy Vibrator Service.
 */
object VibratorProvider {
    fun get(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
