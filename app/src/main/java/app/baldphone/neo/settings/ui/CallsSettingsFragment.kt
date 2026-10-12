package app.baldphone.neo.settings.ui

import android.os.Bundle
import android.view.View

import androidx.core.view.isVisible

import app.baldphone.neo.R
import app.baldphone.neo.data.Prefs
import app.baldphone.neo.features.calls.CallUtils
import app.baldphone.neo.settings.BaseSettingsFragment
import app.baldphone.neo.settings.views.SettingsSwitchButtonView

class CallsSettingsFragment : BaseSettingsFragment(R.layout.fragment_calls_settings) {
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val btConfirmCalls = view.findViewById<SettingsSwitchButtonView>(R.id.bt_confirm_calls)
        btConfirmCalls.apply {
            setChecked(Prefs.shouldConfirmCalls)
            setOnCheckedChangeListener { isChecked ->
                Prefs.shouldConfirmCalls = isChecked
            }
        }

        // We check Dual SIM visibility only once when the view is created.
        // To keep the code simple, we ignore rare edge cases like inserting/removing
        // a SIM card while this screen is open or returning from system settings.
        if (CallUtils.hasMultipleSims(requireContext())) {
            val btDualSim = view.findViewById<SettingsSwitchButtonView>(R.id.bt_dual_sim)
            btDualSim.apply {
                isVisible = true
                setChecked(Prefs.isDualSimActive)
                setOnCheckedChangeListener { isChecked ->
                    Prefs.isDualSimActive = isChecked
                }
            }
        }
    }
}
