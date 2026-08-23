package app.baldphone.neo.utils

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat

import java.util.Locale

import app.baldphone.neo.data.Prefs

object LocaleUtils {
    /**
     * Applies the given language/locale tag, saving it to Prefs and setting it in AppCompatDelegate.
     * Pass empty string to follow the system language.
     */
    fun applyLocale(tag: String) {
        Prefs.locale = tag
        val localeList =
            if (tag.isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tag)
            }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    /**
     * Retrieves the device's system-wide locale, ignoring any application locale override.
     */
    fun getSystemLocale(context: Context): Locale {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val systemLocales = context.getSystemService(LocaleManager::class.java)?.systemLocales
            if (systemLocales != null && !systemLocales.isEmpty) {
                return systemLocales.get(0)
            }
        }

        return ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0] ?: Locale.getDefault()
    }

    /**
     * Initializes the stored locale on application startup.
     */
    fun initLocale() {
        val savedLocale = Prefs.locale
        val localeList =
            if (savedLocale.isNullOrEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(savedLocale)
            }
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}
