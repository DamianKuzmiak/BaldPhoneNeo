package app.baldphone.neo.launcher.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.ArrayMap
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

import app.baldphone.neo.R
import app.baldphone.neo.activities.DialerActivity
import app.baldphone.neo.data.Prefs
import app.baldphone.neo.databinding.FragmentHomePage1Binding
import app.baldphone.neo.features.calls.ui.RecentCallsActivity
import app.baldphone.neo.features.contacts.ui.ContactsActivity
import app.baldphone.neo.features.notifications.data.NotificationRepository
import app.baldphone.neo.features.notifications.sendNotificationIntent
import app.baldphone.neo.launcher.apps.AppIconBinder
import app.baldphone.neo.launcher.apps.data.AppsRepository
import app.baldphone.neo.launcher.apps.data.PredefinedApps
import app.baldphone.neo.launcher.apps.data.db.AppEntry
import app.baldphone.neo.launcher.apps.ui.AppsActivity
import app.baldphone.neo.launcher.data.HomeSlot
import app.baldphone.neo.permissions.PermissionManager
import app.baldphone.neo.permissions.model.SpecialPermission
import app.baldphone.neo.services.DeviceLock
import app.baldphone.neo.services.DeviceLockResult
import app.baldphone.neo.ui.dialogs.BaldDialog
import app.baldphone.neo.utils.launchAssistant
import app.baldphone.neo.utils.messaging.WhatsAppHandler
import app.baldphone.neo.utils.openCamera
import app.baldphone.neo.utils.openMessages
import app.baldphone.neo.utils.startActivityWithNewTaskClear
import app.baldphone.neo.utils.startComponentName

import com.bald.uriah.baldphone.activities.SOSActivity
import com.bald.uriah.baldphone.utils.BaldToast
import com.bald.uriah.baldphone.views.FirstPageAppIcon
import com.bald.uriah.baldphone.views.home.HomeView

class HomePage1 : HomeView {
    private val repo = NotificationRepository

    private var customizedIcons: MutableMap<FirstPageAppIcon, String> = ArrayMap()
    private lateinit var binding: FragmentHomePage1Binding

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet? = null) : super(
        context as? HomeScreenActivity,
        context as Activity
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        binding = FragmentHomePage1Binding.inflate(inflater, container, false)
        customizedIcons = ArrayMap()

        return binding.root
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val owner: LifecycleOwner =
            findViewTreeLifecycleOwner() ?: run {
                Log.e(TAG, "onAttachedToWindow: LifecycleOwner is null.")
                return
            }

        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    var lastApps: List<AppEntry>? = null

                    combine(
                        AppsRepository.allAppsFlow,
                        repo.activeNotificationPackages,
                        repo.getMissedCallNotificationsFlow(activity)
                    ) { apps, packages, missedCalls ->
                        Triple(apps, packages, missedCalls)
                    }.collect { (apps, packages, missedCalls) ->
                        if (apps != lastApps) {
                            lastApps = apps
                            customizedIcons.clear()
                            setupOnClickListeners()
                        }
                        refreshBadges(packages)
                        refreshMissedCalls(missedCalls.isNotEmpty())
                    }
                }

                if (homeScreen != null) {
                    launch {
                        repo.getTopNotification(activity).collect { item ->
                            val areaView = binding.notificationArea.root
                            val itemView = binding.notificationOverlay

                            val showOverlay = item != null
                            areaView.visibility = if (showOverlay) GONE else VISIBLE
                            itemView.visibility = if (showOverlay) VISIBLE else GONE

                            if (item != null) {
                                itemView.bind(item, { notificationItem ->
                                    notificationItem.contentIntent?.sendNotificationIntent(null)
                                }, { notificationItem ->
                                    NotificationRepository.cancelNotification(notificationItem.key)
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupOnClickListeners() {
        setupButton(HomeSlot.RECENTS, binding.btRecent) {
            homeScreen.startActivityWithNewTaskClear(Intent(homeScreen, RecentCallsActivity::class.java))
        }
        setupButton(HomeSlot.DIALER, binding.btDialer) {
            homeScreen.startActivityWithNewTaskClear(Intent(homeScreen, DialerActivity::class.java))
        }
        setupButton(HomeSlot.CONTACTS, binding.btContacts) {
            homeScreen.startActivityWithNewTaskClear(Intent(homeScreen, ContactsActivity::class.java))
        }
        setupButton(HomeSlot.WHATSAPP, binding.btWhatsapp) {
            try {
                WhatsAppHandler.launch(homeScreen)
            } catch (e: Exception) {
                BaldToast.error(homeScreen, e.localizedMessage)
            }
        }
        setupButton(HomeSlot.EMERGENCY, binding.btEmergency) {
            homeScreen.startActivity(Intent(homeScreen, SOSActivity::class.java))
        }
        setupButton(HomeSlot.ASSISTANT, binding.btAssistant) {
            homeScreen.launchAssistant()
        }
        setupButton(HomeSlot.MESSAGES, binding.btMessages) {
            context.openMessages()
        }
        setupButton(HomeSlot.CAMERA, binding.btCamera) {
            context.openCamera()
        }
        setupButton(HomeSlot.LOCK_SCREEN, binding.btLockScreen) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                requestDeviceLock()
            } else {
                homeScreen.startActivity(Intent(homeScreen, AppsActivity::class.java))
            }
        }
    }

    private fun setupButton(
        slot: HomeSlot,
        button: FirstPageAppIcon?,
        defaultAction: () -> Unit
    ) {
        val btn = button ?: return
        val app = findAppByPreference(slot)

        if (app != null) {
            btn.text = app.label
            AppIconBinder.loadPic(app, btn.imageView)
            customizedIcons[btn] = app.component.packageName
        } else {
            customizedIcons.remove(btn)
            // Default home screen behavior with legacy lock screen support
            if (btn === binding.btLockScreen && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                PredefinedApps.getAppsActivityEntry(context)?.let {
                    btn.text = it.label
                    AppIconBinder.loadPic(it, btn.imageView)
                }
            } else {
                btn.setText(slot.labelRes)
                btn.setImageResource(slot.iconRes)
            }
        }

        if (homeScreen == null) {
            setupEditorClick(btn, slot, app)
        } else if (app != null) {
            btn.setOnClickListener { homeScreen.startComponentName(app) }
        } else {
            btn.setOnClickListener { defaultAction() }
        }
    }

    private fun setupEditorClick(
        bt: FirstPageAppIcon,
        slot: HomeSlot,
        app: AppEntry?
    ) {
        // Handle legacy lock screen button UI in editor mode
        if (bt === binding.btLockScreen && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            if (app == null) {
                PredefinedApps.getAppsActivityEntry(activity)?.let {
                    bt.text = it.label
                    AppIconBinder.loadPic(it, bt.imageView)
                }
            }
        }

        val defaultName: CharSequence =
            if (bt !== binding.btLockScreen || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                activity.getText(slot.labelRes)
            } else {
                activity.getText(R.string.apps)
            }

        bt.setOnClickListener {
            BaldDialog
                .Builder(activity)
                .setTitle(R.string.custom_app)
                .setMessage(R.string.custom_app_subtext)
                .setPositiveButton(activity.getText(R.string.custom)) {
                    activity.startActivityForResult(
                        Intent(activity, AppsActivity::class.java).putExtra(AppsActivity.CHOOSE_MODE, slot.key),
                        0
                    )
                }.setNegativeButton(defaultName) {
                    Prefs.setCustomApp(slot, null)
                    activity.recreate()
                }.show()
        }

        if (app != null) {
            customizedIcons[bt] = app.component.packageName
        }
    }

    // Helper
    private fun findAppByPreference(slot: HomeSlot): AppEntry? {
        val componentName = Prefs.getCustomApp(slot) ?: return null
        return AppsRepository.findByComponentName(componentName)
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private fun requestDeviceLock() {
        DeviceLock.requestLock(homeScreen) { result ->
            when (result) {
                DeviceLockResult.FAILURE -> {
                    // System reports failure despite the service being technically enabled.
                    // Prompt user to re-enable to fix the internal state.
                    BaldDialog
                        .Builder(homeScreen)
                        .setTitle(R.string.accessibility_permission_check_title)
                        .setMessage(R.string.accessibility_permission_check_message)
                        .setPositiveButton(R.string.dialog_button_enable) {
                            try {
                                homeScreen.startActivity(SpecialPermission.Accessibility.settingsIntent(activity))
                            } catch (_: ActivityNotFoundException) {
                                BaldToast.error(homeScreen, "Failed to open accessibility settings.")
                            }
                        }.setNegativeButton(R.string.dialog_button_not_now, null)
                        .show()
                }

                DeviceLockResult.ACCESS_DENIED -> {
                    PermissionManager.checkOrRequest(
                        activity = homeScreen!!,
                        permission = SpecialPermission.Accessibility,
                        callback = {}
                    )
                }

                else -> {}
            }
        }
    }

    private fun refreshBadges(packagesSet: Set<String>) {
        fun FirstPageAppIcon.updateBadge(defaultPackage: String?) {
            val pkg = customizedIcons[this] ?: defaultPackage
            setBadgeVisibility(pkg != null && packagesSet.contains(pkg))
        }

        binding.btWhatsapp.updateBadge(WhatsAppHandler.WHATSAPP_PACKAGE_NAME)
        binding.btMessages.updateBadge(Telephony.Sms.getDefaultSmsPackage(context))

        // Update other customized buttons not covered above
        customizedIcons.forEach { (icon, packageName) ->
            if (icon !== binding.btWhatsapp && icon !== binding.btMessages) {
                icon.setBadgeVisibility(packagesSet.contains(packageName))
            }
        }
    }

    private fun refreshMissedCalls(hasMissedCalls: Boolean) {
        val recent = binding.btRecent
        if (!customizedIcons.containsKey(recent)) {
            recent.setBadgeVisibility(hasMissedCalls)
        }
    }

    companion object {
        val TAG: String = HomePage1::class.java.simpleName
    }
}
