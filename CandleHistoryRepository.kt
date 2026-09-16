package com.candlemovetracker.app.data

import android.content.Context
import android.content.SharedPreferences
import com.candlemovetracker.app.model.CandleRecord
import org.json.JSONArray
import org.json.JSONObject

class CandleHistoryRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getHistory(): List<CandleRecord> {
        val jsonString = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val list = mutableListOf<CandleRecord>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val dirString = obj.optString("direction", "TIE")
                val direction = try {
                    CandleRecord.Direction.valueOf(dirString)
                } catch (e: Exception) {
                    CandleRecord.Direction.TIE
                }
                list.add(
                    CandleRecord(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        upCount = obj.optInt("upCount", 0),
                        downCount = obj.optInt("downCount", 0),
                        durationSeconds = obj.optInt("durationSeconds", 60),
                        dominantDirection = direction
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addRecord(record: CandleRecord) {
        val current = getHistory().toMutableList()
        current.add(0, record) // newest first
        if (current.size > 100) {
            current.removeAt(current.lastIndex)
        }
        saveHistory(current)
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun saveHistory(list: List<CandleRecord>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("timestamp", item.timestamp)
            obj.put("upCount", item.upCount)
            obj.put("downCount", item.downCount)
            obj.put("durationSeconds", item.durationSeconds)
            obj.put("direction", item.dominantDirection.name)
            array.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "candle_history_prefs"
        private const val KEY_HISTORY = "history_json"
    }
}
