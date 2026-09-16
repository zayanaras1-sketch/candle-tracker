package com.candlemovetracker.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.candlemovetracker.app.model.CalibrationRect
import kotlin.math.abs

class CalibrationOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val selectionRect = RectF()
    private val scrimPaint = Paint().apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#4D38BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        isAntiAlias = true
    }
    private val handlePaint = Paint().apply {
        color = Color.parseColor("#F59E0B")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val handleStrokePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
    }

    private val handleRadius = 36f
    private val touchSlop = 60f

    private enum class TouchTarget {
        NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
    }

    private var currentTarget = TouchTarget.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (selectionRect.isEmpty) {
            // Default center-right frame typical for forming 1-min candles
            val defaultLeft = w * 0.45f
            val defaultTop = h * 0.25f
            val defaultRight = w * 0.92f
            val defaultBottom = h * 0.70f
            selectionRect.set(defaultLeft, defaultTop, defaultRight, defaultBottom)
        }
    }

    fun setInitialRect(rect: CalibrationRect) {
        if (rect.isValid) {
            selectionRect.set(
                rect.left.toFloat(),
                rect.top.toFloat(),
                rect.right.toFloat(),
                rect.bottom.toFloat()
            )
            invalidate()
        }
    }

    fun getSelectedRect(): CalibrationRect {
        return CalibrationRect(
            left = selectionRect.left.toInt(),
            top = selectionRect.top.toInt(),
            right = selectionRect.right.toInt(),
            bottom = selectionRect.bottom.toInt(),
            screenWidth = width,
            screenHeight = height
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw darkened scrim outside selection
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        // 2. Clear inside selection rect (showing underlying chart transparently)
        canvas.drawRect(selectionRect, clearPaint)

        // 3. Draw border and dashed crosshairs
        canvas.drawRect(selectionRect, borderPaint)
        val midX = (selectionRect.left + selectionRect.right) / 2f
        val midY = (selectionRect.top + selectionRect.bottom) / 2f
        canvas.drawLine(midX, selectionRect.top, midX, selectionRect.bottom, gridPaint)
        canvas.drawLine(selectionRect.left, midY, selectionRect.right, midY, gridPaint)

        // 4. Draw corner drag handles
        drawHandle(canvas, selectionRect.left, selectionRect.top)
        drawHandle(canvas, selectionRect.right, selectionRect.top)
        drawHandle(canvas, selectionRect.left, selectionRect.bottom)
        drawHandle(canvas, selectionRect.right, selectionRect.bottom)

        // 5. Draw dimensions info
        val wPx = (selectionRect.width()).toInt()
        val hPx = (selectionRect.height()).toInt()
        val label = "${wPx}x${hPx}px (Drag handles to adjust candle area)"
        canvas.drawText(label, selectionRect.left, (selectionRect.top - 16f).coerceAtLeast(40f), textPaint)
    }

    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float) {
        canvas.drawCircle(cx, cy, handleRadius, handlePaint)
        canvas.drawCircle(cx, cy, handleRadius, handleStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentTarget = determineTarget(x, y)
                lastTouchX = x
                lastTouchY = y
                return currentTarget != TouchTarget.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastTouchX
                val dy = y - lastTouchY

                when (currentTarget) {
                    TouchTarget.TOP_LEFT -> {
                        selectionRect.left = (selectionRect.left + dx).coerceIn(0f, selectionRect.right - 80f)
                        selectionRect.top = (selectionRect.top + dy).coerceIn(0f, selectionRect.bottom - 80f)
                    }
                    TouchTarget.TOP_RIGHT -> {
                        selectionRect.right = (selectionRect.right + dx).coerceIn(selectionRect.left + 80f, width.toFloat())
                        selectionRect.top = (selectionRect.top + dy).coerceIn(0f, selectionRect.bottom - 80f)
                    }
                    TouchTarget.BOTTOM_LEFT -> {
                        selectionRect.left = (selectionRect.left + dx).coerceIn(0f, selectionRect.right - 80f)
                        selectionRect.bottom = (selectionRect.bottom + dy).coerceIn(selectionRect.top + 80f, height.toFloat())
                    }
                    TouchTarget.BOTTOM_RIGHT -> {
                        selectionRect.right = (selectionRect.right + dx).coerceIn(selectionRect.left + 80f, width.toFloat())
                        selectionRect.bottom = (selectionRect.bottom + dy).coerceIn(selectionRect.top + 80f, height.toFloat())
                    }
                    TouchTarget.CENTER -> {
                        val w = selectionRect.width()
                        val h = selectionRect.height()
                        var newLeft = selectionRect.left + dx
                        var newTop = selectionRect.top + dy
                        if (newLeft < 0f) newLeft = 0f
                        if (newLeft + w > width) newLeft = width - w
                        if (newTop < 0f) newTop = 0f
                        if (newTop + h > height) newTop = height - h

                        selectionRect.set(newLeft, newTop, newLeft + w, newTop + h)
                    }
                    TouchTarget.NONE -> {}
                }

                lastTouchX = x
                lastTouchY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentTarget = TouchTarget.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun determineTarget(x: Float, y: Float): TouchTarget {
        if (isNear(x, y, selectionRect.left, selectionRect.top)) return TouchTarget.TOP_LEFT
        if (isNear(x, y, selectionRect.right, selectionRect.top)) return TouchTarget.TOP_RIGHT
        if (isNear(x, y, selectionRect.left, selectionRect.bottom)) return TouchTarget.BOTTOM_LEFT
        if (isNear(x, y, selectionRect.right, selectionRect.bottom)) return TouchTarget.BOTTOM_RIGHT
        if (selectionRect.contains(x, y)) return TouchTarget.CENTER
        return TouchTarget.NONE
    }

    private fun isNear(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        return abs(x1 - x2) <= touchSlop && abs(y1 - y2) <= touchSlop
    }
}
