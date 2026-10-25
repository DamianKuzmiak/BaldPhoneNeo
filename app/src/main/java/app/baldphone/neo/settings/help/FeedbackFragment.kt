// Copyright (c) 2026 Damian Kuźmiak
// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.settings.help

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View

import app.baldphone.neo.Constants
import app.baldphone.neo.R
import app.baldphone.neo.databinding.FragmentFeedbackBinding
import app.baldphone.neo.settings.BaseSettingsFragment
import app.baldphone.neo.ui.dialogs.showWarningSnackbar
import app.baldphone.neo.utils.getDeviceInfoFull
import app.baldphone.neo.utils.startActivitySafe

class FeedbackFragment : BaseSettingsFragment(R.layout.fragment_feedback) {
    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFeedbackBinding.bind(view)

        with(binding) {
            if (draft.isNotEmpty()) {
                feedbackInput.setText(draft)
            }

            feedbackSendButton.setOnClickListener { sendFeedback() }
        }
    }

    private fun sendFeedback() {
        val message =
            binding.feedbackInput.text
                ?.toString()
                .orEmpty()
                .trim()
        if (message.isEmpty()) {
            requireActivity().showWarningSnackbar(R.string.feedback_cannot_be_empty)
            return
        }

        val body =
            buildString {
                append(message)
                if (binding.feedbackIncludeInfoCheckbox.isChecked) {
                    append("\n\n---\n")
                    append(requireContext().getDeviceInfoFull())
                }
            }

        launchEmailApp(body)

        // The feedback has been likely handed off to an email app; drop the draft
        draft = ""
        binding.feedbackInput.text?.clear()
    }

    private fun launchEmailApp(body: String) {
        val subject =
            getString(
                R.string.feedback_email_subject,
                getString(R.string.app_display_name)
            )

        val intent =
            Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.fromParts("mailto", Constants.APP_CONTACT_EMAIL, null)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }

        val chooserIntent = Intent.createChooser(intent, getString(R.string.feedback_choose_email_app))
        requireContext().startActivitySafe(
            chooserIntent,
            errorMsgRes = R.string.feedback_no_email_app_found
        )
    }

    override fun onDestroyView() {
        _binding?.let { binding ->
            draft =
                binding.feedbackInput.text
                    ?.toString()
                    .orEmpty()
        }
        super.onDestroyView()
        _binding = null
    }

    companion object {
        // In-memory store for the feedback draft typed in [FeedbackFragment].
        private var draft: String = ""
    }
}
