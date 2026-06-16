package app.baldphone.neo.permissions.model

import android.content.Context
import androidx.annotation.StringRes

sealed class AppPermission(
    @get:StringRes val titleRes: Int,
    @get:StringRes val messageRes: Int,
) {
    abstract fun isGranted(context: Context): Boolean
}
