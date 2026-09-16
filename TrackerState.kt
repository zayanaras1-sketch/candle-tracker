package com.candlemovetracker.app.model

data class TrackerState(
    val isRunning: Boolean = false,
    val currentUpCount: Int = 0,
    val currentDownCount: Int = 0,
    val remainingSeconds: Int = 60,
    val candleIndex: Int = 0,
    val lastCompletedCandle: CandleRecord? = null,
    val isCalibrated: Boolean = false
) {
    fun formatTimer(): String {
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
