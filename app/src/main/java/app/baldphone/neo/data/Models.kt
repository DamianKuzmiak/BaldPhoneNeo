package app.baldphone.neo.data

enum class AccessibilityLevel(
    val value: Int,
) {
    BASIC(0),
    ENHANCED(1),
    FULL(2), ;

    companion object {
        private val mapping = entries.associateBy(AccessibilityLevel::value)

        fun fromValue(value: Int) = mapping[value] ?: BASIC
    }
}

enum class Theme(
    val value: Int,
) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2), ;

    companion object {
        private val mapping = entries.associateBy(Theme::value)

        fun fromValue(value: Int): Theme {
            val theme = mapping[value] ?: SYSTEM
            return if (theme == SYSTEM && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                LIGHT
            } else {
                theme
            }
        }
    }
}

enum class StatusBarMode(
    val value: Int,
) {
    HIDDEN(0),
    ONLY_HOME(1),
    EVERYWHERE(2), ;

    companion object {
        private val mapping = entries.associateBy(StatusBarMode::value)

        fun fromValue(value: Int) = mapping[value] ?: HIDDEN
    }
}
