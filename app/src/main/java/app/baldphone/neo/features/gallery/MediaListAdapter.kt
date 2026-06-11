package app.baldphone.neo.features.gallery

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView

import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

import coil3.dispose
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import coil3.request.transformations
import coil3.transform.RoundedCornersTransformation

import app.baldphone.neo.core.assisttouch.enableAssistTouch

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.views.ModularRecyclerView

class MediaListAdapter(
    private val onItemClick: (item: MediaItem) -> Unit
) : ModularRecyclerView.ModularAdapter<MediaListAdapter.ViewHolder>() {
    private var itemWidth: Int = 0
    private val differ = AsyncListDiffer(this, DIFF_CALLBACK)

    private val layoutListener =
        View.OnLayoutChangeListener { v, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left == oldRight - oldLeft) return@OnLayoutChangeListener
            val rv = v as RecyclerView
            val layoutManager = rv.layoutManager as? GridLayoutManager ?: return@OnLayoutChangeListener
            val width = (right - left - rv.paddingLeft - rv.paddingRight) / layoutManager.spanCount
            Log.d(TAG, "layoutListener: calculated width=$width, current itemWidth=$itemWidth")
            if (this.itemWidth != width) {
                rv.post {
                    this.itemWidth = width
                    Log.d(TAG, "Notifying adapter of width change: $width")
                    notifyDataSetChanged()
                }
            }
        }

    /** Current snapshot of the list; safe to index into from the main thread. */
    val currentList: List<MediaItem> get() = differ.currentList

    /** Submit a new list. */
    fun submitList(list: List<MediaItem>) {
        differ.submitList(list)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.addOnLayoutChangeListener(layoutListener)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        recyclerView.removeOnLayoutChangeListener(layoutListener)
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media_thumbnail, parent, false)
        val size = itemWidth.coerceAtLeast(1)
        val lp = view.layoutParams
        if (lp != null) {
            lp.width = size
            lp.height = size
            view.layoutParams = lp
        }
        return ViewHolder(view)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.pic.dispose()
        holder.pic.setImageDrawable(null)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        val item = differ.currentList[position]
        val width = itemWidth.coerceAtLeast(1)

//        val lp = holder.itemView.layoutParams
//        if (lp != null && (lp.width != width || lp.height != width)) {
//            lp.width = width
//            lp.height = width
//            holder.itemView.layoutParams = lp
//        }

        holder.pic.contentDescription = holder.pic.context.getString(
            if (item.type == MediaType.VIDEO) {
                R.string.content_desc_gallery_video_thumbnail
            } else {
                R.string.content_desc_gallery_photo_thumbnail
            }
        )

        holder.pic.load(item.uri) {
            size(width, width)
//            scale(Scale.FILL)
//            precision(Precision.INEXACT)
            transformations(RoundedCornersTransformation(0f))
            crossfade(false)
            placeholder(R.drawable.placeholder_media)
            error(R.drawable.broken_image)
        }
    }

    override fun getItemCount(): Int {
        val count = if (itemWidth <= 0) 0 else differ.currentList.size
        return count
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        val pic: ImageView = itemView as ImageView

        init {
            itemView.setOnClickListener(this)
            itemView.enableAssistTouch()
        }

        override fun onClick(v: View) {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                onItemClick(differ.currentList[position])
            }
        }
    }

    companion object {
        private const val TAG = "MediaListAdapter"

        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<MediaItem>() {
                override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean = oldItem.id == newItem.id

                override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean = oldItem == newItem
            }
    }
}
