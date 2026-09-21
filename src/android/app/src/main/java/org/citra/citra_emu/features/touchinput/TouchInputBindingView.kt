// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version.
// Refer to the license.txt file included.

package org.citra.citra_emu.features.touchinput

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.annotation.AttrRes
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import kotlin.math.min
import com.google.android.material.R as MaterialR

/**
 * Preview of the 3DS bottom screen. Bindings are drawn as numbered dots with a name tag, and a
 * tap on the screen is reported as a position between 0 and 1 on both axes.
 */
class TouchInputBindingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    /** Called with the normalized (x, y) position of a tap on the screen. */
    var onTouchPointSelected: ((Float, Float) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val inset = INSET_DP * density

    private val screenRect = RectF()
    private val tagRect = RectF()

    private val bindings = mutableListOf<TouchInputBinding>()
    private val tagTexts = mutableListOf<String>()
    private var highlightedIndex = NO_HIGHLIGHT
    private var pendingX = NO_POINT
    private var pendingY = NO_POINT

    // The view is recreated when the theme changes, so the colors only need resolving once
    private val surfaceColor = themeColor(MaterialR.attr.colorSurface)
    private val surfaceVariantColor = themeColor(MaterialR.attr.colorSurfaceVariant)
    private val onSurfaceColor = themeColor(MaterialR.attr.colorOnSurface)
    private val outlineColor = themeColor(MaterialR.attr.colorOutline)
    private val primaryColor = themeColor(androidx.appcompat.R.attr.colorPrimary)
    private val onPrimaryColor = themeColor(MaterialR.attr.colorOnPrimary)
    private val primaryContainerColor = themeColor(MaterialR.attr.colorPrimaryContainer)
    private val onPrimaryContainerColor = themeColor(MaterialR.attr.colorOnPrimaryContainer)

    private val screenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = surfaceColor
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = OUTLINE_WIDTH_DP * density
        color = ColorUtils.setAlphaComponent(outlineColor, OUTLINE_ALPHA)
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ColorUtils.setAlphaComponent(outlineColor, GRID_ALPHA)
    }

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ColorUtils.setAlphaComponent(primaryColor, HALO_ALPHA)
    }

    private val dotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = surfaceColor
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = DOT_OUTLINE_WIDTH_DP * density
        color = ColorUtils.setAlphaComponent(primaryColor, DOT_OUTLINE_ALPHA)
    }

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = spToPx(NUMBER_TEXT_SIZE_SP)
        isFakeBoldText = true
    }

    private val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tagTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = spToPx(TAG_TEXT_SIZE_SP)
        isFakeBoldText = true
    }

    /** With a wrap_content height the view takes the 4:3 shape of the 3DS bottom screen. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val widthPx = MeasureSpec.getSize(widthMeasureSpec)
        val screenWidth = widthPx - 2 * inset
        val heightPx = (screenWidth * SCREEN_HEIGHT / SCREEN_WIDTH + 2 * inset).toInt()
        setMeasuredDimension(widthPx, heightPx)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateScreenRect()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (screenRect.isEmpty) return

        val corner = SCREEN_CORNER_RADIUS_DP * density
        canvas.drawRoundRect(screenRect, corner, corner, screenPaint)
        drawGrid(canvas)
        canvas.drawRoundRect(screenRect, corner, corner, outlinePaint)

        val showTags = screenRect.width() >= MIN_TAG_SCREEN_WIDTH_DP * density

        // The highlighted binding is drawn last so that it ends up on top
        bindings.forEachIndexed { index, binding ->
            if (index != highlightedIndex) {
                drawBinding(canvas, binding, index, selected = false, showTag = showTags)
            }
        }
        bindings.getOrNull(highlightedIndex)?.let {
            drawBinding(canvas, it, highlightedIndex, selected = true, showTag = showTags)
        }

        // A tapped position that isn't bound yet is shown as an empty, highlighted dot
        if (pendingX != NO_POINT && pendingY != NO_POINT) {
            drawDot(canvas, pendingX, pendingY, number = 0, selected = true)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) {
            return super.onTouchEvent(event)
        }

        if (!screenRect.contains(event.x, event.y)) {
            return false
        }

        pendingX = event.x
        pendingY = event.y
        invalidate()

        val normalizedX = (event.x - screenRect.left) / screenRect.width()
        val normalizedY = (event.y - screenRect.top) / screenRect.height()
        onTouchPointSelected?.invoke(normalizedX.coerceIn(0f, 1f), normalizedY.coerceIn(0f, 1f))
        return true
    }

    fun setBindings(newBindings: List<TouchInputBinding>) {
        bindings.clear()
        bindings.addAll(newBindings)

        tagTexts.clear()
        newBindings.mapTo(tagTexts) { it.displayName() }

        // The saved binding replaces the temporary point
        pendingX = NO_POINT
        pendingY = NO_POINT
        invalidate()
    }

    /** Highlights the binding at [index], or nothing if the index is out of range. */
    fun setHighlightedIndex(index: Int) {
        highlightedIndex = if (index in bindings.indices) index else NO_HIGHLIGHT
        invalidate()
    }

    fun clearSelection() {
        pendingX = NO_POINT
        pendingY = NO_POINT
        invalidate()
    }

    private fun updateScreenRect() {
        val availableWidth = width - 2 * inset
        val availableHeight = height - 2 * inset
        if (availableWidth <= 0f || availableHeight <= 0f) return

        val scale = min(availableWidth / SCREEN_WIDTH, availableHeight / SCREEN_HEIGHT)
        val screenWidth = SCREEN_WIDTH * scale
        val screenHeight = SCREEN_HEIGHT * scale
        val left = (width - screenWidth) / 2f
        val top = (height - screenHeight) / 2f

        screenRect.set(left, top, left + screenWidth, top + screenHeight)
        invalidate()
    }

    private fun drawGrid(canvas: Canvas) {
        val radius = GRID_DOT_RADIUS_DP * density
        val columnWidth = screenRect.width() / GRID_COLUMNS
        val rowHeight = screenRect.height() / GRID_ROWS

        for (column in 1 until GRID_COLUMNS) {
            for (row in 1 until GRID_ROWS) {
                canvas.drawCircle(
                    screenRect.left + column * columnWidth,
                    screenRect.top + row * rowHeight,
                    radius,
                    gridPaint
                )
            }
        }
    }

    private fun drawBinding(
        canvas: Canvas,
        binding: TouchInputBinding,
        index: Int,
        selected: Boolean,
        showTag: Boolean
    ) {
        val x = screenRect.left + binding.x * screenRect.width()
        val y = screenRect.top + binding.y * screenRect.height()

        if (showTag) {
            drawTag(canvas, tagTexts[index], x, y, selected)
        }
        drawDot(canvas, x, y, number = index + 1, selected = selected)
    }

    private fun drawDot(canvas: Canvas, x: Float, y: Float, number: Int, selected: Boolean) {
        val radius = (if (selected) SELECTED_DOT_RADIUS_DP else DOT_RADIUS_DP) * density
        val ringWidth = DOT_RING_WIDTH_DP * density

        if (selected) {
            canvas.drawCircle(x, y, HALO_RADIUS_DP * density, haloPaint)
        }

        canvas.drawCircle(x, y, radius + ringWidth, dotRingPaint)
        dotPaint.color = if (selected) primaryColor else primaryContainerColor
        canvas.drawCircle(x, y, radius, dotPaint)
        canvas.drawCircle(x, y, radius, dotOutlinePaint)

        if (number > 0) {
            numberPaint.color = if (selected) onPrimaryColor else onPrimaryContainerColor
            val baseline = y - (numberPaint.ascent() + numberPaint.descent()) / 2f
            canvas.drawText(number.toString(), x, baseline, numberPaint)
        }
    }

    /** Draws the name of a binding beside its dot, on whichever side has more room. */
    private fun drawTag(canvas: Canvas, text: String, x: Float, y: Float, selected: Boolean) {
        val padding = TAG_PADDING_DP * density
        val tagHeight = TAG_HEIGHT_DP * density
        val gap = (DOT_RADIUS_DP + DOT_RING_WIDTH_DP + TAG_GAP_DP) * density
        val edge = TAG_EDGE_MARGIN_DP * density

        val label = TextUtils.ellipsize(
            text,
            tagTextPaint,
            TAG_MAX_WIDTH_DP * density - 2 * padding,
            TextUtils.TruncateAt.END
        )
        val tagWidth = tagTextPaint.measureText(label, 0, label.length) + 2 * padding

        val fitsRight = x + gap + tagWidth <= screenRect.right - edge
        val fitsLeft = x - gap - tagWidth >= screenRect.left + edge
        val placeRight = if (x < screenRect.centerX()) fitsRight || !fitsLeft else !fitsLeft

        val left = if (placeRight) x + gap else x - gap - tagWidth
        val top = (y - tagHeight / 2f).coerceIn(
            screenRect.top + edge,
            screenRect.bottom - edge - tagHeight
        )
        tagRect.set(left, top, left + tagWidth, top + tagHeight)

        tagPaint.color = if (selected) primaryColor else surfaceVariantColor
        tagTextPaint.color = if (selected) onPrimaryColor else onSurfaceColor

        canvas.drawRoundRect(tagRect, tagHeight / 2f, tagHeight / 2f, tagPaint)
        val baseline = tagRect.centerY() - (tagTextPaint.ascent() + tagTextPaint.descent()) / 2f
        canvas.drawText(label, 0, label.length, tagRect.centerX(), baseline, tagTextPaint)
    }

    private fun themeColor(@AttrRes attr: Int): Int =
        MaterialColors.getColor(context, attr, Color.TRANSPARENT)

    private fun spToPx(sp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)

    companion object {
        private const val NO_HIGHLIGHT = -1
        private const val NO_POINT = -1f

        // The 3DS bottom screen is 320x240 whatever layout or orientation the emulator uses
        private const val SCREEN_WIDTH = 320f
        private const val SCREEN_HEIGHT = 240f
        private const val SCREEN_CORNER_RADIUS_DP = 16f
        private const val INSET_DP = 2f
        private const val OUTLINE_WIDTH_DP = 1.5f
        private const val OUTLINE_ALPHA = 130

        private const val GRID_COLUMNS = 16
        private const val GRID_ROWS = 12
        private const val GRID_DOT_RADIUS_DP = 1.1f
        private const val GRID_ALPHA = 110

        private const val DOT_RADIUS_DP = 12f
        private const val SELECTED_DOT_RADIUS_DP = 14f
        private const val HALO_RADIUS_DP = 26f
        private const val HALO_ALPHA = 60
        private const val DOT_RING_WIDTH_DP = 2f
        private const val DOT_OUTLINE_WIDTH_DP = 1f
        private const val DOT_OUTLINE_ALPHA = 90
        private const val NUMBER_TEXT_SIZE_SP = 13f

        // Name tags are left out when the screen is too small to fit them
        private const val MIN_TAG_SCREEN_WIDTH_DP = 220f
        private const val TAG_TEXT_SIZE_SP = 11f
        private const val TAG_HEIGHT_DP = 22f
        private const val TAG_PADDING_DP = 9f
        private const val TAG_GAP_DP = 4f
        private const val TAG_EDGE_MARGIN_DP = 4f
        private const val TAG_MAX_WIDTH_DP = 110f
    }
}
