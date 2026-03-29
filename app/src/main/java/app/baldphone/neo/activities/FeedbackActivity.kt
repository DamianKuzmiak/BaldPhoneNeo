package app.baldphone.neo.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle

import androidx.core.net.toUri
import androidx.core.view.isVisible

import app.baldphone.neo.Constants
import app.baldphone.neo.crashes.CrashViewerActivity
import app.baldphone.neo.ui.dialogs.showErrorSnackbar
import app.baldphone.neo.ui.dialogs.showWarningSnackbar
import app.baldphone.neo.utils.getDeviceInfoFull

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.databinding.ActivityFeedbackBinding

class FeedbackActivity : BaseActivity() {
    private lateinit var binding: ActivityFeedbackBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.apply {
            feedbackSendButton.setOnClickListener { sendFeedback() }
            feedbackAdvancedToggle.setOnClickListener { toggleAdvanced() }
            buttonCrashReport.setOnClickListener {
                startActivity(Intent(root.context, CrashViewerActivity::class.java))
            }
            buttonViewLogs.setOnClickListener {
                startActivity(Intent(root.context, LogActivity::class.java))
            }
        }
    }

    private fun toggleAdvanced() {
        val isVisible = binding.feedbackAdvancedSection.isVisible
        val newVisibility = !isVisible

        binding.feedbackAdvancedSection.isVisible = newVisibility

        val icon = if (newVisibility) R.drawable.drop_up_on_button else R.drawable.drop_down_on_button
        binding.feedbackAdvancedToggle.setCompoundDrawablesWithIntrinsicBounds(0, 0, icon, 0)
    }

    private fun sendFeedback() {
        val message =
            binding.feedbackInput.text
                .toString()
                .trim()
        if (message.isEmpty()) {
            showWarningSnackbar(R.string.feedback_cannot_be_empty)
            return
        }
        val body =
            buildString {
                append(message)
                if (binding.feedbackIncludeInfoCheckbox.isChecked) {
                    append("\n\n---\n")
                    append(getDeviceInfoFull())
                }
            }
        launchEmailApp(body)
    }

    private fun launchEmailApp(body: String) {
        val subject =
            getString(
                R.string.feedback_email_subject,
                getString(R.string.app_display_name)
            )

        val intent =
            Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:${Constants.APP_CONTACT_EMAIL}".toUri()
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }

        launchChooserSafely(intent, getString(R.string.feedback_choose_email_app))
    }

    private fun launchChooserSafely(intent: Intent, chooserTitle: String) {
        try {
            val finalIntent = Intent.createChooser(intent, chooserTitle)
            startActivity(finalIntent)
        } catch (_: ActivityNotFoundException) {
            showErrorSnackbar(R.string.feedback_no_email_app_found)
        }
    }
}
