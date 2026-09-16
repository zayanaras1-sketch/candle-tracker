package com.candlemovetracker.app.data

import android.content.Context
import android.content.SharedPreferences
import com.candlemovetracker.app.model.CalibrationRect

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveCalibration(rect: CalibrationRect) {
        prefs.edit()
            .putInt(KEY_CALIB_LEFT, rect.left)
            .putInt(KEY_CALIB_TOP, rect.top)
            .putInt(KEY_CALIB_RIGHT, rect.right)
            .putInt(KEY_CALIB_BOTTOM, rect.bottom)
            .putInt(KEY_CALIB_SCREEN_W, rect.screenWidth)
            .putInt(KEY_CALIB_SCREEN_H, rect.screenHeight)
            .putBoolean(KEY_CALIB_CONFIGURED, true)
            .apply()
    }

    fun getCalibration(): CalibrationRect {
        val configured = prefs.getBoolean(KEY_CALIB_CONFIGURED, false)
        if (!configured) {
            return CalibrationRect()
        }
        return CalibrationRect(
            left = prefs.getInt(KEY_CALIB_LEFT, 0),
            top = prefs.getInt(KEY_CALIB_TOP, 0),
            right = prefs.getInt(KEY_CALIB_RIGHT, 0),
            bottom = prefs.getInt(KEY_CALIB_BOTTOM, 0),
            screenWidth = prefs.getInt(KEY_CALIB_SCREEN_W, 1080),
            screenHeight = prefs.getInt(KEY_CALIB_SCREEN_H, 2400)
        )
    }

    fun isOverlayEnabled(): Boolean {
        return prefs.getBoolean(KEY_OVERLAY_ENABLED, true)
    }

    fun setOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "candle_move_tracker_prefs"
        private const val KEY_CALIB_LEFT = "calib_left"
        private const val KEY_CALIB_TOP = "calib_top"
        private const val KEY_CALIB_RIGHT = "calib_right"
        private const val KEY_CALIB_BOTTOM = "calib_bottom"
        private const val KEY_CALIB_SCREEN_W = "calib_screen_w"
        private const val KEY_CALIB_SCREEN_H = "calib_screen_h"
        private const val KEY_CALIB_CONFIGURED = "calib_configured"
        private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
    }
}
