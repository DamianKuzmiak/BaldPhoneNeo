package app.baldphone.neo.launcher.apps.data.db

import android.content.ComponentName

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore

import app.baldphone.neo.launcher.apps.data.PredefinedApps

/**
 * Room entity representing a launchable application (activity) in the database.
 *
 * Icons are not stored in the database. They are saved as .webp files in the system cache directory.
 */
@Entity(
    tableName = "app_cache",
    primaryKeys = ["package_name", "class_name", "user_id"]
)
data class AppEntry(
    /** Package name of the application, e.g. "com.example". */
    @ColumnInfo(name = "package_name") val packageName: String,
    /** Class name of the launchable activity, e.g. "com.example.MainActivity". */
    @ColumnInfo(name = "class_name") val className: String,
    /** Unique serial number of the user profile this app belongs to. */
    @ColumnInfo(name = "user_id") val userId: Long = 0L,
    /** Label of the application. */
    @ColumnInfo(name = "label") val label: String,
    /** Whether this app is pinned to the home screen. */
    @ColumnInfo(name = "pinned") val isPinned: Boolean = false,
    /** Timestamp when the app was first installed. -1L for built-in apps. */
    @ColumnInfo(name = "install_time") val installTime: Long = -1L,
    /** Timestamp when the app was last updated. -1L for built-in apps. */
    @ColumnInfo(name = "update_time") val updateTime: Long = -1L
) {
    /** The [ComponentName] object representing this entry. */
    @Ignore
    val component: ComponentName =
        ComponentName(
            packageName,
            if (className.startsWith(".")) "$packageName$className" else className
        )

    /** Flattened component name in "package/class" format, e.g. "com.example/com.example.MainActivity". */
    @Ignore
    val componentName: String = "$packageName/$className"

    /** Whether this app is a predefined (built-in) application. */
    @get:Ignore
    val isPredefined: Boolean
        get() = PredefinedApps.isPredefined(componentName)
}
