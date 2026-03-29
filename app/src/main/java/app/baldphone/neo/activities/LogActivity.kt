package app.baldphone.neo.activities

import android.os.Bundle

import app.baldphone.neo.utils.AppLog

import com.bald.uriah.baldphone.databinding.ActivityLogBinding

/**
 * LogActivity displays recent application logs using [AppLog.dumpRecent].
 */
class LogActivity : BaseActivity() {
    private lateinit var binding: ActivityLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding) {
            titleBar.setOnExitClickListener {
                finish()
            }

            // Set up Refresh button
            titleBar.setOnMoreClickListener {
                logText.text = AppLog.dumpRecent()
            }

            // Populate logs
            logText.text = AppLog.dumpRecent()
        }
    }
}
