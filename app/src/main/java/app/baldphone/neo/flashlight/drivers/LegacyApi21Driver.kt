@file:Suppress("DEPRECATION")

package app.baldphone.neo.flashlight.drivers

import android.graphics.SurfaceTexture
import android.hardware.Camera

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import app.baldphone.neo.flashlight.FlashlightError
import app.baldphone.neo.flashlight.FlashlightState

internal class LegacyApi21Driver(
    private val scope: CoroutineScope,
    private val sendEvent: (FlashlightState) -> Unit
) : FlashlightDriver {
    private var camera: Camera? = null
    private var dummyTexture: SurfaceTexture? = null
    private var torchState: Boolean? = null

    override fun onDestroy() {
        safelySetTorch(false)
        releaseCamera()
    }

    override fun setTorch(enabled: Boolean) {
        safelySetTorch(enabled)
    }

    override fun getTorchState(): Boolean? = torchState

    private fun safelySetTorch(on: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                if (on) {
                    if (camera == null) {
                        camera = Camera.open()
                        if (dummyTexture == null) dummyTexture = SurfaceTexture(0)
                        camera?.setPreviewTexture(dummyTexture)
                        camera?.startPreview()
                    }
                    camera?.let {
                        val params = it.parameters
                        params.flashMode = Camera.Parameters.FLASH_MODE_TORCH
                        it.parameters = params
                    }
                    torchState = true
                    withContext(Dispatchers.Main.immediate) {
                        sendEvent(FlashlightState.OnOff(true))
                    }
                } else {
                    camera?.let {
                        val params = it.parameters
                        params.flashMode = Camera.Parameters.FLASH_MODE_OFF
                        it.parameters = params
                        try {
                            it.stopPreview()
                        } catch (_: Throwable) {
                        }
                    }
                    torchState = false
                    withContext(Dispatchers.Main.immediate) {
                        sendEvent(FlashlightState.OnOff(false))
                    }
                }
            } catch (t: Throwable) {
                torchState = null
                withContext(Dispatchers.Main.immediate) {
                    sendEvent(FlashlightState.Error(FlashlightError.CAMERA_ERROR, t.message))
                }
                releaseCamera()
            }
        }
    }

    private fun releaseCamera() {
        try {
            camera?.release()
        } catch (_: Throwable) {
        }
        camera = null
        try {
            dummyTexture?.release()
        } catch (_: Throwable) {
        }
        dummyTexture = null
    }
}
