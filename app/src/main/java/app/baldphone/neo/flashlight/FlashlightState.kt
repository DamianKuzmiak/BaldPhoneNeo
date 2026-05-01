package app.baldphone.neo.flashlight

sealed class FlashlightState {
    object Idle : FlashlightState()

    data class OnOff(val isOn: Boolean) : FlashlightState()

    data class Error(val code: FlashlightError, val detail: String? = null) : FlashlightState()
}

enum class FlashlightError {
    NO_HARDWARE,
    CAMERA_ERROR
}
