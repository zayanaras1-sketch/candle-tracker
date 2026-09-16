package com.candlemovetracker.app.detector

data class CandleAnalysisResult(
    val hasMovement: Boolean = false,
    val movementDirection: MovementDirection = MovementDirection.NONE,
    val currentCloseY: Int = -1,
    val previousCloseY: Int = -1,
    val isBullish: Boolean = false,
    val activeCandleX: Int = -1,
    val debugInfo: String = ""
) {
    enum class MovementDirection {
        UP, DOWN, NONE
    }
}
