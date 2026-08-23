package app.baldphone.neo.extensions

import android.widget.Button
import android.widget.ImageButton
import android.widget.RadioButton

enum class AccessibilityRole(val className: String) {
    BUTTON(Button::class.java.name),
    IMAGE_BUTTON(ImageButton::class.java.name),
    RADIO_BUTTON(RadioButton::class.java.name)
}
