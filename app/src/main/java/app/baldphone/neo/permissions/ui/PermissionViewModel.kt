package app.baldphone.neo.permissions.ui

import android.os.SystemClock
import android.util.Log

import androidx.lifecycle.ViewModel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import app.baldphone.neo.permissions.PermissionManager
import app.baldphone.neo.permissions.model.AppPermission

class PermissionViewModel : ViewModel() {
    companion object {
        private const val TAG = "PermissionViewModel"
        private const val SYSTEM_DIALOG_NOT_SHOWN_THRESHOLD_MS = 500L
    }

    sealed class UiState {
        object Idle : UiState()

        data class Rationale(val permission: AppPermission, val denied: List<String>) : UiState()

        data class Recovery(val permission: AppPermission) : UiState()

        object WaitingForSystem : UiState()

        /**
         * Indicates that the batch processing has completed and the fragment should remove itself.
         *
         * Storing completion in the ViewModel ensures durability across configuration changes.
         * A recreated fragment will collect [Completed] (stateFlow replays the latest value)
         * and safely remove itself, preventing orphaned fragment instances.
         */
        object Completed : UiState()
    }

    data class ActiveRequest(
        val permission: AppPermission,
        val startTimeMs: Long? = null
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val queue = ArrayDeque<AppPermission>()
    var current: ActiveRequest? = null

    private var activePermissions: List<AppPermission>? = null
    private val callbackMap = mutableMapOf<AppPermission, PermissionManager.PermissionCallback>()
    var showRationale: Boolean = true

    /**
     * Returns and removes the callback for [permission].
     *
     * Callbacks hold references to UI components (Views / Activities). Removing the callback immediately
     * after it fires prevents memory leaks if an Activity is destroyed while permission requests remain pending.
    */
    fun consumeCallback(permission: AppPermission): PermissionManager.PermissionCallback? =
        callbackMap.remove(permission)

    fun updateUiState(newState: UiState) {
        _uiState.value = newState
    }

    fun clearState() {
        queue.clear()
        current = null
        activePermissions = null
        _uiState.value = UiState.Idle
        callbackMap.clear()
        showRationale = true
    }

    /**
     * Prepares for a new batch of requests or reattaches to the existing one.
     *
     * Reattachment is essential for supporting configuration changes (like screen rotation).
     * When the Activity is recreated, it will call PermissionManager again; so we can resume the
     * existing flow and re-link the new Activity's callbacks to the active process.
     */
    fun syncWithRequests(
        requests: List<PermissionManager.RequestEntry>,
        showRationale: Boolean
    ): Boolean {
        val newPermissions: List<AppPermission> = requests.map { it.permission }
        val isNew = (newPermissions != activePermissions) // TODO: it compares order

        if (isNew) {
            Log.d(TAG, "Starting new batch, count=${requests.size}")
            clearState()
            activePermissions = newPermissions
            queue.addAll(newPermissions)
            this.showRationale = showRationale
        } else {
            Log.d(TAG, "Same batch, reattached callbacks")
        }

        requests.forEach { updateCallbacks(it) }
        return isNew
    }

    /** Signals that the batch has completed so the attached fragment can remove itself. */
    fun markCompleted() {
        _uiState.value = UiState.Completed
    }

    /**
     * Registers the callback for a permission request, overwriting any existing entry
     * to prevent leaking destroyed Activity instances across configuration changes.
     */
    private fun updateCallbacks(entry: PermissionManager.RequestEntry) {
        callbackMap[entry.permission] = entry.callback
    }

    fun markSystemDialogLaunched() {
        current = current?.copy(startTimeMs = SystemClock.elapsedRealtime())
        _uiState.value = UiState.WaitingForSystem
    }

    fun wasSystemDialogNotShown(): Boolean {
        val start = current?.startTimeMs ?: return false
        val elapsed = SystemClock.elapsedRealtime() - start
        return elapsed < SYSTEM_DIALOG_NOT_SHOWN_THRESHOLD_MS
    }

    override fun onCleared() {
        super.onCleared()
        clearState()
    }
}
