package app.baldphone.neo.launcher.apps.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.PopupWindow

import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

import kotlinx.coroutines.launch

import app.baldphone.neo.activities.BaseActivity
import app.baldphone.neo.launcher.apps.AppIconBinder
import app.baldphone.neo.launcher.apps.data.AppsRepository
import app.baldphone.neo.launcher.apps.data.db.AppEntry

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.utils.BDB
import com.bald.uriah.baldphone.utils.BDialog
import com.bald.uriah.baldphone.utils.BPrefs
import com.bald.uriah.baldphone.utils.DropDownRecyclerViewAdapter
import com.bald.uriah.baldphone.utils.S

/**
 * Activity that displays all installed applications in a scrollable grid, grouped by alphabetical section headers.
 *
 * Supports two modes:
 * - Browse mode (default) - tapping an app shows a drop-down popup with Open / Pin / Uninstall options.
 * - Choose mode - tapping an app returns its [ComponentName] as the result.
 */
class AppsActivity : BaseActivity() {
    private val viewModel: AppsViewModel by viewModels()

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppsListAdapter
    private var chooseKey: String? = null

    /** Number of app columns. Headers always span the full width. */
    private var spanCount = DEFAULT_SPAN_COUNT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)

        chooseKey = intent.getStringExtra(CHOOSE_MODE)
        recyclerView = findViewById(R.id.rc_apps)

        // Check orientation
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        spanCount = if (isLandscape) LANDSCAPE_SPAN_COUNT else DEFAULT_SPAN_COUNT

        val showHeaders =
            !BPrefs.get(this).getBoolean(
                BPrefs.APPS_ONE_GRID_KEY,
                BPrefs.APPS_ONE_GRID_DEFAULT_VALUE
            )
        viewModel.setShowHeaders(showHeaders)
        viewModel.setIsChooseMode(chooseKey != null)

        setupAdapter()
        setupLayoutManager()
        observeApps()
    }

    private fun setupAdapter() {
        adapter =
            AppsListAdapter(
                onAppClick = if (chooseKey != null) ::appChosen else ::showDropDown
            )
        recyclerView.adapter = adapter
    }

    private fun setupLayoutManager() {
        val gridLayoutManager = GridLayoutManager(this, spanCount)
        gridLayoutManager.spanSizeLookup =
            object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (adapter.getItemViewType(position) == AppsListAdapter.TYPE_HEADER) spanCount else 1
            }
        recyclerView.layoutManager = gridLayoutManager
    }

    private fun observeApps() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.appItems.collect { items ->
                    adapter.submitList(items)
                }
            }
        }
    }

    /**
     * Switches the layout between grid and list mode.
     *
     * For a grid: `setLayoutSpanCount(3)` (portrait) or `6` (landscape).
     * For a list: `setLayoutSpanCount(1)`.
     */
    @Suppress("unused") // Reserved for future grid/list toggle feature
    fun setLayoutSpanCount(newSpanCount: Int) {
        spanCount = newSpanCount
        (recyclerView.layoutManager as? GridLayoutManager)?.spanCount = spanCount
    }

    private fun showDropDown(index: Int, app: AppEntry) {
        val view = recyclerView.layoutManager?.findViewByPosition(index) ?: return

        S.showDropDownPopup(
            this,
            recyclerView.width,
            object : DropDownRecyclerViewAdapter.DropDownListener {
                override fun onUpdate(
                    viewHolder: DropDownRecyclerViewAdapter.ViewHolder,
                    position: Int,
                    popupWindow: PopupWindow
                ) {
                    when (position) {
                        0 -> setupOpenOption(viewHolder, app, popupWindow)
                        1 -> setupPinOption(viewHolder, app, popupWindow)
                        2 -> setupUninstallOption(viewHolder, app, popupWindow)
                    }
                }

                override fun size(): Int = if (app.isPredefined) 2 else 3

                override fun onDismiss() {
                    adapter.deselect()
                }
            },
            view
        )

        // Ensure visibility for items at the bottom of the list
        if (index + spanCount >= adapter.itemCount) {
            recyclerView.scrollToPosition(index)
        }
    }

    private fun setupOpenOption(vh: DropDownRecyclerViewAdapter.ViewHolder, app: AppEntry, pw: PopupWindow) {
        AppIconBinder.loadPic(app, vh.pic)
        vh.text.setText(R.string.open)
        vh.itemView.setOnClickListener {
            S.startComponentName(this, app)
            pw.dismiss()
        }
    }

    private fun setupPinOption(vh: DropDownRecyclerViewAdapter.ViewHolder, app: AppEntry, pw: PopupWindow) {
        vh.pic.setImageResource(if (app.isPinned) R.drawable.remove_on_button else R.drawable.add_on_button)
        vh.text.setText(if (app.isPinned) R.string.remove_shortcut else R.string.add_shortcut)
        vh.itemView.setOnClickListener {
            AppsRepository.updatePinnedJava(app.componentName, app.userId, !app.isPinned)
            pw.dismiss()
        }
    }

    private fun setupUninstallOption(vh: DropDownRecyclerViewAdapter.ViewHolder, app: AppEntry, pw: PopupWindow) {
        vh.pic.setImageResource(R.drawable.delete_on_button)
        vh.text.setText(R.string.uninstall)
        vh.itemView.setOnClickListener {
            BDB
                .from(this)
                .setTitle(String.format("%s %s", getText(R.string.uninstall), app.label))
                .setSubText(getString(R.string.uninstall_subtext, app.label, app.label))
                .addFlag(BDialog.FLAG_YES or BDialog.FLAG_CANCEL)
                .setPositiveButtonListener {
                    uninstallApp(app)
                    true
                }.show()
            pw.dismiss()
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

    private fun uninstallApp(app: AppEntry) {
        val intent =
            Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = "package:${app.packageName}".toUri()
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
        startActivity(intent)
    }

    companion object {
        const val REQUEST_SELECT_CUSTOM_APP = 88

        const val CHOOSE_MODE = "CHOOSE_MODE"
        private const val SELECTED_APP_INDEX = "SELECTED_APP_INDEX"
        private const val DEFAULT_SPAN_COUNT = 3
        private const val LANDSCAPE_SPAN_COUNT = 6
    }
}
