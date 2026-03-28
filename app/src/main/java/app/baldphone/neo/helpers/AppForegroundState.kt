package app.baldphone.neo.helpers

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Holds process-level foreground / background state for the app.

 * WHY THIS EXISTS AS A SINGLE OBJECT
 * ---------------------------------
 * For the current use case, separating lifecycle observation and state
 * into multiple classes would be unnecessary complexity.
 *
 * This keeps the solution pragmatic while the logic is trivial.
 *
 * HOW TO REFACTOR IF THIS GROWS
 * -----------------------------
 * If ANY of the following start to appear:
 *  - conditional logic ("only if X and Y")
 *  - infrastructure control (workers, receivers, services)
 *  - multiple behaviors reacting to foreground/background
 *
 * THEN refactor as follows:
 *
 * 1) Extract state holder:
 *    class AppVisibilityState {
 *        @Volatile var isForeground: Boolean = false
 *    }
 *
 * 2) Convert this object into a pure lifecycle observer:
 *    class AppForegroundObserver(
 *        private val state: AppVisibilityState
 *    ) : DefaultLifecycleObserver {
 *        override fun onStart(owner: LifecycleOwner) {
 *            state.isForeground = true
 *        }
 *
 *        override fun onStop(owner: LifecycleOwner) {
 *            state.isForeground = false
 *        }
 *    }
 *
 * 3) Move any rules or actions into a separate coordinator class.
 *
 * This refactor is intentionally cheap and safe.
 */

object AppForegroundState : DefaultLifecycleObserver {
    @Volatile
    var isForeground: Boolean = false
        private set

    // Lifecycle Bridge (Android events)
    override fun onStart(owner: LifecycleOwner) {
        isForeground = true
    }

    override fun onStop(owner: LifecycleOwner) {
        isForeground = false
    }
}
