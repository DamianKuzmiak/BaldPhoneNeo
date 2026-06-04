package app.baldphone.neo.launcher.apps.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageView

import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import kotlinx.coroutines.launch

import app.baldphone.neo.activities.BaseActivity
import app.baldphone.neo.launcher.apps.data.AppsRepository
import app.baldphone.neo.launcher.apps.data.db.AppEntry
import app.baldphone.neo.ui.dialogs.showErrorSnackbar
import app.baldphone.neo.ui.menu.showActionMenu
import app.baldphone.neo.utils.startComponentName

import com.bald.uriah.baldphone.R

/**
 * Activity that displays all installed applications in a scrollable list, grouped by alphabetical section headers.
 *
 * Supports two modes:
 * - Browse mode (default) - tapping an app shows a drop-down popup with Open / Pin / Uninstall options.
 * - Choose mode - tapping an app returns its ComponentName as the result.
 */
class AppsActivity : BaseActivity() {
    private val viewModel: AppsViewModel by viewModels()

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppsListAdapter
    private var chooseKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)

        chooseKey = intent.getStringExtra(CHOOSE_MODE)
        recyclerView = findViewById(R.id.rc_apps)

        viewModel.setIsChooseMode(chooseKey != null)

        setupRecyclerView()
        observeApps()
    }

    private fun setupRecyclerView() {
        adapter =
            AppsListAdapter(
                onAppClick = if (chooseKey != null) ::appChosen else ::showDropDown
            )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun observeApps() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.appItems.collect { items -> adapter.submitList(items) }
            }
        }
    }

    private fun showDropDown(index: Int, app: AppEntry) {
        val view = recyclerView.layoutManager?.findViewByPosition(index) ?: return
        val appIconDrawable = view.findViewById<ImageView>(R.id.app_icon)?.drawable

        showActionMenu(view) {
            showCancel = false
            onDismiss = { adapter.deselect() }

            option(
                iconDrawable = appIconDrawable,
                labelRes = R.string.open
            ) {
                startComponentName(app)
            }

            option(
                iconRes = if (app.isPinned) R.drawable.remove_on_button else R.drawable.add_on_button,
                labelRes = if (app.isPinned) R.string.remove_shortcut else R.string.add_shortcut
            ) {
                AppsRepository.updatePinned(app.componentName, app.userId, !app.isPinned)
            }

            if (!app.isPredefined) {
                option(
                    iconRes = R.drawable.delete_on_background,
                    labelRes = R.string.uninstall
                ) {
                    uninstallApp(app.packageName)
                }
            }
        }
    }

    private fun appChosen(index: Int, app: AppEntry) {
        setResult(
            RESULT_OK,
            Intent().apply {
                component = app.component
                putExtra(CHOOSE_MODE, chooseKey)
            }
        )
        finish()
    }

    private fun uninstallApp(packageName: String) {
        val uri = Uri.fromParts("package", packageName, null)
        val intent = Intent(Intent.ACTION_DELETE, uri)

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error uninstalling $packageName", e)
            showErrorSnackbar(e.message ?: "Error uninstalling $packageName")
        }
    }

    companion object {
        private const val TAG = "AppsActivity"

        const val CHOOSE_MODE = "CHOOSE_MODE"
    }
}
