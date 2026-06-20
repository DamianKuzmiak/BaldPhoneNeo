package app.baldphone.neo.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.Size
import coil3.size.pxOrElse
import coil3.toAndroidUri

class MediaStoreThumbnailFetcher(
    private val uri: Uri,
    private val context: Context,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val size = options.size

        if (size == Size.ORIGINAL) return null

        val width = size.width.pxOrElse { DEFAULT_THUMB_SIZE }
        val height = size.height.pxOrElse { DEFAULT_THUMB_SIZE }

        val bitmap = loadThumbnail(width, height)
        val rotatedBitmap =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                bitmap
            } else {
                val rotation = getOrientation(uri)
                rotateBitmap(bitmap, rotation)
            }

        return rotatedBitmap?.let {
            ImageFetchResult(
                image = it.asImage(),
                isSampled = true,
                dataSource = DataSource.DISK
            )
        }
    }

    private fun getOrientation(uri: Uri): Int {
        val columns = arrayOf(MediaStore.Images.ImageColumns.ORIENTATION)
        return runCatching {
            context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getInt(0)
                } else {
                    0
                }
            } ?: 0
        }.getOrDefault(0)
    }

    private fun rotateBitmap(bitmap: Bitmap?, degrees: Int): Bitmap? {
        if (bitmap == null || degrees == 0) return bitmap
        return try {
            val matrix = Matrix().apply { postRotate(0 + degrees.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun loadThumbnail(width: Int, height: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.loadThumbnail(uri, android.util.Size(width, height), null)
            }.getOrNull()
        } else {
            runCatching {
                ContentUris.parseId(uri)
            }.getOrNull()?.let { id ->
                val mimeType = context.contentResolver.getType(uri)
                val isVideo =
                    mimeType?.startsWith("video/") == true ||
                        (mimeType == null && uri.path?.contains("video", ignoreCase = true) == true)

                val kind =
                    if (width > MICRO_KIND_MAX_DIMEN || height > MICRO_KIND_MAX_DIMEN) {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Thumbnails.MINI_KIND
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Thumbnails.MICRO_KIND
                    }

                runCatching {
                    if (isVideo) {
                        @Suppress("DEPRECATION")
                        MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, id, kind, null)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, id, kind, null)
                    }
                }.getOrNull()
            }
        }

    class Factory(private val context: Context) : Fetcher.Factory<coil3.Uri> {
        override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val androidUri = data.toAndroidUri()
            if (androidUri.scheme == ContentResolver.SCHEME_CONTENT && androidUri.authority == MediaStore.AUTHORITY) {
                return MediaStoreThumbnailFetcher(androidUri, context.applicationContext, options)
            }
            return null
        }
    }

    companion object {
        private const val DEFAULT_THUMB_SIZE = 512
        private const val MICRO_KIND_MAX_DIMEN = 96
    }
}
