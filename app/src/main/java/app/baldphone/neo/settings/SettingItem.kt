// Copyright (c) 2026 Damian Kuźmiak
// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.settings

import androidx.annotation.StringRes

sealed class SettingId {
    object Calls : SettingId()

    object Help : SettingId()

    object Home : SettingId()

    object System : SettingId()
}

data class Item(
    val id: SettingId,
    @StringRes val titleRes: Int,
    val iconRes: Int? = null
)
