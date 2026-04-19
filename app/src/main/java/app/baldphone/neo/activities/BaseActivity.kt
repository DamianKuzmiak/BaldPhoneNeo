package app.baldphone.neo.activities

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.os.Vibrator

import androidx.activity.addCallback
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity

import java.util.ArrayDeque

import app.baldphone.neo.data.Prefs
import app.baldphone.neo.extensions.applyStatusBarSettings
import app.baldphone.neo.utils.baldAlertDialog

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.utils.D

/**
 * Note: This class coexists with the legacy [com.bald.uriah.baldphone.activities.BaldActivity] to allow for a gradual
 * migration.
 */
abstract class BaseActivity :
    AppCompatActivity(),
    SensorEventListener {
    companion object {
        private const val ACCIDENTAL_TIME = 3_000L
        private const val ACCIDENTAL_TAP_THRESHOLD = 3
        private const val ACCIDENTAL_TAP_LIMIT = 9
    }

    private var accidentalMinTouches = ACCIDENTAL_TAP_THRESHOLD
    private var isAccidentalDialogShowing = false
    private var near = false
    private val recentTouches = ArrayDeque<Long>()

    // Use the existing Prefs and D constants
    private val useAccidentalGuard by lazy { Prefs.useAccidentalGuard }

    private val sensorManager: SensorManager? by lazy {
        if (useAccidentalGuard) getSystemService(SENSOR_SERVICE) as SensorManager else null
    }
    private val proximitySensor: Sensor? by lazy {
        sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    }

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        applyStatusBarSettings()
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this) {
            vibrateOnPress()
            // Handle back press or disable this callback to let system handle it
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    @CallSuper
    override fun onResume() {
        super.onResume()
        if (useAccidentalGuard) {
            proximitySensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    @CallSuper
    override fun onPause() {
        if (useAccidentalGuard) {
            sensorManager?.unregisterListener(this)
        }
        super.onPause()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (!useAccidentalGuard || isAccidentalDialogShowing) return

        if (!near) {
            recentTouches.clear()
            return
        }

        val now = SystemClock.elapsedRealtime()
        recentTouches.addLast(now)

        // Remove touches older than the window
        while (recentTouches.isNotEmpty() && (now - recentTouches.peekFirst()!!) > ACCIDENTAL_TIME) {
            recentTouches.removeFirst()
        }

        if (recentTouches.size > accidentalMinTouches) {
            recentTouches.clear()
            showAccidentalTouchDialog()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        near = event.values[0] == 0f
    }

    override fun onAccuracyChanged(
        p0: Sensor?,
        p1: Int
    ) {
        // No operation
    }

    protected fun vibrateOnPress() {
        if (Prefs.isVibrationFeedbackEnabled) {
            (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.vibrate(D.vibetime.toLong())
        }
    }

    private fun showAccidentalTouchDialog() {
        isAccidentalDialogShowing = true
        baldAlertDialog {
            setTitle(R.string.accidental_touches)
            setMessage(R.string.accidental_touches_subtext)
            setCancelable(false)
            setPositiveButton(R.string.ok) {
                recentTouches.clear()
                accidentalMinTouches = (accidentalMinTouches + 2).coerceAtMost(ACCIDENTAL_TAP_LIMIT)
                isAccidentalDialogShowing = false
            }
        }.show()
    }
}
