package app.baldphone.neo.settings.system

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView

import androidx.navigation.fragment.findNavController

import app.baldphone.neo.core.assisttouch.enableAssistTouchHierarchy
import app.baldphone.neo.extensions.setClickableAccessibilityRole
import app.baldphone.neo.settings.BaseSettingsFragment

import com.bald.uriah.baldphone.R

class SystemSettingsFragment : BaseSettingsFragment(R.layout.fragment_system_settings) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnPermissions = view.findViewById<View>(R.id.btn_permissions)
        btnPermissions.apply {
            findViewById<TextView>(R.id.title).setText(R.string.permissions_part)
            findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.grant_all_permissions_on_button)
            contentDescription = context.getString(R.string.permissions_part)
            setOnClickListener {
                findNavController().navigate(R.id.action_system_to_permissions)
            }
            setClickableAccessibilityRole()
            enableAssistTouchHierarchy()
        }
    }
}
