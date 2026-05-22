package app.baldphone.neo.extensions

import android.app.Activity
import android.util.Log
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.Toast

import java.lang.reflect.Field

import com.bald.uriah.baldphone.BuildConfig

private const val TAG = "ActivityExtensions"

private var cachedSurfaceField: Field? = null
private var reflectionSupported = true

/**
 * Checks if the activity's window surface is valid. If invalid, forces a window relayout.
 * This acts as a workaround for platform-specific rendering/surface issues - seen on some devices (e.g. Xiaomi).
 */
fun Activity.ensureValidSurface() {
    if (!reflectionSupported || isFinishing || isDestroyed || isChangingConfigurations) return

    val decorView = window?.decorView ?: return
    if (decorView.windowVisibility != View.VISIBLE) return

    try {
        val parent = decorView.parent ?: return
        if (parent.javaClass.name != "android.view.ViewRootImpl") return

        val field = getSurfaceField(parent.javaClass)
        val surface = field?.get(parent) as? Surface

        if (surface == null || !surface.isValid) {
            Log.w(TAG, "Invalid surface detected in! Forcing relayout.")

            // Minimal method to force a relayout by updating layout params
            val lp = window.attributes
            lp.softInputMode = lp.softInputMode or WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
            window.attributes = lp

            decorView.requestLayout()

            if (BuildConfig.DEBUG) {
                Toast.makeText(this, "Invalid surface detected", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to check surface validity or force relayout", e)
        reflectionSupported = false // Stop trying if we hit a security or field error
    }
}

private fun getSurfaceField(klass: Class<*>): Field? {
    cachedSurfaceField?.let { return it }

    return try {
        klass.getDeclaredField("mSurface").apply {
            isAccessible = true
            cachedSurfaceField = this
        }
    } catch (e: NoSuchFieldException) {
        Log.w(TAG, "mSurface filed not found", e)
        reflectionSupported = false
        null
    }
}
