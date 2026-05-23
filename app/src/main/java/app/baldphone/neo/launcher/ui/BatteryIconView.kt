package app.baldphone.neo.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button

import androidx.annotation.UiThread
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.withStyledAttributes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlin.properties.Delegates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import app.baldphone.neo.battery.BatteryRepository
import app.baldphone.neo.battery.BatteryState

import com.bald.uriah.baldphone.R

/**
 * A custom vertical battery icon with five segmented bars.
 *
 * Visuals are driven by [setBatteryState]:
 * - Critical low (≤ 5%): red + blinking.
 * - Low (> 5%, system low): red, no blink.
 * - Charging: bolt overlay drawn.
 * - Full (100%): Solid green.
 * - Normal: Gray.
 */
class BatteryIconView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = R.attr.batteryIconViewStyle
    ) : View(context, attrs, defStyleAttr) {
        companion object {
            private const val SEGMENT_COUNT = 5
            private const val GAP_FRACTION = 0.15f
            private const val BLINK_DURATION_MS = 800L
            private const val CRITICAL_LOW_LEVEL = 0.05f

            // Segment bounds ratios based on a 32x32 grid (see drawable/ic_battery_outline.xml)
            private const val SEGMENT_LEFT_RATIO = 11.55f / 32f
            private const val SEGMENT_TOP_RATIO = 7.15f / 32f
            private const val SEGMENT_RIGHT_RATIO = 20.45f / 32f
            private const val SEGMENT_BOTTOM_RATIO = 26.55f / 32f

            private const val DEFAULT_COLOR_NORMAL = Color.GRAY
            private const val DEFAULT_COLOR_LOW = Color.RED
            private const val DEFAULT_COLOR_FULL = Color.GREEN
        }

        private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        // Safe casting to avoid crashes if the XML changes
        private val containerDrawable: LayerDrawable? =
            AppCompatResources
                .getDrawable(context, R.drawable.battery_container)
                ?.mutate()
                ?.let { it as? LayerDrawable }
        private val outlineDrawable = containerDrawable?.findDrawableByLayerId(R.id.outline_layer)
        private val boltDrawable = containerDrawable?.findDrawableByLayerId(R.id.bolt_layer)

        private val segmentRects = Array(SEGMENT_COUNT) { RectF() }

        // Cached values for onDraw
        private var cachedSegmentHeight = 0f
        private var cachedSlotHeight = 0f
        private var cachedTotalUnits = 0f

        // Single delegate for alpha to ensure Paint and Drawable stay in sync
        private var internalAlpha by Delegates.observable(255) { _, old, new ->
            if (old == new) return@observable
            outlineDrawable?.alpha = new
            segmentPaint.alpha = new
            boltDrawable?.alpha = new
            postInvalidateOnAnimation()
        }

        private var colorNormal = DEFAULT_COLOR_NORMAL
        private var colorLow = DEFAULT_COLOR_LOW
        private var colorFull = DEFAULT_COLOR_FULL
        private var colorCharging = DEFAULT_COLOR_NORMAL

        private var batteryLevel: Float = 0f
        private var mode: Mode = Mode.NORMAL

        private enum class Mode { NORMAL, LOW, CRITICAL_LOW, FULL, CHARGING }

        private var viewScope: CoroutineScope? = null
        private var blinkJob: Job? = null

        private var lastBatteryState: BatteryState? = null

        val detailedContentDescription: String
            get() = lastBatteryState?.formatInfo(context) ?: ""

        init {
            context.withStyledAttributes(attrs, R.styleable.BatteryIconView, defStyleAttr, 0) {
                colorNormal = getColor(R.styleable.BatteryIconView_batteryNormalColor, DEFAULT_COLOR_NORMAL)
                colorLow = getColor(R.styleable.BatteryIconView_batteryLowColor, DEFAULT_COLOR_LOW)
                colorFull = getColor(R.styleable.BatteryIconView_batteryFullColor, DEFAULT_COLOR_FULL)
                colorCharging = getColor(R.styleable.BatteryIconView_batteryChargingColor, colorNormal)
            }

            // Prevents black segments if the first state update is NORMAL.
            handleModeChange()
        }

        /**
         * Updates the view state based on the provided [BatteryState].
         */
        @UiThread
        fun setBatteryState(batteryState: BatteryState) {
            if (lastBatteryState == batteryState) return

            lastBatteryState = batteryState
            val percentage = batteryState.percentage
            val newBatteryLevel = (percentage ?: 0) / 100f

            val newMode =
                when {
                    batteryState.isFull -> Mode.FULL
                    batteryState.isCharging -> Mode.CHARGING
                    percentage != null && newBatteryLevel <= CRITICAL_LOW_LEVEL -> Mode.CRITICAL_LOW
                    batteryState.isLow -> Mode.LOW
                    else -> Mode.NORMAL
                }

            val visualChanged = newBatteryLevel != batteryLevel || newMode != mode
            batteryLevel = newBatteryLevel
            contentDescription = batteryState.formatSimpleInfo(context)

            if (newMode != mode) {
                mode = newMode
                handleModeChange()
            }

            if (visualChanged) {
                invalidate()
            }
        }

        /**
         * Binds this view to the [BatteryRepository], automatically updating its state in sync with the lifecycle.
         */
        fun observeBatteryState(lifecycleOwner: LifecycleOwner) {
            lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    BatteryRepository.get(context).batteryState.collect { state ->
                        setBatteryState(state)
                    }
                }
            }
        }

        private fun handleModeChange() {
            val targetColor =
                when (mode) {
                    Mode.NORMAL -> colorNormal
                    Mode.LOW -> colorLow
                    Mode.CRITICAL_LOW -> colorLow
                    Mode.FULL -> colorFull
                    Mode.CHARGING -> colorCharging
                }

            segmentPaint.color = targetColor
            outlineDrawable?.setTint(targetColor)
//            boltDrawable?.setTint(targetColor) // Apply theme color to bolt as well

            // Re-apply alpha to the new color/tint
            val alpha = internalAlpha
            segmentPaint.alpha = alpha
            outlineDrawable?.alpha = alpha

            updateAnimationState()
        }

        private fun updateAnimationState() {
            if (mode == Mode.CRITICAL_LOW && canAnimate()) {
                if (blinkJob?.isActive != true) startBlinking()
            } else {
                stopBlinking()
            }
        }

        private fun startBlinking() {
            blinkJob =
                viewScope?.launch {
                    try {
                        while (isActive && canAnimate() && mode == Mode.CRITICAL_LOW) {
                            internalAlpha = if (internalAlpha == 255) 0 else 255
                            delay(BLINK_DURATION_MS)
                        }
                    } finally {
                        // Ensure icon is visible when coming back
                        internalAlpha = 255
                    }
                }
        }

        private fun stopBlinking() {
            blinkJob?.cancel()
            if (blinkJob == null && internalAlpha != 255) internalAlpha = 255
            blinkJob = null
        }

        private fun canAnimate(): Boolean = isShown && windowVisibility == VISIBLE

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int
        ) {
            if (w == oldw && h == oldh) return

            val contentW = (w - paddingLeft - paddingRight).toFloat()
            val contentH = (h - paddingTop - paddingBottom).toFloat()
            if (contentW <= 0 || contentH <= 0) return

            val size = minOf(contentW, contentH)
            val left = paddingLeft + (contentW - size) / 2f
            val top = paddingTop + (contentH - size) / 2f

            val drBounds = RectF(left, top, left + size, top + size)
            val intBounds = Rect()
            drBounds.roundOut(intBounds) // To avoid 1px gaps

            outlineDrawable?.bounds = intBounds
            boltDrawable?.bounds = intBounds

            val sLeft = left + size * SEGMENT_LEFT_RATIO
            val sTop = top + size * SEGMENT_TOP_RATIO
            val sRight = left + size * SEGMENT_RIGHT_RATIO
            val sBottom = top + size * SEGMENT_BOTTOM_RATIO

            cachedSlotHeight = (sBottom - sTop) / SEGMENT_COUNT
            val gapH = cachedSlotHeight * GAP_FRACTION
            cachedSegmentHeight = cachedSlotHeight - gapH
            cachedTotalUnits = SEGMENT_COUNT * cachedSlotHeight

            for (i in 0 until SEGMENT_COUNT) {
                val b = sBottom - i * cachedSlotHeight
                segmentRects[i].set(sLeft, b - cachedSegmentHeight, sRight, b)
            }
        }

        override fun onDraw(canvas: Canvas) {
            outlineDrawable?.draw(canvas)

            if (segmentRects.isEmpty() || segmentRects[0].isEmpty) return

            // Should draw segments
            if (mode != Mode.CRITICAL_LOW) {
                var remainingFillUnits = batteryLevel * cachedTotalUnits

                for (i in 0 until SEGMENT_COUNT) {
                    val drawH = minOf(remainingFillUnits, cachedSegmentHeight)
                    if (drawH > 0) {
                        val rect = segmentRects[i]
                        canvas.drawRect(rect.left, rect.bottom - drawH, rect.right, rect.bottom, segmentPaint)
                    }
                    remainingFillUnits -= cachedSlotHeight
                    if (remainingFillUnits <= 0) break
                }
            }

            if (mode == Mode.CHARGING) {
                boltDrawable?.draw(canvas)
            }
        }

        // Standard View lifecycle hooks to trigger animation state updates
        override fun onAttachedToWindow() {
            super.onAttachedToWindow()

            viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            updateAnimationState()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()

            viewScope?.cancel()
            viewScope = null
            internalAlpha = 255
        }

        // This or parent visibility changed
        override fun onVisibilityChanged(
            v: View,
            visibility: Int
        ) {
            super.onVisibilityChanged(v, visibility)
            updateAnimationState()
        }

        // App foreground/background
        override fun onWindowVisibilityChanged(visibility: Int) {
            super.onWindowVisibilityChanged(visibility)
            updateAnimationState()
        }

        override fun getAccessibilityClassName(): CharSequence = Button::class.java.name

        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.className = Button::class.java.name
        }
    }
