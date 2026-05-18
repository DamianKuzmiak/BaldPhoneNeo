package app.baldphone.neo.settings.ui

import android.os.Bundle
import android.view.View

import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView

import app.baldphone.neo.settings.Item
import app.baldphone.neo.settings.SettingId
import app.baldphone.neo.settings.SettingsAdapter

import com.bald.uriah.baldphone.R

class SettingsFragment : Fragment(R.layout.fragment_settings_list) {
    private val items =
        listOf(
            Item(SettingId.Home, R.string.edit_home_screen, R.drawable.home_on_button),
            Item(SettingId.Calls, R.string.settings_section_calls, R.drawable.phone_on_button),
            Item(SettingId.Battery, R.string.battery_settings, R.drawable.low_battery_alert_on_button),
            Item(SettingId.System, R.string.settings_system, R.drawable.settings_on_button),
            Item(SettingId.Help, R.string.about, R.drawable.ic_info)
        )

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = SettingsAdapter(items) { handleSettingClick(it) }

        view.findViewById<RecyclerView>(R.id.recyclerView).apply {
            this.adapter = adapter

            val divider = DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
            ContextCompat.getDrawable(requireContext(), R.drawable.ll_divider)?.let {
                divider.setDrawable(it)
                addItemDecoration(divider)
            }
        }
    }

    private fun handleSettingClick(id: SettingId) {
        @IdRes
        val actionId =
            when (id) {
                is SettingId.Home -> R.id.action_settings_to_home
                is SettingId.Calls -> R.id.action_settings_to_calls
                is SettingId.Battery -> R.id.action_settings_to_battery
                is SettingId.System -> R.id.action_settings_to_system
                is SettingId.Help -> R.id.action_settings_to_suppport
            }
        findNavController().navigate(actionId)
    }
}
