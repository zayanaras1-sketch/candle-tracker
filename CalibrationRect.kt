package com.candlemovetracker.app.model

data class CalibrationRect(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val screenWidth: Int = 1080,
    val screenHeight: Int = 2400
) {
    val width: Int get() = (right - left).coerceAtLeast(10)
    val height: Int get() = (bottom - top).coerceAtLeast(10)
    val isValid: Boolean get() = width > 20 && height > 20 && right > left && bottom > top

    fun formatSummary(): String {
        return if (isValid) {
            "Region: [${left}, ${top}] to [${right}, ${bottom}] (${width}x${height}px)"
        } else {
            "Not calibrated yet (Full screen)"
        }
    }
}
