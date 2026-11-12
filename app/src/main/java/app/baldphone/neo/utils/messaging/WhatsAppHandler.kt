// Copyright (c) 2026 Damian Kuźmiak
// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.utils.messaging

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

object WhatsAppHandler : ExternalMessagingHandler {
    override val packageName = WHATSAPP_PACKAGE_NAME
    override val appName = "WhatsApp"

    const val WHATSAPP_PACKAGE_NAME = "com.whatsapp"
    const val WHATSAPP_CONVERSATION_ACTIVITY = "com.whatsapp.Conversation"
    const val WHATSAPP_JID_SUFFIX = "@s.whatsapp.net"
    const val WHATSAPP_PROFILE_MIMETYPE = "vnd.android.cursor.item/vnd.com.whatsapp.profile"

    private const val TAG = "WhatsAppHandler"

    override fun startChat(context: Context, identifier: String) {
        val intent = Intent()
            .setComponent(ComponentName(WHATSAPP_PACKAGE_NAME, WHATSAPP_CONVERSATION_ACTIVITY))
            .putExtra("jid", identifier)

        context.launchMessagingIntent(intent)
    }

    /**
     * Extracts a human-readable phone number from a WhatsApp JID.
     */
    fun getPhoneNumberFromJid(jid: String?): String? {
        if (jid == null || !jid.endsWith(WHATSAPP_JID_SUFFIX)) {
            return null
        }
        val phoneNumber = jid.substring(0, jid.length - WHATSAPP_JID_SUFFIX.length)
        if (phoneNumber.isEmpty()) {
            return phoneNumber
        }

        val internationalNumber = "+$phoneNumber"
        return android.telephony.PhoneNumberUtils.formatNumber(internationalNumber, null)
    }

    /** Starts a voice call via WhatsApp for the given identifier (JID). */
    fun startVoiceCall(context: Context, identifier: String) {
        Log.i(TAG, "Opening WhatsApp chat for user to initiate voice call with JID: $identifier")
        startChat(context, identifier)
    }
}
