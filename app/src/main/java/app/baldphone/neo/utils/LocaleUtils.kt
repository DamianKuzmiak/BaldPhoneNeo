// Copyright (c) 2026 Damian Kuźmiak
// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.utils

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.util.Log

import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat

import java.util.Locale

import app.baldphone.neo.data.Prefs

object LocaleUtils {
    private const val TAG = "LocaleUtils"

    // TODO - Plan migrating the Hebrew/Indonesian resource folders from the obsolete codes
    // TODO - Adding explicit scripts for Chinese/Serbian (e.g. values-b+zh+Hans, values-b+sr+Cyrl)
    //        Today values-b+zh holds Traditional Chinese and values-b+sr holds Latin Serbian.

    /**
     * Applies the given language/locale tag, saving it to Prefs and setting it in AppCompatDelegate.
     * Pass empty string to follow the system language.
     */
    fun applyLocale(tag: String) {
        Log.d(TAG, "applyLocale: tag='$tag'")
        Prefs.locale = tag
        AppCompatDelegate.setApplicationLocales(tag.toLocaleListCompat())
        Log.d(TAG, "applyLocale: applied='${AppCompatDelegate.getApplicationLocales().toLanguageTags()}'")
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
     *
     * 1) API 33+: the system ([LocaleManager]) is the source of truth (the user can also change the
     * app language in system settings), synced to Prefs.
     *
     * 2) API 32-: AppCompat keeps the locale in memory only, so it is restored from Prefs.
     */
    fun initLocale(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            initLocaleFromSystem(context)
            return
        }

        val savedLocale = Prefs.locale

        // One-time locale migration
        val migratedLocale = migratedLocale(savedLocale)
        if (migratedLocale != savedLocale) {
            Prefs.locale = migratedLocale
        }

        AppCompatDelegate.setApplicationLocales(migratedLocale.toLocaleListCompat())
        Log.d(TAG, "initLocale: applied='${AppCompatDelegate.getApplicationLocales().toLanguageTags()}'")
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun initLocaleFromSystem(context: Context) {
        val localeManager = context.getSystemService(LocaleManager::class.java) ?: return
        val appLocale = localeManager.applicationLocales.toLanguageTags()

        // One-time locale migration
        val migratedLocale = migratedLocale(appLocale).orEmpty()
        if (migratedLocale != appLocale) {
            localeManager.applicationLocales = LocaleList.forLanguageTags(migratedLocale)
        }
        if (Prefs.locale != migratedLocale) {
            Prefs.locale = migratedLocale
        }

        val toLanguageTags = localeManager.applicationLocales.toLanguageTags()
        Log.d(TAG, "initLocaleFromSystem: systemLocale='$appLocale' applied='$toLanguageTags'")
    }

    /**
     * TODO - Remove 3-5 releases after v15.2.0.
     * Consolidated fi-FI, nl-NL, sr-RS, et-EE into their base language tags in v15.2.0.
     */
    internal fun migratedLocale(tag: String?): String? =
        when (tag) {
            "fi-FI" -> "fi"
            "nl-NL" -> "nl"
            "sr-RS" -> "sr"
            "et-EE" -> "et"
            else -> tag
        }

    /** Converts a language tag string into a LocaleListCompat. */
    private fun String?.toLocaleListCompat(): LocaleListCompat =
        if (this.isNullOrEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(this)
        }
}
