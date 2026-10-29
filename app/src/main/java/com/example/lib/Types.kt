package com.example.lib

enum class RouteLabel(val display: String) {
    FASTEST("Fastest"),
    CHEAPEST("Cheapest"),
    LEAST_WALKING("Least walking")
}

enum class CrowdLevel(val display: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High")
}

enum class DataSourceLabel(val display: String) {
    TIMETABLE("TIMETABLE"),
    SAMPLE("SAMPLE"),
    ESTIMATED("ESTIMATED")
}

data class RouteStep(
    val mode: TravelMode,
    val text: String,
    val minutes: Int
)

data class Route(
    val id: String,
    val label: RouteLabel,
    val minutes: Int,
    val costInr: Int,
    val distanceKm: Double,
    val transfers: Int,
    val walkMeters: Int,
    val crowd: CrowdLevel,
    val dataLabel: DataSourceLabel,
    val reliability: Int, // 0..10
    val steps: List<RouteStep>,
    val path: List<Pair<Double, Double>> // [lat, lng][]
)
