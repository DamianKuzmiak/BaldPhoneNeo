package app.baldphone.neo.settings.ui

import android.os.Bundle
import android.view.View

import com.google.android.material.materialswitch.MaterialSwitch

import app.baldphone.neo.data.Prefs
import app.baldphone.neo.extensions.setClickableAccessibilityRole
import app.baldphone.neo.settings.BaseSettingsFragment

import com.bald.uriah.baldphone.R

class DisplaySettingsFragment : BaseSettingsFragment(R.layout.fragment_display_settings) {
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val btShowWallpaper = view.findViewById<View>(R.id.bt_show_wallpaper)
        val cbShowWallpaper = view.findViewById<MaterialSwitch>(R.id.cb_show_wallpaper)

        cbShowWallpaper.isChecked = Prefs.showWallpaper

        btShowWallpaper.setClickableAccessibilityRole()
        btShowWallpaper.setOnClickListener {
            Prefs.showWallpaper = !Prefs.showWallpaper
            cbShowWallpaper.isChecked = Prefs.showWallpaper
            requireActivity().recreate()
        }
    }
}
