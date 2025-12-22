package app.baldphone.neo.launcher.apps.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log

import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.io.File

/**
 * Handles saving, deleting, and caching app icons as .webp files in internal storage.
 * Separated from UI concerns to ensure clean architecture.
 */
object AppIconStorage {
    private const val TAG = "AppIconStorage"
    private const val APP_ICONS_FOLDER = "icons" // Resolves to "app_icons" in data/data/[app]/ folder
    private const val BITMAP_COMPRESS_QUALITY = 85
    private const val DEFAULT_ICON_SIZE = 192

    /**
     * Returns the [File] representing the cached icon path for the given [componentName] and [userId].
     */
    fun getCachedIconFile(
        context: Context,
        componentName: String,
        userId: Long = 0L
    ): File = File(getIconCacheFolder(context), computeIconFileName(componentName, userId))

    /**
     * Saves an app icon drawable as a .webp file in the cache directory. Overwrites any existing file.
     */
    suspend fun saveIcon(
        context: Context,
        drawable: Drawable,
        componentName: String,
        userId: Long = 0L,
        size: Int = DEFAULT_ICON_SIZE
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            val saveStart = System.currentTimeMillis()
            val appContext = context.applicationContext
            var bitmap: Bitmap? = null
            try {
                // Force a consistent size for storage efficiency
                bitmap = drawable.toBitmapSafe(appContext, size, size)
                val file = getCachedIconFile(appContext, componentName, userId)

                // Use BufferedOutputStream for better I/O performance
                file.outputStream().buffered().use { out ->
                    val format =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSY
                        } else {
                            @Suppress("DEPRECATION")
                            Bitmap.CompressFormat.WEBP
                        }
                    bitmap.compress(format, BITMAP_COMPRESS_QUALITY, out)
                }
                val duration = System.currentTimeMillis() - saveStart
                Log.d(
                    TAG,
                    "Saved icon for: $componentName, size: ${file.length()} bytes, userId: $userId in ${duration}ms"
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save icon for: $componentName", e)
                Result.failure(e)
            } finally {
                // Only recycle if we created a new bitmap instance
                if (bitmap != null && (drawable !is BitmapDrawable || drawable.bitmap != bitmap)) {
                    bitmap.recycle()
                }
            }
        }

    /**
     * Deletes the icon file for [componentName] and [userId] from the cache directory.
     */
    suspend fun deleteIconFile(context: Context, componentName: String, userId: Long = 0L) =
        withContext(Dispatchers.IO) {
            try {
                getCachedIconFile(context, componentName, userId).delete()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete icon for: $componentName", e)
            }
        }

    /**
     * Checks whether the icon storage directory exists and contains any files.
     */
    fun isCacheEmpty(context: Context): Boolean = getIconCacheFolder(context).list()?.isEmpty() ?: true

    /**
     * Checks if an icon is already cached.
     */
    suspend fun isIconCached(
        context: Context,
        componentName: String,
        userId: Long = 0L
    ): Boolean =
        withContext(Dispatchers.IO) {
            getCachedIconFile(context, componentName, userId).exists()
        }

    /**
     * Clears all icon files from the cache directory.
     */
    suspend fun clearIconCache(context: Context) =
        withContext(Dispatchers.IO) {
            try {
                getIconCacheFolder(context).deleteRecursively()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear icon cache", e)
            }
        }

    private fun computeIconFileName(componentName: String, userId: Long): String {
        val safeName = componentName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        return if (userId == 0L) "$safeName.webp" else "${safeName}_u$userId.webp"
    }

    private fun getIconCacheFolder(context: Context): File = context.getDir(APP_ICONS_FOLDER, Context.MODE_PRIVATE)

    @WorkerThread
    private fun Drawable.toBitmapSafe(
        context: Context,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        // If it's already a BitmapDrawable with the acceptable size, use it.
        if (this is BitmapDrawable && bitmap != null) {
            Log.v(TAG, "Reusing existing bitmap: width: ${bitmap.width}, height: ${bitmap.height}")
            if (bitmap.width <= targetWidth && bitmap.height <= targetHeight) {
                return bitmap
            }
        }

        val bmp = createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val oldBounds = copyBounds()

        try {
            // Adaptive icons should always be drawn to match the target bounds
            setBounds(0, 0, targetWidth, targetHeight)
            draw(canvas)
            Log.d(TAG, "Successfully drawn drawable to bitmap (width: ${bmp.width}, height: ${bmp.height})")
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing drawable to bitmap", e)

            val fallback = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
            fallback?.let {
                it.setBounds(0, 0, targetWidth, targetHeight)
                it.draw(canvas)
            }
        } finally {
            bounds = oldBounds
        }
        return bmp
    }
}
