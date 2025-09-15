package app.baldphone.neo.wizard

import android.content.Intent
import android.os.Bundle

import androidx.navigation.fragment.NavHostFragment

import app.baldphone.neo.activities.BaseActivity
import app.baldphone.neo.data.Prefs

import com.bald.uriah.baldphone.R

class SetupActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_welcome)
        navigateToLastFragment()
    }

    private fun navigateToLastFragment() {
        val lastFragmentId = Prefs.lastSetupFragment
        if (lastFragmentId != 0) {
            val navHostFragment =
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            if (navHostFragment.navController.graph.findNode(lastFragmentId) != null) {
                navHostFragment.navController.navigate(lastFragmentId)
            }
        }
    }

    fun restart() {
        val intent = Intent(this, SetupActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}
