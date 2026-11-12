// Copyright (c) 2026 Damian Kuźmiak
// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.utils.messaging

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

import app.baldphone.neo.utils.PhoneNumberUtils
import app.baldphone.neo.utils.getDeviceRegion

object SignalHandler : ExternalMessagingHandler {
    override val packageName = "org.thoughtcrime.securesms"
    override val appName = "Signal"

    const val SIGNAL_CONTACT_MIMETYPE =
        "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.contact"
    private const val SIGNAL_PROFILE_URI_PREFIX = "sgnl://signal.me/#p/"

    override fun startChat(context: Context, identifier: String) {
        if (identifier.isEmpty()) {
            throw IllegalArgumentException("Phone number cannot be empty.")
        }

        val region = context.getDeviceRegion()
        val formattedNumber = PhoneNumberUtils.formatToE164(identifier, region)
            ?: throw IllegalArgumentException("Invalid phone number format for Signal: $identifier")

        val uri = (SIGNAL_PROFILE_URI_PREFIX + formattedNumber).toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri)

        context.launchMessagingIntent(intent)
    }
}
