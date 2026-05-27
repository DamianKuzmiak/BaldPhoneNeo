@file:JvmName("ThemeExtensions")

package app.baldphone.neo.extensions

import android.content.Context
import android.content.res.Configuration
import android.util.Log

import androidx.appcompat.app.AppCompatDelegate

import app.baldphone.neo.data.Theme

private const val TAG = "ThemeExtensions"

/**
 * Applies the given theme mode to the system via [AppCompatDelegate].
 */
fun Theme.apply() {
    val mode =
        when (this) {
            Theme.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            Theme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            Theme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
    if (AppCompatDelegate.getDefaultNightMode() != mode) {
        Log.d(TAG, "Applying theme mode: $mode")
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}

/**
 * Returns true if this theme is [Theme.SYSTEM].
 */
val Theme.isSystem: Boolean
    get() = (this == Theme.SYSTEM)

/**
 * Resolves whether the current application theme is resolved as dark mode.
 */
val Context.isDarkTheme: Boolean
    get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
