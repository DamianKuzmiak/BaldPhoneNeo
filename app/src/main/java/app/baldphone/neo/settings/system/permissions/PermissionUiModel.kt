// Copyright (c) 2026 Damian Kuźmiak
// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.settings.system.permissions

import app.baldphone.neo.permissions.model.AppPermission

/**
 * UI representation of a permission, including its state for display.
 */
data class PermissionUiModel(
    val permission: AppPermission,
    val isMandatory: Boolean
)
