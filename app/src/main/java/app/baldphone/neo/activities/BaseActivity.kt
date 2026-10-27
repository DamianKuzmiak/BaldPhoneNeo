// Copyright (c) 2026 Damian Kuźmiak
// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.activities

import android.os.Build
import android.os.Bundle
import android.view.ViewGroup

import androidx.activity.enableEdgeToEdge
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity

import app.baldphone.neo.extensions.applyEdgeToEdgeInsets

/**
 * Base [AppCompatActivity] for this app.
 */
abstract class BaseActivity : AppCompatActivity() {
    /**
     * Whether this activity should use a fully transparent navigation bar without the scrim.
     */
    protected open val useTransparentNavBar: Boolean
        get() = false

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupNavigationBarContrast()
    }

    @CallSuper
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // Applied here (after the setContentView in onCreate) so the content view already holds its root child.
        findViewById<ViewGroup>(android.R.id.content)?.applyEdgeToEdgeInsets()
    }

    // Must run after [enableEdgeToEdge], which forces Window.isNavigationBarContrastEnforced=true
    private fun setupNavigationBarContrast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // The scrim only exists on API 29+
            window.isNavigationBarContrastEnforced = !useTransparentNavBar
        }
    }
}
