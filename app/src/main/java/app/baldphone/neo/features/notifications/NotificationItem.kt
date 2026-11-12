// Copyright (c) 2026 Damian Kuźmiak
// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.features.notifications

import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.text.TextUtils

data class NotificationItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: CharSequence?,
    val text: CharSequence?,
    val timeStamp: Long,
    val contentIntent: PendingIntent?,
    val isClearable: Boolean,
    val category: String?,
    val smallIcon: Icon?, // For API >= 23
    val smallIconResId: Int, // For API < 23
    val largeIcon: Icon?, // For API >= 23
) {
    /**
     * Whether this item renders the same visible card content as [other].
     *
     * Intentionally compares only the fields the card actually displays. Used to avoid redundant
     * re-binds on identical re-posts.
     */
    fun hasSameVisibleContentAs(other: NotificationItem?): Boolean =
        other != null &&
            key == other.key &&
            appName == other.appName &&
            TextUtils.equals(title, other.title) &&
            TextUtils.equals(text, other.text) &&
            isClearable == other.isClearable
}
