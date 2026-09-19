package app.baldphone.neo.utils

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.util.Log

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.io.ByteArrayOutputStream

object ImageUtils {
    private const val TAG = "ImageUtils"
    private const val PHOTO_QUALITY = 30

    /**
     * Decodes the given [uri] into a square, compressed JPEG [ByteArray], suitable for contacts.
     * Returns null if decoding or processing fails.
     */
    suspend fun toSquareJpeg(contentResolver: ContentResolver, uri: Uri): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap =
                    contentResolver.openInputStream(uri)?.use { input ->
                        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                        BitmapFactory.decodeStream(input, null, options)
                    } ?: return@runCatching null

                val side = minOf(bitmap.width, bitmap.height)
                val square = ThumbnailUtils.extractThumbnail(bitmap, side, side)

                ByteArrayOutputStream().use { stream ->
                    square.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, stream)
                    stream.toByteArray()
                }
            }.onFailure { Log.e(TAG, "Failed to process photo from Uri: $uri", it) }.getOrNull()
        }
}
