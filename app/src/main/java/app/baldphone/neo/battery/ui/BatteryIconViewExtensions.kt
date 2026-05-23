package app.baldphone.neo.battery.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlinx.coroutines.launch

import app.baldphone.neo.battery.BatteryRepository

fun BatteryIconView.bindToRepository(lifecycleOwner: LifecycleOwner) {
    lifecycleOwner.lifecycleScope.launch {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            BatteryRepository.get(context).batteryState.collect { state ->
                setBatteryState(state)
            }
        }
    }
}
