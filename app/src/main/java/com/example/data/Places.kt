package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class DelhiPlace(
    val id: String,
    val name: String,
    val area: String,
    val category: String,
    val lat: Double,
    val lng: Double,
    val stationId: String? = null
)

// Live India-wide places client backed by CommuteBuddy backend (/api/places and /api/places/reverse).
// Hardcoded DELHI_PLACES list has been removed per Truth-Over-Completeness policy.
object DelhiPlacesData {
    // Kept as an empty list for legacy callers; all places come from the backend API.
    val places: List<DelhiPlace> = emptyList()

    var backendBaseUrl: String = "http://10.0.2.2:3001"

    fun searchPlaces(query: String, maxResults: Int = 5): List<DelhiPlace> {
        return emptyList()
    }

    suspend fun fetchPlacesFromBackend(
        query: String,
        maxResults: Int = 5,
        lat: Double? = null,
        lng: Double? = null
    ): List<DelhiPlace> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val encodedQ = URLEncoder.encode(trimmed, "UTF-8")
        val biasParams = if (lat != null && lng != null) "&lat=$lat&lng=$lng" else ""
        val urlStr = "$backendBaseUrl/api/places?q=$encodedQ$biasParams"

        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3500
            readTimeout = 3500
            setRequestProperty("Accept", "application/json")
        }

        val code = conn.responseCode
        if (code != 200) {
            throw IllegalStateException("Places search failed with HTTP $code")
        }

        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(body)
        val arr = root.optJSONArray("value") ?: return@withContext emptyList()

        val results = ArrayList<DelhiPlace>(maxResults)
        for (i in 0 until minOf(arr.length(), maxResults)) {
            val v = arr.optJSONObject(i) ?: continue
            val placeLat = v.optDouble("lat", Double.NaN)
            val placeLng = v.optDouble("lng", Double.NaN)
            if (placeLat.isNaN() || placeLng.isNaN()) continue
            val stationId = v.optString("stationId", "").ifEmpty {
                v.optJSONObject("stationHint")?.optString("stationId", "")?.ifEmpty {
                    v.optJSONObject("stationHint")?.optString("id", "")?.ifEmpty { null }
                }
            }
            val area = v.optString("area", "").ifEmpty {
                v.optString("fullAddress", "India")
            }
            val category = v.optString("category", "").ifEmpty {
                v.optString("placeType", "Place")
            }
            results.add(
                DelhiPlace(
                    id = v.optString("id", "place_$i"),
                    name = v.optString("name", trimmed),
                    area = area,
                    category = category,
                    lat = placeLat,
                    lng = placeLng,
                    stationId = stationId
                )
            )
        }
        results
    }

    suspend fun reverseGeocodeFromBackend(lat: Double, lng: Double): DelhiPlace? =
        withContext(Dispatchers.IO) {
            val urlStr = "$backendBaseUrl/api/places/reverse?lat=$lat&lng=$lng"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("Accept", "application/json")
            }

            val code = conn.responseCode
            if (code == 404) return@withContext null
            if (code != 200) {
                throw IllegalStateException("Reverse geocode failed with HTTP $code")
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val v = root.optJSONObject("value") ?: return@withContext null
            val placeLat = v.optDouble("lat", Double.NaN)
            val placeLng = v.optDouble("lng", Double.NaN)
            if (placeLat.isNaN() || placeLng.isNaN()) return@withContext null
            val stationId = v.optString("stationId", "").ifEmpty {
                v.optJSONObject("stationHint")?.optString("stationId", "")?.ifEmpty {
                    v.optJSONObject("stationHint")?.optString("id", "")?.ifEmpty { null }
                }
            }
            val area = v.optString("area", "").ifEmpty {
                v.optString("fullAddress", "India")
            }
            val category = v.optString("category", "").ifEmpty {
                v.optString("placeType", "Place")
            }

            DelhiPlace(
                id = v.optString("id", "rev_${lat}_$lng"),
                name = v.optString("name", "Pinned Location"),
                area = area,
                category = category,
                lat = placeLat,
                lng = placeLng,
                stationId = stationId
            )
        }
}
