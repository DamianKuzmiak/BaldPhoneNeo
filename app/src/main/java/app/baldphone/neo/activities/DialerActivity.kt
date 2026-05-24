package app.baldphone.neo.activities

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View

import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import kotlinx.coroutines.launch

import app.baldphone.neo.data.Prefs
import app.baldphone.neo.features.calls.CallUiHelper
import app.baldphone.neo.features.contacts.data.ContactRepository
import app.baldphone.neo.features.contacts.ui.ContactAdapter
import app.baldphone.neo.features.contacts.ui.ContactDetailsActivity
import app.baldphone.neo.permissions.PermissionManager
import app.baldphone.neo.permissions.RuntimePermission
import app.baldphone.neo.utils.getTextFromClipboard
import app.baldphone.neo.viewmodels.DialerViewModel

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.activities.contacts.AddContactActivity
import com.bald.uriah.baldphone.databinding.DialerBinding
import com.bald.uriah.baldphone.databinding.DialpadButtonBinding

class DialerActivity : BaseActivity() {
    companion object {
        private const val TAG = "DialerActivity"
        private const val TONE_LENGTH_MS = 300
        private const val TONE_VOLUME = 80 // percent
    }

    private val viewModel: DialerViewModel by viewModels()
    private lateinit var binding: DialerBinding
    private var dtmfManager: DtmfManager? = null
    private var isPageLoading = false

    private val adapter by lazy {
        ContactAdapter(
            showPhoneNumbers = true
        ) { contact -> ContactDetailsActivity.openContact(this, contact.lookupKey) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DialerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize DTMF tone generator if enabled
        dtmfManager =
            if (Prefs.areDialerSoundsEnabled) {
                try {
                    DtmfManager(TONE_VOLUME, TONE_LENGTH_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize DTMF tone generator", e)
                    null
                }
            } else {
                null
            }

        setupRecyclerView()
        setupDialPad()
        setupEmptyState()
        setupPermissions()
        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.formattedNumber
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { formatted ->
                    binding.dialPad.tvNumber.text = formatted
                    val isNumberEmpty = formatted.isEmpty()
                    updateBackspaceState(isNumberEmpty)
                    updateEmptyStateVisibility(adapter.itemCount > 0)
                }
        }
    }

    private fun setupPermissions() {
        PermissionManager
            .with(this)
            .add(RuntimePermission.CallPhone) {
                onDenied { finish() }
            }.add(RuntimePermission.ReadWriteContacts) {
                onGranted {
                    updateEmptyStatePermissionUI()
                    viewModel.refresh()
                    observeSearchResults()
                }
            }.request()
    }

    override fun onDestroy() {
        dtmfManager?.release()
        dtmfManager = null
        super.onDestroy()
    }

    private fun observeSearchResults() {
        lifecycleScope.launch {
            viewModel.searchResults
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { contacts ->
                    adapter.submitList(contacts)
                    updateEmptyStateVisibility(contacts?.isNotEmpty() ?: false)
                    isPageLoading = false
                }
        }
    }

    private fun setupRecyclerView() {
        binding.contactsRecyclerView.apply {
            val lm = LinearLayoutManager(this@DialerActivity)
            layoutManager = lm
            adapter = this@DialerActivity.adapter
            setHasFixedSize(true)
            addItemDecoration(createDivider())

            addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                        if (dy <= 0 || isPageLoading) return
                        val lastVisible = lm.findLastVisibleItemPosition()
                        val totalItems = adapter?.itemCount ?: 0
                        val triggerIndex = totalItems - 10
                        if (totalItems >= ContactRepository.PAGE_SIZE && lastVisible >= triggerIndex) {
                            isPageLoading = true
                            Log.d(
                                "DialerActivity",
                                "Scroll trigger: lastVisible=$lastVisible, total=$totalItems. Loading next page."
                            )
                            viewModel.loadNextPage()
                        }
                    }
                }
            )
        }
    }

    private fun createDivider() =
        DividerItemDecoration(this, DividerItemDecoration.VERTICAL).apply {
            ContextCompat.getDrawable(this@DialerActivity, R.drawable.ll_divider)?.let {
                setDrawable(it)
            }
        }

    private fun setupDialPad() =
        with(binding.dialPad) {
            val buttonList =
                listOf(
                    DialKey(b0, '0', "+", ToneGenerator.TONE_DTMF_0),
                    DialKey(b1, '1', null, ToneGenerator.TONE_DTMF_1),
                    DialKey(b2, '2', getString(R.string.t9_key_2), ToneGenerator.TONE_DTMF_2),
                    DialKey(b3, '3', getString(R.string.t9_key_3), ToneGenerator.TONE_DTMF_3),
                    DialKey(b4, '4', getString(R.string.t9_key_4), ToneGenerator.TONE_DTMF_4),
                    DialKey(b5, '5', getString(R.string.t9_key_5), ToneGenerator.TONE_DTMF_5),
                    DialKey(b6, '6', getString(R.string.t9_key_6), ToneGenerator.TONE_DTMF_6),
                    DialKey(b7, '7', getString(R.string.t9_key_7), ToneGenerator.TONE_DTMF_7),
                    DialKey(b8, '8', getString(R.string.t9_key_8), ToneGenerator.TONE_DTMF_8),
                    DialKey(b9, '9', getString(R.string.t9_key_9), ToneGenerator.TONE_DTMF_9),
                    DialKey(bStar, '*', null, ToneGenerator.TONE_DTMF_S),
                    DialKey(bHash, '#', null, ToneGenerator.TONE_DTMF_P)
                )

            buttonList.forEach { key ->
                key.view.tvDigit.text = key.digit.toString()
                key.view.tvLetters.text = key.letters
                key.view.root.contentDescription = key.digit.toString()
                key.view.root.setOnClickListener {
                    handleDialClick(key.digit, key.tone)
                }
            }

            b0.root.setOnLongClickListener {
                handleDialClick('+', ToneGenerator.TONE_DTMF_0)
                true
            }

            bBackspace.setOnClickListener {
                viewModel.removeLastDigit()
            }

            bBackspace.setOnLongClickListener {
                viewModel.clearNumber()
                true
            }

            bCall.setOnClickListener {
                CallUiHelper.call(this@DialerActivity, viewModel.rawNumber.value, directly = false)
            }

            tvNumber.setOnLongClickListener {
                pasteNumberFromClipboard()
            }
        }

    private fun handleDialClick(char: Char, tone: Int) {
        binding.dialPad.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        dtmfManager?.playTone(tone)
        viewModel.addDigit(char)
    }

    private fun setupEmptyState() {
        binding.emptyStateContainer.btAddContact.setOnClickListener {
            if (RuntimePermission.ReadWriteContacts.isGranted(this)) {
                val intent =
                    Intent(this, AddContactActivity::class.java).apply {
                        putExtra(AddContactActivity.CONTACT_NUMBER, viewModel.rawNumber.value)
                    }
                startActivity(intent)
            } else {
                PermissionManager.checkOrRequest(this, RuntimePermission.ReadWriteContacts) {
                    onGranted {
                        updateEmptyStatePermissionUI()
                        observeSearchResults()
                    }
                }
            }
        }

        updateEmptyStatePermissionUI()
    }

    private fun updateEmptyStatePermissionUI() {
        val canReadWriteContacts = RuntimePermission.ReadWriteContacts.isGranted(this)

        val iconRes =
            if (canReadWriteContacts) R.drawable.add_outlined_on_background else R.drawable.ic_info
        val textRes =
            if (canReadWriteContacts) R.string.add_contact else R.string.grant_contacts_permission

        binding.emptyStateContainer.ivIcon.setImageResource(iconRes)
        binding.emptyStateContainer.tvText.setText(textRes)
    }

    private fun updateEmptyStateVisibility(hasContacts: Boolean) {
        if (hasContacts) {
            binding.emptyStateContainer.root.visibility = View.GONE
            binding.scrollingHelper.visibility = View.VISIBLE
        } else {
            binding.emptyStateContainer.root.visibility = View.VISIBLE
            binding.scrollingHelper.visibility = View.INVISIBLE
        }
    }

    private fun updateBackspaceState(isEmpty: Boolean) {
        binding.dialPad.bBackspace.apply {
            alpha = if (isEmpty) 0.5f else 1.0f
            isEnabled = !isEmpty
        }
    }

    private fun pasteNumberFromClipboard(): Boolean {
        val pasteData = getTextFromClipboard()
        if (!pasteData.isNullOrEmpty()) {
            val filtered = pasteData.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
            if (filtered.isNotEmpty()) {
                viewModel.setNumber(filtered.toString())
                return true
            }
        }
        return false
    }

    private data class DialKey(
        val view: DialpadButtonBinding,
        val digit: Char,
        val letters: String?,
        val tone: Int
    )

    private class DtmfManager(volume: Int, private val duration: Int) {
        private val toneGenerator = ToneGenerator(AudioManager.STREAM_DTMF, volume)

        fun playTone(tone: Int) {
            toneGenerator.startTone(tone, duration)
        }

        fun release() {
            toneGenerator.release()
        }
    }
}
