package app.baldphone.neo.features.calls.ui

import android.os.Bundle

import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator

import kotlinx.coroutines.launch

import app.baldphone.neo.activities.BaseActivity
import app.baldphone.neo.features.calls.CallUiHelper
import app.baldphone.neo.features.calls.model.CallListEntry
import app.baldphone.neo.features.contacts.ui.ContactDetailsActivity
import app.baldphone.neo.features.notifications.data.NotificationRepository
import app.baldphone.neo.permissions.PermissionManager
import app.baldphone.neo.permissions.RuntimePermission
import app.baldphone.neo.ui.dialogs.showErrorSnackbar
import app.baldphone.neo.ui.dialogs.showInfoSnackbar
import app.baldphone.neo.utils.sendMessage

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.activities.contacts.AddContactActivity
import com.bald.uriah.baldphone.databinding.ActivityRecentCallsBinding
import com.bald.uriah.baldphone.utils.BDB
import com.bald.uriah.baldphone.utils.BDialog

/**
 * Displays the recent call log.
 *
 * All state and logic lives in [app.baldphone.neo.features.calls.ui.RecentCallsViewModel];
 */
class RecentCallsActivity : BaseActivity() {
    private lateinit var binding: ActivityRecentCallsBinding

    private val viewModel: RecentCallsViewModel by viewModels()
    private val adapter =
        RecentCallsAdapter { number, name, lookupUri ->
            // Requesting Contacts permission to resolve the contact's lookup key, in order to open details.
            PermissionManager.checkOrRequest(this, RuntimePermission.ReadWriteContacts) { _ ->
                viewModel.onCallEntryClicked(number, name, lookupUri)
            }
        }

    private val showLoadingRunnable =
        Runnable {
            if (loadingStartTime != null) {
                binding.loadingSpinner.isVisible = true
            }
        }

    private var loadingStartTime: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecentCallsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()

        PermissionManager.checkOrRequest(this, RuntimePermission.ReadCallLog) {
            onGranted {
                observeViewModel()
                if (savedInstanceState == null) {
                    viewModel.refresh()
                }
            }
            onDenied {
                finish()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        NotificationRepository.clearMissedCalls()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.apply {
            adapter = this@RecentCallsActivity.adapter
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

            addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                        if (dy <= 0) return
                        val lm = rv.layoutManager as? LinearLayoutManager ?: return
                        val lastVisible = lm.findLastVisibleItemPosition()
                        val totalItems = lm.itemCount

                        // Prevent triggering multiple page loads before the adapter has finished diffing
                        val currentList = viewModel.callEntries.value
                        if (currentList != null && currentList.size > totalItems) return

                        if (lastVisible >= totalItems - SCROLL_THRESHOLD) {
                            viewModel.loadNextPage()
                        }
                    }
                }
            )
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.callEntries.collect { state ->
                        updateUiVisibility(state)
                        state?.let { newList -> adapter.submitList(newList) }
                    }
                }

                launch {
                    viewModel.uiEvents.collect { event ->
                        when (event) {
                            is RecentCallsEvent.OpenContact -> {
                                ContactDetailsActivity.openContact(this@RecentCallsActivity, event.lookupKey)
                            }

                            is RecentCallsEvent.UnknownCaller -> {
                                processUnknownCaller(event.number, event.name)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateUiVisibility(state: List<CallListEntry>?) {
        val isLoading = (state == null)

        // Handle Loading Spinner delay
        if (isLoading) {
            if (loadingStartTime == null) {
                loadingStartTime = android.os.SystemClock.elapsedRealtime()
                binding.loadingSpinner.postDelayed(showLoadingRunnable, LOADING_SPINNER_DELAY_MS)
            }
        } else if (loadingStartTime != null) {
            loadingStartTime = null
            binding.loadingSpinner.removeCallbacks(showLoadingRunnable)
            binding.loadingSpinner.isVisible = false
        }

        binding.noCallsText.isVisible = state != null && state.isEmpty()
        binding.recyclerView.isVisible = !state.isNullOrEmpty()
    }

    private fun processUnknownCaller(number: String?, name: String?) {
        if (number.isNullOrEmpty()) {
            showInfoSnackbar(R.string.private_number)
            return
        }

        BDB
            .from(this)
            .setSubText(getString(R.string.what_do_you_want_to_do_with___, number))
            .setOptions(R.string.call, R.string.add_contact, R.string.message)
            .addFlag(BDialog.FLAG_OK)
            .setPositiveButtonListener { params ->
                val selection = params.firstOrNull() as? Int ?: return@setPositiveButtonListener true
                when (selection) {
                    0 -> performCall(number, name)
                    1 -> openAddContact(number)
                    2 -> sendMessage(number)
                    else -> android.util.Log.e("RecentCallsActivity", "Unknown option: $selection")
                }
                true
            }.show()
    }

    private fun performCall(number: String, name: String?) {
        PermissionManager.checkOrRequest(this, RuntimePermission.CallPhone) {
            onGranted { CallUiHelper.call(this@RecentCallsActivity, number) }
            onDenied { showErrorSnackbar("Permission missing") }
        }
    }

    private fun openAddContact(number: String) {
        PermissionManager.checkOrRequest(this, RuntimePermission.ReadWriteContacts) {
            onGranted { AddContactActivity.start(this@RecentCallsActivity, number) }
            onDenied { showErrorSnackbar("Permission missing") }
        }
    }

    private companion object {
        private const val LOADING_SPINNER_DELAY_MS = 150L
        private const val SCROLL_THRESHOLD = 15
    }
}
