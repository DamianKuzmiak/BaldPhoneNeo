package app.baldphone.neo.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue

import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

import app.baldphone.neo.features.notifications.data.NotificationRepository

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.utils.S

class NotificationsButton
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = android.R.attr.imageButtonStyle
    ) : AppCompatImageButton(context, attrs, defStyleAttr) {
        private val alotDrawable by lazy {
            AppCompatResources.getDrawable(context, R.drawable.notification_alot_on_background)?.mutate()
        }

        private var decorationColorOnBackground: Int = 0

        init {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(R.attr.bald_decoration_on_background, typedValue, true)
            decorationColorOnBackground = typedValue.data
        }

        fun bind(lifecycleOwner: LifecycleOwner) {
            lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    NotificationRepository.notifications
                        .map { it.size }
                        .collectLatest { count ->
                            handleNotificationCount(count)
                        }
                }
            }
        }

        private fun handleNotificationCount(count: Int) {
            Log.d("NotificationsButton", "Notification count: $count")
            contentDescription =
                if (count > 0) {
                    resources.getQuantityString(
                        R.plurals.notifications_accessibility_plural,
                        count,
                        count
                    )
                } else {
                    context.getString(R.string.notifications_accessibility_none)
                }

            when {
                count >= NOTIFICATIONS_ALOT -> {
                    val drawable = alotDrawable
                    drawable?.setTint(
                        S.blendColors(
                            decorationColorOnBackground,
                            ContextCompat.getColor(context, R.color.battery_low),
                            1 - ((count - NOTIFICATIONS_ALOT) / 10.0f).coerceAtMost(1.0f)
                        )
                    )
                    setImageDrawable(drawable)
                }

                count >= 1 -> {
                    setImageResource(R.drawable.notification_some_on_background)
                }

                count == 0 -> {
                    setImageResource(R.drawable.notification_none_on_background)
                }

                else -> {
                    setImageResource(R.drawable.error_on_background)
                }
            }
        }

        companion object {
            const val NOTIFICATIONS_ALOT = 5
        }
    }
