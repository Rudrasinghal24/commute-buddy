package com.example.lib

import com.example.data.DelhiPlace
import com.example.data.DelhiPlacesData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Live route and multi-modal journey client backed by CommuteBuddy backend
 * (`POST /api/journeys` for Mix and `POST /api/routes` for single modes).
 * All fake corridor paths, fake step builders, and linear fare formulas are removed.
 */
suspend fun getRoutes(
    start: DelhiPlace,
    end: DelhiPlace,
    vehicle: TravelMode,
    reachBy: String? = null
): List<Route> = withContext(Dispatchers.IO) {
    val baseUrl = DelhiPlacesData.backendBaseUrl
    val isMix = vehicle == TravelMode.MIX
    val endpoint = if (isMix) "$baseUrl/api/journeys" else "$baseUrl/api/routes"

    val requestJson = JSONObject().apply {
        put(
            "start",
            JSONObject().apply {
                put("lat", start.lat)
                put("lng", start.lng)
                put("name", start.name)
                if (start.stationId != null) put("stationId", start.stationId)
            }
        )
        put(
            "end",
            JSONObject().apply {
                put("lat", end.lat)
                put("lng", end.lng)
                put("name", end.name)
                if (end.stationId != null) put("stationId", end.stationId)
            }
        )
        if (!isMix) {
            put("mode", vehicle.label.lowercase())
        }
        if (!reachBy.isNullOrBlank()) {
            put("reachByTime", reachBy)
        }
    }

    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 5000
        readTimeout = 5000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")
    }

    conn.outputStream.bufferedWriter().use { it.write(requestJson.toString()) }

    val responseCode = conn.responseCode
    if (responseCode != 200) {
        throw IllegalStateException("Backend route request failed with HTTP $responseCode")
    }

    val body = conn.inputStream.bufferedReader().use { it.readText() }
    val root = JSONObject(body)
    if (root.optString("status") == "no_data") {
        return@withContext emptyList()
    }

    if (isMix) {
        val journeysArr = root.optJSONArray("journeys") ?: return@withContext emptyList()
        parseJourneysArray(journeysArr, start, end)
    } else {
        val routesArr = root.optJSONArray("routes") ?: return@withContext emptyList()
        parseSingleModeRoutesArray(routesArr, start, end)
    }
}

private fun parseJourneysArray(
    journeysArr: JSONArray,
    start: DelhiPlace,
    end: DelhiPlace
): List<Route> {
    val result = ArrayList<Route>(journeysArr.length())
    for (i in 0 until journeysArr.length()) {
        val j = journeysArr.optJSONObject(i) ?: continue
        val durationEnv = j.optJSONObject("totalDurationSeconds")
        val durationSec = (durationEnv?.optInt("value", 60) ?: 60).coerceAtLeast(60)
        val minutes = (durationSec / 60.0).roundToInt().coerceAtLeast(1)

        val fareEnv = j.optJSONObject("totalFare")
        val fareVal = fareEnv?.optJSONObject("value")
        val totalInr = if (fareVal != null && !fareVal.isNull("amountInr")) {
            fareVal.optInt("amountInr")
        } else null
        val isEst = fareVal?.optBoolean("isEstimate", false) ?: false
        val fareFormatted = Fares.formatVerifiedFareDisplay(
            totalInr = totalInr,
            isEstimate = isEst,
            status = fareEnv?.optString("status")
        )

        val legsArr = j.optJSONArray("legs") ?: JSONArray()
        val steps = ArrayList<RouteStep>(legsArr.length())
        val mergedPath = ArrayList<Pair<Double, Double>>()
        for (k in 0 until legsArr.length()) {
            val leg = legsArr.optJSONObject(k) ?: continue
            val legSec = (leg.optJSONObject("durationSeconds")?.optInt("value", 60) ?: 60).coerceAtLeast(60)
            steps.add(
                RouteStep(
                    mode = mapModeString(leg.optString("mode", "walk")),
                    text = leg.optString("instruction", ""),
                    minutes = (legSec / 60.0).roundToInt().coerceAtLeast(1),
                    durationSeconds = legSec
                )
            )
            val legPath = leg.optJSONArray("path")
            if (legPath != null) {
                for (p in 0 until legPath.length()) {
                    val pt = legPath.optJSONArray(p) ?: continue
                    val lat = pt.optDouble(0, Double.NaN)
                    val lng = pt.optDouble(1, Double.NaN)
                    if (!lat.isNaN() && !lng.isNaN()) {
                        mergedPath.add(Pair(lat, lng))
                    }
                }
            }
        }

        val distanceMeters = j.optDouble("totalDistanceMeters", 1000.0)
        val distanceKm = ((distanceMeters / 1000.0) * 10.0).roundToInt() / 10.0
        val lowestConf = j.optDouble("lowestConfidence", 0.85)
        val reliability = (lowestConf * 10.0).roundToInt().coerceIn(1, 10)

        result.add(
            Route(
                id = j.optString("id", "journey_$i"),
                label = mapRouteLabel(j.optString("label")),
                minutes = minutes,
                costInr = fareFormatted.costInr,
                distanceKm = distanceKm,
                transfers = j.optInt("transfers", 0),
                walkMeters = j.optInt("totalWalkMeters", 0),
                crowd = CrowdLevel.MEDIUM,
                dataLabel = Fares.mapEnvelopeStatusToDataLabel(j.optString("overallStatus")),
                reliability = reliability,
                steps = steps,
                path = if (mergedPath.size >= 2) mergedPath else listOf(
                    Pair(start.lat, start.lng),
                    Pair(end.lat, end.lng)
                ),
                durationSeconds = durationSec,
                costDisplay = fareFormatted.display,
                isEstimate = fareFormatted.isEstimate,
                rawJourneyJson = j.toString()
            )
        )
    }
    return result
}

private fun parseSingleModeRoutesArray(
    routesArr: JSONArray,
    start: DelhiPlace,
    end: DelhiPlace
): List<Route> {
    val result = ArrayList<Route>(routesArr.length())
    for (i in 0 until routesArr.length()) {
        val r = routesArr.optJSONObject(i) ?: continue
        val durationEnv = r.optJSONObject("durationSeconds")
        val durationSec = (durationEnv?.optInt("value", 60) ?: 60).coerceAtLeast(60)
        val minutes = (durationSec / 60.0).roundToInt().coerceAtLeast(1)

        val fareEnv = r.optJSONObject("fare")
        val fareObj = fareEnv?.optJSONObject("value")
        val fareVal = if (fareObj != null && !fareObj.isNull("fareInr")) fareObj.optInt("fareInr") else null
        val fareStatus = fareEnv?.optString("status")
        val fareFormatted = Fares.formatVerifiedFareDisplay(
            totalInr = fareVal,
            isEstimate = (fareObj?.optBoolean("isEstimate", false) ?: false) || fareStatus == "estimate",
            status = fareStatus
        )

        val stepsArr = r.optJSONArray("steps") ?: JSONArray()
        val steps = ArrayList<RouteStep>(stepsArr.length())
        for (k in 0 until stepsArr.length()) {
            val step = stepsArr.optJSONObject(k) ?: continue
            val stepSecObj = step.optJSONObject("durationSeconds")
            val stepSec = (stepSecObj?.optInt("value", 60) ?: step.optInt("durationSeconds", 60)).coerceAtLeast(60)
            steps.add(
                RouteStep(
                    mode = mapModeString(step.optString("mode", "walk")),
                    text = step.optString("instruction", ""),
                    minutes = (stepSec / 60.0).roundToInt().coerceAtLeast(1),
                    durationSeconds = stepSec
                )
            )
        }

        val distMeters = r.optDouble("distanceMeters", 1000.0)
        val distanceKm = ((distMeters / 1000.0) * 10.0).roundToInt() / 10.0

        result.add(
            Route(
                id = r.optString("id", "route_$i"),
                label = mapRouteLabel(r.optString("label")),
                minutes = minutes,
                costInr = fareFormatted.costInr,
                distanceKm = distanceKm,
                transfers = r.optInt("transfers", 0),
                walkMeters = r.optInt("walkMeters", 0),
                crowd = mapCrowdLevel(r.optString("crowdLevel")),
                dataLabel = Fares.mapEnvelopeStatusToDataLabel(durationEnv?.optString("status")),
                reliability = r.optInt("reliabilityScore", 8),
                steps = steps,
                path = parsePathCoordinates(r.optJSONArray("path"), start, end),
                durationSeconds = durationSec,
                costDisplay = fareFormatted.display,
                isEstimate = fareFormatted.isEstimate
            )
        )
    }
    return result
}

private fun parsePathCoordinates(
    pathArr: JSONArray?,
    start: DelhiPlace,
    end: DelhiPlace
): List<Pair<Double, Double>> {
    if (pathArr == null || pathArr.length() < 2) {
        return listOf(Pair(start.lat, start.lng), Pair(end.lat, end.lng))
    }
    val coords = ArrayList<Pair<Double, Double>>(pathArr.length())
    for (i in 0 until pathArr.length()) {
        val pt = pathArr.optJSONArray(i) ?: continue
        val lat = pt.optDouble(0, Double.NaN)
        val lng = pt.optDouble(1, Double.NaN)
        if (!lat.isNaN() && !lng.isNaN()) {
            coords.add(Pair(lat, lng))
        }
    }
    return if (coords.size >= 2) coords else listOf(Pair(start.lat, start.lng), Pair(end.lat, end.lng))
}

private fun mapModeString(raw: String): TravelMode {
    return when (raw.lowercase()) {
        "metro" -> TravelMode.METRO
        "bus" -> TravelMode.BUS
        "train", "rrts" -> TravelMode.TRAIN
        "car" -> TravelMode.CAR
        "bike", "auto", "e_rickshaw" -> TravelMode.BIKE
        "walk" -> TravelMode.WALK
        else -> TravelMode.MIX
    }
}

private fun mapRouteLabel(raw: String?): RouteLabel {
    return when (raw) {
        "Least walking", "Least Walking" -> RouteLabel.LEAST_WALKING
        "Cheapest" -> RouteLabel.CHEAPEST
        else -> RouteLabel.FASTEST
    }
}

private fun mapCrowdLevel(raw: String?): CrowdLevel {
    return when (raw?.lowercase()) {
        "high" -> CrowdLevel.HIGH
        "low" -> CrowdLevel.LOW
        else -> CrowdLevel.MEDIUM
    }
}
