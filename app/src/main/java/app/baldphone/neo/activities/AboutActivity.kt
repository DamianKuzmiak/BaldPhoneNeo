package app.baldphone.neo.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.net.toUri
import com.bald.uriah.baldphone.BuildConfig
import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.activities.BaldActivity
import com.bald.uriah.baldphone.activities.CreditsActivity
import com.bald.uriah.baldphone.utils.BaldToast

class AboutActivity : BaldActivity() {

    companion object {
        private val TAG = AboutActivity::class.java.simpleName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val aboutVersion = findViewById<TextView>(R.id.about_version_top)
        aboutVersion.text = getString(R.string.about_version_title, BuildConfig.VERSION_NAME)

        setupItem(
            R.id.item_changelog,
            R.drawable.ic_update,
            getString(R.string.about_changelog),
            getString(R.string.url_changelog),
            true
        ) { openUrl(getString(R.string.url_changelog)) }

        setupItem(
            R.id.item_github,
            R.drawable.ic_github,
            getString(R.string.about_github),
            getString(R.string.url_github_repo),
            true
        ) { openUrl(getString(R.string.url_github_repo)) }

        // ========== CREDITS SECTION ==========
        setupItem(
            R.id.item_thanks,
            R.drawable.ic_handshake,
            getString(R.string.about_thanks_title),
            getString(R.string.about_thanks_text),
            true
        ) { openUrl(getString(R.string.url_origin_project)) }

        setupItem(
            R.id.item_contributors, R.drawable.ic_people, getString(R.string.about_contributors)
        ) { startActivity(Intent(this, CreditsActivity::class.java)) }

        // ========== LEGAL SECTION ==========
        setupItem(
            R.id.item_app_license,
            R.drawable.ic_licence,
            getString(R.string.about_app_license),
            null,
            true
        ) { openUrl(getString(R.string.url_app_license)) }

        setupItem(
            R.id.item_licenses,
            R.drawable.ic_description,
            getString(R.string.about_licenses),
            null,
            true
        ) { openUrl(getString(R.string.url_third_party_licenses)) }

        setupItem(
            R.id.item_privacy,
            R.drawable.ic_privacy_tip,
            getString(R.string.about_privacy_policy),
            null,
            true
        ) { openUrl(getString(R.string.url_privacy_policy)) }
    }

    private fun setupItem(
        containerId: Int,
        @DrawableRes iconRes: Int,
        title: String,
        summary: String? = null,
        hasExternalLink: Boolean = false,
        listener: ((View) -> Unit)? = null
    ) {
        val item = findViewById<View>(containerId)
        item.findViewById<ImageView>(R.id.icon).setImageResource(iconRes)
        item.findViewById<TextView>(R.id.title).text = title
        val summaryView = item.findViewById<TextView>(R.id.summary)
        if (summary != null) {
            summaryView.text = summary
        } else {
            summaryView.visibility = View.GONE
        }

        item.findViewById<View>(R.id.icon_link).visibility =
            if (hasExternalLink) View.VISIBLE else View.GONE

        if (listener != null) {
            item.setOnClickListener(listener)
        } else {
            item.isEnabled = false
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Cannot open URL: " + e.message)
            BaldToast.from(this).setText("No app found to open URL").setType(BaldToast.TYPE_ERROR)
                .show()
        }
    }

    override fun requiredPermissions(): Int {
        return PERMISSION_NONE
    }
}
