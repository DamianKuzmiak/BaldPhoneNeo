package app.baldphone.neo.flashlight.drivers

internal interface FlashlightDriver {
    /** Turn torch ON/OFF */
    fun setTorch(enabled: Boolean)

    /** Last known torch state (true/false), null if unknown. */
    fun getTorchState(): Boolean?

    /** Lifecycle forwarding - cleans up resources and releases any hardware references. */
    fun onDestroy() {}
}
