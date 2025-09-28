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
            Item(SettingId.Calls, R.string.settings_section_calls, R.drawable.phone_on_button)
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
                is SettingId.Calls -> R.id.action_to_calls
            }
        findNavController().navigate(actionId)
    }
}
