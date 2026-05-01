package app.baldphone.neo.features.notifications.ui

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log

import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlinx.coroutines.launch

import app.baldphone.neo.activities.BaseActivity
import app.baldphone.neo.features.notifications.NotificationItem
import app.baldphone.neo.permissions.PermissionManager
import app.baldphone.neo.permissions.SpecialPermission
import app.baldphone.neo.ui.dialogs.showErrorSnackbar

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.databinding.ActivityNotificationsBinding

class NotificationsActivity : BaseActivity() {
    private lateinit var binding: ActivityNotificationsBinding
    private val viewModel: NotificationsViewModel by viewModels()
    private val adapter =
        NotificationListAdapter(
            onItemCleared = { item -> viewModel.dismiss(item) },
            onContentClick = { item -> onContentClick(item) }
        )

    private val timeTickReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_TIME_TICK) {
                    Log.v(TAG, "ACTION_TIME_TICK: refreshing timestamps")
                    adapter.notifyItemRangeChanged(0, adapter.itemCount, NotificationListAdapter.PAYLOAD_TIME_TICK)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.v(TAG, "onCreate")

        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerView.adapter = adapter
        binding.clearAllNotifications.setOnClickListener {
            viewModel.clearAll()
            finish()
        }

        // observe ViewModel
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.notificationItems.collect { items -> updateUI(items) }
            }
        }

        PermissionManager.checkOrRequest(this, SpecialPermission.NotificationListener) {
            onDenied {
                finish()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(Intent.ACTION_TIME_TICK)
        ContextCompat.registerReceiver(this, timeTickReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(timeTickReceiver)
        super.onStop()
    }

    private fun updateUI(items: List<NotificationItem>) {
        Log.d(TAG, "processNotifications: ${items.size}")
        adapter.submitList(items)

        binding.noNotificationsText.isVisible = items.isEmpty()
        binding.clearAllNotificationsContainer.isVisible = items.any { it.isClearable }

        val titleText =
            if (items.isNotEmpty()) {
                getString(R.string.text_with_count, getString(R.string.notifications), items.size)
            } else {
                getString(R.string.notifications)
            }
        binding.baldTitleBar.setTitle(titleText)
    }

    private fun onContentClick(item: NotificationItem) {
        item.contentIntent?.let { intent ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val options = ActivityOptions.makeBasic()
                    options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                    intent.send(options.toBundle())
                } else {
                    intent.send()
                }
                finish()
            } catch (e: PendingIntent.CanceledException) {
                showErrorSnackbar(R.string.an_error_has_occurred)
                Log.e(TAG, "Notification intent was canceled", e)
            }
        }
    }

    companion object {
        private val TAG = NotificationsActivity::class.java.simpleName
    }
}
