package app.baldphone.neo.features.home.ui

import android.content.Context
import android.util.AttributeSet
import android.util.Log

import androidx.appcompat.widget.AppCompatImageButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import app.baldphone.neo.flashlight.FlashLightController
import app.baldphone.neo.flashlight.FlashlightState
import app.baldphone.neo.ui.dialogs.BaldSnackbar

import com.bald.uriah.baldphone.R

class FlashlightButton
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = android.R.attr.imageButtonStyle
    ) : AppCompatImageButton(context, attrs, defStyleAttr) {
        fun bind(
            lifecycleOwner: LifecycleOwner,
            requestPermission: (Runnable) -> Unit
        ) {
            val flashlight = FlashLightController.getInstance(context)
            if (flashlight == null) {
                Log.i("FlashlightButton", "Flashlight instance is null - hiding toggle icon")
                visibility = GONE
                return
            }

            contentDescription = context.getString(R.string.flashlight_off)
            setOnClickListener {
                requestPermission(
                    Runnable {
                        flashlight.toggle()
                    }
                )
            }

            lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    flashlight.state.collectLatest { event ->
                        if (event is FlashlightState.OnOff) {
                            setImageResource(
                                if (event.isOn) {
                                    R.drawable.flashlight_on_background
                                } else {
                                    R.drawable.flashlight_off_on_background
                                }
                            )
                            contentDescription =
                                context.getString(
                                    if (event.isOn) {
                                        R.string.flashlight_on
                                    } else {
                                        R.string.flashlight_off
                                    }
                                )
                        } else if (event is FlashlightState.Error) {
                            val errorMsg = event.detail ?: "Flashlight not available"
                            BaldSnackbar.show(context, errorMsg, BaldSnackbar.TYPE_ERROR)
                        }
                    }
                }
            }
        }
    }
