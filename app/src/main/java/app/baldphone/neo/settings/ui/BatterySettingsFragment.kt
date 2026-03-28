package app.baldphone.neo.settings.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView

import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

import app.baldphone.neo.battery.alert.BatteryMonitor
import app.baldphone.neo.data.Prefs
import app.baldphone.neo.extensions.setClickableAccessibilityRole
import app.baldphone.neo.settings.BaseSettingsFragment

import com.bald.uriah.baldphone.R

class BatterySettingsFragment : BaseSettingsFragment(R.layout.fragment_battery_settings) {
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val btBatteryAlert = view.findViewById<View>(R.id.bt_battery_alert)
        val cbBatteryAlert = view.findViewById<MaterialSwitch>(R.id.cb_battery_alert)
        val sliderThreshold = view.findViewById<Slider>(R.id.slider_battery_threshold)
        val tvThreshold = view.findViewById<TextView>(R.id.tv_battery_threshold)

        cbBatteryAlert.isChecked = Prefs.isBatteryAlertEnabled

        btBatteryAlert.setClickableAccessibilityRole()
        btBatteryAlert.setOnClickListener {
            Prefs.isBatteryAlertEnabled = !Prefs.isBatteryAlertEnabled
            cbBatteryAlert.isChecked = Prefs.isBatteryAlertEnabled
            BatteryMonitor.syncWithSettings(requireContext())
        }

        fun updateThresholdText(value: Int) {
            tvThreshold.text = getString(R.string.battery_alert_threshold, value)
        }

        sliderThreshold.value = Prefs.batteryAlertThreshold.toFloat()
        updateThresholdText(Prefs.batteryAlertThreshold)

        sliderThreshold.addOnChangeListener { _, value, _ ->
            val intValue = value.toInt()
            Prefs.batteryAlertThreshold = intValue
            updateThresholdText(intValue)
        }
    }
}
