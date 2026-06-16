package app.baldphone.neo.permissions

import android.util.Log

import androidx.fragment.app.FragmentActivity

import app.baldphone.neo.permissions.model.AppPermission
import app.baldphone.neo.permissions.model.PermissionResult
import app.baldphone.neo.permissions.ui.PermissionFragment

/**
 * PermissionManager handles permission requests scoped to the specific Activity.
 */
object PermissionManager {

    private const val TAG = "PermissionManager"
    private const val FRAGMENT_TAG = "PermissionManagerFragment"

    /** Callback interface used to receive the result of a permission request. */
    fun interface PermissionCallback {
        fun onResult(result: PermissionResult)
    }

    data class RequestEntry(
        val permission: AppPermission, val callback: PermissionCallback
    )

    /**
     * Entry point for building a [RequestBatch]
     *
     * Usage:
     * ```kotlin
     * PermissionManager.with(this)
     *     .add(RuntimePermission.Camera, callback)
     *     .request()
     * ```
     */
    fun with(activity: FragmentActivity): RequestBatch = RequestBatch(activity)

    /**
     * Checks if the given [permission] is granted and requests it if necessary.
     *
     * If the permission is already granted, the [callback] is invoked immediately with [PermissionResult.Granted].
     *
     * If the permission is not granted, it initiates a request flow using a hidden [app.baldphone.neo.permissions.ui.PermissionFragment].
     * This involves attaching a fragment to the activity, so it must be called from the main thread
     * and when the activity is in a valid state (e.g., in `onCreate`, `onStart`).
     *
     * @param activity The [FragmentActivity] used to check and request permissions.
     * @param permission The [AppPermission] to check or request.
     * @param callback The [PermissionCallback] to be notified of the result.
     */
    @JvmStatic
    fun checkOrRequest(
        activity: FragmentActivity, permission: AppPermission, callback: PermissionCallback
    ) {
        // Fast path: if permission is already granted, exit early
        if (permission.isGranted(activity)) {
            callback.onResult(PermissionResult.Granted)
            return
        }
        with(activity).add(permission, callback).request()
    }

    /**
     * DSL wrapper for [checkOrRequest(FragmentActivity, AppPermission, PermissionCallback)].
     *
     * Usage:
     * ```kotlin
     * PermissionManager.checkOrRequest(this, RuntimePermission.Camera) {
     *     onGranted { /* proceed */ }
     *     onDenied { /* show rationale or exit */ }
     * }
     * ```
     *
     * @param activity The [FragmentActivity] used to check and request permissions.
     * @param permission The [AppPermission] to check or request.
     * @param block A configuration block for [PermissionResultHandler].
     */
    fun checkOrRequest(
        activity: FragmentActivity,
        permission: AppPermission,
        block: PermissionResultHandler.() -> Unit
    ) {
        val handler = PermissionResultHandler().apply(block)
        checkOrRequest(activity, permission) { result ->
            handler.handle(result)
        }
    }

    /**
     * Starts a permission request batch using a DSL builder.
     *
     * Usage:
     * ```kotlin
     * PermissionManager.request(this) {
     *     add(RuntimePermission.Camera) { onGranted { /* ... */ } }
     *     add(RuntimePermission.Location) { onGranted { /* ... */ } }
     * }
     * ```
     */
    fun request(activity: FragmentActivity, builder: RequestBatch.() -> Unit) {
        RequestBatch(activity).apply(builder).request()
    }

    class RequestBatch internal constructor(private val activity: FragmentActivity) {
        private val pending = ArrayDeque<RequestEntry>()

        fun add(permission: AppPermission, callback: PermissionCallback): RequestBatch {
            Log.d(TAG, "add: $permission")
            pending += RequestEntry(permission, callback)
            return this
        }

        fun add(
            permission: AppPermission,
            callback: PermissionResultHandler.() -> Unit
        ): RequestBatch {
            val handler = PermissionResultHandler().apply(callback)
            return add(permission) { result -> handler.handle(result) }
        }

        fun request() {
            // Fast path: if all are already granted, don't create fragment
            val iterator = pending.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.permission.isGranted(activity)) {
                    Log.d(TAG, "request: already granted: $entry")
                    entry.callback.onResult(PermissionResult.Granted)
                    iterator.remove()
                }
            }

            if (pending.isEmpty()) {
                return
            }

            val fm = activity.supportFragmentManager
            val existing = fm.findFragmentByTag(FRAGMENT_TAG) as? PermissionFragment

            val fragment = if (existing != null) {
                Log.w(TAG, "request: reusing existing fragment")
                existing
            } else {
                Log.d(TAG, "request: creating new fragment")
                PermissionFragment().also {
                    fm.beginTransaction().add(it, FRAGMENT_TAG).commitNow()
                }
            }

            fragment.processRequests(
                pending.toList()
            ) {
                Log.d(TAG, "request: batch complete, removing fragment")
                if (fragment.isAdded) {
                    fm.beginTransaction().remove(fragment).commitAllowingStateLoss()
                }
            }
        }
    }

    class PermissionResultHandler {
        private var onGranted: (() -> Unit)? = null
        private var onDenied: (() -> Unit)? = null
        private var onPermanentlyDenied: (() -> Unit)? = null
        private var onError: (() -> Unit)? = null

        fun onGranted(block: () -> Unit) {
            onGranted = block
        }

        fun onDenied(block: () -> Unit) {
            onDenied = block
        }

        fun onPermanentlyDenied(block: () -> Unit) {
            onPermanentlyDenied = block
        }

        fun onError(block: () -> Unit) {
            onError = block
        }

        fun handle(result: PermissionResult) {
            when (result) {
                PermissionResult.Granted -> onGranted?.invoke()
                PermissionResult.Denied -> onDenied?.invoke()
                PermissionResult.PermanentlyDenied -> onPermanentlyDenied?.invoke()
                PermissionResult.Error -> onError?.invoke()
            }
        }
    }
}
