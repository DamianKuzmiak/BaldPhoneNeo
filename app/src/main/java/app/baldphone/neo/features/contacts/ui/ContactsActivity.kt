package app.baldphone.neo.features.contacts.ui

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup

import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
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
import app.baldphone.neo.features.contacts.ContactItemType
import app.baldphone.neo.features.contacts.SimpleContact
import app.baldphone.neo.permissions.PermissionManager
import app.baldphone.neo.permissions.model.RuntimePermission
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
        ContactAdapter(
            onAddContactClick =
                if (!isPickerMode) {
                    { startActivity(Intent(this, AddContactActivity::class.java)) }
                } else {
                    null
                },
            onContactClick = { contact ->
                if (isPickerMode) {
                    showPickerConfirmation(contact)
                } else {
                    ContactDetailsActivity.openContact(this, contact.lookupKey)
                }
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSearch()
        setupButtons()

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
                            val listWithAction =
                                if (!isPickerMode) {
                                    listOf(ContactItemType.AddContact) + contacts
                                } else {
                                    contacts
                                }
                            adapter.submitList(listWithAction) {
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
        binding.contactsRecyclerView.isVisible = !isEmpty || !isPickerMode
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
                        if (totalItems > 0 && totalItems != lastTriggeredTotalItems &&
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
        binding.buttonFavorites.setOnClickListener {
            viewModel.toggleFavorites()
        }

        // Handle Edge-to-Edge: Ensure button stays above Nav Bar and RecyclerView content isn't obscured
        val originalMarginBottom = binding.buttonFavorites.marginBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemInsets =
                insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

            binding.buttonFavorites.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemInsets.bottom + originalMarginBottom
            }

            binding.buttonFavorites.post {
                if (isDestroyed || isFinishing) return@post
                val totalBottomPadding =
                    systemInsets.bottom + binding.buttonFavorites.height + (originalMarginBottom * 1.5).toInt()
                binding.contactsRecyclerView.updatePadding(bottom = totalBottomPadding)
            }

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
