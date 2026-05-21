package app.baldphone.neo.features.contacts.ui

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.View
import android.widget.Toast

import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.text.htmlEncode
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

import coil3.load
import coil3.request.crossfade
import coil3.request.error

import app.baldphone.neo.activities.BaseActivity
import app.baldphone.neo.features.calls.CallUiHelper
import app.baldphone.neo.features.contacts.Contact
import app.baldphone.neo.ui.dialogs.BaldDialog
import app.baldphone.neo.ui.dialogs.showErrorSnackbar
import app.baldphone.neo.ui.dialogs.showSuccessSnackbar
import app.baldphone.neo.ui.menu.showActionMenu
import app.baldphone.neo.utils.getHtmlString
import app.baldphone.neo.utils.messaging.SignalHandler
import app.baldphone.neo.utils.messaging.WhatsAppHandler
import app.baldphone.neo.utils.openMap
import app.baldphone.neo.utils.sendEmail
import app.baldphone.neo.utils.sendMessage
import app.baldphone.neo.utils.startActivityWithNewTask

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.activities.contacts.AddContactActivity
import com.bald.uriah.baldphone.databinding.ActivityContactDetailsBinding
import com.bald.uriah.baldphone.databinding.ContactCallItemBinding
import com.bald.uriah.baldphone.databinding.ItemContactFieldBinding
import com.bald.uriah.baldphone.utils.S
import com.bald.uriah.baldphone.views.BaldImageButton

/** Activity for viewing and interacting with a single contact. */
class ContactDetailsActivity : BaseActivity() {
    private lateinit var binding: ActivityContactDetailsBinding
    private val viewModel: ContactDetailsViewModel by viewModels()

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

        observeViewModel()
        viewModel.loadContact(lookupKey)
    }

    private fun initViews() {
        binding.titleBar.setOnMoreClickListener(::showPopup)
        binding.titleBar.showMoreButton()
        binding.btShow.setOnClickListener { viewModel.toggleCallLogVisibility() }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState
                        .map { it.contact to it.fields }
                        .distinctUntilChanged()
                        .collect { (contact, fields) ->
                            if (contact != null) {
                                renderContactInfo(contact, fields)
                            }
                        }
                }

                launch {
                    viewModel.uiState
                        .map { it.isFavorite }
                        .distinctUntilChanged()
                        .collect { isFavorite ->
                            updateFavoriteIcon(isFavorite)
                        }
                }

                launch {
                    viewModel.uiState
                        .map { it.callHistory to it.isCallLogVisible }
                        .distinctUntilChanged()
                        .collect { (callHistory, isCallLogVisible) ->
                            renderRecentCalls(callHistory, isCallLogVisible)
                        }
                }

                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            ContactDetailsResult.ContactDeleted -> {
                                finish()
                            }

                            ContactDetailsResult.ContactNotFound -> {
                                this@ContactDetailsActivity.showErrorSnackbar("No contact found!")
                                finish()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderContactInfo(
        contact: Contact,
        fields: List<ContactFieldUiModel>
    ) {
        binding.name.text = contact.name

        val container = binding.llContactInfoContainer
        val currentChildCount = container.childCount
        val targetChildCount = fields.size

        if (currentChildCount > targetChildCount) {
            container.removeViews(targetChildCount, currentChildCount - targetChildCount)
        }

        fields.forEachIndexed { index, field ->
            val itemBinding =
                if (index < container.childCount) {
                    ItemContactFieldBinding.bind(container.getChildAt(index))
                } else {
                    ItemContactFieldBinding.inflate(layoutInflater, container, true)
                }

            with(itemBinding) {
                fieldLabel.text = field.label
                fieldValue.text = field.value
                fieldValue.setTypeface(null, if (field.isBold) Typeface.BOLD else Typeface.NORMAL)
                setupFieldButton(btnActionPrimary, field.primaryAction)
                setupFieldButton(btnActionSecondary, field.secondaryAction)
            }
        }

        loadPhoto(contact.photoUri)
    }

    private fun setupFieldButton(
        btn: BaldImageButton,
        action: FieldActionUiModel?
    ) {
        if (action == null) {
            btn.visibility = View.GONE
            return
        }
        btn.visibility = View.VISIBLE
        btn.setImageResource(action.icon)
        if (action.tint != null) {
            btn.setColorFilter(ContextCompat.getColor(this, action.tint))
        } else {
            btn.clearColorFilter()
        }
        btn.contentDescription = getString(action.description)
        btn.setOnClickListener { handleFieldAction(action) }
    }

    private fun handleFieldAction(action: FieldActionUiModel) {
        when (action.type) {
            FieldActionType.CALL -> {
                CallUiHelper.call(this, action.data)
            }

            FieldActionType.SMS -> {
                sendMessage(action.data)
            }

            FieldActionType.WHATSAPP -> {
                runCatching { WhatsAppHandler.startVoiceCall(this, action.data) }
                    .onFailure { Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show() }
            }

            FieldActionType.SIGNAL -> {
                runCatching { SignalHandler.startChat(this, action.data) }
                    .onFailure { Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show() }
            }

            FieldActionType.EMAIL -> {
                sendEmail(action.data)
            }

            FieldActionType.MAP -> {
                openMap(action.data)
            }
        }
    }

    private fun updateFavoriteIcon(isFavorite: Boolean) {
        binding.name.setCompoundDrawablesRelativeWithIntrinsicBounds(
            0,
            0,
            if (isFavorite) R.drawable.star_gold else 0,
            0
        )
    }

    private fun renderRecentCalls(
        calls: List<CallUiModel>,
        isExpanded: Boolean
    ) {
        val isLogEmpty = calls.isEmpty()
        binding.llHistoryContainer.isVisible = !isLogEmpty
        if (isLogEmpty) return

        binding.btShow.apply {
            val icon = if (isExpanded) R.drawable.drop_up_on_button else R.drawable.drop_down_on_button
            val textRes = if (isExpanded) R.string.hide else R.string.show
            imageView.setImageResource(icon)
            textView.setText(textRes)
        }

        binding.llCalls.isVisible = isExpanded
        if (isExpanded) {
            val container = binding.llCalls
            val currentChildCount = container.childCount
            val targetChildCount = calls.size

            if (currentChildCount > targetChildCount) {
                container.removeViews(targetChildCount, currentChildCount - targetChildCount)
            }

            calls.forEachIndexed { index, call ->
                val view =
                    if (index < currentChildCount) {
                        container.getChildAt(index)
                    } else {
                        val itemBinding = ContactCallItemBinding.inflate(layoutInflater, container, false)
                        container.addView(itemBinding.root)
                        itemBinding.root
                    }

                val itemBinding = ContactCallItemBinding.bind(view)
                with(itemBinding) {
                    day.isVisible = call.isFirstInDay
                    if (call.isFirstInDay) {
                        day.text = call.relativeDate
                    }
                    tvTime.text = call.timeString
                    tvCallType.setText(call.callTypeStringRes)
                    tvCallType.setTextColor(ContextCompat.getColor(root.context, call.callTypeColorRes))
                    ivCallType.setImageResource(call.callTypeDrawableRes)
                    ivCalltime.isVisible = call.isDurationVisible
                    tvDuration.text = call.durationText
                }
            }
        }
    }

    private fun loadPhoto(uri: String?) {
        val hasPhoto = !uri.isNullOrEmpty()
        binding.avatar.isVisible = hasPhoto

        if (hasPhoto) {
            binding.avatar.load(uri) {
                crossfade(true)
                error(R.drawable.error_on_background)
//                placeholder(R.drawable.face_in_recent_calls) // show while loading
            }
        }
    }

    private fun showPopup(anchor: View) {
        val isFavorite = viewModel.uiState.value.isFavorite
        val isPinned = viewModel.uiState.value.isPinned

        showActionMenu(anchor) {
            toggle(
                iconRes = R.drawable.star_on_button,
                labelRes = R.string.action_toggle_favorite,
                checked = isFavorite
            ) { checked ->
                viewModel.toggleFavorite()
                val resId =
                    if (checked) R.string.contact_added_to_favorites else R.string.contact_removed_from_favorites
                showSuccessSnackbar(resId)
            }

            toggle(
                iconRes = R.drawable.home_on_button,
                labelRes = R.string.action_pin_to_home,
                checked = isPinned
            ) { checked ->
                viewModel.toggleHomeScreenPin()
                val resId = if (checked) R.string.contact_added_to_home else R.string.contact_removed_from_home
                showSuccessSnackbar(resId)
            }

            separator()

            option(
                iconRes = R.drawable.share_on_background,
                labelRes = R.string.share,
                onClick = { shareContact() }
            )

            option(
                iconRes = R.drawable.edit_on_background,
                labelRes = R.string.edit,
                onClick = { editContactDetails() }
            )

            option(
                iconRes = R.drawable.delete_on_background,
                labelRes = R.string.delete,
                onClick = { showDeleteConfirmationDialog() }
            )
        }
    }

    private fun shareContact() {
        val contact = viewModel.uiState.value.contact ?: return
        val vcardUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, contact.lookupKey)
        val shareIntent =
            Intent(Intent.ACTION_SEND)
                .setType(ContactsContract.Contacts.CONTENT_VCARD_TYPE)
                .putExtra(Intent.EXTRA_STREAM, vcardUri)
                .putExtra(Intent.EXTRA_SUBJECT, contact.name)
        S.share(this, shareIntent)
    }

    private fun editContactDetails() {
        val contact = viewModel.uiState.value.contact ?: return
        val edit = Intent(this, AddContactActivity::class.java).putExtra(CONTACT_LOOKUP_KEY, contact.lookupKey)
        startActivity(edit)
    }

    private fun showDeleteConfirmationDialog() {
        val contact = viewModel.uiState.value.contact ?: return
        BaldDialog
            .Builder(this)
            .setTitle(R.string.dialog_delete_contact_title)
            .setMessage(getHtmlString(R.string.dialog_delete_contact_message, "<b>${contact.name.htmlEncode()}</b>"))
            .setPositiveButton(R.string.dialog_delete_contact_confirm) {
                viewModel.deleteContact()
            }.setNegativeButton(R.string.dialog_delete_contact_cancel)
            .show()
    }

    companion object {
        const val CONTACT_LOOKUP_KEY = "contactLookupKey"
        private const val TAG = "ContactDetailsActivity"

        /**
         * Open the BaldPhone contact details activity for the given contact.
         */
        fun openContact(
            context: Context,
            key: String
        ) {
            try {
                val intent =
                    Intent(context, ContactDetailsActivity::class.java).apply { putExtra(CONTACT_LOOKUP_KEY, key) }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast
                    .makeText(
                        context,
                        "Failed to open contact details: $key",
                        Toast.LENGTH_LONG
                    ).show()
            }
        }
    }
}
