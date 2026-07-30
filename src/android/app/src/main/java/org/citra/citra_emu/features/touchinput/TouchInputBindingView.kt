// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.features.touchinput

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.citra.citra_emu.NativeLibrary
import kotlin.math.min

class TouchInputBindingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val bottomScreenRect = RectF()
    private val textBoundsCache = Rect()

    private val bottomScreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4444")
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textSize = POINT_LABEL_TEXT_SIZE
        isFakeBoldText = true
        setShadowLayer(SHADOW_RADIUS, SHADOW_DX, SHADOW_DY, Color.BLACK)
    }

    private val bindings = mutableListOf<TouchInputBinding>()

    private var selectedX = UNSELECTED_COORDINATE
    private var selectedY = UNSELECTED_COORDINATE

    var onTouchPointSelected: ((Float, Float) -> Unit)? = null

    companion object {
        private const val UNSELECTED_COORDINATE = -1f
        private const val SCREEN_SCALE_FACTOR = 0.8f
        private const val BINDING_POINT_RADIUS = 14f
        private const val SELECTED_POINT_RADIUS = 16f
        private const val LABEL_OFFSET_X = 20f
        private const val POINT_LABEL_TEXT_SIZE = 28f
        private const val SHADOW_RADIUS = 3f
        private const val SHADOW_DX = 1f
        private const val SHADOW_DY = 1f
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

            if (bottomWidth <= 0f || bottomHeight <= 0f) return

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
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(bottomScreenRect, bottomScreenPaint)

        bindings.forEachIndexed { index, binding ->
            val pointX = bottomScreenRect.left + binding.x * bottomScreenRect.width()
            val pointY = bottomScreenRect.top + binding.y * bottomScreenRect.height()

            canvas.drawCircle(pointX, pointY, BINDING_POINT_RADIUS, pointPaint)

            val label = (index + 1).toString()
            labelPaint.getTextBounds(label, 0, label.length, textBoundsCache)

            canvas.drawText(
                label,
                pointX + LABEL_OFFSET_X,
                pointY + textBoundsCache.height() / 2f,
                labelPaint
            )
        }

        if (selectedX >= 0f && selectedY >= 0f) {
            canvas.drawCircle(selectedX, selectedY, SELECTED_POINT_RADIUS, pointPaint)
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

        val normalizedX = ((event.x - bottomScreenRect.left) / bottomScreenRect.width()).coerceIn(0f, 1f)
        val normalizedY = ((event.y - bottomScreenRect.top) / bottomScreenRect.height()).coerceIn(0f, 1f)

        onTouchPointSelected?.invoke(normalizedX, normalizedY)

        return true
    }

    fun setBindings(newBindings: List<TouchInputBinding>) {
        bindings.clear()
        bindings.addAll(newBindings)
        invalidate()
    }

    fun clearSelection() {
        selectedX = UNSELECTED_COORDINATE
        selectedY = UNSELECTED_COORDINATE
        invalidate()
    }
}
