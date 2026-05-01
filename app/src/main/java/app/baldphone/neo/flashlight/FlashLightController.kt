package app.baldphone.neo.flashlight

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import app.baldphone.neo.flashlight.drivers.FlashlightDriver
import app.baldphone.neo.flashlight.drivers.LegacyApi21Driver
import app.baldphone.neo.flashlight.drivers.TorchApi23Driver

/**
 * A singleton controller for managing the device's camera flashlight.
 */
class FlashLightController private constructor(appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<FlashlightState>(FlashlightState.Idle)
    val state: StateFlow<FlashlightState> = _state.asStateFlow()

    /** Reactive bridge for Java consumers. */
    val stateLiveData: LiveData<FlashlightState> = state.asLiveData()

    private val driver: FlashlightDriver by lazy {
        Log.d(TAG, "Initializing driver.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            TorchApi23Driver(appContext, scope, ::handleDriverEvent)
        } else {
            LegacyApi21Driver(scope, ::handleDriverEvent)
        }
    }

    /** Turns the flashlight ON. */
    fun turnOn() {
        driver.setTorch(true)
    }

    /** Turns the flashlight OFF. */
    fun turnOff() {
        driver.setTorch(false)
    }

    /** Toggles the flashlight, defaulting to ON if state is unknown. */
    fun toggle() {
        Log.v(TAG, "Toggling flashlight")
        if (driver.getTorchState() == true) {
            turnOff()
        } else {
            turnOn()
        }
    }

    /** Releases driver resources. */
    fun onDestroy() {
        driver.onDestroy()
    }

    private fun handleDriverEvent(event: FlashlightState) {
        Log.d(TAG, "Driver event received: $event")
        _state.value = event
    }

    companion object {
        private const val TAG = "FlashLightController"

        @Volatile
        private var instance: FlashLightController? = null

        fun getInstance(context: Context): FlashLightController =
            instance ?: synchronized(this) {
                instance ?: FlashLightController(context.applicationContext).also { instance = it }
            }

        /** Returns true if the device has flashlight hardware. */
        fun isFlashHardwarePresent(context: Context): Boolean =
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }
}
