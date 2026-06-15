package app.baldphone.neo.settings

import androidx.annotation.StringRes

sealed class SettingId {
    object Calls : SettingId()

    object Help : SettingId()

    object System : SettingId()
}

data class Item(
    val id: SettingId,
    @StringRes val titleRes: Int,
    val iconRes: Int? = null
)
