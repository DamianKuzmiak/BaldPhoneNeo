package app.baldphone.neo.permissions.ui

import app.baldphone.neo.permissions.model.AppPermission

/**
 * UI representation of a permission, including its state for display.
 */
data class PermissionUiModel(
    val permission: AppPermission,
    val isMandatory: Boolean
)
