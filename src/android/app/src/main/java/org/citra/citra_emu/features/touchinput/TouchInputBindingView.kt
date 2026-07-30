// Copyright 2025 Azahar Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.features.touchinput

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.citra.citra_emu.NativeLibrary

class TouchInputBindingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val bottomScreenRect = RectF()

    private val bottomScreenPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val pointPaint = Paint().apply {
        color = Color.parseColor("#FF4444")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val labelPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
        textSize = 28f
        isFakeBoldText = true
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }

    private val bindings = mutableListOf<TouchInputBinding>()

    private var selectedX = -1f
    private var selectedY = -1f

    var onTouchPointSelected: ((Float, Float) -> Unit)? = null

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
        val viewWidth = width
        val viewHeight = height

        if (viewWidth <= 0 || viewHeight <= 0) {
            return
        }

        val layout = NativeLibrary.getFramebufferLayout()

        if (layout.size >= 6) {
            val bottomLeft = layout[2]
            val bottomTop = layout[3]
            val bottomRight = layout[4]
            val bottomBottom = layout[5]

            val bottomWidth = (bottomRight - bottomLeft).toFloat()
            val bottomHeight = (bottomBottom - bottomTop).toFloat()

            val scaleX = (viewWidth * 0.8f) / bottomWidth
            val scaleY = (viewHeight * 0.8f) / bottomHeight
            val scale = minOf(scaleX, scaleY)

            val scaledWidth = bottomWidth * scale
            val scaledHeight = bottomHeight * scale

            val offsetX = (viewWidth - scaledWidth) / 2f
            val offsetY = (viewHeight - scaledHeight) / 2f

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

        canvas.drawRect(
            bottomScreenRect,
            bottomScreenPaint
        )

        bindings.forEachIndexed { index, binding ->
            val x = bottomScreenRect.left +
                binding.x * bottomScreenRect.width()

            val y = bottomScreenRect.top +
                binding.y * bottomScreenRect.height()

            canvas.drawCircle(
                x,
                y,
                14f,
                pointPaint
            )

            val label = (index + 1).toString()
            val textBounds = android.graphics.Rect()

            labelPaint.getTextBounds(
                label,
                0,
                label.length,
                textBounds
            )

            canvas.drawText(
                label,
                x + 20f,
                y + textBounds.height() / 2f,
                labelPaint
            )
        }

        if (selectedX >= 0 && selectedY >= 0) {
            canvas.drawCircle(
                selectedX,
                selectedY,
                16f,
                pointPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) {
            return true
        }

        if (!bottomScreenRect.contains(event.x, event.y)) {
            return true
        }

        selectedX = event.x
        selectedY = event.y
        invalidate()

        val normalizedX =
            (event.x - bottomScreenRect.left) /
                bottomScreenRect.width()

        val normalizedY =
            (event.y - bottomScreenRect.top) /
                bottomScreenRect.height()

        onTouchPointSelected?.invoke(
            normalizedX.coerceIn(0f, 1f),
            normalizedY.coerceIn(0f, 1f)
        )

        return true
    }

    fun setBindings(newBindings: List<TouchInputBinding>) {
        bindings.clear()
        bindings.addAll(newBindings)
        invalidate()
    }
}