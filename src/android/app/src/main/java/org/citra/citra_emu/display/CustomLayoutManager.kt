// Copyright 2025 Azahar Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.display

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.features.settings.model.IntSetting
import org.citra.citra_emu.features.settings.utils.SettingsFile

/**
 * Manages the custom framebuffer layout editor.
 *
 * Handles:
 * - Opening and closing the editor
 * - Saving layout changes
 * - Resetting screen positions
 * - Applying framebuffer updates
 *
 * The visual editing logic is handled by CustomLayoutEditorView.
 */
class CustomLayoutManager(
    private val editorView: CustomLayoutEditorView,
    private val doneButton: View,
    private val cancelButton: View,
    private val resetButton: View,
    private val toolbar: View
) {
    /** Initializes all editor button controls. */
    fun setupControls() {
        setupSaveButton()
        setupCancelButton()
        setupResetButton()
    }

    /**
     * Opens the custom layout editor.
     * Loads the current framebuffer positions from emulator settings.
     */
    fun openEditor() {
        editorView.visibility = View.VISIBLE
        toolbar.visibility = View.VISIBLE
        editorView.post { editorView.loadLayoutSettings() }
    }

    /** Closes the custom layout editor. */
    fun closeEditor() {
        editorView.visibility = View.GONE
        toolbar.visibility = View.GONE
    }

    /**
     * Configures the save button.
     * Saves the edited layout and reloads the framebuffer configuration.
     */
    private fun setupSaveButton() {
        doneButton.setOnClickListener {
            editorView.saveLayoutSettings()
            saveLayoutConfiguration()
            NativeLibrary.reloadSettings()
            NativeLibrary.updateFramebuffer(NativeLibrary.isPortraitMode())
            closeEditor()
        }
    }

    /** Configures the cancel button. Discards current changes and closes the editor. */
    private fun setupCancelButton() {
        cancelButton.setOnClickListener { closeEditor() }
    }

    /** Configures the reset button. Restores default screen positions. */
    private fun setupResetButton() {
        resetButton.setOnClickListener { editorView.resetToDefaultLayout() }
    }

    /**
     * Saves current layout values into the config file.
     * Saves either portrait or landscape layout settings.
     */
    private fun saveLayoutConfiguration() {
        if (NativeLibrary.isPortraitMode()) {
            SettingsFile.saveFile(SettingsFile.FILE_NAME_CONFIG, IntSetting.PORTRAIT_TOP_X)
            SettingsFile.saveFile(SettingsFile.FILE_NAME_CONFIG, IntSetting.PORTRAIT_TOP_Y)
            SettingsFile.saveFile(SettingsFile.FILE_NAME_CONFIG, IntSetting.PORTRAIT_TOP_WIDTH)
            SettingsFile.saveFile(SettingsFile.FILE_NAME_CONFIG, IntSetting.PORTRAIT_TOP_HEIGHT)
            SettingsFile.saveFile(SettingsFile.FILE_NAME_CONFIG, IntSetting.PORTRAIT_BOTTOM_X)
            SettingsFile.saveFile(SettingsFile.FILE_NAME_CONFIG, IntSetting.PORTRAIT_BOTTOM_Y)
            SettingsFile.saveFile(SettingsFile.FILE_NAME_CONFIG, IntSetting.PORTRAIT_BOTTOM_WIDTH)
            SettingsFile.saveFile(SettingsFile.FILE_NAME_CONFIG, IntSetting.PORTRAIT_BOTTOM_HEIGHT)
        } else {
            SettingsFile.saveFile(SettingsFile.FILE_NAME_CONFIG, IntSetting.LANDSCAPE_TOP_X)
            SettingsFile.saveFile(SettingsFile.FILE_NAME_CONFIG, IntSetting.LANDSCAPE_TOP_Y)
            SettingsFile.saveFile(SettingsFile.FILE_NAME_CONFIG, IntSetting.LANDSCAPE_TOP_WIDTH)
            SettingsFile.saveFile(SettingsFile.FILE_NAME_CONFIG, IntSetting.LANDSCAPE_TOP_HEIGHT)
            SettingsFile.saveFile(SettingsFile.FILE_NAME_CONFIG, IntSetting.LANDSCAPE_BOTTOM_X)
            SettingsFile.saveFile(SettingsFile.FILE_NAME_CONFIG, IntSetting.LANDSCAPE_BOTTOM_Y)
            SettingsFile.saveFile(SettingsFile.FILE_NAME_CONFIG, IntSetting.LANDSCAPE_BOTTOM_WIDTH)
            SettingsFile.saveFile(SettingsFile.FILE_NAME_CONFIG, IntSetting.LANDSCAPE_BOTTOM_HEIGHT)
        }
    }
}

/**
 * Custom framebuffer layout editor view.
 *
 * Provides:
 * - Visual top/bottom screen preview
 * - Screen selection
 * - Dragging support
 * - Resize handles
 * - Layout editing canvas
 *
 * Layout changes are applied through NativeLibrary.
 */
class CustomLayoutEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ==================== PAINTS ====================

    /** Border paint used for screen outlines */
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 5f
    }

    /** Handle paint used for resize points */
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
    }

    /** Top screen preview color */
    private val topScreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 0, 120, 255)
    }

    /** Bottom screen preview color */
    private val bottomScreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 0, 255, 120)
    }

    /** Selected screen border */
    private val selectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW; style = Paint.Style.STROKE; strokeWidth = 10f
    }

    /** Selected resize handle */
    private val selectedHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW; style = Paint.Style.FILL
    }

    /** Screen title text */
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 42f
    }

    // ==================== SCREEN RECTANGLES ====================

    /** Top screen rectangle */
    private val topScreenRect = RectF()

    /** Bottom screen rectangle */
    private val bottomScreenRect = RectF()

    // ==================== CONSTANTS ====================

    /** Resize handle display size */
    private val handleSize = 48f

    /** Touch area around resize handles */
    private val touchHandleSize = 80f

    /** Snap distance between screens */
    private val snapDistance = 12f

    /** Original framebuffer width */
    private val framebufferWidth = 800f

    /** Original framebuffer height */
    private val framebufferHeight = 960f

    // ==================== EDITOR STATE ====================

    /** Currently selected screen */
    private var selectedScreen = SelectedScreen.NONE

    /** Current drag operation */
    private var dragMode = DragMode.NONE

    /** Currently active rectangle */
    private var activeRectangle: RectF? = null

    /** Active handle position */
    private var activeHandleX = -1f
    private var activeHandleY = -1f

    // ==================== GUIDE STATE ====================

    /** Shows vertical alignment guide */
    private var showVerticalGuide = false

    /** Shows horizontal alignment guide */
    private var showHorizontalGuide = false

    private var guideX = 0f
    private var guideY = 0f

    // ==================== ENUMS ====================

    /** Represents which screen is selected. */
    private enum class SelectedScreen { NONE, TOP, BOTTOM }

    /** Represents current drag operation. */
    private enum class DragMode {
        NONE,
        MOVE_TOP, MOVE_BOTTOM,
        TOP_TOP_LEFT, TOP_TOP, TOP_TOP_RIGHT, TOP_LEFT, TOP_RIGHT,
        TOP_BOTTOM_LEFT, TOP_BOTTOM, TOP_BOTTOM_RIGHT,
        BOTTOM_TOP_LEFT, BOTTOM_TOP, BOTTOM_TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
        BOTTOM_BOTTOM_LEFT, BOTTOM_BOTTOM, BOTTOM_BOTTOM_RIGHT
    }

    // ==================== VIEW LIFECYCLE ====================

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        loadLayoutSettings()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawScreen(canvas, topScreenRect, topScreenPaint, "Top Screen")
        drawScreen(canvas, bottomScreenRect, bottomScreenPaint, "Bottom Screen")
        drawSelectedBorder(canvas)
        drawGuideLines(canvas)
    }

    // ==================== SETTINGS ====================

    /** Loads screen positions from emulator settings. */
    fun loadLayoutSettings() {
        if (NativeLibrary.isPortraitMode()) {
            topScreenRect.set(
                IntSetting.PORTRAIT_TOP_X.int.toFloat(),
                IntSetting.PORTRAIT_TOP_Y.int.toFloat(),
                (IntSetting.PORTRAIT_TOP_X.int + IntSetting.PORTRAIT_TOP_WIDTH.int).toFloat(),
                (IntSetting.PORTRAIT_TOP_Y.int + IntSetting.PORTRAIT_TOP_HEIGHT.int).toFloat()
            )
            bottomScreenRect.set(
                IntSetting.PORTRAIT_BOTTOM_X.int.toFloat(),
                IntSetting.PORTRAIT_BOTTOM_Y.int.toFloat(),
                (IntSetting.PORTRAIT_BOTTOM_X.int + IntSetting.PORTRAIT_BOTTOM_WIDTH.int).toFloat(),
                (IntSetting.PORTRAIT_BOTTOM_Y.int + IntSetting.PORTRAIT_BOTTOM_HEIGHT.int).toFloat()
            )
        } else {
            topScreenRect.set(
                IntSetting.LANDSCAPE_TOP_X.int.toFloat(),
                IntSetting.LANDSCAPE_TOP_Y.int.toFloat(),
                (IntSetting.LANDSCAPE_TOP_X.int + IntSetting.LANDSCAPE_TOP_WIDTH.int).toFloat(),
                (IntSetting.LANDSCAPE_TOP_Y.int + IntSetting.LANDSCAPE_TOP_HEIGHT.int).toFloat()
            )
            bottomScreenRect.set(
                IntSetting.LANDSCAPE_BOTTOM_X.int.toFloat(),
                IntSetting.LANDSCAPE_BOTTOM_Y.int.toFloat(),
                (IntSetting.LANDSCAPE_BOTTOM_X.int + IntSetting.LANDSCAPE_BOTTOM_WIDTH.int).toFloat(),
                (IntSetting.LANDSCAPE_BOTTOM_Y.int + IntSetting.LANDSCAPE_BOTTOM_HEIGHT.int).toFloat()
            )
        }
        invalidate()
    }

    // ==================== TOUCH HANDLING ====================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeRectangle = findTouchedRectangle(event.x, event.y)
                dragMode = detectDragMode(event.x, event.y, activeRectangle)
                selectedScreen = when (activeRectangle) {
                    topScreenRect -> SelectedScreen.TOP
                    bottomScreenRect -> SelectedScreen.BOTTOM
                    else -> SelectedScreen.NONE
                }
            }
            MotionEvent.ACTION_MOVE -> processDrag(event)
            MotionEvent.ACTION_UP -> {
                dragMode = DragMode.NONE
                activeRectangle = null
                clearActiveHandle()
                invalidate()
            }
        }
        return true
    }

    // ==================== SAVING ====================

    /** Saves current editor positions into settings. */
    fun saveLayoutSettings() = applyLayoutChanges()

    /** Applies current screen positions to emulator settings. */
    private fun applyLayoutChanges() {

    if (NativeLibrary.isPortraitMode()) {

        IntSetting.PORTRAIT_TOP_X.int = topScreenRect.left.toInt()
        IntSetting.PORTRAIT_TOP_Y.int = topScreenRect.top.toInt()
        IntSetting.PORTRAIT_TOP_WIDTH.int = topScreenRect.width().toInt()
        IntSetting.PORTRAIT_TOP_HEIGHT.int = topScreenRect.height().toInt()

        IntSetting.PORTRAIT_BOTTOM_X.int = bottomScreenRect.left.toInt()
        IntSetting.PORTRAIT_BOTTOM_Y.int = bottomScreenRect.top.toInt()
        IntSetting.PORTRAIT_BOTTOM_WIDTH.int = bottomScreenRect.width().toInt()
        IntSetting.PORTRAIT_BOTTOM_HEIGHT.int = bottomScreenRect.height().toInt()

    } else {

        IntSetting.LANDSCAPE_TOP_X.int = topScreenRect.left.toInt()
        IntSetting.LANDSCAPE_TOP_Y.int = topScreenRect.top.toInt()
        IntSetting.LANDSCAPE_TOP_WIDTH.int = topScreenRect.width().toInt()
        IntSetting.LANDSCAPE_TOP_HEIGHT.int = topScreenRect.height().toInt()

        IntSetting.LANDSCAPE_BOTTOM_X.int = bottomScreenRect.left.toInt()
        IntSetting.LANDSCAPE_BOTTOM_Y.int = bottomScreenRect.top.toInt()
        IntSetting.LANDSCAPE_BOTTOM_WIDTH.int = bottomScreenRect.width().toInt()
        IntSetting.LANDSCAPE_BOTTOM_HEIGHT.int = bottomScreenRect.height().toInt()
    }


    NativeLibrary.setCustomLayout(
        topScreenRect.left.toInt(),
        topScreenRect.top.toInt(),
        topScreenRect.width().toInt(),
        topScreenRect.height().toInt(),

        bottomScreenRect.left.toInt(),
        bottomScreenRect.top.toInt(),
        bottomScreenRect.width().toInt(),
        bottomScreenRect.height().toInt(),

        NativeLibrary.isPortraitMode()
    )
}

    /** Restores default screen positions. */
    fun resetToDefaultLayout() {
        if (NativeLibrary.isPortraitMode()) {
            IntSetting.PORTRAIT_TOP_X.int = 0; IntSetting.PORTRAIT_TOP_Y.int = 0
            IntSetting.PORTRAIT_TOP_WIDTH.int = 800; IntSetting.PORTRAIT_TOP_HEIGHT.int = 480
            IntSetting.PORTRAIT_BOTTOM_X.int = 80; IntSetting.PORTRAIT_BOTTOM_Y.int = 480
            IntSetting.PORTRAIT_BOTTOM_WIDTH.int = 640; IntSetting.PORTRAIT_BOTTOM_HEIGHT.int = 480
        } else {
            IntSetting.LANDSCAPE_TOP_X.int = 0; IntSetting.LANDSCAPE_TOP_Y.int = 0
            IntSetting.LANDSCAPE_TOP_WIDTH.int = 800; IntSetting.LANDSCAPE_TOP_HEIGHT.int = 480
            IntSetting.LANDSCAPE_BOTTOM_X.int = 80; IntSetting.LANDSCAPE_BOTTOM_Y.int = 480
            IntSetting.LANDSCAPE_BOTTOM_WIDTH.int = 640; IntSetting.LANDSCAPE_BOTTOM_HEIGHT.int = 480
        }
        loadLayoutSettings()
        NativeLibrary.reloadSettings()
        NativeLibrary.updateFramebuffer(NativeLibrary.isPortraitMode())
        invalidate()
    }

    // ==================== DRAWING ====================

    /** Draws a single screen preview. */
    private fun drawScreen(canvas: Canvas, rect: RectF, paint: Paint, title: String) {
        canvas.drawRect(rect, paint)
        canvas.drawRect(rect, borderPaint)
        canvas.drawText(title, rect.left + 20f, rect.top + 50f, labelPaint)
        drawResizeHandles(canvas, rect)
    }

    /** Draws resize handles around a screen. */
    private fun drawResizeHandles(canvas: Canvas, rect: RectF) {
        val points = listOf(
            rect.left to rect.top, rect.centerX() to rect.top, rect.right to rect.top,
            rect.left to rect.centerY(), rect.right to rect.centerY(),
            rect.left to rect.bottom, rect.centerX() to rect.bottom, rect.right to rect.bottom
        )
        for ((x, y) in points) {
            val paint = if (isActiveHandle(x, y)) selectedHandlePaint else handlePaint
            canvas.drawCircle(x, y, handleSize, paint)
        }
    }

    /** Draws selected screen outline. */
    private fun drawSelectedBorder(canvas: Canvas) {
        when (selectedScreen) {
            SelectedScreen.TOP -> canvas.drawRect(topScreenRect, selectedBorderPaint)
            SelectedScreen.BOTTOM -> canvas.drawRect(bottomScreenRect, selectedBorderPaint)
            else -> Unit
        }
    }

    /** Draws alignment guide lines. */
    private fun drawGuideLines(canvas: Canvas) {
        if (showVerticalGuide) canvas.drawLine(guideX, 0f, guideX, height.toFloat(), selectedBorderPaint)
        if (showHorizontalGuide) canvas.drawLine(0f, guideY, width.toFloat(), guideY, selectedBorderPaint)
    }

    // ==================== DRAG PROCESSING ====================

    /** Processes current drag operation. */
    private fun processDrag(event: MotionEvent) {
        when (dragMode) {
            DragMode.MOVE_TOP -> moveScreen(topScreenRect, event)
            DragMode.MOVE_BOTTOM -> moveScreen(bottomScreenRect, event)
            DragMode.TOP_TOP_LEFT -> resizeTopLeft(topScreenRect, event)
            DragMode.TOP_TOP -> resizeTop(topScreenRect, event)
            DragMode.TOP_TOP_RIGHT -> resizeTopRight(topScreenRect, event)
            DragMode.TOP_LEFT -> resizeLeft(topScreenRect, event)
            DragMode.TOP_RIGHT -> resizeRight(topScreenRect, event)
            DragMode.TOP_BOTTOM_LEFT -> resizeBottomLeft(topScreenRect, event)
            DragMode.TOP_BOTTOM -> resizeBottom(topScreenRect, event)
            DragMode.TOP_BOTTOM_RIGHT -> resizeBottomRight(topScreenRect, event)
            DragMode.BOTTOM_TOP_LEFT -> resizeTopLeft(bottomScreenRect, event)
            DragMode.BOTTOM_TOP -> resizeTop(bottomScreenRect, event)
            DragMode.BOTTOM_TOP_RIGHT -> resizeTopRight(bottomScreenRect, event)
            DragMode.BOTTOM_LEFT -> resizeLeft(bottomScreenRect, event)
            DragMode.BOTTOM_RIGHT -> resizeRight(bottomScreenRect, event)
            DragMode.BOTTOM_BOTTOM_LEFT -> resizeBottomLeft(bottomScreenRect, event)
            DragMode.BOTTOM_BOTTOM -> resizeBottom(bottomScreenRect, event)
            DragMode.BOTTOM_BOTTOM_RIGHT -> resizeBottomRight(bottomScreenRect, event)
            else -> Unit
        }
    }

    // ==================== MOVEMENT ====================

    /** Moves a screen rectangle while keeping it inside bounds. */
    private fun moveScreen(rect: RectF, event: MotionEvent) {
        val deltaX = if (event.historySize > 0) event.x - event.getHistoricalX(0) else 0f
        val deltaY = if (event.historySize > 0) event.y - event.getHistoricalY(0) else 0f
        val maxX = width - rect.width()
        val maxY = height - rect.height()
        rect.offsetTo(
            (rect.left + deltaX).coerceIn(0f, maxX),
            (rect.top + deltaY).coerceIn(0f, maxY)
        )
        if (rect == topScreenRect) snapScreens(topScreenRect, bottomScreenRect)
        else snapScreens(bottomScreenRect, topScreenRect)
        applyLayoutChanges()
        invalidate()
    }

    // ==================== RESIZING ====================

    private fun resizeTopLeft(rect: RectF, event: MotionEvent) {
        rect.left = event.x.coerceIn(0f, rect.right - 100f)
        rect.top = event.y.coerceIn(0f, rect.bottom - 100f)
        finishResize(rect)
    }

    private fun resizeTopRight(rect: RectF, event: MotionEvent) {
        rect.right = event.x.coerceIn(rect.left + 100f, width.toFloat())
        rect.top = event.y.coerceIn(0f, rect.bottom - 100f)
        finishResize(rect)
    }

    private fun resizeBottomLeft(rect: RectF, event: MotionEvent) {
        rect.left = event.x.coerceIn(0f, rect.right - 100f)
        rect.bottom = event.y.coerceIn(rect.top + 100f, height.toFloat())
        finishResize(rect)
    }

    private fun resizeBottomRight(rect: RectF, event: MotionEvent) {
        rect.right = event.x.coerceIn(rect.left + 100f, width.toFloat())
        rect.bottom = event.y.coerceIn(rect.top + 100f, height.toFloat())
        finishResize(rect)
    }

    private fun resizeTop(rect: RectF, event: MotionEvent) {
        rect.top = event.y.coerceIn(0f, rect.bottom - 100f)
        finishResize(rect)
    }

    private fun resizeBottom(rect: RectF, event: MotionEvent) {
        rect.bottom = event.y.coerceIn(rect.top + 100f, height.toFloat())
        finishResize(rect)
    }

    private fun resizeLeft(rect: RectF, event: MotionEvent) {
        rect.left = event.x.coerceIn(0f, rect.right - 100f)
        finishResize(rect)
    }

    private fun resizeRight(rect: RectF, event: MotionEvent) {
        rect.right = event.x.coerceIn(rect.left + 100f, width.toFloat())
        finishResize(rect)
    }

    /** Finishes resize operation. */
    private fun finishResize(rect: RectF) {
        clampRectangle(rect)
        snapAfterResize(rect)
        applyLayoutChanges()
        invalidate()
    }

    // ==================== TOUCH DETECTION ====================

    /** Finds which screen was touched. */
    private fun findTouchedRectangle(x: Float, y: Float): RectF? {
        if (isOnResizeHandle(bottomScreenRect, x, y)) return bottomScreenRect
        if (isOnResizeHandle(topScreenRect, x, y)) return topScreenRect
        if (bottomScreenRect.contains(x, y)) return bottomScreenRect
        if (topScreenRect.contains(x, y)) return topScreenRect
        return null
    }

    /** Determines the current drag operation. */
    private fun detectDragMode(x: Float, y: Float, rect: RectF?): DragMode {
        if (rect == null) return DragMode.NONE
        return when (rect) {
            topScreenRect -> detectScreenHandle(x, y, true)
            bottomScreenRect -> detectScreenHandle(x, y, false)
            else -> DragMode.NONE
        }
    }

    // ==================== HANDLE DETECTION ====================

    /**
     * Detects which part of the screen was touched.
     * Returns either move or resize operation.
     */
    private fun detectScreenHandle(x: Float, y: Float, isTopScreen: Boolean): DragMode {
        val rect = if (isTopScreen) topScreenRect else bottomScreenRect
        return when {
            isInsideHandle(x, y, rect.left, rect.top) -> {
                setActiveHandle(rect.left, rect.top)
                if (isTopScreen) DragMode.TOP_TOP_LEFT else DragMode.BOTTOM_TOP_LEFT
            }
            isInsideHandle(x, y, rect.centerX(), rect.top) -> {
                setActiveHandle(rect.centerX(), rect.top)
                if (isTopScreen) DragMode.TOP_TOP else DragMode.BOTTOM_TOP
            }
            isInsideHandle(x, y, rect.right, rect.top) -> {
                setActiveHandle(rect.right, rect.top)
                if (isTopScreen) DragMode.TOP_TOP_RIGHT else DragMode.BOTTOM_TOP_RIGHT
            }
            isInsideHandle(x, y, rect.left, rect.centerY()) -> {
                setActiveHandle(rect.left, rect.centerY())
                if (isTopScreen) DragMode.TOP_LEFT else DragMode.BOTTOM_LEFT
            }
            isInsideHandle(x, y, rect.right, rect.centerY()) -> {
                setActiveHandle(rect.right, rect.centerY())
                if (isTopScreen) DragMode.TOP_RIGHT else DragMode.BOTTOM_RIGHT
            }
            isInsideHandle(x, y, rect.left, rect.bottom) -> {
                setActiveHandle(rect.left, rect.bottom)
                if (isTopScreen) DragMode.TOP_BOTTOM_LEFT else DragMode.BOTTOM_BOTTOM_LEFT
            }
            isInsideHandle(x, y, rect.centerX(), rect.bottom) -> {
                setActiveHandle(rect.centerX(), rect.bottom)
                if (isTopScreen) DragMode.TOP_BOTTOM else DragMode.BOTTOM_BOTTOM
            }
            isInsideHandle(x, y, rect.right, rect.bottom) -> {
                setActiveHandle(rect.right, rect.bottom)
                if (isTopScreen) DragMode.TOP_BOTTOM_RIGHT else DragMode.BOTTOM_BOTTOM_RIGHT
            }
            else -> {
                clearActiveHandle()
                if (isTopScreen) DragMode.MOVE_TOP else DragMode.MOVE_BOTTOM
            }
        }
    }

    /** Checks if the user touched a resize handle. */
    private fun isOnResizeHandle(rect: RectF, x: Float, y: Float): Boolean {
        return listOf(
            rect.left to rect.top, rect.centerX() to rect.top, rect.right to rect.top,
            rect.left to rect.centerY(), rect.right to rect.centerY(),
            rect.left to rect.bottom, rect.centerX() to rect.bottom, rect.right to rect.bottom
        ).any { (hx, hy) -> isInsideHandle(x, y, hx, hy) }
    }

    /** Checks if a coordinate is inside a resize handle area. */
    private fun isInsideHandle(x: Float, y: Float, handleX: Float, handleY: Float): Boolean {
        return x >= handleX - touchHandleSize && x <= handleX + touchHandleSize &&
                y >= handleY - touchHandleSize && y <= handleY + touchHandleSize
    }

    /** Sets the currently active resize handle. */
    private fun setActiveHandle(x: Float, y: Float) {
        activeHandleX = x; activeHandleY = y
    }

    /** Clears active resize handle. */
    private fun clearActiveHandle() {
        activeHandleX = -1f; activeHandleY = -1f
    }

    /** Checks if a handle is selected. */
    private fun isActiveHandle(x: Float, y: Float): Boolean {
        return kotlin.math.abs(x - activeHandleX) <= 2f && kotlin.math.abs(y - activeHandleY) <= 2f
    }

    // ==================== SNAP SYSTEM ====================

    /** Snaps a moving screen to another screen. */
    private fun snapScreens(moving: RectF, target: RectF) {
        showVerticalGuide = false
        showHorizontalGuide = false
        if (kotlin.math.abs(moving.left - target.left) < snapDistance) {
            moving.offset(target.left - moving.left, 0f)
            showVerticalGuide = true; guideX = target.left
        }
        if (kotlin.math.abs(moving.right - target.right) < snapDistance) {
            moving.offset(target.right - moving.right, 0f)
            showVerticalGuide = true; guideX = target.right
        }
        if (kotlin.math.abs(moving.top - target.top) < snapDistance) {
            moving.offset(0f, target.top - moving.top)
            showHorizontalGuide = true; guideY = target.top
        }
        if (kotlin.math.abs(moving.bottom - target.bottom) < snapDistance) {
            moving.offset(0f, target.bottom - moving.bottom)
            showHorizontalGuide = true; guideY = target.bottom
        }
    }

    /** Snaps a resized screen. */
    private fun snapAfterResize(rect: RectF) {
        val target = if (rect == topScreenRect) bottomScreenRect else topScreenRect
        snapScreens(rect, target)
    }

    // ==================== UTILITY ====================

    /** Keeps a rectangle inside the editor bounds. */
    private fun clampRectangle(rect: RectF) {
        rect.left = rect.left.coerceIn(0f, width.toFloat())
        rect.top = rect.top.coerceIn(0f, height.toFloat())
        rect.right = rect.right.coerceIn(0f, width.toFloat())
        rect.bottom = rect.bottom.coerceIn(0f, height.toFloat())
    }
}