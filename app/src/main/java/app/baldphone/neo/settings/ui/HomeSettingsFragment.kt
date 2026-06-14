package app.baldphone.neo.settings.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.fragment.app.Fragment

import app.baldphone.neo.data.Prefs
import app.baldphone.neo.extensions.setClickableAccessibilityRole
import app.baldphone.neo.settings.BaseSettingsFragment
import app.baldphone.neo.utils.PhoneNumberUtils
import app.baldphone.neo.utils.getDeviceRegion

import com.bald.uriah.baldphone.databinding.FragmentHomeSettingsBinding

class HomeSettingsFragment : BaseSettingsFragment() {
    private var _binding: FragmentHomeSettingsBinding? = null
    val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val isNumberEnabled = Prefs.isHomePhoneNumberEnabled
        val currentNumber = Prefs.homePhoneNumber ?: ""

        binding.apply {
            switchCustomPhone.isChecked = isNumberEnabled
            etCustomPhoneNumber.isEnabled = isNumberEnabled
            etCustomPhoneNumber.setText(currentNumber)

            btCustomPhoneSwitch.setClickableAccessibilityRole()
            btCustomPhoneSwitch.setOnClickListener {
                val isChecked = !binding.switchCustomPhone.isChecked
                switchCustomPhone.isChecked = isChecked
                etCustomPhoneNumber.isEnabled = isChecked
            }
        }
    }

    private fun saveToPrefs() {
        if (_binding == null) return

        val isChecked = binding.switchCustomPhone.isChecked
        Prefs.isHomePhoneNumberEnabled = isChecked

        val entered =
            binding.etCustomPhoneNumber.text
                .toString()
                .trim()
        if (entered.isNotEmpty()) {
            val region = requireContext().getDeviceRegion()
            val formatted = PhoneNumberUtils.formatSmartly(entered, region)
            if (entered != formatted) {
                binding.etCustomPhoneNumber.setText(formatted)
            }
            Prefs.homePhoneNumber = formatted
        } else {
            Prefs.homePhoneNumber = null
        }
    }

    override fun onPause() {
        super.onPause()
        saveToPrefs()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
