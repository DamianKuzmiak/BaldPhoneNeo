package app.baldphone.neo.settings.ui

import android.os.Bundle
import android.view.View

import app.baldphone.neo.R
import app.baldphone.neo.data.Prefs
import app.baldphone.neo.databinding.ContentCustomPhoneBinding
import app.baldphone.neo.databinding.FragmentHomeSettingsBinding
import app.baldphone.neo.settings.BaseSettingsFragment
import app.baldphone.neo.utils.PhoneNumberUtils
import app.baldphone.neo.utils.getDeviceRegion

class HomeSettingsFragment : BaseSettingsFragment(R.layout.fragment_home_settings) {
    private var binding: FragmentHomeSettingsBinding? = null
    private var contentBinding: ContentCustomPhoneBinding? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val binding = FragmentHomeSettingsBinding.bind(view).also { this.binding = it }
        val contentView = binding.btCustomPhoneSwitch.setContentView(R.layout.content_custom_phone)
        val content = ContentCustomPhoneBinding.bind(contentView).also { contentBinding = it }

        binding.btCustomPhoneSwitch.setChecked(Prefs.isHomePhoneNumberEnabled)
        binding.btCustomPhoneSwitch.setContentVisible(Prefs.isHomePhoneNumberEnabled)
        content.etCustomPhoneNumber.setText(Prefs.homePhoneNumber.orEmpty())

        binding.btCustomPhoneSwitch.setOnCheckedChangeListener { isChecked ->
            Prefs.isHomePhoneNumberEnabled = isChecked
            binding.btCustomPhoneSwitch.setContentVisible(isChecked)
        }

        // Format the input visually when focus is lost; the number is persisted on view exit.
        content.etCustomPhoneNumber.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                formatPhoneNumberInput(content)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        contentBinding?.let { content ->
            val formatted = formatPhoneNumberInput(content)
            persistPhoneNumber(formatted)

            // An empty number means there is nothing to display, so disable the feature.
            if (formatted.isEmpty()) {
                Prefs.isHomePhoneNumberEnabled = false
                binding?.btCustomPhoneSwitch?.setChecked(false)
                binding?.btCustomPhoneSwitch?.setContentVisible(false)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        contentBinding = null
    }

    private fun formatPhoneNumberInput(content: ContentCustomPhoneBinding): String {
        val etNumber = content.etCustomPhoneNumber
        val entered =
            etNumber.text
                ?.toString()
                ?.trim()
                .orEmpty()

        if (entered.isEmpty()) return ""

        val region = requireContext().getDeviceRegion()
        val formatted = PhoneNumberUtils.formatSmartly(entered, region)
        if (entered != formatted) {
            etNumber.setText(formatted)
        }
        return formatted
    }

    /**
     * Persists the phone number: a non-empty value is stored as-is, an empty value clears the preference.
     */
    private fun persistPhoneNumber(value: String) {
        Prefs.homePhoneNumber = value.ifEmpty { null }
    }
}
