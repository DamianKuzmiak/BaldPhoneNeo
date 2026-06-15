package app.baldphone.neo.settings.help

import android.content.Intent
import android.os.Bundle
import android.view.View

import androidx.navigation.fragment.findNavController

import app.baldphone.neo.Constants
import app.baldphone.neo.settings.BaseSettingsFragment
import app.baldphone.neo.ui.dialogs.BaldDialog
import app.baldphone.neo.ui.dialogs.BaldSnackbar
import app.baldphone.neo.utils.copyToClipboard
import app.baldphone.neo.utils.getDeviceInfoFull
import app.baldphone.neo.utils.openUrl

import com.bald.uriah.baldphone.BuildConfig
import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.activities.CreditsActivity
import com.bald.uriah.baldphone.databinding.FragmentSettingsAboutBinding

class SettingsAboutFragment : BaseSettingsFragment(R.layout.fragment_settings_about) {
    private var binding: FragmentSettingsAboutBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentSettingsAboutBinding.bind(view)

        setupUI()
    }

    private fun setupUI() =
        with(binding!!) {
            aboutVersion.apply {
                text = getString(R.string.about_version_title, BuildConfig.VERSION_NAME)
                setOnClickListener { showTechnicalInfoDialog() }
            }

            whatsNew.setOnClickListener { showComingSoon() }
            webPage.setOnClickListener { requireContext().openUrl(Constants.URL_GITHUB_REPO) }

            credits.setOnClickListener { startActivity(Intent(requireContext(), CreditsActivity::class.java)) }

            itemLicense.setOnClickListener { findNavController().navigate(R.id.action_about_to_license) }
            thirdPartyLicenses.setOnClickListener { showComingSoon() }
            itemPrivacy.setOnClickListener { showComingSoon() }
        }

    private fun showComingSoon() {
        BaldSnackbar.show(requireActivity(), R.string.coming_soon, BaldSnackbar.TYPE_INFO)
    }

    private fun showTechnicalInfoDialog() {
        val context = requireContext()
        val deviceInfo = context.getDeviceInfoFull()

        BaldDialog
            .Builder(context)
            .setTitle(R.string.technical_information)
            .setMessage(deviceInfo)
            .setPositiveButton(android.R.string.copy) {
                context.copyToClipboard("Device Info", deviceInfo)
            }.setNegativeButton(android.R.string.cancel)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
