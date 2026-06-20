package app.baldphone.neo.features.gallery

import android.app.Application
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for [MediaActivity].
 */
class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val contentResolver = application.contentResolver

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items

    /**
     * Loads media items from the [MediaStore] based on the provided [mode] and updates [items].
     */
    fun loadMedia(mode: Int) {
        viewModelScope.launch {
            val list =
                withContext(Dispatchers.IO) {
                    try {
                        queryMediaCursor(mode)?.use { cursor ->
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
                                    val type =
                                        if (mediaTypeInt == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                                            MediaType.VIDEO
                                        } else {
                                            MediaType.PHOTO
                                        }
                                    val uri =
                                        ContentUris.withAppendedId(
                                            if (type == MediaType.VIDEO) {
                                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                            } else {
                                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                            },
                                            id
                                        )
                                    add(MediaItem(id, uri, type))
                                }
                            }
                        } ?: emptyList()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load media", e)
                        emptyList()
                    }
                }
            _items.value = list
        }
    }

    /** Re-triggers data loading for the current mode. */
    fun refresh(mode: Int) {
        loadMedia(mode)
    }

    private fun queryMediaCursor(mode: Int): Cursor? {
        val uri = MediaStore.Files.getContentUri("external")

        val mediaTypes =
            when (mode) {
                MediaActivity.MODE_PHOTOS_ONLY -> {
                    listOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE)
                }

                MediaActivity.MODE_VIDEOS_ONLY -> {
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

        return contentResolver?.query(uri, PROJECTION, selection, selectionArgs, SORT_ORDER)
    }

    companion object {
        private const val TAG = "MediaViewModel"

        private val PROJECTION =
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )

        private const val SORT_ORDER = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
    }
}

enum class MediaType { PHOTO, VIDEO }

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val type: MediaType
)
