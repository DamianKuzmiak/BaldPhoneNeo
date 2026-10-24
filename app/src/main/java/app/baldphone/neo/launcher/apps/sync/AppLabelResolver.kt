// Copyright (c) 2026 Damian Kuźmiak
// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.launcher.apps.sync

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

import app.baldphone.neo.utils.LocaleUtils
import app.baldphone.neo.utils.LocaleUtils.withLocales

/**
 * Resolves launcher labels of installed apps in the app-selected language.
 *
 * On API 32- it loads target app resources for the selected locale and falls back on system locale,
 * e.g. when missing main-profile package, missing string resource, or errors.
 */
internal object AppLabelResolver {
    private const val TAG = "AppLabelResolver"

    fun getAppLabel(context: Context, info: LauncherActivityInfo): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return info.label.toString()
        }

        return getLocalizedAppLabel(context, info) ?: info.label.toString()
    }

    private fun getLocalizedAppLabel(context: Context, info: LauncherActivityInfo): String? {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (appLocales.isEmpty) return null

        return try {
            val activityInfo = context.packageManager.getActivityInfo(info.componentName, 0)
            val labelRes = activityInfo.labelRes.takeIf { it != 0 } ?: activityInfo.applicationInfo.labelRes

            if (labelRes == 0) {
                Log.d(TAG, "${info.componentName}: no label resource")
                null
            } else {
                val systemLocaleTag = LocaleUtils.getSystemLocale(context).toLanguageTag()
                val locales = LocaleListCompat.forLanguageTags("${appLocales.toLanguageTags()},$systemLocaleTag")

                val packageContext = context.createPackageContext(info.componentName.packageName, 0)
                packageContext.withLocales(locales).getText(labelRes).toString()
            }
        } catch (_: PackageManager.NameNotFoundException) {
            Log.d(TAG, "${info.componentName}: not installed in the main profile")
            null
        } catch (e: Exception) {
            Log.w(TAG, "${info.componentName}: failed to load localized label", e)
            null
        }
    }
}
