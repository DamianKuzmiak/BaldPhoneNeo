package app.baldphone.neo.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import com.bald.uriah.baldphone.BuildConfig

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.activities.BaldActivity
import com.bald.uriah.baldphone.activities.TechnicalInfoActivity
import com.bald.uriah.baldphone.utils.BaldToast

class FeedbackActivity : BaldActivity() {

    private lateinit var feedbackInput: EditText
    private lateinit var advancedSection: LinearLayout
    private lateinit var advancedToggle: TextView
    private var advancedVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        feedbackInput = findViewById(R.id.feedback_input)
        advancedSection = findViewById(R.id.feedback_advanced_section)
        advancedToggle = findViewById(R.id.feedback_advanced_toggle)

        findViewById<Button>(R.id.feedback_send_button).setOnClickListener {
            submitFeedback()
        }

        advancedToggle.setOnClickListener { toggleAdvanced() }


        findViewById<Button>(R.id.button_crash_report).setOnClickListener {
//            startActivity(Intent(this, CrashReportActivity::class.java))
        }

        findViewById<Button>(R.id.button_technical_info).setOnClickListener {
            startActivity(Intent(this, TechnicalInfoActivity::class.java))
        }
    }

    private fun toggleAdvanced() {
        advancedVisible = !advancedVisible
        advancedSection.visibility = if (advancedVisible) View.VISIBLE else View.GONE
        val icon =
            if (advancedVisible) R.drawable.drop_up_on_button else R.drawable.drop_down_on_button
        advancedToggle.setCompoundDrawablesWithIntrinsicBounds(0, 0, icon, 0)
    }

    private fun submitFeedback() {
        val message = feedbackInput.text.toString().trim()
        if (message.isEmpty()) {
            BaldToast.from(this).setType(BaldToast.TYPE_INFORMATIVE)
                .setText(R.string.feedback_cannot_be_empty).show()
            return
        }

        val body = buildString {
            append(message)
            append("\n\n---\n")
            append(getString(R.string.feedback_app_version, BuildConfig.VERSION_NAME))
            append("\n")
            append(getString(R.string.feedback_device, Build.MODEL))
            append("\n")
            append(getString(R.string.feedback_android_version, Build.VERSION.RELEASE))
        }

        Intent(Intent.ACTION_SENDTO).apply {
            data = ("mailto:" + getString(R.string.app_contact_email)).toUri()
            putExtra(
                Intent.EXTRA_SUBJECT,
                getString(R.string.feedback_email_subject, getString(R.string.app_display_name))
            )
            putExtra(Intent.EXTRA_TEXT, body)
            startExternalActivity(this, getString(R.string.feedback_choose_email_app))
        }
    }

    private fun startExternalActivity(intent: Intent, chooserTitle: String?) {
        try {
            val activityIntent = chooserTitle?.let { Intent.createChooser(intent, it) } ?: intent
            startActivity(activityIntent)
        } catch (_: ActivityNotFoundException) {
            BaldToast.from(this).setType(BaldToast.TYPE_ERROR)
                .setText(R.string.feedback_no_email_app_found).show()
        }
    }

    override fun requiredPermissions(): Int {
        return PERMISSION_NONE
    }
}
