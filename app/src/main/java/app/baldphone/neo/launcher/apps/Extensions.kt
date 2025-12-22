@file:JvmName("UserUtils")

package app.baldphone.neo.launcher.apps

import android.content.Context
import android.os.UserHandle
import android.os.UserManager

import java.util.concurrent.ConcurrentHashMap

private val userToSerialCache = ConcurrentHashMap<UserHandle, Long>()
private val serialToUserCache = ConcurrentHashMap<Long, UserHandle>()

/**
 * Returns the serial number for the given [UserHandle], using a thread-safe cache.
 */
fun Context.getSerialNumberForUser(user: UserHandle): Long =
    userToSerialCache
        .getOrPut(user) {
            val userManager = getSystemService(Context.USER_SERVICE) as UserManager
            userManager.getSerialNumberForUser(user)
        }.also { serial ->
            serialToUserCache.putIfAbsent(serial, user)
        }

/**
 * Returns the [UserHandle] for the given serial number, using a thread-safe cache.
 */
fun Context.getUserForSerialNumber(serial: Long): UserHandle? {
    val cached = serialToUserCache[serial]
    if (cached != null) return cached

    val userManager = getSystemService(Context.USER_SERVICE) as? UserManager
    val user = userManager?.getUserForSerialNumber(serial) ?: return null
    serialToUserCache.putIfAbsent(serial, user)
    userToSerialCache.putIfAbsent(user, serial)
    return user
}
