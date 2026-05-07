package app.baldphone.neo.activities

import android.content.Intent
import android.os.Bundle

import app.baldphone.neo.Constants
import app.baldphone.neo.ui.dialogs.showInfoSnackbar
import app.baldphone.neo.utils.copyToClipboard
import app.baldphone.neo.utils.getDeviceInfoFull
import app.baldphone.neo.utils.openUrl

import com.bald.uriah.baldphone.BuildConfig
import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.activities.CreditsActivity
import com.bald.uriah.baldphone.databinding.ActivityAboutBinding
import com.bald.uriah.baldphone.utils.BDB
import com.bald.uriah.baldphone.utils.BDialog

class AboutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding) {
            aboutVersion.text = getString(R.string.about_version_title, BuildConfig.VERSION_NAME)
            aboutVersion.setOnClickListener { showTechnicalInfoDialog() }

            whatsNew.setOnClickListener { showSoonPopup() }
            webPage.setOnClickListener { openUrl(Constants.URL_GITHUB_REPO) }
            itemLicense.setOnClickListener { openUrl(Constants.URL_LICENSE) }
            thirdPartyLicenses.setOnClickListener { showSoonPopup() }
            itemPrivacy.setOnClickListener { showSoonPopup() }

            credits.setOnClickListener {
                startActivity(Intent(this@AboutActivity, CreditsActivity::class.java))
            }
        }
    }

    private fun showSoonPopup() {
        showInfoSnackbar(R.string.coming_soon)
    }

    private fun showTechnicalInfoDialog() {
        val deviceInfo = getDeviceInfoFull()
        BDB.from(this).setTitle(R.string.technical_information).setSubText(deviceInfo)
            .addFlag(BDialog.FLAG_CUSTOM_POSITIVE)
            .setPositiveCustomText(this.getString(android.R.string.copy))
            .setPositiveButtonListener { _: Array<Any?>? ->
                copyToClipboard("Device Info", deviceInfo)
                false
            }.show()
    }
}
