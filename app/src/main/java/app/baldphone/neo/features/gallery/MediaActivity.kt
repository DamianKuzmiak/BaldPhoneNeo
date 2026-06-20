package app.baldphone.neo.features.gallery

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.os.Build
import android.os.Bundle

import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

import kotlinx.coroutines.launch

import app.baldphone.neo.activities.BaseActivity
import app.baldphone.neo.permissions.PermissionManager
import app.baldphone.neo.permissions.model.RuntimePermission
import app.baldphone.neo.permissions.model.SpecialPermission
import app.baldphone.neo.views.TitleBarView

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.activities.media.SingleMediaActivity
import com.bald.uriah.baldphone.activities.media.SinglePhotoActivity
import com.bald.uriah.baldphone.activities.media.SingleVideoActivity

/**
 * Unified media browsing activity that supports three display modes, controlled via [EXTRA_MODE]:
 * - [MODE_PHOTOS_AND_VIDEOS] – all media (default).
 * - [MODE_PHOTOS_ONLY] – photos only.
 * - [MODE_VIDEOS_ONLY] – videos only.
 */
class MediaActivity : BaseActivity() {
    private val viewModel: MediaViewModel by viewModels()

    private val startMediaActivityForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == SingleMediaActivity.SHOULD_REFRESH) {
                viewModel.refresh(mode)
            }
        }

    private var mode = MODE_PHOTOS_AND_VIDEOS
    private var mediaChoose = false

    private lateinit var titleBar: TitleBarView
    private lateinit var recyclerView: RecyclerView
    private var adapter: MediaListAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media)

        mode = resolveMode()
        mediaChoose = intent?.action in listOf(Intent.ACTION_GET_CONTENT, Intent.ACTION_PICK)

        setupViews()
        observeViewModel()

        PermissionManager.checkOrRequest(this, RuntimePermission.MediaStorage) { result ->
            if (result == PermissionManager.GRANTED) {
                viewModel.refresh(mode)
            }
        }
    }

    private fun setupViews() {
        titleBar = findViewById(R.id.title_bar)
        recyclerView = findViewById(R.id.child)
        titleBar.setTitle(resolveTitle())

        val columnCount = calculateColumnCount()
        recyclerView.layoutManager = GridLayoutManager(this, columnCount)

        adapter = MediaListAdapter { handleItemClick(it) }
        recyclerView.adapter = adapter
        recyclerView.setItemViewCacheSize(columnCount)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.items.collect { list ->
                    adapter?.submitList(list)
                }
            }
        }
    }

    private fun calculateColumnCount(): Int {
        val screenWidthPx =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.currentWindowMetrics.bounds.width()
            } else {
                val point = Point()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getSize(point)
                point.x
            }
        val density = resources.displayMetrics.density
        val screenWidthDp = screenWidthPx / density
        val columnCount = (screenWidthDp / THUMBNAIL_DIMENSION_DP).toInt()

        return columnCount.coerceAtLeast(2)
    }

    private fun resolveTitle(): String =
        when (mode) {
            MODE_PHOTOS_ONLY -> getString(R.string.photos)
            MODE_VIDEOS_ONLY -> getString(R.string.videos)
            else -> getString(R.string.photos_and_videos)
        }

    private fun resolveMode(): Int {
        val intentMode = intent?.getIntExtra(EXTRA_MODE, -1)
        if (intentMode in MODE_PHOTOS_AND_VIDEOS..MODE_VIDEOS_ONLY) return intentMode!!

        return try {
            packageManager
                .getActivityInfo(componentName, PackageManager.GET_META_DATA)
                .metaData
                ?.getInt(EXTRA_MODE, MODE_PHOTOS_AND_VIDEOS) ?: MODE_PHOTOS_AND_VIDEOS
        } catch (_: Exception) {
            MODE_PHOTOS_AND_VIDEOS
        }
    }

    private fun handleItemClick(item: MediaItem) {
        if (mediaChoose) {
            setResult(RESULT_OK, Intent().setData(item.uri))
            finish()
        } else {
            val position = adapter?.currentList?.indexOf(item) ?: -1
            if (position == -1) return

            val targetActivity: Class<out SingleMediaActivity> =
                when (item.type) {
                    MediaType.PHOTO -> SinglePhotoActivity::class.java
                    MediaType.VIDEO -> SingleVideoActivity::class.java
                }
            val intent =
                Intent(this, targetActivity)
                    .putExtra(SingleMediaActivity.MEDIA_KEY, position)
            startMediaActivityForResult.launch(intent)
        }
    }

    companion object {
        private const val TAG = "MediaActivity"

        private const val THUMBNAIL_DIMENSION_DP = 120f

        /** Intent extra key – one of [MODE_PHOTOS_ONLY], [MODE_VIDEOS_ONLY], [MODE_PHOTOS_AND_VIDEOS]. */
        const val EXTRA_MODE = "media_mode"
        const val MODE_PHOTOS_AND_VIDEOS = 0
        const val MODE_PHOTOS_ONLY = 1
        const val MODE_VIDEOS_ONLY = 2
    }
}
