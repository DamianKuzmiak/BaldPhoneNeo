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

import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

        private val ringerModeFlow =
            callbackFlow {
                val am = audioManager ?: return@callbackFlow
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            if (intent.action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
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
            }

        fun bind(lifecycleOwner: LifecycleOwner) {
            val am = audioManager
            if (am?.isVolumeFixed != false) {
                Log.w("SoundButton", "AudioManager is null or volume is fixed - hiding sound button")
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

            setOnClickListener { anchor ->
                onSoundButtonClicked(anchor)
            }
        }

        private fun onSoundButtonClicked(anchor: View) {
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
                val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
                if (vibrator?.hasVibrator() == true) {
                    add(AudioManager.RINGER_MODE_VIBRATE)
                }
                add(AudioManager.RINGER_MODE_SILENT)
            }

        private fun setRingerMode(mode: Int): Boolean {
            val am = audioManager ?: return false
            return try {
                am.ringerMode = mode
                true
            } catch (e: SecurityException) {
                BaldSnackbar.show(context, e.message ?: "SecurityException", BaldSnackbar.TYPE_ERROR)
                false
            }
        }

        private fun updateSoundIcon(mode: Int) {
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
        }
    }
