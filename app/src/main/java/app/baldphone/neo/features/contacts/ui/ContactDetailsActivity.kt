package app.baldphone.neo.features.contacts.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.View
import android.widget.Toast

import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration

import kotlinx.coroutines.launch

import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.fallback

import app.baldphone.neo.contacts.Contact
import app.baldphone.neo.features.calls.CallUiHelper
import app.baldphone.neo.ui.dialogs.showErrorSnackbar
import app.baldphone.neo.ui.dialogs.showSuccessSnackbar
import app.baldphone.neo.utils.messaging.SignalHandler
import app.baldphone.neo.utils.messaging.WhatsAppHandler
import app.baldphone.neo.views.menu.ActionMenu
import app.baldphone.neo.views.menu.ActionMenuItem

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.activities.BaldActivity
import com.bald.uriah.baldphone.activities.contacts.AddContactActivity
import com.bald.uriah.baldphone.adapters.CallsRecyclerViewAdapter
import com.bald.uriah.baldphone.databases.calls.Call
import com.bald.uriah.baldphone.databinding.ActivityContactDetailsBinding
import com.bald.uriah.baldphone.databinding.ContactHistoryBinding
import com.bald.uriah.baldphone.databinding.ItemContactFieldBinding
import com.bald.uriah.baldphone.utils.BDB
import com.bald.uriah.baldphone.utils.BDialog
import com.bald.uriah.baldphone.utils.S
import com.bald.uriah.baldphone.views.BaldImageButton

/** Activity for viewing and interacting with a single contact. */
class ContactDetailsActivity : BaldActivity() {

    private lateinit var binding: ActivityContactDetailsBinding
    private lateinit var viewModel: ContactDetailsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val lookupKey = intent.getStringExtra(CONTACT_LOOKUP_KEY)
        if (lookupKey.isNullOrEmpty()) {
            Log.e(TAG, "Missing contactLookupKey from intent: $intent")
            showErrorSnackbar(R.string.an_error_has_occurred)
            finish()
            return
        }

        binding = ActivityContactDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initViews()

        viewModel = ViewModelProvider(this)[ContactDetailsViewModel::class.java]
        observeViewModel()
        viewModel.loadContact(lookupKey)
    }

    private fun initViews() {
        binding.titleBar.setOnMoreClickListener(::showPopup)
        binding.titleBar.showMoreButton()
    }

    override fun startActivity(intent: Intent) {
        try {
            super.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showErrorToast("Activity not found: ${e.localizedMessage}")
        }
    }

    override fun finish() {
        if (viewModel.uiState.value.contactChanged) setResult(RESULT_OK)
        super.finish()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.contact?.let { contact ->
                        renderContactInfo(contact, state.fields)
                    }
                    updateFavoriteIcon(state.isFavorite)
                    renderRecentCalls(state.callHistory, state.isCallLogVisible)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        ContactDetailsResult.ContactDeleted -> finish()
                        ContactDetailsResult.ContactNotFound -> {
                            showErrorToast("No contact found!")
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun renderContactInfo(contact: Contact, fields: List<ContactFieldUiModel>) {
        binding.name.text = contact.name
        binding.llContactInfoContainer.removeAllViews()

        fields.forEach { field ->
            val fieldBinding = ItemContactFieldBinding.inflate(
                layoutInflater,
                binding.llContactInfoContainer,
                false
            )

            fieldBinding.fieldLabel.text = field.label
            fieldBinding.fieldValue.also {
                it.text = field.value
                it.setTypeface(null, if (field.isBold) Typeface.BOLD else Typeface.NORMAL)
            }

            setupFieldButton(fieldBinding.btnActionPrimary, field.primaryAction)
            setupFieldButton(fieldBinding.btnActionSecondary, field.secondaryAction)

            binding.llContactInfoContainer.addView(fieldBinding.root)
        }

        loadPhoto(contact.photoUri)
    }

    private fun setupFieldButton(btn: BaldImageButton, action: FieldActionUiModel?) {
        if (action == null) {
            btn.visibility = View.GONE
            return
        }
        btn.visibility = View.VISIBLE
        btn.setImageResource(action.icon)
        action.tint?.let {
            btn.setColorFilter(ContextCompat.getColor(this, it))
        }
        btn.contentDescription = getString(action.description)
        btn.setOnClickListener { handleFieldAction(action) }
    }

    private fun handleFieldAction(action: FieldActionUiModel) {
        when (action.type) {
            FieldActionType.CALL -> CallUiHelper.call(this, action.data)
            FieldActionType.SMS -> S.sendMessage(action.data, this)
            FieldActionType.WHATSAPP -> {
                runCatching { WhatsAppHandler.startVoiceCall(this, action.data) }
                    .onFailure { Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show() }
            }

            FieldActionType.SIGNAL -> {
                runCatching { SignalHandler.startChat(this, action.data) }
                    .onFailure { Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show() }
            }

            FieldActionType.EMAIL -> sendEmail(action.data)
            FieldActionType.MAP -> openMap(action.data)
        }
    }

    private fun updateFavoriteIcon(isFavorite: Boolean) {
        binding.name.setCompoundDrawablesRelativeWithIntrinsicBounds(
            0, 0, if (isFavorite) R.drawable.star_gold else 0, 0,
        )
    }

    private fun renderRecentCalls(calls: List<Call>, isVisible: Boolean) {
        binding.llContactInfoContainer.findViewWithTag<View>("history_section")?.let {
            binding.llContactInfoContainer.removeView(it)
        }
        if (calls.isNotEmpty()) inflateHistory(calls, isVisible)
    }

    private fun loadPhoto(uri: String?) {
        if (isDestroyed || isFinishing) return
        if (uri.isNullOrEmpty()) {
            binding.avatar.visibility = View.GONE
            return
        }
        binding.avatar.visibility = View.VISIBLE
        binding.avatar.load(uri) {
            crossfade(true)
            fallback(R.drawable.face_in_recent_calls)
            error(R.drawable.error_on_background)
        }
    }

    private fun inflateHistory(calls: List<Call>, isVisible: Boolean) {
        val historyBinding = ContactHistoryBinding.inflate(
            layoutInflater,
            binding.llContactInfoContainer,
            false
        ).apply {
            root.tag = "history_section"

            val divider = DividerItemDecoration(
                this@ContactDetailsActivity,
                DividerItemDecoration.VERTICAL
            ).apply {
                AppCompatResources.getDrawable(this@ContactDetailsActivity, R.drawable.ll_divider)
                    ?.let { setDrawable(it) }
            }
            child.addItemDecoration(divider)
            child.adapter =
                CallsRecyclerViewAdapter(calls.toMutableList(), this@ContactDetailsActivity)

            btShow.setOnClickListener { viewModel.toggleCallLogVisibility() }

            updateHistoryVisibility(this, isVisible)
        }

        binding.llContactInfoContainer.addView(historyBinding.root)
    }

    private fun updateHistoryVisibility(historyBinding: ContactHistoryBinding, visible: Boolean) {
        if (visible) {
            historyBinding.scrollingHelper.visibility = View.VISIBLE
            historyBinding.btShow.imageView.setImageResource(R.drawable.drop_up_on_button)
            historyBinding.btShow.textView.setText(R.string.hide)
        } else {
            historyBinding.scrollingHelper.visibility = View.GONE
            historyBinding.btShow.imageView.setImageResource(R.drawable.drop_down_on_button)
            historyBinding.btShow.textView.setText(R.string.show)
        }
    }

    private fun showPopup(anchor: View) {
        val isFavorite = viewModel.uiState.value.isFavorite
        val isPinned = viewModel.uiState.value.isPinned

        val items = listOf(
            ActionMenuItem.Toggle(
                ACTION_FAVORITE,
                R.drawable.star_on_button, R.drawable.star_remove_on_button,
                R.string.remove_from_favorite, R.string.add_to_favorite,
                isFavorite, true,
            ),
            ActionMenuItem.Toggle(
                ACTION_HOME,
                R.drawable.remove_on_button, R.drawable.add_on_button,
                R.string.remove_from_home, R.string.add_to_home,
                isPinned, true,
            ),
            ActionMenuItem.Separator,
            ActionMenuItem.Option(
                ACTION_SHARE,
                R.drawable.share_on_background,
                R.string.share,
                destructive = false,
                enabled = true
            ),
            ActionMenuItem.Option(
                ACTION_EDIT,
                R.drawable.edit_on_background,
                R.string.edit,
                destructive = false,
                enabled = true
            ),
            ActionMenuItem.Option(
                ACTION_DELETE,
                R.drawable.delete_on_background,
                R.string.delete,
                destructive = false,
                enabled = true
            ),
        )

        ActionMenu(this, items) { onMenuAction(it) }.show(anchor)
    }

    private fun onMenuAction(item: ActionMenuItem) {
        when (item.id) {
            ACTION_EDIT -> {
                editContactDetails()
            }

            ACTION_SHARE -> {
                shareContact()
            }

            ACTION_DELETE -> {
                showDeleteConfirmationDialog()
            }

            ACTION_FAVORITE -> {
                viewModel.toggleFavorite()
                val toggle = item as? ActionMenuItem.Toggle
                val resId =
                    if (toggle?.checked == true) {
                        R.string.contact_added_to_favorites
                    } else {
                        R.string.contact_removed_from_favorites
                    }
                showSuccessSnackbar(resId)
            }

            ACTION_HOME -> {
                viewModel.toggleHomeScreenPin()
                val toggle = item as? ActionMenuItem.Toggle
                val resId =
                    if (toggle?.checked == true) R.string.contact_added_to_home else R.string.contact_removed_from_home
                showSuccessSnackbar(resId)
            }
        }
    }

    private fun shareContact() {
        val contact = viewModel.uiState.value.contact ?: return
        val vcardUri = Uri.withAppendedPath(
            ContactsContract.Contacts.CONTENT_VCARD_URI,
            contact.lookupKey,
        )
        val share = Intent(Intent.ACTION_SEND)
            .setType(ContactsContract.Contacts.CONTENT_VCARD_TYPE)
            .putExtra(Intent.EXTRA_STREAM, vcardUri)
            .putExtra(Intent.EXTRA_SUBJECT, contact.name)
        S.share(this, share)
    }

    private fun editContactDetails() {
        val contact = viewModel.uiState.value.contact ?: return
        val edit = Intent(this, AddContactActivity::class.java)
            .putExtra(CONTACT_LOOKUP_KEY, contact.lookupKey)
        startActivity(edit)
    }

    private fun showDeleteConfirmationDialog() {
        val contact = viewModel.uiState.value.contact ?: return
        BDB.from(this)
            .addFlag(BDialog.FLAG_YES or BDialog.FLAG_CANCEL)
            .setSubText(getString(R.string.are_you_sure_you_want_to_delete___, contact.name))
            .setPositiveButtonListener { viewModel.deleteContact(); true }
            .show()
    }

    private fun openMap(address: String) {
        if (address.isEmpty()) return
        val intent = Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(address)}".toUri())
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        else showErrorToast("No map app found")
    }

    private fun sendEmail(email: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", email, null))
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        else showErrorToast("No email app found")
    }

    private fun showErrorToast(message: CharSequence) {
        showErrorSnackbar(message)
    }

    override fun requiredPermissions() = PERMISSION_NONE

    companion object {
        const val CONTACT_LOOKUP_KEY = "contactLookupKey"
        private const val TAG = "ContactDetailsActivity"
        private const val ACTION_FAVORITE = 1
        private const val ACTION_HOME = 2
        private const val ACTION_SHARE = 3
        private const val ACTION_EDIT = 4
        private const val ACTION_DELETE = 5

        /**
         * Open the BaldPhone contact details activity for the given contact.
         */
        fun openContact(context: Context, key: String) {
            try {
                val intent = Intent(context, ContactDetailsActivity::class.java).apply {
                    putExtra(CONTACT_LOOKUP_KEY, key)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(
                    context, "Failed to open contact details: $key", Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
