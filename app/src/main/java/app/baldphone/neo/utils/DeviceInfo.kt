package app.baldphone.neo.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.telephony.TelephonyManager

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

import coil3.SingletonImageLoader

import com.bald.uriah.baldphone.BuildConfig

private const val BYTES_IN_KB = 1024

fun Context.getDeviceInfoFull(): String {
    val dm = resources.displayMetrics
    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }

    val maxMemInKb = Runtime.getRuntime().maxMemory() / BYTES_IN_KB
    val freeMemInKb = Runtime.getRuntime().freeMemory() / BYTES_IN_KB
    val usedMemInKb = (Runtime.getRuntime().totalMemory() / BYTES_IN_KB) - freeMemInKb

    val coilCacheInfo = getCoilCacheInfo(this)

    return """
        Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
        Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
        Manufacturer: ${Build.MANUFACTURER}
        Brand: ${Build.BRAND}
        Device: ${Build.DEVICE}
        Model: ${Build.MODEL}
        Product: ${Build.PRODUCT}
        Hardware: ${Build.HARDWARE}
        Board: ${Build.BOARD}
        Type: ${Build.TYPE}
        Tags: ${Build.TAGS}
        Screen: ${dm.widthPixels}x${dm.heightPixels} (${dm.densityDpi} dpi)
        Locale: ${Locale.getDefault()}
        Timezone: ${gmtOffsetString()}
        App Heap Max: $maxMemInKb kB
        App Heap Used: $usedMemInKb kB
        App Heap Free: $freeMemInKb kB
        Memory Class: ${am.memoryClass} MB
        Large Mem Class: ${am.largeMemoryClass} MB
        Low Memory: ${memInfo.lowMemory}
        Threads: ${Thread.activeCount()}
        Uptime: ${SystemClock.uptimeMillis()} ms
        Coil RAM Cache: ${coilCacheInfo.first}
        Coil Disk Cache: ${coilCacheInfo.second}
        """.trimIndent()
}

private fun gmtOffsetString(): String {
    val zoneId = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("OOOO", Locale.getDefault())
    return formatter.format(zoneId.rules.getOffset(java.time.Instant.now()))
}

/**
 * Attempts to determine the user's current country region.
 *
 * It checks for the region in the following order:
 * 1. Network country ISO from TelephonyManager.
 * 2. SIM country ISO from TelephonyManager.
 * 3. Device's primary locale.
 *
 * @return A two-letter uppercase country code (ISO 3166-1), e.g., "PL".
 */
fun Context.getDeviceRegion(): String {
    val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    if (tm != null) {
        val networkIso = tm.networkCountryIso
        if (networkIso.isNotEmpty()) {
            return networkIso.uppercase(Locale.US)
        }

        // Fallback to SIM country ISO
        val simIso = tm.simCountryIso
        if (simIso.isNotEmpty()) {
            return simIso.uppercase(Locale.US)
        }
    }

    // 2. Fallback to device locale
    val primaryLocale =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale
        }

    if (primaryLocale != null) {
        val country = primaryLocale.country
        if (country.isNotEmpty()) {
            return country.uppercase(Locale.US)
        }
    }

    // 3. Final fallback to a default region
    return "US"
}

private fun getCoilCacheInfo(context: Context): Pair<String, String> {
    val imageLoader = SingletonImageLoader.get(context)

    val ramCacheInfo =
        imageLoader.memoryCache?.let { ramCache ->
            val currentSizeMb = ramCache.size / (BYTES_IN_KB * BYTES_IN_KB)
            val maxSizeMb = ramCache.maxSize / (BYTES_IN_KB * BYTES_IN_KB)
            "$currentSizeMb/$maxSizeMb MB"
        } ?: "Disabled"

    val diskCacheInfo =
        imageLoader.diskCache?.let { diskCache ->
            val currentSizeMb = diskCache.size / (BYTES_IN_KB * BYTES_IN_KB)
            val maxSizeMb = diskCache.maxSize / (BYTES_IN_KB * BYTES_IN_KB)
            "$currentSizeMb/$maxSizeMb MB"
        } ?: "Disabled"

    return Pair(ramCacheInfo, diskCacheInfo)
}
