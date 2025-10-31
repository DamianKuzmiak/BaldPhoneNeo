package app.baldphone.neo.crashes

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.core.content.edit
import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

class CrashHandler private constructor(context: Context) : UncaughtExceptionHandler {

    private val appContext = context.applicationContext
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val prefs =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun uncaughtException(t: Thread, e: Throwable) {
        if (!handling.compareAndSet(false, true)) {
            defaultHandler?.uncaughtException(t, e)
            return
        }

        if (e is VirtualMachineError || e is ThreadDeath || e is LinkageError) {
            defaultHandler?.uncaughtException(t, e)
            return
        }

        runCatching {
            CrashReporter.persistCrash(appContext, t, e)
        }

        val crashLoop = runCatching { isCrashLoop() }.getOrDefault(false)

        if (crashLoop && isAppInForeground()) {
            runCatching { showCrashLoopScreen() }
            killProcess()
            return
        }

        if (!crashLoop && isAppInForeground()) {
            runCatching { restartApp() }
            killProcess()
            return
        }

        defaultHandler?.uncaughtException(t, e)
            ?: killProcess()
    }

    /**
     * Returns true when [CRASH_LOOP_COUNT_THRESHOLD] or more crashes have occurred
     * within the rolling [CRASH_LOOP_WINDOW_MS] window.
     *
     * When a loop IS confirmed the counters are reset immediately so that a
     * subsequent crash inside [CrashLoopActivity] itself does not re-trigger
     * the loop state and restart the cycle all over again.
     */
    private fun isCrashLoop(): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = prefs.getLong(KEY_WINDOW_START, 0L)
        val count = prefs.getInt(KEY_CRASH_COUNT, 0)

        val withinWindow = now - windowStart <= CRASH_LOOP_WINDOW_MS
        val newCount = if (withinWindow) count + 1 else 1
        val newWindowStart = if (withinWindow) windowStart else now

        val isLoop = newCount >= CRASH_LOOP_COUNT_THRESHOLD

        prefs.edit(commit = true) {
            if (isLoop) {
                // Loop confirmed — wipe the state so the next cold start is clean.
                remove(KEY_CRASH_COUNT)
                remove(KEY_WINDOW_START)
            } else {
                putInt(KEY_CRASH_COUNT, newCount)
                putLong(KEY_WINDOW_START, newWindowStart)
            }
        }

        return isLoop
    }

    private fun restartApp() {
        val intent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(CRASH_RESTART, true)
            }
        if (intent != null) {
            appContext.startActivity(intent)
        }
    }

    private fun showCrashLoopScreen() {
        val intent = Intent(appContext, CrashLoopActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        appContext.startActivity(intent)
    }

    private fun isAppInForeground(): Boolean {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processName = appContext.packageName
        return am.runningAppProcesses?.any {
            it.processName == processName &&
                    it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } == true
    }

    private fun killProcess() {
        Process.killProcess(Process.myPid())
        exitProcess(10)
    }

    companion object {
        const val CRASH_RESTART = "crash_restart"

        private const val PREFS_NAME = "crash_prefs"
        private const val KEY_WINDOW_START = "crash_window_start"
        private const val KEY_CRASH_COUNT = "crash_count"

        /** Rolling window duration. Wide enough to survive a slow device boot cycle. */
        private const val CRASH_LOOP_WINDOW_MS = 60_000L

        /** Number of crashes within the window that constitutes a loop. */
        private const val CRASH_LOOP_COUNT_THRESHOLD = 3

        private val handling = AtomicBoolean(false)

        @Volatile
        private var instance: CrashHandler? = null

        @JvmStatic
        fun init(context: Context) {
            if (instance == null) {
                synchronized(CrashHandler::class.java) {
                    if (instance == null) {
                        instance = CrashHandler(context)
                        Thread.setDefaultUncaughtExceptionHandler(instance)
                    }
                }
            }
        }
    }
}
