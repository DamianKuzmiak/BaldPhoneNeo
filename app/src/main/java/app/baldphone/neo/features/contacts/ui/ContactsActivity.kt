package app.baldphone.neo.features.contacts.ui

import android.content.Intent
import android.os.Bundle

import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import kotlinx.coroutines.launch

import app.baldphone.neo.activities.BaseActivity
import app.baldphone.neo.features.contacts.SimpleContact
import app.baldphone.neo.permissions.PermissionManager
import app.baldphone.neo.permissions.RuntimePermission
import app.baldphone.neo.ui.dialogs.baldDialog

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.activities.contacts.AddContactActivity
import com.bald.uriah.baldphone.databinding.ActivityContactsBinding

class ContactsActivity : BaseActivity() {
    companion object {
        /** Pass as a boolean extra to open the activity in contact-picker mode. */
        const val EXTRA_PICK_CONTACT = "extra_pick_contact"

        /** Result extra containing the selected contact's lookup key. */
        const val EXTRA_CONTACT_LOOKUP_KEY = "extra_contact_lookup_key"

        private const val PRELOAD_THRESHOLD = 15
    }

    private lateinit var binding: ActivityContactsBinding
    private val viewModel: ContactsViewModel by viewModels()
    private var lastTriggeredTotalItems = -1

    private val isPickerMode: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_PICK_CONTACT, false)
    }

    private val adapter by lazy {
        ContactAdapter { contact ->
            if (isPickerMode) {
                showPickerConfirmation(contact)
            } else {
                ContactDetailsActivity.openContact(this, contact.lookupKey)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSearch()
        setupButtons()
        setupKeyboardInsets()

        PermissionManager.checkOrRequest(this, RuntimePermission.ReadWriteContacts) {
            onGranted {
                viewModel.refresh()
                observeViewModel()
            }
            onDenied { finish() }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isFavoritesOnly.collect { binding.buttonFavorites.isChecked = it }
                }

                launch {
                    viewModel.contactsFlow.collect { contacts ->
                        if (contacts != null) {
                            adapter.submitList(contacts) {
                                lastTriggeredTotalItems = -1
                                binding.progressBar.isVisible = false
                                updateEmptyState(contacts.isEmpty())
                            }
                        } else {
                            binding.progressBar.isVisible = true
                            updateEmptyState(false)
                        }
                    }
                }
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyStateText.isVisible = isEmpty
        binding.contactsRecyclerView.isVisible = !isEmpty
    }

    private fun setupRecyclerView() {
        binding.contactsRecyclerView.apply {
            val lm = LinearLayoutManager(this@ContactsActivity)
            layoutManager = lm
            adapter = this@ContactsActivity.adapter
            itemAnimator = null
            setHasFixedSize(true)
            addItemDecoration(DividerItemDecoration(this@ContactsActivity, VERTICAL))

            addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(
                        rv: RecyclerView,
                        dx: Int,
                        dy: Int
                    ) {
                        if (dy <= 0) return
                        val lastVisible = lm.findLastVisibleItemPosition()
                        val totalItems = adapter?.itemCount ?: 0
                        if (totalItems > 0 &&
                            totalItems != lastTriggeredTotalItems &&
                            lastVisible >= totalItems - PRELOAD_THRESHOLD
                        ) {
                            lastTriggeredTotalItems = totalItems
                            android.util.Log.d(
                                "ContactsActivity",
                                "Scroll trigger: lastVisible=$lastVisible, total=$totalItems. Loading next page."
                            )
                            viewModel.loadNextPage()
                        }
                    }
                }
            )
        }
    }

    private fun setupSearch() {
        binding.searchEditText.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            viewModel.searchQuery.value = query
        }
    }

    private fun setupButtons() {
        binding.buttonAddContact.isVisible = !isPickerMode
        binding.buttonAddContact.setOnClickListener {
            startActivity(Intent(this, AddContactActivity::class.java))
        }
        binding.buttonFavorites.setOnClickListener {
            viewModel.toggleFavorites()
        }
    }

    /** Hides the bottom action buttons when the keyboard is visible to save screen space. */
    private fun setupKeyboardInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            binding.contactsBottomActions.isGone = isImeVisible

            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = if (isImeVisible) imeInsets.bottom else systemBars.bottom
            )
            insets
        }
    }

    /**
     * Shows a confirmation dialog before returning the selected contact to the caller.
     * Cancelling dismisses the dialog and keeps the list open.
     */
    private fun showPickerConfirmation(contact: SimpleContact) {
        baldDialog {
            setTitle(contact.name)
            setMessage(getString(R.string.add_as_an_emergency_contact, contact.name))
            setPositiveButton(android.R.string.ok) { _ ->
                val resultIntent =
                    Intent().apply {
                        putExtra(EXTRA_CONTACT_LOOKUP_KEY, contact.lookupKey)
                    }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
            setNegativeButton(android.R.string.cancel, null)
        }.show()
    }
}
