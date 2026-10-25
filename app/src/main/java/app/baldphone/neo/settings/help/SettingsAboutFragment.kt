// Copyright (c) 2026 Damian Kuźmiak
// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.settings.help

import android.os.Bundle
import android.util.Log
import android.view.View

import androidx.navigation.fragment.findNavController

import coil3.SingletonImageLoader

import app.baldphone.neo.BuildConfig
import app.baldphone.neo.Constants
import app.baldphone.neo.R
import app.baldphone.neo.databinding.FragmentSettingsAboutBinding
import app.baldphone.neo.settings.BaseSettingsFragment
import app.baldphone.neo.ui.dialogs.BaldDialog
import app.baldphone.neo.ui.dialogs.BaldSnackbar
import app.baldphone.neo.utils.copyToClipboard
import app.baldphone.neo.utils.getDeviceInfoFull
import app.baldphone.neo.utils.openUrl

class SettingsAboutFragment : BaseSettingsFragment(R.layout.fragment_settings_about) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentSettingsAboutBinding.bind(view)

        with(binding) {
            aboutVersion.text = getString(R.string.about_version_title, BuildConfig.VERSION_NAME)
            aboutVersion.setOnClickListener {
                showTechnicalInfoDialog()
                logCoilCacheStatus()
            }

            feedbackLink.setOnClickListener { findNavController().navigate(R.id.action_about_to_feedback) }

            whatsNew.setOnClickListener { showComingSoon() }
            webPage.setOnClickListener { requireContext().openUrl(Constants.URL_GITHUB_REPO) }

            credits.setOnClickListener { findNavController().navigate(R.id.action_about_to_credits) }

            itemPrivacy.setOnClickListener { showComingSoon() }
            itemLicense.setOnClickListener { findNavController().navigate(R.id.action_about_to_license) }
            thirdPartyLicenses.setOnClickListener { showComingSoon() }
        }
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
            .setPositiveButton(android.R.string.copy) { context.copyToClipboard("Device Info", deviceInfo) }
            .setNegativeButton(android.R.string.cancel)
            .show()
    }

    private fun logCoilCacheStatus() {
        val memoryCache = SingletonImageLoader.get(requireContext()).memoryCache
        memoryCache?.let { cache ->
            cache.keys.forEachIndexed { index, key ->
                val image = cache[key]?.image
                if (image != null) {
                    val dimensions = "${image.width}x${image.height}"
                    val sizeInKB = image.size / BYTES_IN_KB

                    Log.d("CoilCache", "id: $index, key: ${key.key} | Size: $dimensions | Memory: ${sizeInKB}KB")
                }
            }
        }
    }

    companion object {
        private const val BYTES_IN_KB = 1024
    }
}
