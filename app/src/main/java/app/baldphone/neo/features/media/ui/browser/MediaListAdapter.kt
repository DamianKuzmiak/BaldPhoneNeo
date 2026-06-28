package app.baldphone.neo.features.media.ui.browser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView

import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

import coil3.dispose
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import coil3.request.transformations
import coil3.transform.RoundedCornersTransformation

import app.baldphone.neo.R
import app.baldphone.neo.core.assisttouch.enableAssistTouch
import app.baldphone.neo.features.media.MediaItem

import com.bald.uriah.baldphone.views.ModularRecyclerView

class MediaListAdapter(private val onItemClick: (item: MediaItem) -> Unit) :
    ModularRecyclerView.ModularAdapter<MediaListAdapter.ViewHolder>() {
    private val differ = AsyncListDiffer(this, DIFF_CALLBACK)

    val currentListSize: Int get() = differ.currentList.size

    fun submitList(list: List<MediaItem>, onCommit: (() -> Unit)? = null) {
        differ.submitList(list, onCommit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media_thumbnail, parent, false)
        val holder = ViewHolder(view)

        view.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                onItemClick(differ.currentList[position])
            }
        }

        return holder
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.clear()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        holder.bind(differ.currentList[position])
    }

    override fun getItemCount(): Int = currentListSize

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val pic: ImageView = itemView as ImageView

        private companion object {
            private var videoDesc: String? = null
            private var photoDesc: String? = null
        }

        init {
            itemView.enableAssistTouch()
            if (photoDesc == null) {
                videoDesc = itemView.context.getString(R.string.content_desc_gallery_video_thumbnail)
                photoDesc = itemView.context.getString(R.string.content_desc_gallery_photo_thumbnail)
            }
        }

        fun bind(item: MediaItem) {
            pic.contentDescription = if (item is MediaItem.Photo) photoDesc else videoDesc
            pic.load(item.uri) {
                transformations(RoundedCornersTransformation(0f))
                crossfade(false)
                placeholder(R.drawable.placeholder_media)
                error(R.drawable.broken_image)
            }
        }

        fun clear() {
            pic.dispose()
            pic.setImageDrawable(null)
        }
    }

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<MediaItem>() {
                override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean = oldItem.id == newItem.id

                override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean = oldItem == newItem
            }
    }
}
