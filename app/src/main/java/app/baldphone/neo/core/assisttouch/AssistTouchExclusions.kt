package app.baldphone.neo.core.assisttouch

import android.view.View
import android.widget.EditText
import android.widget.SeekBar

import androidx.recyclerview.widget.RecyclerView

/**
 * List of view exclusions for AssistTouch.
 * Some views should not use AssistTouch, e.g. is an input widget or a RecyclerView container.
 */
object AssistTouchExclusions {
    fun shouldExclude(view: View): Boolean =
        view is EditText ||
            view is SeekBar ||
            view is RecyclerView
}
