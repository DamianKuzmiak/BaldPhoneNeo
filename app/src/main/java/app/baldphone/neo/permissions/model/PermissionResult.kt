// Copyright (c) 2026 Damian Kuźmiak
// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.permissions.model

sealed interface PermissionResult {
    data object Denied : PermissionResult

    data object Error : PermissionResult

    data object Granted : PermissionResult

    data object PermanentlyDenied : PermissionResult
}
