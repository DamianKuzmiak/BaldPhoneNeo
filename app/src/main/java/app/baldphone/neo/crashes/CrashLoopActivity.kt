package app.baldphone.neo.crashes

import android.app.Activity
import android.os.Bundle
import android.os.Process
import androidx.appcompat.app.AlertDialog
import kotlin.system.exitProcess

class CrashLoopActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AlertDialog.Builder(this)
            .setTitle("App problem detected")
            .setMessage(
                "The app keeps crashing and was stopped to prevent a loop.\n\n" +
                        "Please restart your device or contact support."
            )
            .setCancelable(false)
            .setPositiveButton("Close") { _, _ ->
                finish()
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
            .show()
    }
}
