package app.baldphone.neo

import android.content.Intent
import android.os.Bundle

import androidx.appcompat.app.AppCompatActivity

import app.baldphone.neo.utils.HomeAppUtils

import com.bald.uriah.baldphone.activities.HomeScreenActivity

/**
 * Invisible proxy activity to handle launching the app drawer entry point.
 * It ensures HomeScreenActivity is only ever launched via the dedicated system HOME task, avoiding duplicate instances.
 */
class LauncherProxyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when {
            HomeAppUtils.isDefaultLauncher(this) -> {
                val homeIntent =
                    Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.extras?.let { putExtras(it) }
                    }
                startActivity(homeIntent)
            }

            else -> {
                // We are not the default launcher:
                val homeScreenIntent =
                    Intent(this, HomeScreenActivity::class.java).apply {
                        intent.extras?.let { putExtras(it) }
                    }
                startActivity(homeScreenIntent)
            }
        }
        finish()
    }
}
