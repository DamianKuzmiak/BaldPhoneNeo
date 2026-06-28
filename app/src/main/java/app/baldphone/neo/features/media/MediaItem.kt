package app.baldphone.neo.features.media

import android.net.Uri

sealed class MediaItem {
    abstract val id: Long
    abstract val uri: Uri

    data class Photo(
        override val id: Long,
        override val uri: Uri
    ) : MediaItem()

    data class Video(
        override val id: Long,
        override val uri: Uri
    ) : MediaItem()
}
