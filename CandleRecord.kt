package com.candlemovetracker.app.model

data class CandleRecord(
    val id: String = System.currentTimeMillis().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val upCount: Int = 0,
    val downCount: Int = 0,
    val durationSeconds: Int = 60,
    val dominantDirection: Direction = calculateDominant(upCount, downCount)
) {
    enum class Direction {
        UP, DOWN, TIE
    }

    companion object {
        fun calculateDominant(up: Int, down: Int): Direction {
            return when {
                up > down -> Direction.UP
                down > up -> Direction.DOWN
                else -> Direction.TIE
            }
        }
    }

    fun getDifference(): Int = kotlin.math.abs(upCount - downCount)
}
