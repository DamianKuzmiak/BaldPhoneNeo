package app.baldphone.neo.features.notifications.ui

import android.content.res.ColorStateList
import android.graphics.RenderEffect.createBlurEffect
import android.graphics.Shader.TileMode
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup

import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

import app.baldphone.neo.core.assisttouch.disableAssistTouch
import app.baldphone.neo.core.assisttouch.enableAssistTouch
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
        return ViewHolder(binding)
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

    inner class ViewHolder(
        private val binding: ItemNotificationBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.clickableContentArea.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onContentClick(getItem(position))
                }
            }
            binding.buttonClear.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemCleared(getItem(position))
                }
            }
        }

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
                        backgroundIcon.setRenderEffect(RENDER_EFFECT)
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
                    isClickable = hasIntent
                    isFocusable = hasIntent
                    if (hasIntent) {
                        enableAssistTouch()
                    } else {
                        disableAssistTouch()
                    }
                }

                buttonClear.isVisible = item.isClearable
                if (item.isClearable) {
                    buttonClear.enableAssistTouch()
                } else {
                    buttonClear.disableAssistTouch()
                }
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

        private val RENDER_EFFECT by lazy {
            createBlurEffect(15f, 15f, TileMode.CLAMP)
        }

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
