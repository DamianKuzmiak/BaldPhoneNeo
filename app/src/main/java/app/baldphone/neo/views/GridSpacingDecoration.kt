package app.baldphone.neo.views

import android.graphics.Rect
import android.util.Log
import android.view.View

import androidx.recyclerview.widget.RecyclerView

/**
 * An [RecyclerView.ItemDecoration] that adds consistent spacing between items in a grid layout.
 */
class GridSpacingDecoration(
    private val spanCount: Int,
    private val spacingPx: Int,
    private val itemWidth: Int
) : RecyclerView.ItemDecoration() {
    private val borders: IntArray

    init {
        val totalSpace = spanCount * itemWidth + (spanCount - 1) * spacingPx
        borders = calculateItemBorders(totalSpace)
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val column = position % spanCount

        val expectedStart = column * (itemWidth + spacingPx)
        val expectedEnd = expectedStart + itemWidth

        val spanStart = borders[column]
        val spanEnd = borders[column + 1]

        outRect.left = expectedStart - spanStart
        outRect.right = spanEnd - expectedEnd

        if (position >= spanCount) {
            outRect.top = spacingPx
        }
        outRect.bottom = 0
    }

    private fun calculateItemBorders(totalSpace: Int): IntArray {
        val borders = IntArray(spanCount + 1)
        borders[0] = 0
        val sizePerSpan = totalSpace / spanCount
        val sizePerSpanRemainder = totalSpace % spanCount
        var consumedPixels = 0
        var additionalPixels = 0
        for (i in 1..spanCount) {
            var itemSize = sizePerSpan
            additionalPixels += sizePerSpanRemainder
            if (additionalPixels > 0 && (spanCount - additionalPixels) < sizePerSpanRemainder) {
                itemSize += 1
                additionalPixels -= spanCount
            }
            consumedPixels += itemSize
            borders[i] = consumedPixels
        }
        return borders
    }
}
