package com.candlemovetracker.app.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TrackerStateHolder {

    private val _state = MutableStateFlow(TrackerState())
    val state: StateFlow<TrackerState> = _state.asStateFlow()

    fun update(transform: (TrackerState) -> TrackerState) {
        _state.value = transform(_state.value)
    }

    fun setRunning(running: Boolean) {
        update { it.copy(isRunning = running) }
    }

    fun incrementUp() {
        update { it.copy(currentUpCount = it.currentUpCount + 1) }
    }

    fun incrementDown() {
        update { it.copy(currentDownCount = it.currentDownCount + 1) }
    }

    fun updateTimer(secondsRemaining: Int) {
        update { it.copy(remainingSeconds = secondsRemaining) }
    }

    fun completeCandle(record: CandleRecord) {
        update {
            it.copy(
                candleIndex = it.candleIndex + 1,
                lastCompletedCandle = record,
                currentUpCount = 0,
                currentDownCount = 0,
                remainingSeconds = 60
            )
        }
    }

    fun reset() {
        _state.value = TrackerState()
    }
}
