package com.candlemovetracker.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class CandleMoveTrackerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Candle Move Tracker Active Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs background screen capture and real-time candle analysis"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "candle_move_tracker_channel"
        const val NOTIFICATION_ID = 1001
    }
}
