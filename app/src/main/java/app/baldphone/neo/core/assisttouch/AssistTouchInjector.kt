package app.baldphone.neo.core.assisttouch

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager

/**
 * Handles global, automatic installation of AssistTouch on Activities and Fragments.
 */
object AssistTouchInjector {
    private const val TAG = "AssistTouchInjector"

    /**
     * Injects AssistTouch touch delegation into the given [activity]'s view hierarchy,
     * including any child fragments and dynamically added views.
     */
    fun inject(activity: Activity) {
        if (activity is FragmentActivity) {
            activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                object : FragmentManager.FragmentLifecycleCallbacks() {
                    override fun onFragmentViewCreated(
                        fm: FragmentManager,
                        f: Fragment,
                        v: View,
                        savedInstanceState: Bundle?
                    ) {
                        Log.d(TAG, "Fragment view created: ${f.javaClass.simpleName}")
                        v.enableAssistTouchHierarchy()
                    }
                },
                true
            )
        }

        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        rootView?.let { root ->
            root.setOnHierarchyChangeListener(
                object : ViewGroup.OnHierarchyChangeListener {
                    override fun onChildViewAdded(parent: View?, child: View?) {
                        if (activity.isFinishing || activity.isDestroyed) return
                        Log.v(TAG, "onChildViewAdded: view=$child")
                        child?.enableAssistTouchHierarchy()
                    }

                    override fun onChildViewRemoved(parent: View?, child: View?) {
                        Log.v(TAG, "onChildViewRemoved: view=$child")
                    }
                }
            )
            // Trigger it immediately as well
            root.enableAssistTouchHierarchy()
        }
    }
}
