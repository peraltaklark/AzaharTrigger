// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version.
// Refer to the license.txt file included.

package org.citra.citra_emu.features.touchinput

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import org.citra.citra_emu.NativeLibrary
import kotlin.math.min

class TouchInputBindingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private val bottomScreenRect = RectF()
    private val clipPath = Path()

    private val bottomScreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    private val pointRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pointFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            POINT_LABEL_TEXT_SIZE_SP,
            resources.displayMetrics
        )
        isFakeBoldText = true
    }

    private val bindings = mutableListOf<TouchInputBinding>()

    private var selectedX = UNSELECTED_COORDINATE
    private var selectedY = UNSELECTED_COORDINATE

    var onTouchPointSelected: ((Float, Float) -> Unit)? = null

    companion object {
        private const val UNSELECTED_COORDINATE = -1f

        // Use nearly the full view; the outline needs a little room
        private const val SCREEN_SCALE_FACTOR = 0.98f

        private const val BINDING_POINT_RADIUS_DP = 12f
        private const val SELECTED_POINT_RADIUS_DP = 14f
        private const val SELECTED_HALO_RADIUS_DP = 26f
        private const val POINT_RING_WIDTH_DP = 2f
        private const val POINT_LABEL_TEXT_SIZE_SP = 13f
        private const val CORNER_RADIUS_DP = 16f

        // Grid lines density (16 columns x 12 rows)
        private const val GRID_COLUMNS = 16
        private const val GRID_ROWS = 12
    }

    private fun themeColor(attr: Int): Int =
        MaterialColors.getColor(this, attr)

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateScreenRect()
    }

    private fun updateScreenRect() {
        if (width <= 0 || height <= 0) return

        val layout = NativeLibrary.getFramebufferLayout()
        if (layout.size >= 6) {
            val bottomLeft = layout[2]
            val bottomTop = layout[3]
            val bottomRight = layout[4]
            val bottomBottom = layout[5]

            val bottomWidth = (bottomRight - bottomLeft).toFloat()
            val bottomHeight = (bottomBottom - bottomTop).toFloat()

            if (bottomWidth <= 0 || bottomHeight <= 0) return

            val scaleX = (width * SCREEN_SCALE_FACTOR) / bottomWidth
            val scaleY = (height * SCREEN_SCALE_FACTOR) / bottomHeight
            val scale = min(scaleX, scaleY)

            val scaledWidth = bottomWidth * scale
            val scaledHeight = bottomHeight * scale

            val offsetX = (width - scaledWidth) / 2f
            val offsetY = (height - scaledHeight) / 2f

            bottomScreenRect.set(
                offsetX,
                offsetY,
                offsetX + scaledWidth,
                offsetY + scaledHeight
            )

            // Update clip path for rounded corner grid clipping
            val cornerPx = CORNER_RADIUS_DP * density
            clipPath.reset()
            clipPath.addRoundRect(bottomScreenRect, cornerPx, cornerPx, Path.Direction.CW)
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cornerPx = CORNER_RADIUS_DP * density

        // 1. Rounded "screen" background. Theme-aware, so it works in dark mode too.
        bottomScreenPaint.color = themeColor(com.google.android.material.R.attr.colorSurface)
        canvas.drawRoundRect(bottomScreenRect, cornerPx, cornerPx, bottomScreenPaint)

        // 2. Subtle grid clipped to the rounded rectangle
        drawGrid(canvas)

        // 3. Outline
        outlinePaint.color = themeColor(com.google.android.material.R.attr.colorOutlineVariant)
        canvas.drawRoundRect(bottomScreenRect, cornerPx, cornerPx, outlinePaint)

        // 4. Confirmed bindings, numbered 1, 2, 3...
        bindings.forEachIndexed { index, binding ->
            val pointX = bottomScreenRect.left + binding.x * bottomScreenRect.width()
            val pointY = bottomScreenRect.top + binding.y * bottomScreenRect.height()

            drawBindingPoint(
                canvas = canvas,
                x = pointX,
                y = pointY,
                number = index + 1,
                selected = false
            )
        }

        // 5. Pending selection: blank marker with a halo while waiting for a button press
        if (selectedX >= 0f && selectedY >= 0f) {
            drawBindingPoint(
                canvas = canvas,
                x = selectedX,
                y = selectedY,
                number = 0,
                selected = true
            )
        }
    }

    private fun drawGrid(canvas: Canvas) {
        gridPaint.color = themeColor(com.google.android.material.R.attr.colorOutlineVariant)
        gridPaint.alpha = 55

        canvas.save()
        canvas.clipPath(clipPath)

        val width = bottomScreenRect.width()
        val height = bottomScreenRect.height()

        val columnWidth = width / GRID_COLUMNS
        for (i in 1 until GRID_COLUMNS) {
            val x = bottomScreenRect.left + (i * columnWidth)
            canvas.drawLine(x, bottomScreenRect.top, x, bottomScreenRect.bottom, gridPaint)
        }

        val rowHeight = height / GRID_ROWS
        for (i in 1 until GRID_ROWS) {
            val y = bottomScreenRect.top + (i * rowHeight)
            canvas.drawLine(bottomScreenRect.left, y, bottomScreenRect.right, y, gridPaint)
        }

        canvas.restore()
    }

    private fun drawBindingPoint(
        canvas: Canvas,
        x: Float,
        y: Float,
        number: Int,
        selected: Boolean
    ) {
        val radius = (if (selected) SELECTED_POINT_RADIUS_DP else BINDING_POINT_RADIUS_DP) * density
        val ringWidth = POINT_RING_WIDTH_DP * density

        val primary = themeColor(androidx.appcompat.R.attr.colorPrimary)
        val primaryContainer = themeColor(com.google.android.material.R.attr.colorPrimaryContainer)
        val surface = themeColor(com.google.android.material.R.attr.colorSurface)

        if (selected) {
            // Soft halo so the pending point stands out from confirmed ones
            haloPaint.color = ColorUtils.setAlphaComponent(primary, 60)
            canvas.drawCircle(x, y, SELECTED_HALO_RADIUS_DP * density, haloPaint)
        }

        // Ring uses the surface color so it follows light/dark theme
        pointRingPaint.color = surface
        canvas.drawCircle(x, y, radius, pointRingPaint)

        pointFillPaint.color = if (selected) primary else primaryContainer
        canvas.drawCircle(x, y, radius - ringWidth, pointFillPaint)

        // Number only for confirmed bindings
        if (number > 0) {
            labelPaint.color = themeColor(com.google.android.material.R.attr.colorOnPrimaryContainer)

            val textY = y - (labelPaint.ascent() + labelPaint.descent()) / 2f
            canvas.drawText(number.toString(), x, textY, labelPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) {
            return super.onTouchEvent(event)
        }

        if (!bottomScreenRect.contains(event.x, event.y)) {
            return false
        }

        selectedX = event.x
        selectedY = event.y
        invalidate()

        val normalizedX = (event.x - bottomScreenRect.left) / bottomScreenRect.width()
        val normalizedY = (event.y - bottomScreenRect.top) / bottomScreenRect.height()

        onTouchPointSelected?.invoke(
            normalizedX.coerceIn(0f, 1f),
            normalizedY.coerceIn(0f, 1f)
        )

        return true
    }

    fun setBindings(newBindings: List<TouchInputBinding>) {
        bindings.clear()
        bindings.addAll(newBindings)
        // Clear temporary point so the new saved binding renders in place
        selectedX = UNSELECTED_COORDINATE
        selectedY = UNSELECTED_COORDINATE

        if (isAttachedToWindow) {
            updateScreenRect()
            invalidate()
        } else {
            post {
                updateScreenRect()
                invalidate()
            }
        }
    }

    fun clearSelection() {
        selectedX = UNSELECTED_COORDINATE
        selectedY = UNSELECTED_COORDINATE
        invalidate()
    }
}
