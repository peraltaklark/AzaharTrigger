// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version.
// Refer to the license.txt file included.

package org.citra.citra_emu.features.touchinput

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import org.citra.citra_emu.NativeLibrary
import kotlin.math.min

class TouchInputBindingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val bottomScreenRect = RectF()
    private val clipPath = Path()

    private val bottomScreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F4F4F6")
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
    }

    private val pointOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val pointInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = POINT_LABEL_TEXT_SIZE
        isFakeBoldText = true
    }

    private val bindings = mutableListOf<TouchInputBinding>()

    private var selectedX = UNSELECTED_COORDINATE
    private var selectedY = UNSELECTED_COORDINATE

    var onTouchPointSelected: ((Float, Float) -> Unit)? = null

    companion object {
        private const val UNSELECTED_COORDINATE = -1f
        private const val SCREEN_SCALE_FACTOR = 0.8f
        private const val BINDING_POINT_RADIUS = 18f
        private const val SELECTED_POINT_RADIUS = 20f
        private const val POINT_LABEL_TEXT_SIZE = 22f
        private const val CORNER_RADIUS_DP = 16f

        // Grid lines density (16 columns x 12 rows)
        private const val GRID_COLUMNS = 16
        private const val GRID_ROWS = 12
    }

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
            val cornerPx = CORNER_RADIUS_DP * resources.displayMetrics.density
            clipPath.reset()
            clipPath.addRoundRect(bottomScreenRect, cornerPx, cornerPx, Path.Direction.CW)
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cornerPx = CORNER_RADIUS_DP * resources.displayMetrics.density

        // 1. Draw rounded bottom screen background
        canvas.drawRoundRect(bottomScreenRect, cornerPx, cornerPx, bottomScreenPaint)

        // 2. Draw subtle grid pattern clipped inside rounded rectangle
        drawGrid(canvas)

        // 3. Draw rounded grey outline stroke
        outlinePaint.color = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOutlineVariant
        )
        canvas.drawRoundRect(bottomScreenRect, cornerPx, cornerPx, outlinePaint)

        // 4. Draw existing confirmed bindings with their numbers (1, 2, 3...)
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

        // 5. Draw active selection as BLANK (number = 0) while waiting for user to bind a key
        if (selectedX >= 0f && selectedY >= 0f) {
            drawBindingPoint(
                canvas = canvas,
                x = selectedX,
                y = selectedY,
                number = 0, // 0 = Keep blank during selection prompt
                selected = true
            )
        }
    }

    private fun drawGrid(canvas: Canvas) {
        gridPaint.color = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOutlineVariant
        )
        gridPaint.alpha = 55 // Subtle grid intensity

        canvas.save()
        canvas.clipPath(clipPath)

        val width = bottomScreenRect.width()
        val height = bottomScreenRect.height()

        // Draw Vertical Grid Lines
        val columnWidth = width / GRID_COLUMNS
        for (i in 1 until GRID_COLUMNS) {
            val x = bottomScreenRect.left + (i * columnWidth)
            canvas.drawLine(x, bottomScreenRect.top, x, bottomScreenRect.bottom, gridPaint)
        }

        // Draw Horizontal Grid Lines
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
        val radius = if (selected) SELECTED_POINT_RADIUS else BINDING_POINT_RADIUS

        pointOuterPaint.color = Color.WHITE
        pointInnerPaint.color = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorPrimaryContainer
        )

        canvas.drawCircle(x, y, radius, pointOuterPaint)
        canvas.drawCircle(x, y, radius - 3f, pointInnerPaint)

        // Only draw the number inside the circle when a valid number (> 0) exists
        if (number > 0) {
            labelPaint.color = MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOnPrimaryContainer
            )

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
