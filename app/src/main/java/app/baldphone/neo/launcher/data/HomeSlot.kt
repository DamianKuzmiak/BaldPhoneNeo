package app.baldphone.neo.launcher.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

import app.baldphone.neo.R
import app.baldphone.neo.data.PrefKeys

/**
 * Defines the logical slots available on the Home Screen - [app.baldphone.neo.launcher.ui.HomePage1].
 */
enum class HomeSlot(
    val key: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int
) {
    ASSISTANT(PrefKeys.KEY_HOME_SLOT_ASSISTANT, R.string.assistant, R.drawable.voice_on_background),
    CAMERA(PrefKeys.KEY_HOME_SLOT_CAMERA, R.string.camera, R.drawable.camera_on_background),
    CONTACTS(PrefKeys.KEY_HOME_SLOT_CONTACTS, R.string.contacts, R.drawable.human_on_background),
    DIALER(PrefKeys.KEY_HOME_SLOT_DIALER, R.string.dialer, R.drawable.phone_on_background),
    EMERGENCY(PrefKeys.KEY_HOME_SLOT_EMERGENCY, R.string.sos, R.drawable.emergency),
    LOCK_SCREEN(PrefKeys.KEY_HOME_SLOT_LOCK_SCREEN, R.string.label_lock_screen_short, R.drawable.icon_lock_outline),
    MESSAGES(PrefKeys.KEY_HOME_SLOT_MESSAGES, R.string.messages, R.drawable.message_on_background),
    RECENTS(PrefKeys.KEY_HOME_SLOT_RECENTS, R.string.recent, R.drawable.history_on_background),
    WHATSAPP(PrefKeys.KEY_HOME_SLOT_WHATSAPP, R.string.whatsapp, R.drawable.whatsapp_on_background)
}
