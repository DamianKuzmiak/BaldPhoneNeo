package app.baldphone.neo.features.touchguard

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager

import androidx.core.content.ContextCompat

import com.bald.uriah.baldphone.R

/**
 * Manages the full-screen accidental-touch blocking overlay as a system overlay. The overlay consumes all touch events
 * so the UI underneath is unreachable.
 */
internal class TouchOverlayController(private val appContext: Context, private val onDismissed: () -> Unit) {
    private var overlayView: View? = null
    private var isAddedToWindowManager = false

    var isShowing: Boolean = false
        private set

    /** [BroadcastReceiver] that dismisses the overlay when the screen turns off. */
    private val screenOffReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    hide()
                }
            }
        }

    /** Creates a full-screen overlay [View] and shows it using WindowManager or DecorView. */
    fun show(activity: Activity) {
        if (isShowing) return
        if (activity.isFinishing || activity.isDestroyed) return

        @SuppressLint("InflateParams")
        val overlay = LayoutInflater.from(activity).inflate(R.layout.overlay_pocket_mode_guard, null)

        // Consume all touches –> prevent interaction with the UI underneath.
        @SuppressLint("ClickableViewAccessibility")
        overlay.setOnTouchListener { _, _ -> true }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(appContext)) {
            val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params =
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                    },
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
            try {
                windowManager.addView(overlay, params)
                isAddedToWindowManager = true
                overlayView = overlay
                isShowing = true
            } catch (e: WindowManager.BadTokenException) {
                Log.e(TAG, "Failed to add overlay via WindowManager", e)
                showInDecorView(activity, overlay)
            }
        } else {
            showInDecorView(activity, overlay)
        }

        if (isShowing) {
            // Register screen-off receiver as a fallback dismissal mechanism.
            ContextCompat.registerReceiver(
                appContext,
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            Log.d(TAG, "Overlay shown")
        }
    }

    /**
     * Removes the overlay from the view hierarchy and cleans up state.
     */
    fun hide() {
        if (!isShowing) return

        overlayView?.let { view ->
            if (isAddedToWindowManager) {
                try {
                    val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    windowManager.removeView(view)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Failed to remove overlay from WindowManager", e)
                }
            } else {
                (view.parent as? ViewGroup)?.removeView(view)
            }
        }
        overlayView = null
        isShowing = false

        try {
            appContext.unregisterReceiver(screenOffReceiver)
        } catch (_: IllegalArgumentException) {
            // Was already unregistered
        }

        Log.d(TAG, "Overlay removed")
        onDismissed()
    }

    private fun showInDecorView(activity: Activity, overlay: View) {
        val decorView = activity.window.decorView as? ViewGroup ?: return
        decorView.addView(
            overlay,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        isAddedToWindowManager = false
        overlayView = overlay
        isShowing = true
    }

    companion object {
        private const val TAG = "TouchOverlayController"
    }
}
