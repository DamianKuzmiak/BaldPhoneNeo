package app.baldphone.neo.features.notifications.ui

import android.content.res.ColorStateList
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup

import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

import app.baldphone.neo.features.notifications.NotificationItem
import app.baldphone.neo.utils.formatDayAwareTimestamp

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.adapters.ModularListAdapter
import com.bald.uriah.baldphone.databinding.ItemNotificationBinding

class NotificationListAdapter(
    private val onItemCleared: (NotificationItem) -> Unit,
    private val onContentClick: (NotificationItem) -> Unit
) : ModularListAdapter<NotificationItem, NotificationListAdapter.ViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = ItemNotificationBinding.inflate(layoutInflater, parent, false)
        return ViewHolder(binding, onItemCleared, onContentClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_TIME_TICK)) {
            holder.updateTimeOnly(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    class ViewHolder(
        private val binding: ItemNotificationBinding,
        private val onDismiss: (NotificationItem) -> Unit,
        private val onContentClick: (NotificationItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: NotificationItem) {
            val context = itemView.context

            with(binding) {
                appName.text = item.appName
                title.text = item.title
                text.text = item.text

                updateTimeOnly(item)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    backgroundIcon.setImageIcon(item.smallIcon)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        backgroundIcon.setRenderEffect(RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP))
                    }

                    if (item.largeIcon != null) {
                        notificationIcon.setImageIcon(item.largeIcon)
                        notificationIcon.imageTintList = null
                    } else {
                        notificationIcon.setImageIcon(item.smallIcon)
                        notificationIcon.imageTintList =
                            ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary))
                    }
                } else {
                    // Legacy fallback for API < 23. To be removed once minSDK is 23+.
                    val icon =
                        try {
                            val resources = context.packageManager.getResourcesForApplication(item.packageName)
                            ResourcesCompat.getDrawable(resources, item.smallIconResId, null)
                        } catch (_: Exception) {
                            null
                        }
                    notificationIcon.setImageDrawable(icon)
                    backgroundIcon.setImageDrawable(icon)
                }

                val hasIntent = item.contentIntent != null
                clickableContentArea.apply {
                    setOnClickListener { onContentClick(item) }
                    isClickable = hasIntent
                    isFocusable = hasIntent
                }

                buttonClear.isVisible = item.isClearable
                buttonClear.setOnClickListener { onDismiss(item) }
            }
        }

        fun updateTimeOnly(item: NotificationItem) {
            binding.timeStamp.text =
                if (item.timeStamp == 0L) {
                    ""
                } else {
                    item.timeStamp.formatDayAwareTimestamp(itemView.context)
                }
        }
    }

    companion object {
        const val PAYLOAD_TIME_TICK = "PAYLOAD_TIME_TICK"

        private object DiffCallback : DiffUtil.ItemCallback<NotificationItem>() {
            override fun areItemsTheSame(oldItem: NotificationItem, newItem: NotificationItem) =
                (oldItem.key == newItem.key)

            override fun areContentsTheSame(oldItem: NotificationItem, newItem: NotificationItem) =
                oldItem.appName == newItem.appName &&
                    android.text.TextUtils.equals(oldItem.title, newItem.title) &&
                    android.text.TextUtils.equals(oldItem.text, newItem.text) &&
                    oldItem.timeStamp == newItem.timeStamp &&
                    oldItem.isClearable == newItem.isClearable
        }
    }
}
