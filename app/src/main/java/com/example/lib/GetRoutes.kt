package com.example.lib

import com.example.data.DelhiPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Stable async route generator returning 3 realistic routes after a 600ms delay.
// Calibrated against Google Maps Delhi NCR travel times & distances (e.g., Millennium City Centre
// Gurugram to Botanical Garden Noida = ~41 km, ~71 min by Car, ~72 min by Bike, ~77 min by Metro).
suspend fun getRoutes(
    start: DelhiPlace,
    end: DelhiPlace,
    vehicle: TravelMode,
    reachBy: String? = null
): List<Route> {
    val startTimeMs = System.currentTimeMillis()

    // Try fetching real road geometry & distance from OSRM within the 600ms window
    val osrmResult = withTimeoutOrNull(450L) {
        fetchOsrmRoutes(start, end)
    }

    val elapsedMs = System.currentTimeMillis() - startTimeMs
    val remainingDelayMs = (600L - elapsedMs).coerceAtLeast(50L)
    delay(remainingDelayMs)

    val straightKm = haversineKm(start.lat, start.lng, end.lat, end.lng).coerceAtLeast(1.2)
    // Delhi NCR road circuity factor (~1.455x for cross-city highway routes like Gurugram-Noida 28.1km -> 41.0km)
    val windingFactor = when {
        straightKm >= 20.0 -> 1.455
        straightKm >= 10.0 -> 1.38
        else -> 1.30
    }
    val fallbackRoadKm = ((straightKm * windingFactor) * 10.0).roundToInt() / 10.0
    val baseRoadKm = osrmResult?.primaryDistanceKm ?: fallbackRoadKm

    val reachSuffix = if (reachBy != null) " (by $reachBy)" else ""

    // Build 3 realistic road-following paths between start and end
    val pathFastest = osrmResult?.primaryPath?.takeIf { it.size >= 4 }
        ?: buildDelhiCorridorPath(start, end, RouteLabel.FASTEST)
    val pathLeastWalk = osrmResult?.altPath1?.takeIf { it.size >= 4 }
        ?: buildDelhiCorridorPath(start, end, RouteLabel.LEAST_WALKING)
    val pathCheapest = osrmResult?.altPath2?.takeIf { it.size >= 4 }
        ?: buildDelhiCorridorPath(start, end, RouteLabel.CHEAPEST)

    val fastestKm = baseRoadKm
    val leastWalkKm = ((baseRoadKm * 1.02) * 10.0).roundToInt() / 10.0
    val cheapestKm = ((baseRoadKm * 1.05) * 10.0).roundToInt() / 10.0

    val fastestSteps = buildStepsForVariant(
        start = start,
        end = end,
        vehicle = vehicle,
        distanceKm = fastestKm,
        label = RouteLabel.FASTEST,
        reachSuffix = reachSuffix
    )
    val leastWalkSteps = buildStepsForVariant(
        start = start,
        end = end,
        vehicle = vehicle,
        distanceKm = leastWalkKm,
        label = RouteLabel.LEAST_WALKING,
        reachSuffix = reachSuffix
    )
    val cheapestSteps = buildStepsForVariant(
        start = start,
        end = end,
        vehicle = vehicle,
        distanceKm = cheapestKm,
        label = RouteLabel.CHEAPEST,
        reachSuffix = reachSuffix
    )

    val fastestMinutes = fastestSteps.sumOf { it.minutes }
    val leastWalkMinutes = leastWalkSteps.sumOf { it.minutes }.coerceAtLeast(fastestMinutes + 1)
    val cheapestMinutes = cheapestSteps.sumOf { it.minutes }.coerceAtLeast(fastestMinutes + 3)

    val fastestCost = Fares.calculateRouteCostInr(vehicle, fastestSteps, fastestKm, 1.0)
    val cheapestCost = Fares.calculateRouteCostInr(vehicle, cheapestSteps, fastestKm, 0.88)
        .coerceAtMost(if (fastestCost > 15) fastestCost - 10 else fastestCost)
    val leastWalkCost = Fares.calculateRouteCostInr(vehicle, leastWalkSteps, leastWalkKm, 1.06)

    val isPrivateMode = vehicle == TravelMode.CAR || vehicle == TravelMode.BIKE || vehicle == TravelMode.WALK

    return listOf(
        Route(
            id = "route_fastest_${vehicle.name.lowercase()}",
            label = RouteLabel.FASTEST,
            minutes = fastestMinutes,
            costInr = fastestCost,
            distanceKm = fastestKm,
            transfers = if (isPrivateMode) 0 else 1,
            walkMeters = if (isPrivateMode) (if (vehicle == TravelMode.WALK) (fastestKm * 1000).roundToInt() else 50) else 320,
            crowd = CrowdLevel.LOW,
            dataLabel = DataSourceLabel.TIMETABLE,
            reliability = 9,
            steps = fastestSteps,
            path = pathFastest
        ),
        Route(
            id = "route_least_walk_${vehicle.name.lowercase()}",
            label = RouteLabel.LEAST_WALKING,
            minutes = leastWalkMinutes,
            costInr = leastWalkCost,
            distanceKm = leastWalkKm,
            transfers = 0,
            walkMeters = if (vehicle == TravelMode.WALK) (leastWalkKm * 960).roundToInt() else 40,
            crowd = CrowdLevel.MEDIUM,
            dataLabel = DataSourceLabel.ESTIMATED,
            reliability = 9,
            steps = leastWalkSteps,
            path = pathLeastWalk
        ),
        Route(
            id = "route_cheapest_${vehicle.name.lowercase()}",
            label = RouteLabel.CHEAPEST,
            minutes = cheapestMinutes,
            costInr = cheapestCost,
            distanceKm = cheapestKm,
            transfers = if (isPrivateMode) 0 else 1,
            walkMeters = if (isPrivateMode) (if (vehicle == TravelMode.WALK) (cheapestKm * 1040).roundToInt() else 80) else 480,
            crowd = CrowdLevel.HIGH,
            dataLabel = DataSourceLabel.SAMPLE,
            reliability = 8,
            steps = cheapestSteps,
            path = pathCheapest
        )
    )
}

private data class OsrmRouteResult(
    val primaryDistanceKm: Double,
    val primaryPath: List<Pair<Double, Double>>,
    val altPath1: List<Pair<Double, Double>>?,
    val altPath2: List<Pair<Double, Double>>?
)

private suspend fun fetchOsrmRoutes(start: DelhiPlace, end: DelhiPlace): OsrmRouteResult? =
    withContext(Dispatchers.IO) {
        try {
            val urlStr =
                "https://router.project-osrm.org/route/v1/driving/${start.lng},${start.lat};${end.lng},${end.lat}?overview=simplified&geometries=geojson&alternatives=true"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 350
                readTimeout = 350
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val routesArr = json.optJSONArray("routes") ?: return@withContext null
            if (routesArr.length() == 0) return@withContext null

            fun parseCoords(routeObj: JSONObject): List<Pair<Double, Double>> {
                val coords = routeObj.optJSONObject("geometry")?.optJSONArray("coordinates")
                    ?: return emptyList()
                val list = ArrayList<Pair<Double, Double>>(coords.length())
                for (i in 0 until coords.length()) {
                    val pt = coords.optJSONArray(i) ?: continue
                    val lng = pt.optDouble(0)
                    val lat = pt.optDouble(1)
                    if (!lat.isNaN() && !lng.isNaN()) {
                        list.add(Pair(lat, lng))
                    }
                }
                return list
            }

            val primaryObj = routesArr.getJSONObject(0)
            val distMeters = primaryObj.optDouble("distance", 0.0)
            if (distMeters <= 100.0) return@withContext null
            val distKm = ((distMeters / 1000.0) * 10.0).roundToInt() / 10.0
            val primaryPath = parseCoords(primaryObj)
            val alt1 = if (routesArr.length() > 1) parseCoords(routesArr.getJSONObject(1)) else null
            val alt2 = if (routesArr.length() > 2) parseCoords(routesArr.getJSONObject(2)) else null

            OsrmRouteResult(
                primaryDistanceKm = distKm,
                primaryPath = primaryPath,
                altPath1 = alt1,
                altPath2 = alt2
            )
        } catch (_: Exception) {
            null
        }
    }

private fun buildStepsForVariant(
    start: DelhiPlace,
    end: DelhiPlace,
    vehicle: TravelMode,
    distanceKm: Double,
    label: RouteLabel,
    reachSuffix: String
): List<RouteStep> {
    // Calibrated against Google Maps Delhi NCR speeds:
    // Car: ~35.5 km/h on arterial/NH-48/Ring Rd (~1.66 min/km + 3 min terminal) -> 41 km = 71 min (1 hr 11 min)
    // Bike: ~35.0 km/h (~1.68 min/km + 3 min) -> 41 km = 72 min (1 hr 12 min)
    // Metro: ~33.5 km/h including stops/interchange (~1.73 min/km + 6 min) -> 41 km = 77 min (1 hr 17 min)
    // Walk: ~5.0 km/h (~11.6 min/km) -> 41 km = 478 min (~8 hr)
    return when (vehicle) {
        TravelMode.MIX -> {
            val metroMin = (distanceKm * 1.62).roundToInt().coerceAtLeast(8)
            val busMin = (distanceKm * 1.95).roundToInt().coerceAtLeast(10)
            when (label) {
                RouteLabel.FASTEST -> listOf(
                    RouteStep(TravelMode.WALK, "Walk to ${start.name} gate", 3),
                    RouteStep(TravelMode.METRO, "DMRC Express line toward ${end.area}$reachSuffix", metroMin),
                    RouteStep(TravelMode.BIKE, "Last-mile electric bike to ${end.name}", 4)
                )
                RouteLabel.LEAST_WALKING -> listOf(
                    RouteStep(TravelMode.BIKE, "Feeder e-rickshaw from ${start.name}", 3),
                    RouteStep(TravelMode.METRO, "Direct Metro corridor to ${end.area}$reachSuffix", metroMin + 2),
                    RouteStep(TravelMode.CAR, "Shared auto drop at ${end.name}", 3)
                )
                RouteLabel.CHEAPEST -> listOf(
                    RouteStep(TravelMode.WALK, "Walk to ${start.area} stop", 4),
                    RouteStep(TravelMode.BUS, "DTC AC feeder bus via Ring Road", (busMin * 0.45).roundToInt().coerceAtLeast(6)),
                    RouteStep(TravelMode.METRO, "Metro interchange to ${end.name}$reachSuffix", (metroMin * 0.62).roundToInt().coerceAtLeast(7)),
                    RouteStep(TravelMode.WALK, "Walk to ${end.name} entrance", 4)
                )
            }
        }

        TravelMode.METRO -> {
            val rideMin = (distanceKm * 1.73).roundToInt().coerceAtLeast(8)
            when (label) {
                RouteLabel.FASTEST -> listOf(
                    RouteStep(TravelMode.METRO, "Board DMRC Line at ${start.name}", rideMin),
                    RouteStep(TravelMode.METRO, "Interchange connection to ${end.name}$reachSuffix", 6)
                )
                RouteLabel.LEAST_WALKING -> listOf(
                    RouteStep(TravelMode.METRO, "Direct platform boarding at ${start.name}", rideMin + 1),
                    RouteStep(TravelMode.METRO, "Exit via Gate 2 lift at ${end.name}$reachSuffix", 6)
                )
                RouteLabel.CHEAPEST -> listOf(
                    RouteStep(TravelMode.METRO, "Standard DMRC route from ${start.name}", rideMin + 3),
                    RouteStep(TravelMode.METRO, "Arrive at ${end.name} concourse$reachSuffix", 6)
                )
            }
        }

        TravelMode.BUS -> {
            val rideMin = (distanceKm * 2.05).roundToInt().coerceAtLeast(11)
            when (label) {
                RouteLabel.FASTEST -> listOf(
                    RouteStep(TravelMode.BUS, "Board DTC Rapid Bus at ${start.name}", rideMin),
                    RouteStep(TravelMode.BUS, "Alight at ${end.name} bus bay$reachSuffix", 4)
                )
                RouteLabel.LEAST_WALKING -> listOf(
                    RouteStep(TravelMode.BUS, "Direct AC Low-Floor Bus from ${start.name}", rideMin + 2),
                    RouteStep(TravelMode.BUS, "Drop directly outside ${end.name}$reachSuffix", 3)
                )
                RouteLabel.CHEAPEST -> listOf(
                    RouteStep(TravelMode.BUS, "Non-AC Orange Cluster Bus from ${start.area}", rideMin + 4),
                    RouteStep(TravelMode.BUS, "Stop at ${end.name} terminal$reachSuffix", 4)
                )
            }
        }

        TravelMode.TRAIN -> {
            val rideMin = (distanceKm * 1.75).roundToInt().coerceAtLeast(10)
            when (label) {
                RouteLabel.FASTEST -> listOf(
                    RouteStep(TravelMode.TRAIN, "Board NCR EMU Express from ${start.name}", rideMin),
                    RouteStep(TravelMode.TRAIN, "Arrive at ${end.name} platform$reachSuffix", 4)
                )
                RouteLabel.LEAST_WALKING -> listOf(
                    RouteStep(TravelMode.TRAIN, "Direct suburban rail service to ${end.name}", rideMin + 2),
                    RouteStep(TravelMode.TRAIN, "Main concourse exit at ${end.name}$reachSuffix", 3)
                )
                RouteLabel.CHEAPEST -> listOf(
                    RouteStep(TravelMode.TRAIN, "Unreserved Ring Railway passenger to ${end.area}", rideMin + 4),
                    RouteStep(TravelMode.TRAIN, "Exit at ${end.name}$reachSuffix", 4)
                )
            }
        }

        TravelMode.CAR -> {
            // Calibrated to match Google Maps Drive time: 41 km -> 68 + 3 = 71 min (1 hr 11 min)
            val driveMin = (distanceKm * 1.66).roundToInt().coerceAtLeast(8)
            when (label) {
                RouteLabel.FASTEST -> listOf(
                    RouteStep(TravelMode.CAR, "Via NH-48, Ring Rd & DND Flyway from ${start.name}", driveMin),
                    RouteStep(TravelMode.CAR, "Drop off at ${end.name}$reachSuffix", 3)
                )
                RouteLabel.LEAST_WALKING -> listOf(
                    RouteStep(TravelMode.CAR, "Via Outer Ring Rd corridor from ${start.name}", driveMin),
                    RouteStep(TravelMode.CAR, "Gate-to-gate drop at ${end.name}$reachSuffix", 2)
                )
                RouteLabel.CHEAPEST -> listOf(
                    RouteStep(TravelMode.CAR, "Toll-free arterial route from ${start.name}", driveMin + 1),
                    RouteStep(TravelMode.CAR, "Arrive at ${end.name}$reachSuffix", 2)
                )
            }
        }

        TravelMode.BIKE -> {
            // Calibrated to match Google Maps Two-Wheeler time: 41 km -> 70 + 2 = 72 min (1 hr 12 min)
            val bikeMin = (distanceKm * 1.70).roundToInt().coerceAtLeast(7)
            when (label) {
                RouteLabel.FASTEST -> listOf(
                    RouteStep(TravelMode.BIKE, "Ride via NH-48 & Ring Rd corridor from ${start.name}", bikeMin),
                    RouteStep(TravelMode.BIKE, "Park at ${end.name} stand$reachSuffix", 2)
                )
                RouteLabel.LEAST_WALKING -> listOf(
                    RouteStep(TravelMode.BIKE, "Direct arterial corridor from ${start.name}", bikeMin + 1),
                    RouteStep(TravelMode.BIKE, "Drop at ${end.name} front gate$reachSuffix", 2)
                )
                RouteLabel.CHEAPEST -> listOf(
                    RouteStep(TravelMode.BIKE, "Shortest arterial route to ${end.area}", bikeMin + 2),
                    RouteStep(TravelMode.BIKE, "Arrive at ${end.name}$reachSuffix", 2)
                )
            }
        }

        TravelMode.WALK -> {
            val walkMin = (distanceKm * 11.6).roundToInt().coerceAtLeast(15)
            when (label) {
                RouteLabel.FASTEST -> listOf(
                    RouteStep(TravelMode.WALK, "Walk via main boulevard from ${start.name}", walkMin - 2),
                    RouteStep(TravelMode.WALK, "Reach ${end.name}$reachSuffix", 2)
                )
                RouteLabel.LEAST_WALKING -> listOf(
                    RouteStep(TravelMode.WALK, "Direct pedestrian skywalk shortcut from ${start.name}", (walkMin - 4).coerceAtLeast(10)),
                    RouteStep(TravelMode.WALK, "Enter ${end.name}$reachSuffix", 2)
                )
                RouteLabel.CHEAPEST -> listOf(
                    RouteStep(TravelMode.WALK, "Walk via shaded pedestrian avenue to ${end.area}", walkMin),
                    RouteStep(TravelMode.WALK, "Reach ${end.name}$reachSuffix", 2)
                )
            }
        }
    }
}

// Generates a realistic multi-waypoint Delhi NCR road path following NH-48, Ring Road, Outer Ring Road,
// and Yamuna crossings (DND Flyway / Barapullah / Kalindi Kunj / ITO) instead of a straight diagonal line.
private fun buildDelhiCorridorPath(
    start: DelhiPlace,
    end: DelhiPlace,
    label: RouteLabel
): List<Pair<Double, Double>> {
    val isGurugramStart = start.lat < 28.52 && start.lng < 77.12
    val isGurugramEnd = end.lat < 28.52 && end.lng < 77.12
    val isNoidaStart = start.lng > 77.28
    val isNoidaEnd = end.lng > 77.28

    // Special high-precision Delhi NCR corridor for Gurugram <-> Noida/East Delhi (matches Google Maps NH-48 -> Ring Rd -> DND)
    if ((isGurugramStart && isNoidaEnd) || (isNoidaStart && isGurugramEnd)) {
        val forward = isGurugramStart
        val gPlace = if (forward) start else end
        val nPlace = if (forward) end else start

        val corridor = when (label) {
            RouteLabel.FASTEST -> listOf(
                Pair(gPlace.lat, gPlace.lng),
                Pair(28.4722, 77.0725), // IFFCO Chowk NH-48
                Pair(28.5042, 77.0964), // Ambience / Sirhaul Border
                Pair(28.5285, 77.1155), // Rajokri Flyover NH-48
                Pair(28.5468, 77.1260), // Mahipalpur / IGI Airport NH-48
                Pair(28.5710, 77.1460), // Shankar Vihar / Delhi Cantt
                Pair(28.5880, 77.1625), // Dhaula Kuan Ring Road
                Pair(28.5765, 77.1850), // Moti Bagh / RK Puram Ring Road
                Pair(28.5678, 77.2100), // AIIMS / South Extension Ring Road
                Pair(28.5695, 77.2435), // Lajpat Nagar / Moolchand
                Pair(28.5735, 77.2630), // Ashram / Maharani Bagh
                Pair(28.5760, 77.2850), // DND Flyway Yamuna Bridge
                Pair(28.5685, 77.3120), // Noida Film City Sector 16A
                Pair(nPlace.lat, nPlace.lng)
            )
            RouteLabel.LEAST_WALKING -> listOf(
                Pair(gPlace.lat, gPlace.lng),
                Pair(28.4722, 77.0725), // IFFCO Chowk
                Pair(28.5042, 77.0964), // Sirhaul NH-48
                Pair(28.5468, 77.1260), // Mahipalpur
                Pair(28.5550, 77.1630), // Munirka / Vasant Vihar Outer Ring Rd
                Pair(28.5450, 77.1950), // IIT Delhi / Hauz Khas
                Pair(28.5495, 77.2260), // Chirag Delhi Flyover
                Pair(28.5515, 77.2517), // Nehru Place
                Pair(28.5620, 77.2690), // Modi Mill / New Friends Colony
                Pair(28.5720, 77.2880), // DND / Mayur Vihar Link
                Pair(28.5675, 77.3150), // Noida Sector 18 Link
                Pair(nPlace.lat, nPlace.lng)
            )
            RouteLabel.CHEAPEST -> listOf(
                Pair(gPlace.lat, gPlace.lng),
                Pair(28.4780, 77.0940), // Sikanderpur MG Road
                Pair(28.4980, 77.1420), // Arjan Garh / Sultanpur MG Road
                Pair(28.5245, 77.1880), // Qutub Minar / Mehrauli
                Pair(28.5260, 77.2190), // Saket / MB Road
                Pair(28.5365, 77.2580), // Kalkaji / Govindpuri
                Pair(28.5430, 77.2850), // Jasola / Sarita Vihar
                Pair(28.5465, 77.3090), // Kalindi Kunj Yamuna Bridge
                Pair(28.5565, 77.3240), // Noida Sector 37 / Amity
                Pair(nPlace.lat, nPlace.lng)
            )
        }
        return if (forward) corridor else corridor.reversed()
    }

    // General multi-segment road curve through Delhi's arterial ring/radial grid
    val dLat = end.lat - start.lat
    val dLng = end.lng - start.lng
    val perpLat = -dLng
    val perpLng = dLat
    val norm = sqrt(perpLat * perpLat + perpLng * perpLng).coerceAtLeast(0.001)
    val uLat = perpLat / norm
    val uLng = perpLng / norm

    val distDeg = sqrt(dLat * dLat + dLng * dLng)
    val maxDeflection = (distDeg * 0.16).coerceIn(0.004, 0.032)

    val sign = when (label) {
        RouteLabel.FASTEST -> 1.0
        RouteLabel.LEAST_WALKING -> 0.45
        RouteLabel.CHEAPEST -> -0.75
    }

    val fractions = listOf(0.0, 0.12, 0.25, 0.38, 0.52, 0.66, 0.79, 0.90, 1.0)
    return fractions.mapIndexed { idx, t ->
        if (idx == 0) return@mapIndexed Pair(start.lat, start.lng)
        if (idx == fractions.lastIndex) return@mapIndexed Pair(end.lat, end.lng)

        // Smooth highway-like bend with a secondary arterial dogleg
        val arch = sin(t * Math.PI) * maxDeflection * sign
        val ripple = sin(t * 2.0 * Math.PI) * (maxDeflection * 0.28)
        val offset = arch + ripple
        Pair(
            start.lat + dLat * t + uLat * offset,
            start.lng + dLng * t + uLng * offset
        )
    }
}

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}
