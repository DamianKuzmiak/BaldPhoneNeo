package app.baldphone.neo.flashlight.drivers

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

import androidx.annotation.RequiresApi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

import app.baldphone.neo.flashlight.FlashlightError
import app.baldphone.neo.flashlight.FlashlightState

@RequiresApi(Build.VERSION_CODES.M)
internal class TorchApi23Driver(
    appContext: Context,
    private val scope: CoroutineScope,
    private val notify: (FlashlightState) -> Unit
) : FlashlightDriver {
    private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    // Fetch the ID once and store it. Deferred is still used to handle the async initialization.
    private val torchCameraIdDeferred =
        scope.async(Dispatchers.Default) {
            findFlashCameraId()
        }

    @Volatile
    private var torchState: Boolean? = null
    private var torchCallback: CameraManager.TorchCallback? = null

    init {
        registerCallback()
    }

    private fun registerCallback() {
        if (torchCallback != null) return

        val cb =
            object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    Log.v(TAG, "onTorchModeChanged called: cameraId=$cameraId, enabled=$enabled")
                    scope.launch {
                        val targetId = torchCameraIdDeferred.await()
                        if (targetId == null) {
                            Log.w(TAG, "onTorchModeChanged: targetId is null, cannot match")
                            return@launch
                        }
                        if (cameraId == targetId) {
                            Log.d(TAG, "Torch state updated for $cameraId: $enabled")
                            torchState = enabled
                            notify(FlashlightState.OnOff(enabled))
                        }
                    }
                }

                override fun onTorchModeUnavailable(cameraId: String) {
                    Log.w(TAG, "onTorchModeUnavailable called: cameraId=$cameraId")
                    scope.launch {
                        if (cameraId == torchCameraIdDeferred.await()) {
                            torchState = false
                            notify(FlashlightState.Error(FlashlightError.CAMERA_ERROR, "Torch unavailable"))
                        }
                    }
                }
            }

        torchCallback = cb
        cameraManager.registerTorchCallback(cb, Handler(Looper.getMainLooper()))
    }

    override fun setTorch(enabled: Boolean) {
        Log.d(TAG, "setTorch: $enabled")
        scope.launch {
            try {
                val id = torchCameraIdDeferred.await()
                if (id != null) {
                    Log.v(TAG, "Calling setTorchMode for $id: $enabled")
                    cameraManager.setTorchMode(id, enabled)
                } else {
                    Log.e(TAG, "Torch camera not found (id is null)")
                    notify(FlashlightState.Error(FlashlightError.NO_HARDWARE))
                }
            } catch (e: CameraAccessException) {
                Log.e(TAG, "CameraAccessException in setTorch", e)
                notify(FlashlightState.Error(FlashlightError.CAMERA_ERROR, e.message))
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "IllegalArgumentException in setTorch", e)
                notify(FlashlightState.Error(FlashlightError.CAMERA_ERROR, e.message))
            } catch (e: Exception) {
                Log.e(TAG, "Unknown exception in setTorch", e)
                notify(FlashlightState.Error(FlashlightError.CAMERA_ERROR, e.message))
            }
        }
    }

    override fun onDestroy() {
        torchCallback?.let { cameraManager.unregisterTorchCallback(it) }
        torchCallback = null
    }

    private fun findFlashCameraId(): String? =
        try {
            val ids = cameraManager.cameraIdList
            val flashCameras =
                ids
                    .map { id ->
                        id to cameraManager.getCameraCharacteristics(id)
                    }.filter { (_, chars) ->
                        chars[CameraCharacteristics.FLASH_INFO_AVAILABLE] == true
                    }

            if (flashCameras.isEmpty()) return null

            // Prioritize back-facing camera, otherwise the first available with flash
            flashCameras
                .firstOrNull { (_, chars) ->
                    chars[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_BACK
                }?.first ?: flashCameras.firstOrNull()?.first
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Could not query camera", e)
            null
        }

    override fun getTorchState(): Boolean? = torchState

    companion object {
        private const val TAG = "TorchApi23Driver"
    }
}
