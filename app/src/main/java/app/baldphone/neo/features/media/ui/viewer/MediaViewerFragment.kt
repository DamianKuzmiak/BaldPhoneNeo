package app.baldphone.neo.features.media.ui.viewer

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import app.baldphone.neo.R
import app.baldphone.neo.features.media.MediaItem
import app.baldphone.neo.features.media.MediaMode
import app.baldphone.neo.features.media.ui.MediaViewModel
import app.baldphone.neo.features.media.ui.browser.MediaActivity
import app.baldphone.neo.ui.dialogs.BaldDialog
import app.baldphone.neo.ui.dialogs.showErrorSnackbar
import app.baldphone.neo.ui.menu.showActionMenu
import app.baldphone.neo.utils.shareMedia

/**
 * Fragment for full-screen media viewing with swipe navigation.
 *
 * Uses [MediaViewModel] (Activity-scoped) to share the media list
 * and selected position with [app.baldphone.neo.features.media.ui.browser.MediaBrowserFragment].
 */
class MediaViewerFragment : Fragment() {
    private val viewModel: MediaViewModel by activityViewModels()

    private var viewPager: ViewPager2? = null
    private var adapter: MediaViewerAdapter? = null
    private var pendingDeleteUri: Uri? = null
    private var initialPositionApplied = false
    private val playbackManager = VideoPlaybackManager()

    private val currentMediaItem: MediaItem?
        get() = viewPager?.currentItem?.let { adapter?.getItemAt(it) }

    private val intentSenderLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    val uri = pendingDeleteUri ?: currentMediaItem?.uri
                    if (uri != null) {
                        try {
                            requireContext().contentResolver.delete(uri, null, null)
                        } catch (e: SecurityException) {
                            Log.e(TAG, "Failed to delete after user permission on Q", e)
                            requireActivity().showErrorSnackbar(R.string.an_error_has_occurred)
                        }
                    }
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_media_viewer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewPager = view.findViewById(R.id.view_pager)
        initialPositionApplied = false
        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        adapter = MediaViewerAdapter(playbackManager)
        viewPager?.adapter = adapter
        viewPager?.offscreenPageLimit = 1
        viewPager?.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    playbackManager.stop()
                    viewModel.setCurrentPosition(position)
                }
            }
        )
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.items.collectLatest { mediaList ->
                        if (mediaList.isEmpty()) return@collectLatest
                        adapter?.submitList(mediaList) {
                            if (!initialPositionApplied) {
                                val targetIndex = viewModel.selectedPosition.value.coerceIn(0, mediaList.size - 1)
                                viewPager?.setCurrentItem(targetIndex, false)
                                initialPositionApplied = true
                            }
                        }
                    }
                }

                launch {
                    viewModel.moreMenuClickEvent.collect {
                        val hostActivity = requireActivity() as? MediaActivity
                        val anchor: View? = hostActivity?.moreButtonAnchor
                        if (anchor != null) {
                            showPopup(anchor)
                        }
                    }
                }
            }
        }
    }

    /**
     * Shows the share/delete action menu, anchored to the given view.
     * Triggered via [MediaViewModel.moreMenuClickEvent].
     */
    private fun showPopup(anchor: View) {
        requireContext().showActionMenu(anchor) {
            option(iconRes = R.drawable.share_on_background, labelRes = R.string.share, onClick = {
                currentMediaItem?.let { shareMediaItem(it) }
            })

            option(iconRes = R.drawable.delete_on_background, labelRes = R.string.delete, onClick = {
                currentMediaItem?.let { deleteMediaItem(it) }
            })
        }
    }

    private fun shareMediaItem(item: MediaItem) {
        val mimeType =
            when (item) {
                is MediaItem.Photo -> "image/*"
                is MediaItem.Video -> "video/*"
            }
        requireContext().shareMedia(item.uri, mimeType)
    }

    private fun deleteMediaItem(item: MediaItem) {
        val uri = item.uri
        val contentResolver = requireContext().contentResolver
        pendingDeleteUri = uri

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
            try {
                val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                intentSenderLauncher.launch(request)
            } catch (e: IntentSender.SendIntentException) {
                Log.e(TAG, "Error starting delete intent sender", e)
                requireActivity().showErrorSnackbar(R.string.an_error_has_occurred)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                contentResolver.delete(uri, null, null)
            } catch (e: RecoverableSecurityException) {
                Log.e(TAG, "RecoverableSecurityException on delete", e)
                try {
                    val request = IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                    intentSenderLauncher.launch(request)
                } catch (ex: IntentSender.SendIntentException) {
                    Log.e(TAG, "Error starting recover intent sender", ex)
                    requireActivity().showErrorSnackbar(R.string.an_error_has_occurred)
                }
            }
        } else {
            val mode = (requireActivity() as MediaActivity).mode
            val title = resolveTitle(mode)
            BaldDialog
                .Builder(requireContext())
                .setTitle(getString(R.string.delete___, title))
                .setMessage(getString(R.string.are_you_sure_you_want_to_delete___, title))
                .setPositiveButton(R.string.yes) {
                    try {
                        contentResolver.delete(uri, null, null)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting media item", e)
                        requireActivity().showErrorSnackbar(R.string.an_error_has_occurred)
                    }
                }.setNegativeButton(R.string.cancel)
                .show()
        }
    }

    private fun resolveTitle(mode: Int): String =
        when (mode) {
            MediaMode.PHOTOS -> getString(R.string.photo)
            MediaMode.VIDEOS -> getString(R.string.videos)
            else -> getString(R.string.photos_and_videos)
        }

    override fun onPause() {
        super.onPause()
        playbackManager.pause()
    }

    override fun onDestroyView() {
        playbackManager.stop()
        viewPager = null
        adapter = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "MediaViewerFragment"
    }
}
