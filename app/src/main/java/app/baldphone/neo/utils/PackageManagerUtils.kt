// Copyright (c) 2026 Damian Kuźmiak
// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.utils

import android.content.pm.PackageInfo
import android.content.pm.PackageManager

/** Returns the [PackageInfo] of [packageName], or null if the package is not installed. */
fun PackageManager.getPackageInfoOrNull(packageName: String, flags: Int = 0): PackageInfo? =
    try {
        getPackageInfo(packageName, flags)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

/** True if a package named [packageName] is installed. */
fun PackageManager.isPackageInstalled(packageName: String): Boolean = getPackageInfoOrNull(packageName) != null
