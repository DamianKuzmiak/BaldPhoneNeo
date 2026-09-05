package app.baldphone.neo.features.notifications.ui

import android.os.Bundle
import android.util.Log

import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlinx.coroutines.launch

import app.baldphone.neo.R
import app.baldphone.neo.activities.BaseActivity
import app.baldphone.neo.databinding.ActivityNotificationsBinding
import app.baldphone.neo.extensions.applyBottomInsetsAsMargin
import app.baldphone.neo.features.notifications.NotificationItem
import app.baldphone.neo.features.notifications.sendNotificationIntent
import app.baldphone.neo.permissions.PermissionManager
import app.baldphone.neo.permissions.model.SpecialPermission
import app.baldphone.neo.ui.dialogs.showErrorSnackbar

class NotificationsActivity : BaseActivity() {
    private lateinit var binding: ActivityNotificationsBinding
    private val viewModel: NotificationsViewModel by viewModels()
    private val adapter =
        NotificationListAdapter(
            onItemCleared = { item -> viewModel.dismiss(item) },
            onContentClick = { item -> onContentClick(item) }
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.v(TAG, "onCreate")

        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerView.adapter = adapter
        binding.clearAllNotifications.applyBottomInsetsAsMargin()

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

    private fun updateUI(items: List<NotificationItem>) {
        Log.d(TAG, "processNotifications: ${items.size}")
        adapter.submitList(items)

        binding.noNotificationsText.isVisible = items.isEmpty()
        binding.clearAllNotifications.isVisible = items.any { it.isClearable }

        val titleText =
            if (items.isNotEmpty()) {
                getString(R.string.text_with_count, getString(R.string.notifications), items.size)
            } else {
                getString(R.string.notifications)
            }
        binding.baldTitleBar.setTitle(titleText)
    }

    private fun onContentClick(item: NotificationItem) {
        val success =
            item.contentIntent?.sendNotificationIntent(
                onCanceled = { showErrorSnackbar(R.string.an_error_has_occurred) }
            ) ?: false
        if (success) finish()
    }

    companion object {
        private val TAG = NotificationsActivity::class.java.simpleName
    }
}
