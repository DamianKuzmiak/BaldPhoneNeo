package app.baldphone.neo.settings.ui

import android.os.Bundle
import android.view.View

import app.baldphone.neo.R
import app.baldphone.neo.data.Prefs
import app.baldphone.neo.settings.BaseSettingsFragment
import app.baldphone.neo.views.SettingsSwitchButton

class CallsSettingsFragment : BaseSettingsFragment(R.layout.fragment_calls_settings) {
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val btConfirmCalls = view.findViewById<SettingsSwitchButton>(R.id.bt_confirm_calls)
        btConfirmCalls.apply {
            setChecked(Prefs.shouldConfirmCalls)
            setOnCheckedChangeListener { isChecked ->
                Prefs.shouldConfirmCalls = isChecked
            }
        }
    }
}
