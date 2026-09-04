package app.baldphone.neo.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Vibrator
import android.util.AttributeSet
import android.util.Log
import android.view.View

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

import app.baldphone.neo.permissions.PermissionManager
import app.baldphone.neo.permissions.model.SpecialPermission
import app.baldphone.neo.ui.dialogs.BaldSnackbar
import app.baldphone.neo.ui.menu.showActionMenu

import com.bald.uriah.baldphone.R

class SoundButton
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = android.R.attr.imageButtonStyle
    ) : AppCompatImageButton(context, attrs, defStyleAttr) {
        private val audioManager = ContextCompat.getSystemService(context, AudioManager::class.java)
        private var isUserRequestedChange = false
        private var bindJob: Job? = null
        private val availableRingerModes: List<Int> by lazy {
            buildList {
                add(AudioManager.RINGER_MODE_NORMAL)
                val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
                if (vibrator?.hasVibrator() == true) {
                    add(AudioManager.RINGER_MODE_VIBRATE)
                }
                add(AudioManager.RINGER_MODE_SILENT)
            }
        }

        @OptIn(FlowPreview::class)
        private val ringerModeFlow =
            callbackFlow {
                val am = audioManager ?: return@callbackFlow
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            if (intent.action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
                                Log.d(TAG, "onReceive: RINGER_MODE_CHANGED_ACTION = ${am.ringerMode}")
                                trySend(am.ringerMode)
                            }
                        }
                    }
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION),
                    ContextCompat.RECEIVER_EXPORTED
                )
                trySend(am.ringerMode)
                awaitClose {
                    try {
                        context.unregisterReceiver(receiver)
                    } catch (_: IllegalArgumentException) {
                    }
                }
            }.debounce(100.milliseconds)
                .distinctUntilChanged()

        fun bind(lifecycleOwner: LifecycleOwner) {
            val am = audioManager
            if (am?.isVolumeFixed != false) {
                Log.w(TAG, "AudioManager is null or volume is fixed - hiding sound button")
                visibility = GONE
                return
            }
            visibility = VISIBLE

            bindJob?.cancel()
            bindJob =
                lifecycleOwner.lifecycleScope.launch {
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        ringerModeFlow.collectLatest { mode ->
                            updateSoundIcon(mode)
                        }
                    }
                }

            setOnClickListener { onSoundButtonClicked() }
            setOnLongClickListener { anchor ->
                onSoundButtonLongClicked(anchor)
                true
            }
        }

        private fun getNextRingerMode(mode: Int): Int {
            if (availableRingerModes.isEmpty()) return AudioManager.RINGER_MODE_NORMAL
            val currentIndex = availableRingerModes.indexOf(mode)
            val nextIndex = if (currentIndex != -1) (currentIndex + 1) % availableRingerModes.size else 0
            return availableRingerModes[nextIndex]
        }

        private fun onSoundButtonClicked() {
            val am = audioManager ?: return
            val nextMode = getNextRingerMode(am.ringerMode)
            setRingerMode(nextMode)
        }

        private fun onSoundButtonLongClicked(anchor: View) {
            context.showActionMenu(anchor) {
                showCancel = false
                availableRingerModes.forEach { mode ->
                    val info = getRingerModeUiInfo(mode)
                    option(info.iconRes, info.menuLabelRes) {
                        setRingerMode(mode)
                    }
                }
            }
        }

        private fun setRingerMode(targetMode: Int) {
            val am = audioManager ?: return

            if (am.ringerMode == targetMode) {
                isUserRequestedChange = false
                return
            }

            try {
                am.ringerMode = targetMode
                isUserRequestedChange = true
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException while setting ringer mode", e)
                isUserRequestedChange = false
                handleRingerModeSecurityException(targetMode, e)
            }
        }

        private fun handleRingerModeSecurityException(targetMode: Int, e: SecurityException) {
            val errorMessage = e.message ?: context.getString(R.string.an_error_has_occurred)
            if (SpecialPermission.AccessNotificationPolicy.isGranted(context)) {
                BaldSnackbar.show(context, errorMessage, BaldSnackbar.TYPE_ERROR)
            } else {
                PermissionManager.checkOrRequest(context, SpecialPermission.AccessNotificationPolicy) {
                    onGranted {
                        setRingerMode(targetMode)
                    }
                    onDenied {
                        val nextMode = getNextRingerMode(targetMode)
                        setRingerMode(nextMode)
                    }
                    onError {
                        Log.e(TAG, "Could not resolve FragmentActivity", e)
                        BaldSnackbar.show(context, errorMessage, BaldSnackbar.TYPE_ERROR)
                    }
                }
            }
        }

        private fun updateSoundIcon(mode: Int) {
            Log.v(TAG, "updateSoundIcon: mode=$mode")
            val info = getRingerModeUiInfo(mode)

            setImageResource(info.iconRes)
            contentDescription = context.getString(info.contentDescRes)

            if (isUserRequestedChange) {
                isUserRequestedChange = false
                BaldSnackbar.show(context, info.toastRes, BaldSnackbar.TYPE_SUCCESS)
            }
        }

        private fun getRingerModeUiInfo(mode: Int): RingerModeUiInfo =
            when (mode) {
                AudioManager.RINGER_MODE_SILENT -> {
                    RingerModeUiInfo(
                        iconRes = R.drawable.mute_on_background,
                        menuLabelRes = R.string.mute,
                        contentDescRes = R.string.sound_mode_mute,
                        toastRes = R.string.toast_mode_silent
                    )
                }

                AudioManager.RINGER_MODE_VIBRATE -> {
                    RingerModeUiInfo(
                        iconRes = R.drawable.vibration_on_background,
                        menuLabelRes = R.string.vibrate,
                        contentDescRes = R.string.sound_mode_vibrate,
                        toastRes = R.string.toast_mode_vibrate
                    )
                }

                else -> {
                    RingerModeUiInfo(
                        iconRes = R.drawable.sound_on_background,
                        menuLabelRes = R.string.sound,
                        contentDescRes = R.string.sound_mode_normal,
                        toastRes = R.string.toast_mode_normal
                    )
                }
            }

        companion object {
            private const val TAG = "SoundButton"
        }
    }

private data class RingerModeUiInfo(
    @get:DrawableRes val iconRes: Int,
    @get:StringRes val menuLabelRes: Int,
    @get:StringRes val contentDescRes: Int,
    @get:StringRes val toastRes: Int
)
