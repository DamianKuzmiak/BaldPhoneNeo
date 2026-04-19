@file:JvmName("ThemeExtensions")

package app.baldphone.neo.extensions

import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import app.baldphone.neo.data.Theme

private const val TAG = "ThemeExtensions"

/**
 * Applies the given theme mode to the system via [AppCompatDelegate].
 */
fun Theme.apply() {
    val mode = when (this) {
        Theme.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        Theme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        Theme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
    }
    if (AppCompatDelegate.getDefaultNightMode() != mode) {
        Log.d(TAG, "Applying theme mode: $mode")
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
