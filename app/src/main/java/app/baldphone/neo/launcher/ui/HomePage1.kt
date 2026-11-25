// Copyright (c) 2026 Damian Kuźmiak
// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.launcher.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

import app.baldphone.neo.R
import app.baldphone.neo.activities.DialerActivity
import app.baldphone.neo.data.Prefs
import app.baldphone.neo.databinding.FragmentHomePage1Binding
import app.baldphone.neo.features.calls.ui.RecentCallsActivity
import app.baldphone.neo.features.contacts.ui.list.ContactsActivity
import app.baldphone.neo.features.notifications.NotificationItem
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
import app.baldphone.neo.ui.dialogs.showErrorSnackbar
import app.baldphone.neo.utils.launchAssistant
import app.baldphone.neo.utils.messaging.WhatsAppHandler
import app.baldphone.neo.utils.openCamera
import app.baldphone.neo.utils.openMessages
import app.baldphone.neo.utils.startActivityWithNewTaskClear
import app.baldphone.neo.utils.startComponentName

import com.bald.uriah.baldphone.activities.SOSActivity
import com.bald.uriah.baldphone.views.FirstPageAppIcon
import com.bald.uriah.baldphone.views.home.HomeView

/**
 * Main home screen page with the fixed grid of [HomeSlot] buttons.
 */
class HomePage1 : HomeView {
    private lateinit var binding: FragmentHomePage1Binding
    private lateinit var buttons: Map<HomeSlot, FirstPageAppIcon>
    private var states: Map<HomeSlot, HomeSlotState> = emptyMap()

    private val isEditor: Boolean
        get() = homeScreen == null

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet? = null) : super(
        context as? HomeScreenActivity,
        context as Activity
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        binding = FragmentHomePage1Binding.inflate(inflater, container, false)
        buttons =
            with(binding) {
                mapOf(
                    HomeSlot.CONTACTS to btContacts,
                    HomeSlot.RECENTS to btRecent,
                    HomeSlot.DIALER to btDialer,
                    HomeSlot.CAMERA to btCamera,
                    HomeSlot.WHATSAPP to btWhatsapp,
                    HomeSlot.MESSAGES to btMessages,
                    HomeSlot.EMERGENCY to btEmergency,
                    HomeSlot.ASSISTANT to btAssistant,
                    HomeSlot.LOCK_SCREEN to btLockScreen
                )
            }
        states = emptyMap()

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

                    // The editor shows no badges.
                    val badgesFlow =
                        if (!isEditor) NotificationRepository.badgesFlow(activity) else flowOf(emptySet())

                    combine(
                        AppsRepository.allAppsFlow,
                        badgesFlow,
                        ::Pair
                    ).collect { (apps, keys) ->
                        if (apps != lastApps) {
                            lastApps = apps
                            bindSlots(apps)
                        }

                        // Refresh badges
                        states.forEach { (slot, state) ->
                            buttons.getValue(slot).setBadgeVisibility(badgeKey(slot, state) in keys)
                        }
                    }
                }

                if (!isEditor) {
                    launch {
                        NotificationRepository.getTopNotification(activity).collect { item ->
                            updateNotificationOverlay(item)
                        }
                    }
                }
            }
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            updatePhoneNumber()
        }
    }

    private fun updatePhoneNumber() {
        binding.notificationArea.phoneNumber?.apply {
            val number = Prefs.homePhoneNumber
            if (!Prefs.isHomePhoneNumberEnabled || number.isNullOrEmpty()) {
                visibility = GONE
                return@apply
            }

            text = number
            visibility = VISIBLE
        }
    }

    private fun updateNotificationOverlay(item: NotificationItem?) {
        val areaView = binding.notificationArea.root
        val itemView = binding.notificationOverlay

        val showOverlay = item != null
        areaView.visibility = if (showOverlay) GONE else VISIBLE
        itemView.visibility = if (showOverlay) VISIBLE else GONE

        if (item != null) {
            itemView.bind(
                item = item,
                onContentClick = { it.contentIntent?.sendNotificationIntent(null) },
                onClearClick = { NotificationRepository.cancelNotification(it.key) }
            )
        }
    }

    // Buttons

    private fun bindSlots(apps: List<AppEntry>) {
        states = buttons.keys.associateWith { slot -> HomeSlotState.from(Prefs.getSlotValue(slot), apps) }
        states.forEach { (slot, state) ->
            bindSlot(slot, buttons.getValue(slot), state)
        }
    }

    private fun bindSlot(slot: HomeSlot, btn: FirstPageAppIcon, state: HomeSlotState) {
        when (state) {
            HomeSlotState.Default -> {
                if (slot == HomeSlot.LOCK_SCREEN && !isLockScreenSupported) {
                    // No lock screen before Android 9, so this slot shows the apps list instead.
                    val appsList = PredefinedApps.getAppsActivityEntry(context)
                    btn.text = appsList.label
                    AppIconBinder.loadPic(appsList, btn.imageView)
                } else {
                    btn.setText(slot.labelRes)
                    btn.setImageResource(slot.iconRes)
                }
            }

            is HomeSlotState.Custom -> {
                btn.text = state.app.label
                AppIconBinder.loadPic(state.app, btn.imageView)
            }

            HomeSlotState.Empty -> {
                btn.text = if (isEditor) activity.getText(R.string.empty) else ""
                btn.setImageDrawable(null)
            }
        }

        if (isEditor) {
            btn.alpha = if (state != HomeSlotState.Empty) 1f else EMPTY_EDITOR_ALPHA
            btn.setOnClickListener { showSlotDialog(slot) }
        } else {
            setupButton(slot, btn, state)
        }
    }

    private fun setupButton(slot: HomeSlot, btn: FirstPageAppIcon, state: HomeSlotState) {
        val isInactive = state == HomeSlotState.Empty

        // An empty slot is fully inactive.
        btn.isEnabled = !isInactive
        btn.isFocusable = !isInactive
        btn.importantForAccessibility =
            if (isInactive) IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else IMPORTANT_FOR_ACCESSIBILITY_AUTO

        when (state) {
            is HomeSlotState.Custom -> btn.setOnClickListener { homeScreen.startComponentName(state.app) }
            HomeSlotState.Default -> btn.setOnClickListener { defaultAction(slot) }
            HomeSlotState.Empty -> btn.setOnClickListener(null)
        }

        // After setOnClickListener, which makes the view clickable.
        btn.isClickable = !isInactive
    }

    private fun defaultAction(slot: HomeSlot) {
        when (slot) {
            HomeSlot.RECENTS -> {
                homeScreen.startActivityWithNewTaskClear(Intent(homeScreen, RecentCallsActivity::class.java))
            }

            HomeSlot.DIALER -> {
                homeScreen.startActivityWithNewTaskClear(Intent(homeScreen, DialerActivity::class.java))
            }

            HomeSlot.CONTACTS -> {
                homeScreen.startActivityWithNewTaskClear(Intent(homeScreen, ContactsActivity::class.java))
            }

            HomeSlot.WHATSAPP -> {
                try {
                    WhatsAppHandler.launch(homeScreen)
                } catch (e: Exception) {
                    homeScreen.showErrorSnackbar(e.localizedMessage ?: e.message ?: "Unknown error")
                }
            }

            HomeSlot.EMERGENCY -> {
                homeScreen.startActivity(Intent(homeScreen, SOSActivity::class.java))
            }

            HomeSlot.ASSISTANT -> {
                homeScreen.launchAssistant()
            }

            HomeSlot.MESSAGES -> {
                context.openMessages()
            }

            HomeSlot.CAMERA -> {
                context.openCamera()
            }

            HomeSlot.LOCK_SCREEN -> {
                if (isLockScreenSupported) {
                    requestDeviceLock()
                } else {
                    homeScreen.startActivity(Intent(homeScreen, AppsActivity::class.java))
                }
            }
        }
    }

    private fun showSlotDialog(slot: HomeSlot) {
        val defaultLabel =
            if (slot == HomeSlot.LOCK_SCREEN && !isLockScreenSupported) {
                PredefinedApps.getAppsActivityEntry(context).label
            } else {
                activity.getText(slot.labelRes)
            }
        val defaultName = activity.getString(R.string.dialog_home_slot_option_default, defaultLabel)

        BaldDialog
            .Builder(activity)
            .setTitle(R.string.dialog_home_slot_title)
            .setMessage(R.string.dialog_home_slot_message)
            .setPositiveButton(defaultName) {
                Prefs.setCustomApp(slot, null)
                applySlotState(slot, HomeSlotState.Default)
            }.setNeutralButton(activity.getText(R.string.dialog_home_slot_option_custom)) {
                activity.startActivityForResult(
                    Intent(activity, AppsActivity::class.java).putExtra(AppsActivity.CHOOSE_MODE, slot.key),
                    0
                )
            }.setNegativeButton(R.string.empty) {
                Prefs.setSlotEmpty(slot)
                applySlotState(slot, HomeSlotState.Empty)
            }.show()
    }

    // Minimal effort
    private fun applySlotState(slot: HomeSlot, state: HomeSlotState) {
        states = states + (slot to state)
        bindSlot(slot, buttons.getValue(slot), state)
    }

    /** The lock screen action needs Android 9+ */
    private val isLockScreenSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    // Badges

    private fun badgeKey(slot: HomeSlot, state: HomeSlotState): String? =
        when (state) {
            HomeSlotState.Empty -> {
                null
            }

            is HomeSlotState.Custom -> {
                val opensRecentCalls =
                    state.app.packageName == context.packageName &&
                        state.app.className == RecentCallsActivity::class.java.name
                if (opensRecentCalls) NotificationRepository.MISSED_CALL_BADGE_KEY else state.app.packageName
            }

            HomeSlotState.Default -> {
                when (slot) {
                    HomeSlot.RECENTS -> NotificationRepository.MISSED_CALL_BADGE_KEY
                    HomeSlot.WHATSAPP -> WhatsAppHandler.WHATSAPP_PACKAGE_NAME
                    HomeSlot.MESSAGES -> Telephony.Sms.getDefaultSmsPackage(context)
                    else -> null
                }
            }
        }

    // Actions

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
                                homeScreen.showErrorSnackbar("Failed to open accessibility settings.")
                            }
                        }.setNegativeButton(R.string.dialog_button_not_now, null)
                        .show()
                }

                DeviceLockResult.ACCESS_DENIED -> {
                    PermissionManager.checkOrRequest(homeScreen, SpecialPermission.Accessibility) {}
                }

                else -> {}
            }
        }
    }

    private sealed interface HomeSlotState {
        data object Default : HomeSlotState // built-in action

        data object Empty : HomeSlotState // blank tile

        data class Custom(val app: AppEntry) : HomeSlotState

        companion object {
            fun from(stored: String?, apps: List<AppEntry>): HomeSlotState =
                when {
                    stored.isNullOrBlank() -> {
                        Default
                    }

                    stored == Prefs.EMPTY_SLOT_VALUE -> {
                        Empty
                    }

                    else -> {
                        val app = apps.firstOrNull { it.componentName == stored && it.userId == 0L }
                        if (app != null) Custom(app) else Default
                    }
                }
        }
    }

    companion object {
        private const val TAG = "HomePage1"
        private const val EMPTY_EDITOR_ALPHA = 0.4f
    }
}
