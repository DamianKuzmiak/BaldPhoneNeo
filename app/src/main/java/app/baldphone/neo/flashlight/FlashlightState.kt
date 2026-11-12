// Copyright (c) 2026 Damian Kuźmiak
// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.flashlight

sealed class FlashlightState {
    object Idle : FlashlightState()

    data class OnOff(val isOn: Boolean) : FlashlightState()

    /** Temporarily in use by another app, not an error. */
    object Unavailable : FlashlightState()

    data class Error(val code: FlashlightError, val detail: String? = null) : FlashlightState()
}

enum class FlashlightError {
    NO_HARDWARE,
    CAMERA_ERROR
}
