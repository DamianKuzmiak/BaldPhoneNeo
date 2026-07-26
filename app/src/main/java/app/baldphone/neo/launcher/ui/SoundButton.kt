package app.baldphone.neo.launcher.ui

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Vibrator
import android.util.AttributeSet
import android.util.Log
import android.view.View

import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
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

        fun bind(lifecycleOwner: LifecycleOwner) {
            val am = audioManager
            if (am?.isVolumeFixed != false) {
                Log.w(TAG, "AudioManager is null or volume is fixed - hiding sound button")
                visibility = GONE
                return
            }

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

        private fun onSoundButtonClicked() {
            val am = audioManager ?: return
            val availableModes = getAvailableRingerModes()
            if (availableModes.isEmpty()) return

            val currentMode = am.ringerMode
            val currentIndex = availableModes.indexOf(currentMode)
            val nextIndex = if (currentIndex != -1) (currentIndex + 1) % availableModes.size else 0
            val nextMode = availableModes[nextIndex]

            setRingerMode(nextMode)
        }

        private fun onSoundButtonLongClicked(anchor: View) {
            val availableModes = getAvailableRingerModes()
            context.showActionMenu(anchor) {
                showCancel = false
                availableModes.forEach { mode ->
                    when (mode) {
                        AudioManager.RINGER_MODE_SILENT -> {
                            option(R.drawable.mute_on_background, R.string.mute) {
                                setRingerMode(mode)
                            }
                        }

                        AudioManager.RINGER_MODE_VIBRATE -> {
                            option(R.drawable.vibration_on_background, R.string.vibrate) {
                                setRingerMode(mode)
                            }
                        }

                        AudioManager.RINGER_MODE_NORMAL -> {
                            option(R.drawable.sound_on_background, R.string.sound) {
                                setRingerMode(mode)
                            }
                        }
                    }
                }
            }
        }

        private fun getAvailableRingerModes(): List<Int> =
            buildList {
                add(AudioManager.RINGER_MODE_NORMAL)
                add(AudioManager.RINGER_MODE_SILENT)
                val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
                if (vibrator?.hasVibrator() == true) {
                    add(AudioManager.RINGER_MODE_VIBRATE)
                }
            }

        private fun setRingerMode(mode: Int) {
            val am = audioManager ?: return

            try {
                if (mode == AudioManager.RINGER_MODE_SILENT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val nm = ContextCompat.getSystemService(context, NotificationManager::class.java)
                    nm?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                } else {
                    am.ringerMode = mode
                }
                isUserRequestedChange = true
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException while setting ringer mode", e)
                handleRingerModeSecurityException(e)
            }
        }

        private fun handleRingerModeSecurityException(e: SecurityException) {
            if (SpecialPermission.AccessNotificationPolicy.isGranted(context)) {
                BaldSnackbar.show(context, e.message ?: "SecurityException", BaldSnackbar.TYPE_ERROR)
            } else {
                PermissionManager.checkOrRequest(context, SpecialPermission.AccessNotificationPolicy) {
                    onError {
                        Log.e(TAG, "Could not resolve FragmentActivity", e)
                        BaldSnackbar.show(context, e.message ?: "SecurityException", BaldSnackbar.TYPE_ERROR)
                    }
                }
            }
        }

        private fun updateSoundIcon(mode: Int) {
            Log.v(TAG, "updateSoundIcon: mode=$mode")
            val (iconRes, textRes) =
                when (mode) {
                    AudioManager.RINGER_MODE_SILENT -> {
                        R.drawable.mute_on_background to R.string.sound_mode_mute
                    }

                    AudioManager.RINGER_MODE_VIBRATE -> {
                        R.drawable.vibration_on_background to R.string.sound_mode_vibrate
                    }

                    else -> {
                        R.drawable.sound_on_background to R.string.sound_mode_normal
                    }
                }

            setImageResource(iconRes)
            contentDescription = context.getString(textRes)

            if (isUserRequestedChange) {
                isUserRequestedChange = false
                val msgRes =
                    when (mode) {
                        AudioManager.RINGER_MODE_SILENT -> R.string.toast_mode_silent
                        AudioManager.RINGER_MODE_VIBRATE -> R.string.toast_mode_vibrate
                        else -> R.string.toast_mode_normal
                    }
                BaldSnackbar.show(context, msgRes, BaldSnackbar.TYPE_SUCCESS)
            }
        }

        companion object {
            private const val TAG = "SoundButton"
        }
    }
