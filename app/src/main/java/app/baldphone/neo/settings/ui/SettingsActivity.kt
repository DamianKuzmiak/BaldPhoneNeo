package app.baldphone.neo.settings.ui

import android.os.Bundle

import androidx.navigation.fragment.NavHostFragment

import app.baldphone.neo.activities.BaseActivity

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.databinding.ActivitySettingsNeoBinding

class SettingsActivity : BaseActivity() {
    private lateinit var binding: ActivitySettingsNeoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsNeoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment =
            supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        navHostFragment.navController.addOnDestinationChangedListener { _, destination, _ ->
            destination.label?.let { label ->
                binding.titleBar.setTitle(label.toString())
            }
        }
    }
}
