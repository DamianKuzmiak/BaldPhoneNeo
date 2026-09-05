package app.baldphone.neo.features.notifications.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater

import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible

import com.google.android.material.card.MaterialCardView

import app.baldphone.neo.R
import app.baldphone.neo.core.assisttouch.disableAssistTouch
import app.baldphone.neo.core.assisttouch.enableAssistTouch
import app.baldphone.neo.databinding.ViewNotificationCardBinding
import app.baldphone.neo.extensions.setClickableAccessibilityRole
import app.baldphone.neo.features.notifications.NotificationItem
import app.baldphone.neo.utils.formatDayAwareTimestamp

/**
 * Reusable Card component for displaying a [NotificationItem].
 * Encapsulates layout binding, icon fallback handling, timestamp formatting,
 * AssistTouch integration, and intent execution.
 */
class NotificationItemView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : MaterialCardView(context, attrs, defStyleAttr) {
        private val binding = ViewNotificationCardBinding.inflate(LayoutInflater.from(context), this)
        private var currentItem: NotificationItem? = null
        private var isReceiverRegistered = false

        private val timeTickReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_TIME_TICK) {
                        val item = currentItem
                        if (isShown && item != null) {
                            updateTimeOnly(item)
                        }
                    }
                }
            }

        init {
            val density = context.resources.displayMetrics.density
            radius = 16f * density
            cardElevation = 2f * density
            preventCornerOverlap = false
            useCompatPadding = false
            strokeWidth = 0
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            registerTimeTickReceiver()
        }

        override fun onDetachedFromWindow() {
            unregisterTimeTickReceiver()
            super.onDetachedFromWindow()
        }

        /**
         * Binds a [NotificationItem] to the card view, setting up text fields,
         * app icons, timestamps, accessibility options, and click listeners.
         */
        fun bind(
            item: NotificationItem,
            onContentClick: ((NotificationItem) -> Unit)? = null,
            onClearClick: ((NotificationItem) -> Unit)? = null
        ) {
            this.currentItem = item
            with(binding) {
                appName.text = item.appName

                val hasTitle = !item.title.isNullOrBlank()
                val hasText = !item.text.isNullOrBlank()

                if (hasTitle) {
                    title.text = item.title
                    title.isVisible = true
                } else if (!hasText) {
                    title.text = item.appName
                    title.isVisible = true
                } else {
                    title.isVisible = false
                }

                text.text = item.text
                text.isVisible = hasText

                updateTimeOnly(item)
                bindIcons(item)

                val hasIntent = item.contentIntent != null
                clickableContentArea.apply {
                    isClickable = hasIntent
                    isFocusable = hasIntent
                    if (hasIntent) {
                        setClickableAccessibilityRole()
                        enableAssistTouch()
                        setOnClickListener {
                            onContentClick?.invoke(item)
                        }
                    } else {
                        disableAssistTouch()
                        setOnClickListener(null)
                    }
                }

                buttonClear.isVisible = item.isClearable
                if (item.isClearable) {
                    buttonClear.enableAssistTouch()
                    buttonClear.setOnClickListener {
                        onClearClick?.invoke(item)
                    }
                } else {
                    buttonClear.disableAssistTouch()
                    buttonClear.setOnClickListener(null)
                }
            }
        }

        private fun updateTimeOnly(item: NotificationItem) {
            binding.timeStamp.text =
                if (item.timeStamp == 0L) {
                    ""
                } else {
                    item.timeStamp.formatDayAwareTimestamp(context)
                }
        }

        private fun registerTimeTickReceiver() {
            if (!isReceiverRegistered) {
                val filter = IntentFilter(Intent.ACTION_TIME_TICK)
                ContextCompat.registerReceiver(
                    context,
                    timeTickReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                isReceiverRegistered = true
            }
        }

        private fun unregisterTimeTickReceiver() {
            if (isReceiverRegistered) {
                try {
                    context.unregisterReceiver(timeTickReceiver)
                } catch (_: Exception) {
                }
                isReceiverRegistered = false
            }
        }

        private fun bindIcons(item: NotificationItem) {
            with(binding) {
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
                    // Legacy fallback for API < 23
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
            }
        }

        companion object {
            private val RENDER_EFFECT by lazy {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP)
                } else {
                    null
                }
            }
        }
    }
