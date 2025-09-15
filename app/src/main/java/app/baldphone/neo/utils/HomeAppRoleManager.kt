package app.baldphone.neo.utils

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment

private const val DIALOG_BLOCK_THRESHOLD_MS = 300L

/**
 * Manages the process of requesting the user to set this app as the default home/launcher application.
 *
 * For Android Q (API 29) and above, it uses the [RoleManager] API to request the `ROLE_HOME`.
 * For older versions, it opens the system's home app settings screen.
 */
class HomeAppRoleManager private constructor(
    resultHandler: ActivityResultCaller,
    private val context: Context,
    private val activity: Activity
) {
    private val tag = HomeAppRoleManager::class.java.simpleName

    private var homeRoleRequestTime = 0L

    constructor(fragment: Fragment) : this(
        fragment, fragment.requireContext(), fragment.requireActivity()
    )

    constructor(activity: ComponentActivity) : this(activity, activity, activity)

    private val homeRoleLauncher: ActivityResultLauncher<Intent> =
        resultHandler.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d(tag, "HOME role granted.")
                return@registerForActivityResult
            }
            val elapsedTime = System.currentTimeMillis() - homeRoleRequestTime
            Log.d(tag, "HOME role not granted, elapsed time: $elapsedTime ms")

            if (elapsedTime < DIALOG_BLOCK_THRESHOLD_MS) {
                Log.d(tag, "Dialog likely blocked (Don't ask again). Opening Home Settings.")
                HomeAppUtils.openHomeAppSettings(context)
            }
        }

    fun requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            homeRoleRequestTime = System.currentTimeMillis()
            requestHomeRole(activity, homeRoleLauncher)
        } else {
            HomeAppUtils.openHomeAppSettings(activity)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestHomeRole(
        activity: Activity, roleRequestLauncher: ActivityResultLauncher<Intent>
    ) {
        val roleManager = activity.getSystemService(RoleManager::class.java)
        if (roleManager == null) {
            Log.w(tag, "RoleManager not available. Opening Home Settings.")
            HomeAppUtils.openHomeAppSettings(activity)
            return
        }
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            Log.w(tag, "HOME role not available on this device. Opening Home Settings.")
            HomeAppUtils.openHomeAppSettings(activity)
            return
        }
        if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            Log.d(tag, "App is already default Home. Opening Home Settings.")
            HomeAppUtils.openHomeAppSettings(activity)
            return
        }
        Log.d(tag, "App is not default Home. Requesting HOME role.")
        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        roleRequestLauncher.launch(intent)
    }
}
