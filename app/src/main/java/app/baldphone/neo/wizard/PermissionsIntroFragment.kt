package app.baldphone.neo.wizard

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView

import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment

import app.baldphone.neo.data.Prefs
import app.baldphone.neo.permissions.PermissionRepository
import app.baldphone.neo.settings.ui.SettingsActivity
import app.baldphone.neo.utils.HomeAppRoleManager
import app.baldphone.neo.utils.HomeAppUtils

import com.bald.uriah.baldphone.R

class PermissionsIntroFragment : Fragment() {
    private lateinit var homeAppRoleManager: HomeAppRoleManager
    private var defaultFinishButtonTextColor: ColorStateList? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homeAppRoleManager = HomeAppRoleManager(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_permissions_intro, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Prefs.lastSetupFragment = R.id.permissionsIntroFragment

        view.findViewById<View>(R.id.btnOpenPermissions).setOnClickListener {
            startActivity(
                Intent(requireContext(), SettingsActivity::class.java).apply {
                    data = Uri.parse("myapp://settings/system/permissions")
                }
            )
        }

        view.findViewById<TextView>(R.id.home_desc).text =
            getString(R.string.onb_home_summary, getString(R.string.app_display_name))

        view.findViewById<View>(R.id.btnSetDefaultLauncher).setOnClickListener {
            Prefs.isPendingDefaultLauncherChoice = true
            homeAppRoleManager.requestDefaultLauncher()
        }

        view.findViewById<View>(R.id.btnFinish).setOnClickListener {
            Prefs.isSetupSkipped = false
            Prefs.isSetupComplete = true
            finishSetupAndGoHome()
        }
    }

    override fun onResume() {
        super.onResume()
        updateState()
    }

    private fun updateState() {
        val root = view ?: return

        val isDefaultHome = HomeAppUtils.isDefaultLauncher(requireContext())
        val hasPermissions = PermissionRepository.isFullyGranted(requireContext())

        root.findViewById<View>(R.id.permissions_status).isVisible = hasPermissions
        root.findViewById<View>(R.id.btnOpenPermissions).isVisible = !hasPermissions

        root.findViewById<View>(R.id.home_status).isVisible = isDefaultHome
        root.findViewById<View>(R.id.btnSetDefaultLauncher).isVisible = !isDefaultHome

        val btnFinish = root.findViewById<Button>(R.id.btnFinish)

        if (defaultFinishButtonTextColor == null) {
            defaultFinishButtonTextColor = btnFinish.textColors
        }

        if (isDefaultHome && hasPermissions) {
            btnFinish.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.green)
            btnFinish.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.bald_background),
            )
        } else {
            btnFinish.backgroundTintList = null
            btnFinish.setTextColor(defaultFinishButtonTextColor)
        }
    }
}
