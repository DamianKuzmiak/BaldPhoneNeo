package app.baldphone.neo.features.media.ui.viewer

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.VideoView

import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

import coil3.dispose
import coil3.load
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import coil3.size.Size

import app.baldphone.neo.R
import app.baldphone.neo.databinding.ItemPhotoBinding
import app.baldphone.neo.databinding.ItemVideoBinding
import app.baldphone.neo.extensions.cancelAnimations
import app.baldphone.neo.extensions.fadeIn
import app.baldphone.neo.extensions.fadeToggle
import app.baldphone.neo.features.media.MediaItem

class MediaViewerAdapter(
    private val playbackManager: VideoPlaybackManager
) : ListAdapter<MediaItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {
    /** Returns the media item at the specified position. */
    fun getItemAt(position: Int): MediaItem? = currentList.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_PHOTO -> {
                PhotoViewHolder(ItemPhotoBinding.inflate(inflater, parent, false))
            }

            VIEW_TYPE_VIDEO -> {
                VideoViewHolder(
                    ItemVideoBinding.inflate(inflater, parent, false),
                    onPlayPauseClicked = { holder -> onPlayPauseClicked(holder) },
                    onVideoClicked = { holder -> onRootClicked(holder) }
                )
            }

            else -> {
                error("Invalid view type: $viewType")
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as? MediaViewerHolder)?.bind(getItem(position))
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        (holder as? MediaViewerHolder)?.recycle()
        super.onViewRecycled(holder)
    }

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is MediaItem.Photo -> VIEW_TYPE_PHOTO
            is MediaItem.Video -> VIEW_TYPE_VIDEO
        }

    private fun onPlayPauseClicked(holder: VideoViewHolder) {
        val item = getItem(holder.bindingAdapterPosition)
        playbackManager.togglePlayPause(holder, item.uri)
    }

    private fun onRootClicked(holder: VideoViewHolder) {
        playbackManager.toggleUIOverlay(holder)
    }

    class PhotoViewHolder(
        private val binding: ItemPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root), MediaViewerHolder {
        private val zoomWrapper = binding.root

        init {
            binding.photoView.maximumScale = MAX_IMAGE_SCALE
        }

        override fun bind(item: MediaItem) {
            with(binding.photoView) {
                setScale(1f, false)
                zoomWrapper.isZoomed = false
                load(item.uri) {
                    size(Size.ORIGINAL)
                    crossfade(true)
                    placeholder(R.drawable.placeholder_media)
                    error(R.drawable.broken_image)
                    memoryCachePolicy(CachePolicy.DISABLED)
                    bitmapConfig(Bitmap.Config.RGB_565)
                    allowHardware(true)
                }

                setOnScaleChangeListener { _, _, _ ->
                    zoomWrapper.isZoomed = scale > 1.0f
                }
            }
        }

        override fun recycle() {
            binding.photoView.setOnScaleChangeListener(null)
            binding.photoView.dispose()
        }
    }

    class VideoViewHolder(
        private val binding: ItemVideoBinding,
        private val onPlayPauseClicked: (VideoViewHolder) -> Unit,
        private val onVideoClicked: (VideoViewHolder) -> Unit
    ) : RecyclerView.ViewHolder(binding.root), MediaViewerHolder {
        val videoView: VideoView
            get() = binding.videoView

        init {
            with(binding) {
                playPauseButton.setOnClickListener { onPlayPauseClicked(this@VideoViewHolder) }
                root.setOnClickListener { onVideoClicked(this@VideoViewHolder) }
            }
        }

        override fun bind(item: MediaItem) {
            binding.thumbnailView.apply {
                isVisible = true
                load(item.uri) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder_media)
                    error(R.drawable.broken_image)
                }
            }
            updateUI(isPlaying = false)
        }

        fun updateUI(isPlaying: Boolean) {
            binding.thumbnailView.isVisible = !isPlaying
            binding.playPauseButton.setImageResource(
                if (isPlaying) {
                    R.drawable.stop_on_background
                } else {
                    R.drawable.play_on_background
                }
            )
            binding.playPauseButton.fadeIn()
        }

        fun toggleButtonVisibility() {
            binding.playPauseButton.fadeToggle()
        }

        override fun recycle() {
            binding.playPauseButton.cancelAnimations()
        }
    }

    interface MediaViewerHolder {
        fun bind(item: MediaItem)

        fun recycle()
    }

    companion object {
        private const val VIEW_TYPE_PHOTO = 0
        private const val VIEW_TYPE_VIDEO = 1
        private const val MAX_IMAGE_SCALE = 4.0f

        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<MediaItem>() {
                override fun areItemsTheSame(old: MediaItem, new: MediaItem) = old.id == new.id

                override fun areContentsTheSame(old: MediaItem, new: MediaItem) = old == new
            }
    }
}
