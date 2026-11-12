// Copyright (c) 2026 Damian Kuźmiak
// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.activities

import android.os.Bundle

import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity

/**
 * Note: This class coexists with the legacy [com.bald.uriah.baldphone.activities.BaldActivity] to allow for a gradual
 * migration.
 */
abstract class BaseActivity : AppCompatActivity() {
    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
