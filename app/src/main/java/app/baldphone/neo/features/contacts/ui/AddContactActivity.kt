package app.baldphone.neo.features.contacts.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.View
import android.widget.EditText

import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlinx.coroutines.launch

import coil3.load
import coil3.request.error

import app.baldphone.neo.R
import app.baldphone.neo.activities.BaseActivity
import app.baldphone.neo.databinding.ActivityAddEditContactBinding
import app.baldphone.neo.extensions.applyImeInsets
import app.baldphone.neo.features.contacts.ContactForm
import app.baldphone.neo.features.media.MediaMode
import app.baldphone.neo.features.media.ui.browser.MediaActivity
import app.baldphone.neo.permissions.PermissionManager
import app.baldphone.neo.permissions.model.RuntimePermission
import app.baldphone.neo.ui.dialogs.BaldDialog
import app.baldphone.neo.ui.dialogs.showErrorSnackbar
import app.baldphone.neo.ui.menu.showActionMenu

/**
 * Adds or edits a basic contact (name, up to two phone numbers, address, email, photo).
 *
 * - Add: a starting phone number can be prefilled via [CONTACT_NUMBER].
 * - Edit: triggered by passing [CONTACT_LOOKUP_KEY].
 */
class AddContactActivity : BaseActivity() {
    private lateinit var binding: ActivityAddEditContactBinding
    private val viewModel: AddContactViewModel by viewModels()

    private val pickPhotoLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let(viewModel::onPhotoPicked)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditContactBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this) { handleBack() }

        PermissionManager.checkOrRequest(this, RuntimePermission.ReadWriteContacts) {
            onGranted {
                setup()
            }
            onDenied {
                finish()
            }
        }
    }

    private fun setup() {
        binding.image.setOnClickListener { pickPhoto() }
        binding.buttonDelete.setOnClickListener { viewModel.clearPhoto() }
        binding.save.setOnClickListener { viewModel.save() }
        binding.baldTitleBar.setOnExitClickListener { handleBack() }

        binding.formScroll.applyImeInsets()

        val lookupKey = intent.getStringExtra(CONTACT_LOOKUP_KEY)
        if (!lookupKey.isNullOrEmpty()) {
            binding.baldTitleBar.setTitle(R.string.edit_contact)
            binding.baldTitleBar.setOnMoreClickListener { anchor -> showMoreMenu(anchor, lookupKey) }
        }

        setupTextListeners()
        observeState()
    }

    private fun showMoreMenu(anchor: View, lookupKey: String) {
        showActionMenu(anchor) {
            option(
                iconRes = R.drawable.edit_on_background,
                labelRes = R.string.edit_in_contacts_app,
                onClick = {
                    if (viewModel.hasUnsavedChanges()) {
                        showUnsavedChangesDialog { editContactInSystemApp(lookupKey) }
                    } else {
                        editContactInSystemApp(lookupKey)
                    }
                }
            )
        }
    }

    private fun editContactInSystemApp(lookupKey: String) {
        val contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
        val editIntent =
            Intent(Intent.ACTION_EDIT).apply {
                setDataAndType(contactUri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                putExtra("finishActivityOnSaveCompleted", true)
            }

        try {
            startActivity(editIntent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No app found to edit contact", e)
            showErrorSnackbar(R.string.no_app_was_found)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied editing contact in system app", e)
            showErrorSnackbar(R.string.an_error_has_occurred)
        }
    }

    private fun setupTextListeners() {
        listOf(
            binding.firstName,
            binding.lastName,
            binding.mobileNumber,
            binding.homeNumber,
            binding.address,
            binding.mail
        ).forEach { editText ->
            editText.doAfterTextChanged {
                viewModel.updateDraft(readForm())
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { updateUi(it) }
                }
                launch {
                    viewModel.errors.collect { showErrorSnackbar(it) }
                }
                launch {
                    // The contact was deleted in the system Contacts app; nothing left to edit.
                    viewModel.contactDeleted.collect { finish() }
                }
            }
        }
    }

    private fun updateUi(state: AddContactViewModel.AddContactUiState) {
        fun updateText(editText: EditText, newText: String) {
            if (!editText.hasFocus() && !editText.text.contentEquals(newText)) {
                editText.setText(newText)
            }
        }

        state.currentDraft?.let { draft ->
            updateText(binding.firstName, draft.givenName)
            updateText(binding.lastName, draft.familyName)
            updateText(binding.mobileNumber, draft.preferredPhone)
            updateText(binding.homeNumber, draft.otherPhone)
            updateText(binding.address, draft.address)
            updateText(binding.mail, draft.email)
        }

        val photo: Any = state.photoUri ?: R.drawable.ic_add_photo
        if (binding.image.tag != photo) {
            binding.image.tag = photo
            binding.image.load(photo) {
                error(R.drawable.broken_image)
            }
        }
        binding.buttonDelete.isVisible = state.photoUri != null
        binding.save.isEnabled = !state.isSaving

        if (state.saveSuccess) {
            finish()
        }
    }

    private fun pickPhoto() {
        val intent =
            Intent(this, MediaActivity::class.java)
                .setAction(Intent.ACTION_GET_CONTENT)
                .putExtra(MediaMode.EXTRA, MediaMode.PHOTOS)
        pickPhotoLauncher.launch(intent)
    }

    private fun readForm(): ContactForm =
        ContactForm(
            givenName = binding.firstName.text.toString(),
            familyName = binding.lastName.text.toString(),
            preferredPhone = binding.mobileNumber.text.toString(),
            otherPhone = binding.homeNumber.text.toString(),
            address = binding.address.text.toString(),
            email = binding.mail.text.toString()
        )

    private fun handleBack() {
        if (viewModel.hasUnsavedChanges()) {
            showUnsavedChangesDialog { finish() }
        } else {
            finish()
        }
    }

    private fun showUnsavedChangesDialog(onDiscard: () -> Unit) {
        BaldDialog
            .Builder(this)
            .setTitle(R.string.discard_changes_title)
            .setMessage(R.string.discard_changes_message)
            .setPositiveButton(R.string.discard) { onDiscard() }
            .setNegativeButton(R.string.cancel)
            .show()
    }

    companion object {
        private const val TAG = "AddContactActivity"

        const val CONTACT_NUMBER = "CONTACT_NUMBER"

        /** Intent extra carrying the lookup key of the contact to edit. */
        const val CONTACT_LOOKUP_KEY = "contactLookupKey"

        /** Entry point to add a contact with an optional prefilled number. */
        fun start(context: Context, number: String?) {
            val intent = Intent(context, AddContactActivity::class.java).putExtra(CONTACT_NUMBER, number)
            context.startActivity(intent)
        }
    }
}
