package app.baldphone.neo.features.media.ui.browser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

import kotlinx.coroutines.launch

import app.baldphone.neo.databinding.FragmentMediaBrowserBinding
import app.baldphone.neo.features.media.ui.MediaViewModel
import app.baldphone.neo.views.GridSpacingDecoration

/**
 * Fragment displaying the media gallery grid.
 */
class MediaBrowserFragment : Fragment() {
    private val viewModel: MediaViewModel by activityViewModels()

    private var binding: FragmentMediaBrowserBinding? = null
    private var mediaAdapter: MediaListAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMediaBrowserBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGrid()
        observeViewModel()
    }

    private fun setupGrid() {
        val columnCount = calculateColumnCount()

        val spacingPx = MediaActivity.GRID_SPACING_PX
        val screenWidthPx = requireContext().resources.displayMetrics.widthPixels
        val totalSpacing = (columnCount - 1) * spacingPx
        val itemWidth = (screenWidthPx - totalSpacing) / columnCount
        val remainderPx = screenWidthPx - (columnCount * itemWidth) - totalSpacing
        val paddingLeft = remainderPx / 2
        val paddingRight = remainderPx - paddingLeft

        Log.d("MBF_Grid", "========== setupGrid ==========")
        Log.d("MBF_Grid", "columnCount: $columnCount")
        Log.d("MBF_Grid", "spacingPx: $spacingPx")
        Log.d("MBF_Grid", "screenWidthPx: $screenWidthPx")
        Log.d("MBF_Grid", "totalSpacing: $totalSpacing")
        Log.d("MBF_Grid", "itemWidth: $itemWidth")
        Log.d("MBF_Grid", "remainderPx: $remainderPx")
        Log.d("MBF_Grid", "paddingLeft: $paddingLeft")
        Log.d("MBF_Grid", "paddingRight: $paddingRight")
        Log.d("MBF_Grid", "===============================")

        val rv = binding!!.mediaGrid
        rv.setPadding(paddingLeft, rv.paddingTop, paddingRight, rv.paddingBottom)

        mediaAdapter = MediaListAdapter { item -> handleItemClick(item) }

        rv.let {
            it.setHasFixedSize(true)
            it.layoutManager = GridLayoutManager(requireContext(), columnCount)
            it.addItemDecoration(GridSpacingDecoration(columnCount, spacingPx, itemWidth))
            it.adapter = mediaAdapter
            it.setItemViewCacheSize(columnCount * 2)
        }
    }

    private fun handleItemClick(item: app.baldphone.neo.features.media.MediaItem) {
        val activity = requireActivity() as MediaActivity
        if (activity.isMediaChooseMode) {
            activity.setResult(Activity.RESULT_OK, Intent().setData(item.uri))
            activity.finish()
        } else {
            val position = viewModel.items.value.indexOfFirst { it.id == item.id }
            if (position >= 0) {
                viewModel.openViewer(position)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe items
                launch {
                    viewModel.items.collect { list ->
                        mediaAdapter?.submitList(list) {
                            // After list is submitted, check if we need to scroll to a specific position
                            // usually done only on the first load or orientation change
                            scrollToSelectedIfNeeded()
                        }
                    }
                }
            }
        }
    }

    private fun scrollToSelectedIfNeeded() {
        val pos = viewModel.selectedPosition.value
        val count = mediaAdapter?.currentListSize ?: 0
        if (pos !in 0 until count) return

        binding!!.mediaGrid.doOnLayout { view ->
            val rv = view as RecyclerView
            val lm = rv.layoutManager as? GridLayoutManager ?: return@doOnLayout
            val firstVisible = lm.findFirstVisibleItemPosition()
            val lastVisible = lm.findLastVisibleItemPosition()
            android.util.Log.d(
                "MBF",
                "scrollToSelectedIfNeeded: firstVisible=$firstVisible, lastVisible=$lastVisible, pos=$pos"
            )
            if (pos !in firstVisible..lastVisible) {
                android.util.Log.d("MBF", "scrollToSelectedIfNeeded: scrolling to $pos")
                rv.post {
                    rv.scrollToPosition(pos)
                }
            }
        }
    }

    private fun calculateColumnCount(): Int {
        val displayMetrics = requireContext().resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        val columnCount = (screenWidthDp / THUMBNAIL_DIMENSION_DP).toInt()
        return columnCount.coerceAtLeast(2)
    }

    override fun onDestroyView() {
        binding?.mediaGrid?.let { rv ->
            while (rv.itemDecorationCount > 0) {
                rv.removeItemDecorationAt(0)
            }
        }
        super.onDestroyView()
        binding = null
        mediaAdapter = null
    }

    companion object {
        private const val THUMBNAIL_DIMENSION_DP = 120
    }
}
