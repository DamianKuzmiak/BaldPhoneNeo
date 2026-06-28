package app.baldphone.neo.features.media.ui.browser

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View

import androidx.activity.viewModels
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlinx.coroutines.launch

import app.baldphone.neo.R
import app.baldphone.neo.activities.BaseActivity
import app.baldphone.neo.databinding.ActivityMediaBinding
import app.baldphone.neo.features.media.MediaMode
import app.baldphone.neo.features.media.ui.MediaViewModel
import app.baldphone.neo.features.media.ui.viewer.MediaViewerFragment
import app.baldphone.neo.permissions.PermissionManager
import app.baldphone.neo.permissions.model.RuntimePermission

/**
 * Single-Activity host for the media feature.
 */
class MediaActivity : BaseActivity() {
    private val viewModel: MediaViewModel by viewModels()

    /** The current display mode (photos, videos, or both). */
    var mode = MediaMode.PHOTOS_AND_VIDEOS
        private set

    /** Whether this Activity was launched as a media picker (ACTION_PICK / ACTION_GET_CONTENT). */
    var isMediaChooseMode = false
        private set

    private lateinit var binding: ActivityMediaBinding

    val moreButtonAnchor: View
        get() = binding.titleBar.findViewById(R.id.btnMore)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = resolveMode()
        isMediaChooseMode = intent?.action in listOf(Intent.ACTION_GET_CONTENT, Intent.ACTION_PICK)

        binding.titleBar.setTitle(resolveBrowserTitle())

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add(binding.fragmentContainer.id, MediaBrowserFragment())
            }
        }

        setupBackStackListener()
        observeNavigation()

        PermissionManager.checkOrRequest(this, RuntimePermission.MediaStorage) { result ->
            if (result == PermissionManager.GRANTED) {
                viewModel.loadMedia(mode)
            } else {
                finish()
            }
        }
    }

    private fun setupBackStackListener() {
        supportFragmentManager.addOnBackStackChangedListener {
            val isViewerVisible = supportFragmentManager.backStackEntryCount > 0
            with(binding.titleBar) {
                if (isViewerVisible) {
                    setTitle(resolveViewerTitle())
                    setOnMoreClickListener { _ -> viewModel.onMoreMenuClicked() }
                } else {
                    setTitle(resolveBrowserTitle())
                    showMoreButton(false)
                    setOnMoreClickListener(null)
                }
            }
        }
    }

    private fun observeNavigation() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigationEvent.collect { event ->
                    when (event) {
                        is MediaViewModel.NavigationEvent.OpenViewer -> showViewerFragment()
                        is MediaViewModel.NavigationEvent.CloseViewer -> popViewerFragment()
                    }
                }
            }
        }
    }

    private fun showViewerFragment() {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(binding.fragmentContainer.id, MediaViewerFragment())
            addToBackStack("viewer")
        }
    }

    private fun popViewerFragment() {
        supportFragmentManager.popBackStack()
    }

    private fun resolveMode(): Int {
        val intentMode = intent?.getIntExtra(MediaMode.EXTRA, -1) ?: -1
        if (intentMode in 0..2) return intentMode

        return try {
            val metaValue =
                packageManager
                    .getActivityInfo(intent?.component ?: componentName, PackageManager.GET_META_DATA)
                    .metaData
                    ?.getInt(MediaMode.EXTRA, MediaMode.PHOTOS_AND_VIDEOS) ?: MediaMode.PHOTOS_AND_VIDEOS
            metaValue
        } catch (_: Exception) {
            MediaMode.PHOTOS_AND_VIDEOS
        }
    }

    private fun resolveBrowserTitle(): String =
        when (mode) {
            MediaMode.PHOTOS -> getString(R.string.photos)
            MediaMode.VIDEOS -> getString(R.string.videos)
            else -> getString(R.string.photos_and_videos)
        }

    private fun resolveViewerTitle(): String =
        when (mode) {
            MediaMode.PHOTOS -> getString(R.string.photo)
            MediaMode.VIDEOS -> getString(R.string.videos)
            else -> getString(R.string.photos_and_videos)
        }

    companion object {
        const val GRID_SPACING_PX = 2
    }
}
