package com.example.lib

import android.content.Context
import com.example.data.DelhiPlace
import com.example.data.DelhiPlacesData
import org.json.JSONArray
import org.json.JSONObject

data class SavedRouteEntry(
    val id: String,
    val start: DelhiPlace,
    val end: DelhiPlace,
    val vehicle: TravelMode,
    val createdAt: Long = System.currentTimeMillis()
)

// Persists saved routes (start, end, vehicle) to device storage (SharedPreferences / localStorage equivalent)
object SavedRoutesStore {
    private const val PREFS_NAME = "commutebuddy_saved_routes"
    private const val KEY_ROUTES_JSON = "saved_routes_v1"

    fun buildId(start: DelhiPlace, end: DelhiPlace, vehicle: TravelMode): String {
        return "${start.id}_${end.id}_${vehicle.name.lowercase()}"
    }

    fun loadAll(context: Context): List<SavedRouteEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ROUTES_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val result = mutableListOf<SavedRouteEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val startId = obj.optString("startId")
                val endId = obj.optString("endId")
                val modeName = obj.optString("vehicle", TravelMode.MIX.name)
                val createdAt = obj.optLong("createdAt", 0L)

                val startPlace = DelhiPlacesData.places.find { it.id == startId } ?: continue
                val endPlace = DelhiPlacesData.places.find { it.id == endId } ?: continue
                val vehicle = runCatching { TravelMode.valueOf(modeName) }.getOrDefault(TravelMode.MIX)
                val id = obj.optString("id").ifEmpty { buildId(startPlace, endPlace, vehicle) }

                result.add(
                    SavedRouteEntry(
                        id = id,
                        start = startPlace,
                        end = endPlace,
                        vehicle = vehicle,
                        createdAt = createdAt
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveAll(context: Context, entries: List<SavedRouteEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject().apply {
                put("id", entry.id)
                put("startId", entry.start.id)
                put("endId", entry.end.id)
                put("vehicle", entry.vehicle.name)
                put("createdAt", entry.createdAt)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ROUTES_JSON, array.toString())
            .apply()
    }
}
