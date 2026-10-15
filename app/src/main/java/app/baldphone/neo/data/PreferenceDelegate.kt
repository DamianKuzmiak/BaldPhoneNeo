package app.baldphone.neo.data

import android.content.SharedPreferences
import android.util.Log

import androidx.core.content.edit

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private const val TAG = "Prefs"

/**
 * A generic [ReadWriteProperty] delegate backed by [SharedPreferences].
 *
 * All delegates share a single store set once via [bind] (called from Prefs.init).
 */

class PreferenceDelegate<T>(
    private val key: String,
    private val defaultValue: T,
    private val getter: (SharedPreferences, String, T) -> T,
    private val setter: (SharedPreferences.Editor, String, T) -> SharedPreferences.Editor
) : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T =
        getter(store, key, defaultValue).also {
            Log.v(TAG, "Read $key: $it")
        }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        Log.v(TAG, "Setting $key to $value")
        store.edit { setter(this, key, value) }
    }

    companion object {
        private lateinit var store: SharedPreferences

        fun bind(prefs: SharedPreferences) {
            store = prefs
        }
    }
}

internal fun booleanPref(key: String, default: Boolean) =
    PreferenceDelegate(
        key,
        default,
        SharedPreferences::getBoolean,
        SharedPreferences.Editor::putBoolean
    )

internal fun intPref(key: String, default: Int) =
    PreferenceDelegate(
        key,
        default,
        SharedPreferences::getInt,
        SharedPreferences.Editor::putInt
    )

internal fun stringPref(key: String, default: String?) =
    PreferenceDelegate(
        key,
        default,
        SharedPreferences::getString,
        SharedPreferences.Editor::putString
    )

internal fun <T> enumPref(
    key: String,
    default: T,
    fromInt: (Int) -> T,
    toInt: (T) -> Int
) = PreferenceDelegate(
    key,
    default,
    getter = { p, k, d -> fromInt(p.getInt(k, toInt(d))) },
    setter = { e, k, v -> e.putInt(k, toInt(v)) }
)

/**
 * Builds a [PreferenceDelegate] for a string value that is obfuscated.
 *
 * [onDecode] receives the raw stored string and must return the value to expose.
 * [onEncode] transforms the value before it is written.
 */
internal fun obfuscatedStringPref(
    key: String,
    default: String?,
    onDecode: (String) -> String,
    onEncode: (String) -> String
) = PreferenceDelegate(
    key,
    default,
    getter = { p, k, d -> p.getString(k, null)?.let(onDecode) ?: d },
    setter = { e, k, v -> e.putString(k, v?.let(onEncode)) }
)
