package app.baldphone.neo.permissions

import android.content.Context
import android.content.pm.PackageManager

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

import app.baldphone.neo.permissions.model.AppPermission

object PermissionRepository {
    private var cachedDeclaredPermissions: List<AppPermission>? = null

    /**
     * Triggers a re-evaluation of permissions for all active flow collectors.
     */
    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

    /**
     * Returns a flow of missing permissions. Emits whenever [refresh] is called.
     */
    fun getMissingPermissionsFlow(context: Context): Flow<List<AppPermission>> =
        refreshTrigger
            // .onStart { emit(Unit) }
            .map { getMissingPermissions(context) }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()

    /**
     * Returns a reactive flow indicating if all declared permissions are granted.
     * Short-circuits on the first un-granted permission for performance.
     */
    fun isFullyGrantedFlow(context: Context): Flow<Boolean> {
        val appContext = context.applicationContext
        return refreshTrigger
            .map { isFullyGranted(appContext) }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
    }

    /**
     * Returns a reactive flow indicating if all mandatory permissions are granted.
     */
    fun isMandatoryGrantedFlow(context: Context): Flow<Boolean> {
        val appContext = context.applicationContext
        return refreshTrigger
            .map { isMandatoryGranted(appContext) }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
    }

    /**
     * Returns a LiveData version of [isMandatoryGrantedFlow] for Java consumption.
     */
    fun observeMandatoryPermissions(context: Context): LiveData<Boolean> =
        isMandatoryGrantedFlow(context).asLiveData()

    /**
     * Returns the count of missing permissions.
     */
    fun getMissingPermissionsCount(context: Context): Int = getMissingPermissions(context).size

    /**
     * Checks if all declared permissions are granted.
     * Short-circuits on the first un-granted permission for performance.
     */
    fun isFullyGranted(context: Context): Boolean =
        getAllDeclaredPermissions(context).all { it.isGranted(context) }

    var mandatoryPolicy: PermissionMandatoryPolicy = DefaultPermissionMandatoryPolicy()

    /**
     * Checks if all mandatory permissions are granted.
     * Mandatory permissions are defined by [PermissionMandatoryPolicy].
     */
    fun isMandatoryGranted(context: Context): Boolean =
        getAllDeclaredPermissions(context)
            .filter { mandatoryPolicy.isMandatory(context, it) }
            .all { it.isGranted(context) }

    /**
     * Returns a list of AppPermissions that are declared but not yet granted.
     */
    private fun getMissingPermissions(
        context: Context
    ): List<AppPermission> = getAllDeclaredPermissions(context).filter { !it.isGranted(context) }

    /**
     * Discovers and classifies all permissions requested in the AndroidManifest.xml.
     * Cached after the first call as the manifest does not change at runtime.
     */
    private fun getAllDeclaredPermissions(context: Context): List<AppPermission> {
        cachedDeclaredPermissions?.let { return it }

        val packageInfo =
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
            )

        val permissions = AppPermission.all().filter { it.isDeclared(packageInfo) }

        return permissions.also { cachedDeclaredPermissions = it }
    }

    private val refreshTrigger =
        MutableSharedFlow<Unit>(replay = 1).apply {
            tryEmit(Unit)
        }
}
