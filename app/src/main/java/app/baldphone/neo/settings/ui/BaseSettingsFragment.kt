package app.baldphone.neo.settings.ui

import android.os.Bundle

import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment

import com.google.android.material.transition.MaterialSharedAxis

abstract class BaseSettingsFragment(
    @LayoutRes layoutId: Int = 0
) : Fragment(layoutId) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }
}
