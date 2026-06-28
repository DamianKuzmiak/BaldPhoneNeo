package app.baldphone.neo.features.media.data

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import app.baldphone.neo.features.media.MediaItem
import app.baldphone.neo.features.media.MediaMode

/**
 * Source of truth for querying media items from [MediaStore].
 */
class MediaRepository(private val contentResolver: ContentResolver) {
    fun getMediaFlow(mode: Int): Flow<List<MediaItem>> =
        callbackFlow {
            // Emit the initial list
            send(queryMedia(mode))

            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        super.onChange(selfChange)
                        launch {
                            send(queryMedia(mode))
                        }
                    }
                }

            try {
                listOf(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                ).forEach { uri ->
                    contentResolver.registerContentObserver(uri, true, observer)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register content observers", e)
            }

            awaitClose {
                contentResolver.unregisterContentObserver(observer)
            }
        }.flowOn(Dispatchers.IO)

    /**
     * Queries [MediaStore] for media items matching the given [mode].
     */
    suspend fun queryMedia(mode: Int): List<MediaItem> =
        withContext(Dispatchers.IO) {
            try {
                val result = buildCursor(mode)?.use { cursor -> buildMediaList(cursor) } ?: emptyList()
                Log.d(TAG, "Loaded ${result.size} media items")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load media items from MediaStore", e)
                emptyList()
            }
        }

    /**
     * Determines whether the given [uri] is a video by inspecting MIME type and file extension.
     */
    fun isVideo(uri: Uri): Boolean {
        val mimeType = contentResolver.getType(uri)
        val isVideoMime = mimeType?.startsWith("video/", ignoreCase = true) == true
        val hasVideoExtension =
            uri.path?.let { p ->
                VIDEO_EXTENSIONS.any { ext -> p.endsWith(".$ext", ignoreCase = true) }
            } == true

        return isVideoMime || hasVideoExtension
    }

    private fun buildCursor(mode: Int): Cursor? {
        val queryUri = MediaStore.Files.getContentUri("external")

        val mediaTypes =
            when (mode) {
                MediaMode.PHOTOS -> {
                    listOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE)
                }

                MediaMode.VIDEOS -> {
                    listOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
                }

                else -> {
                    listOf(
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE,
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    )
                }
            }

        val placeholders = mediaTypes.joinToString(",") { "?" }
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN ($placeholders)"
        val selectionArgs = mediaTypes.map { it.toString() }.toTypedArray()

        return contentResolver.query(queryUri, PROJECTION, selection, selectionArgs, SORT_ORDER)
    }

    private fun buildMediaList(cursor: Cursor): List<MediaItem> =
        buildList {
            while (cursor.moveToNext()) {
                val id =
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    )
                val mediaTypeInt =
                    cursor.getInt(
                        cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                    )
                val isVideo = mediaTypeInt == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val uri =
                    ContentUris.withAppendedId(
                        if (isVideo) {
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        } else {
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        },
                        id
                    )
                val item =
                    if (isVideo) {
                        MediaItem.Video(id, uri)
                    } else {
                        MediaItem.Photo(id, uri)
                    }
                add(item)
            }
        }

    companion object {
        private const val TAG = "MediaRepository"

        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "3gp", "webm")

        private val PROJECTION =
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )

        private const val SORT_ORDER = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
    }
}
