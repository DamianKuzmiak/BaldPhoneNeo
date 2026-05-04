package app.baldphone.neo.features.share

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle

import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import app.baldphone.neo.activities.BaseActivity

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.databinding.ActivityShareBinding
import com.bald.uriah.baldphone.utils.BaldToast

/**
 * Activity for Sharing Photos, Videos and Contacts. Only uses the system share sheet (via IntentAdapter).
 */
class ShareActivity : BaseActivity() {
    private lateinit var binding: ActivityShareBinding

    private var shareIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!parseIntent()) {
            BaldToast.error(this)
            finish()
            return
        }

        setupRecyclerView()
        loadApps()
    }

    private fun parseIntent(): Boolean {
        shareIntent = IntentCompat.getParcelableExtra(intent, EXTRA_SHARE_INTENT, Intent::class.java)
        return shareIntent != null
    }

    private fun setupRecyclerView() {
        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL).apply {
                ContextCompat
                    .getDrawable(this@ShareActivity, R.drawable.list_divider)
                    ?.let { setDrawable(it) }
            }
        )
    }

    private fun loadApps() {
        val intent = shareIntent ?: return

        binding.progressBar.isVisible = true

        lifecycleScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching {
                        queryShareActivities(intent)
                            .sortedBy { it.loadLabel(packageManager).toString() }
                    }
                }

            binding.progressBar.isVisible = false

            result
                .onSuccess { resolveInfoList ->
                    updateAdapter(resolveInfoList)
                }.onFailure { throwable ->
                    if (throwable !is CancellationException) {
                        BaldToast.error(this@ShareActivity)
                        finish()
                    }
                }
        }
    }

    private fun updateAdapter(list: List<ResolveInfo>) {
        binding.recyclerView.adapter = IntentAdapter(this, list) { resolveInfo, _ -> launchSelectedApp(resolveInfo) }
    }

    private fun queryShareActivities(si: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(si, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(si, 0)
        }

    private fun launchSelectedApp(resolveInfo: ResolveInfo) {
        shareIntent?.let { intent ->
            val activityIntent =
                Intent(intent).apply {
                    component =
                        ComponentName(
                            resolveInfo.activityInfo.packageName,
                            resolveInfo.activityInfo.name
                        )
                    // Ensure we don't carry over the selector or other package restrictions
                    `package` = resolveInfo.activityInfo.packageName
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            startActivity(activityIntent)
            finish()
        }
    }

    companion object {
        const val EXTRA_SHARE_INTENT = "EXTRA_SHARE_INTENT"
    }
}
