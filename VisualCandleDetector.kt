package com.candlemovetracker.app.detector

import android.graphics.Bitmap
import android.graphics.Color
import com.candlemovetracker.app.model.CalibrationRect

class VisualCandleDetector {

    // Previous vertical position of the active candle close (in pixels relative to crop region)
    private var lastCloseY: Int = -1

    // Previous horizontal position of detected candle
    private var lastActiveX: Int = -1

    // Minimum visual movement threshold in pixels (1 pixel per user specification)
    var minMovementThresholdPx: Int = 1

    /**
     * Resets tracker state between 1-minute candles.
     */
    fun resetForNewCandle() {
        lastCloseY = -1
        lastActiveX = -1
    }

    /**
     * Analyzes a screen frame cropped to the user's calibrated region.
     * Detects the currently forming right-most candle, estimates its close Y level,
     * and checks for 1-pixel visible movements.
     */
    fun analyzeFrame(bitmap: Bitmap, region: CalibrationRect? = null): CandleAnalysisResult {
        val width = bitmap.width
        val height = bitmap.height

        if (width < 5 || height < 5) {
            return CandleAnalysisResult(debugInfo = "Frame too small")
        }

        // 1. Locate the active rightmost candle column by scanning from right to left
        val scanMarginRight = (width * 0.02f).toInt()
        val minScanX = (width * 0.30f).toInt() // inspect rightmost 70% of region
        var activeX = -1
        var detectedBullishCount = 0
        var detectedBearishCount = 0

        // Find candidate candle columns
        for (x in (width - 1 - scanMarginRight) downTo minScanX step 2) {
            var columnBullish = 0
            var columnBearish = 0
            for (y in 2 until (height - 2) step 2) {
                val pixel = bitmap.getPixel(x, y)
                if (isBullishPixel(pixel)) columnBullish++
                else if (isBearishPixel(pixel)) columnBearish++
            }

            // A candle body column typically has consecutive colored pixels
            if (columnBullish >= 4 || columnBearish >= 4) {
                activeX = x
                detectedBullishCount = columnBullish
                detectedBearishCount = columnBearish
                break
            }
        }

        // If no distinct column found with step 2, fallback to center-right column or previous X
        if (activeX == -1) {
            activeX = if (lastActiveX in 0 until width) lastActiveX else (width * 0.85f).toInt()
        } else {
            lastActiveX = activeX
        }

        val isBullish = detectedBullishCount >= detectedBearishCount

        // 2. Scan vertically along active candle column (and adjacent columns for width stability)
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        var bodyMinY = Int.MAX_VALUE
        var bodyMaxY = Int.MIN_VALUE

        val colStart = (activeX - 1).coerceAtLeast(0)
        val colEnd = (activeX + 1).coerceAtMost(width - 1)

        for (x in colStart..colEnd) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val isColorMatch = if (isBullish) isBullishPixel(pixel) else isBearishPixel(pixel)
                val isWickMatch = isWickPixel(pixel)

                if (isColorMatch || isWickMatch) {
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y

                    if (isColorMatch) {
                        if (y < bodyMinY) bodyMinY = y
                        if (y > bodyMaxY) bodyMaxY = y
                    }
                }
            }
        }

        // If no candle structure was resolved, skip frame
        if (minY == Int.MAX_VALUE || maxY == Int.MIN_VALUE) {
            return CandleAnalysisResult(
                hasMovement = false,
                movementDirection = CandleAnalysisResult.MovementDirection.NONE,
                currentCloseY = lastCloseY,
                previousCloseY = lastCloseY,
                debugInfo = "No candle pixels resolved in active column $activeX"
            )
        }

        // 3. Determine current live close position
        // In a bullish candle: close is at the top of the body (lowest Y value in screen coordinates)
        // In a bearish candle: close is at the bottom of the body (highest Y value in screen coordinates)
        val currentCloseY = if (bodyMinY != Int.MAX_VALUE && bodyMaxY != Int.MIN_VALUE) {
            if (isBullish) bodyMinY else bodyMaxY
        } else {
            if (isBullish) minY else maxY
        }

        // First frame initialization: capture initial level without triggering count
        if (lastCloseY == -1) {
            lastCloseY = currentCloseY
            return CandleAnalysisResult(
                hasMovement = false,
                movementDirection = CandleAnalysisResult.MovementDirection.NONE,
                currentCloseY = currentCloseY,
                previousCloseY = currentCloseY,
                isBullish = isBullish,
                activeCandleX = activeX,
                debugInfo = "Initialized at Y=$currentCloseY"
            )
        }

        val prevCloseY = lastCloseY
        val deltaY = currentCloseY - prevCloseY // Screen Y: smaller Y = higher on screen

        var direction = CandleAnalysisResult.MovementDirection.NONE
        var hasMovement = false

        // In screen coordinates:
        // Moving UP on the chart corresponds to deltaY < 0 (moving towards top of screen)
        // Moving DOWN on the chart corresponds to deltaY > 0 (moving towards bottom of screen)
        if (deltaY <= -minMovementThresholdPx) {
            // Visual movement UP
            direction = CandleAnalysisResult.MovementDirection.UP
            hasMovement = true
            lastCloseY = currentCloseY
        } else if (deltaY >= minMovementThresholdPx) {
            // Visual movement DOWN
            direction = CandleAnalysisResult.MovementDirection.DOWN
            hasMovement = true
            lastCloseY = currentCloseY
        }

        return CandleAnalysisResult(
            hasMovement = hasMovement,
            movementDirection = direction,
            currentCloseY = currentCloseY,
            previousCloseY = prevCloseY,
            isBullish = isBullish,
            activeCandleX = activeX,
            debugInfo = "Y: $currentCloseY vs $prevCloseY (Delta: ${-deltaY}px, Dir: $direction)"
        )
    }

    /**
     * Checks whether a pixel represents a bullish (green) candlestick body.
     */
    private fun isBullishPixel(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        // Green dominates Red and Blue with good contrast
        return g > 80 && g > (r * 1.2f) && g > (b * 1.15f)
    }

    /**
     * Checks whether a pixel represents a bearish (red) candlestick body.
     */
    private fun isBearishPixel(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        // Red dominates Green and Blue with good contrast
        return r > 80 && r > (g * 1.25f) && r > (b * 1.25f)
    }

    /**
     * Checks whether a pixel represents a candle wick or border.
     */
    private fun isWickPixel(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        val brightness = (r + g + b) / 3

        // Near-neutral colors (gray/white or dark gray)
        val maxDiff = maxOf(kotlin.math.abs(r - g), kotlin.math.abs(g - b), kotlin.math.abs(r - b))
        return (brightness in 40..220) && (maxDiff < 30)
    }
}
