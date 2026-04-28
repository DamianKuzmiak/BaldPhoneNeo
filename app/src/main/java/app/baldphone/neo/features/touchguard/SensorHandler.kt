package app.baldphone.neo.features.touchguard

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * Interacts with the [SensorManager] proximity sensor and reports near/far state changes via a callback.
 */
internal class SensorHandler(
    context: Context,
    private val onNearChanged: (isNear: Boolean) -> Unit
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    var isRegistered: Boolean = false
        private set

    var isNear: Boolean = false
        private set

    fun start() {
        if (isRegistered) return
        proximitySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            isRegistered = true
            Log.d(TAG, "Proximity sensor started")
        }
    }

    fun stop() {
        if (!isRegistered) return
        sensorManager?.unregisterListener(this)
        isRegistered = false
        isNear = false
        Log.d(TAG, "Proximity sensor stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_PROXIMITY) return

        val near = event.values[0] < event.sensor.maximumRange
        if (near != isNear) {
            isNear = near
            onNearChanged(near)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val TAG = "ProximitySensorHandler"
    }
}
