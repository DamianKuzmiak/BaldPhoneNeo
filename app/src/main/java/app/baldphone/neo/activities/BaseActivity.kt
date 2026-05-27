package app.baldphone.neo.activities

import android.os.Bundle
import android.os.Vibrator

import androidx.activity.addCallback
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity

import app.baldphone.neo.data.Prefs

import com.bald.uriah.baldphone.utils.D

/**
 * Note: This class coexists with the legacy [com.bald.uriah.baldphone.activities.BaldActivity] to allow for a gradual
 * migration.
 */
abstract class BaseActivity : AppCompatActivity() {
    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this) {
            vibrateOnPress()
            // Handle back press or disable this callback to let system handle it
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    protected fun vibrateOnPress() {
        if (Prefs.isVibrationFeedbackEnabled) {
            (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.vibrate(D.vibetime.toLong())
        }
    }
}
