package app.baldphone.neo.permissions.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log

import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import app.baldphone.neo.permissions.PermissionManager
import app.baldphone.neo.permissions.model.AppPermission
import app.baldphone.neo.permissions.model.PermissionResult
import app.baldphone.neo.permissions.model.RuntimePermission
import app.baldphone.neo.permissions.model.SpecialPermission
import app.baldphone.neo.ui.dialogs.BaldDialog

import com.bald.uriah.baldphone.R

import kotlinx.coroutines.launch

class PermissionFragment : Fragment() {

    private companion object {
        const val TAG = "PermissionFragment"
    }

    private val viewModel: PermissionViewModel by viewModels()

    private var dialog: BaldDialog? = null

    private val runtimeLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            Log.d(TAG, "runtimeLauncher: $result")
            val req = viewModel.current
            if (req == null) {
                // Defensive: Rotation happened or some state desync
                Log.w(TAG, "runtimeLauncher: current is null")
                viewModel.updateUiState(PermissionViewModel.UiState.Idle)
                return@registerForActivityResult
            }

            val denied = result.filterValues { !it }.keys
            when {
                denied.isEmpty() -> {
                    finishAndContinue(PermissionResult.Granted)
                }

                viewModel.wasSystemDialogNotShown() -> {
                    // User previously chose "Don't ask again"
                    viewModel.updateUiState(PermissionViewModel.UiState.Recovery(req.permission))
                }

                else -> {
                    finishAndContinue(PermissionResult.Denied)
                }
            }
        }

    private val specialLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.d(TAG, "specialLauncher result received")
            val ctx = context ?: return@registerForActivityResult
            val req = viewModel.current ?: return@registerForActivityResult

            val result = if (req.permission.isGranted(ctx)) {
                PermissionResult.Granted
            } else {
                when (req.permission) {
                    is SpecialPermission -> PermissionResult.PermanentlyDenied
                    is RuntimePermission -> PermissionResult.Denied
                }
            }
            finishAndContinue(result)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            // Collecting while STARTED ensures we only show dialogs when it's safe and visible.
            // On rotation, the new instance will immediately collect the latest state and show needed UI.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Log.d(TAG, "onCreate: new UiState: $state")
                    handleState(state)
                }
            }
        }
    }

    private fun handleState(state: PermissionViewModel.UiState) {
        when (state) {
            is PermissionViewModel.UiState.Rationale -> {
                // Check dialog to avoid re-showing the same dialog on screen rotation
                if (dialog == null) {
                    showRationaleDialog(state.permission, state.denied)
                }
            }

            is PermissionViewModel.UiState.Recovery -> {
                if (dialog == null) {
                    showRecoveryDialog(state.permission)
                }
            }

            else -> {
                // For Idle and WaitingForSystem, we ensure no rationale/recovery dialog is showing
                closeDialog()
            }
        }
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView")
        closeDialog()
        super.onDestroyView()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        closeDialog()
        super.onDestroy()
    }

    /**
     * Process a batch of requests.
     */
    fun processRequests(
        requests: List<PermissionManager.RequestEntry>,
        onCompletion: () -> Unit
    ) {
        if (viewModel.syncWithRequests(requests, onCompletion)) {
            processQueue()
        }
    }

    private fun processQueue() {
        if (activity?.isFinishing == true) {
            Log.d(TAG, "processQueue: activity is finishing, aborting")
            clearState()
            return
        }

        val permission = viewModel.queue.removeFirstOrNull()
        if (permission == null) {
            Log.d(TAG, "Queue exhausted, finishing batch")
            viewModel.batchFinish?.invoke()
            clearState()
            return
        }

        Log.d(TAG, "processQueue: next=${permission}")
        viewModel.current = PermissionViewModel.ActiveRequest(permission)
        evaluatePermission(permission)
    }

    private fun evaluatePermission(permission: AppPermission) {
        val act = activity ?: return
        val ctx = context ?: return

        if (permission.isGranted(ctx)) {
            Log.d(TAG, "evaluatePermission: already granted: $permission")
            finishAndContinue(PermissionResult.Granted)
            return
        }

        when (permission) {
            is RuntimePermission -> {
                val list = permission.permissions.toList()
                if (list.any { ActivityCompat.shouldShowRequestPermissionRationale(act, it) }) {
                    viewModel.updateUiState(PermissionViewModel.UiState.Rationale(permission, list))
                } else {
                    startRuntime(list)
                }
            }

            is SpecialPermission -> {
                viewModel.updateUiState(PermissionViewModel.UiState.Rationale(permission, emptyList()))
            }
        }
    }

    private fun finishAndContinue(result: PermissionResult) {
        val permission = viewModel.current?.permission ?: return
        Log.d(TAG, "finishAndContinue: result=$result for $permission")

        viewModel.getCallback(permission)?.onResult(result)

        if (activity?.isFinishing == true) {
            Log.d(TAG, "finishAndContinue: activity is finishing, aborting")
            clearState()
            return
        }

        viewModel.current = null
        viewModel.updateUiState(PermissionViewModel.UiState.Idle)
        processQueue()
    }

    private fun startRuntime(denied: List<String>) {
        Log.d(TAG, "startRuntime: $denied")
        viewModel.markSystemDialogLaunched()
        runtimeLauncher.launch(denied.toTypedArray())
    }

    private fun startSpecial(permission: SpecialPermission) {
        val ctx = context ?: return
        permission.settingsIntent(ctx)?.let {
            viewModel.markSystemDialogLaunched()
            specialLauncher.launch(it)
        } ?: finishAndContinue(PermissionResult.Error)
    }

    private fun showRationaleDialog(permission: AppPermission, denied: List<String>) {
        showPermissionDialog(
            permission = permission,
            messageRes = permission.messageRes,
            onPositive = {
                when (permission) {
                    is RuntimePermission -> startRuntime(denied)
                    is SpecialPermission -> startSpecial(permission)
                }
            },
            onNegative = { finishAndContinue(PermissionResult.Denied) }
        )
    }

    private fun showRecoveryDialog(permission: AppPermission) {
        showPermissionDialog(
            permission = permission,
            messageRes = R.string.dialog_message_permission_settings,
            onPositive = { openSettings() },
            onNegative = { finishAndContinue(PermissionResult.Denied) }
        )
    }

    private fun showPermissionDialog(
        permission: AppPermission,
        @StringRes messageRes: Int,
        onPositive: () -> Unit,
        onNegative: () -> Unit
    ) {
        if (!isAdded) return

        closeDialog()
        dialog = BaldDialog.Builder(requireContext())
            .setTitle(permission.titleRes)
            .setMessage(messageRes)
            .setPositiveButton(R.string.allow) { onPositive() }
            .setNegativeButton(android.R.string.cancel) { onNegative() }
            .setCancelable(false)
            .show()
            .apply {
                setOnDismissListener { if (dialog == this) dialog = null }
            }
    }

    private fun clearState() {
        viewModel.clearState()
        closeDialog()
    }

    private fun closeDialog() {
        dialog?.dismiss()
        dialog = null
    }

    private fun openSettings() {
        val ctx = context ?: return
        viewModel.markSystemDialogLaunched()
        specialLauncher.launch(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", ctx.packageName, null)
            )
        )
    }
}
