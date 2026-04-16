package app.baldphone.neo.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log

import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Singleton repository providing reactive and one-shot access to the Android battery state.
 */
class BatteryRepository private constructor(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val batteryManager = ContextCompat.getSystemService(applicationContext, BatteryManager::class.java)

    /**
     * Reactive battery state stream. Backed by [Intent.ACTION_BATTERY_CHANGED] broadcasts.
     */
    val batteryState: StateFlow<BatteryState> =
        callbackFlow {
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                            trySend(intent.toBatteryState())
                        }
                    }
                }

            val stickyIntent =
                ContextCompat.registerReceiver(
                    applicationContext,
                    receiver,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            // Emit the sticky intent immediately if available
            stickyIntent?.let { trySend(it.toBatteryState()) }

            awaitClose {
                try {
                    applicationContext.unregisterReceiver(receiver)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Unable to unregister receiver", e)
                }
            }
        }.onStart { Log.v(TAG, "Battery flow starting...") }
            .onCompletion { Log.v(TAG, "Battery flow stopping...") }
            .distinctUntilChanged()
            .onEach { Log.v(TAG, "Battery flow emitting: $it") }
            .stateIn(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = readCurrentState(),
            )

    /**
     * LiveData bridge for Java consumers.
     */
    val batteryLiveData: LiveData<BatteryState> = batteryState.asLiveData(timeoutInMs = LIVE_DATA_TIMEOUT_MS)

    /**
     * Returns a fresh [BatteryState] snapshot by reading the system sticky intent directly.
     */
    fun freshSnapshot(): BatteryState = readCurrentState()

    private fun readCurrentState(): BatteryState =
        applicationContext
            .registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ).toBatteryState()

    private fun Intent?.toBatteryState(): BatteryState {
        if (this == null) return BatteryState()

        val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percentage =
            if (level >= 0 && scale > 0) {
                ((level.toFloat() / scale.toFloat()) * 100f).toInt().coerceIn(0, 100)
            } else {
                null
            }

        val status = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val isFull = status == BatteryManager.BATTERY_STATUS_FULL
        val isPlugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0

        val isLow =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false)
            } else {
                (percentage ?: 100) <= LOW_BATTERY_THRESHOLD && !isCharging
            }

        val chargeTimeRemaining =
            if (isCharging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                batteryManager?.computeChargeTimeRemaining() ?: -1L
            } else {
                -1L
            }

        val minutesUntilCharged =
            if (chargeTimeRemaining >= 0) {
                // Round up to nearest minute so it doesn't show "0 min" when it's almost full
                (chargeTimeRemaining + 59_999) / 60_000
            } else {
                -1L
            }

        return BatteryState(
            percentage = percentage,
            isCharging = isCharging,
            isFull = isFull,
            isPlugged = isPlugged,
            isLow = isLow,
            minutesUntilCharged = minutesUntilCharged,
        )
    }

    companion object {
        private const val TAG = "BatteryRepository"
        private const val LOW_BATTERY_THRESHOLD = 15
        private const val LIVE_DATA_TIMEOUT_MS = 5000L

        @Volatile
        private var instance: BatteryRepository? = null

        @JvmStatic
        fun get(ctx: Context): BatteryRepository =
            instance ?: synchronized(this) {
                instance ?: BatteryRepository(
                    ctx.applicationContext,
                ).also { instance = it }
            }
    }
}
