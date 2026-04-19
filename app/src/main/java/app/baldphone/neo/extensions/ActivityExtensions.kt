@file:JvmName("ActivityExtensions")

package app.baldphone.neo.extensions

import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

import app.baldphone.neo.data.Prefs
import app.baldphone.neo.data.StatusBarMode

import com.bald.uriah.baldphone.activities.HomeScreenActivity
import com.bald.uriah.baldphone.utils.D

/**
 * Applies status bar settings based on the user's preference.
 * Should be called in [AppCompatActivity.onCreate] before [AppCompatActivity.setContentView].
 */
fun AppCompatActivity.applyStatusBarSettings() {
    val window = this.window

    // Keep decor fitting system windows to maintain alignment with navigation bar.
    WindowCompat.setDecorFitsSystemWindows(window, true)

    val insetsController = WindowCompat.getInsetsController(window, window.decorView)

    val statusBar = Prefs.statusBarMode
    val showStatusBar =
        (statusBar == StatusBarMode.EVERYWHERE) ||
            (statusBar == StatusBarMode.ONLY_HOME && this is HomeScreenActivity)
    
    if (showStatusBar) {
        insetsController.show(WindowInsetsCompat.Type.statusBars())
        window.statusBarColor = D.DEFAULT_STATUS_BAR_COLOR

        // Set icon appearance.
        // false = white icons (for dark backgrounds), true = dark icons (for light backgrounds)
        insetsController.isAppearanceLightStatusBars = false
    } else {
        insetsController.hide(WindowInsetsCompat.Type.statusBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
