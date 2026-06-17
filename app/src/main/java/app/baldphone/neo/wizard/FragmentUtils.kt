// Copyright (c) 2026 Damian Kuźmiak
// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.wizard

import android.content.Intent

import androidx.fragment.app.Fragment

import app.baldphone.neo.launcher.ui.HomeScreenActivity

fun Fragment.finishSetupAndGoHome() {
    val intent =
        Intent(requireContext(), HomeScreenActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    startActivity(intent)
    activity?.finish()
}
