package com.example.lib

import kotlin.math.roundToInt

data class ModeFareRate(
    val baseInr: Int,
    val perKmInr: Double,
    val maxCapInr: Int? = null
)

// Realistic per-mode INR rates for Delhi NCR commute modes (DMRC, DTC, EMU, Fuel/Cab)
object Fares {
    private val rates: Map<TravelMode, ModeFareRate> = mapOf(
        TravelMode.WALK to ModeFareRate(baseInr = 0, perKmInr = 0.0, maxCapInr = 0),
        TravelMode.BIKE to ModeFareRate(baseInr = 15, perKmInr = 3.2),
        TravelMode.BUS to ModeFareRate(baseInr = 10, perKmInr = 1.2, maxCapInr = 25),
        TravelMode.METRO to ModeFareRate(baseInr = 10, perKmInr = 2.2, maxCapInr = 60),
        TravelMode.TRAIN to ModeFareRate(baseInr = 10, perKmInr = 1.0, maxCapInr = 30),
        TravelMode.CAR to ModeFareRate(baseInr = 30, perKmInr = 7.2),
        TravelMode.MIX to ModeFareRate(baseInr = 15, perKmInr = 2.6)
    )

    fun calculateModeFareInr(mode: TravelMode, distanceKm: Double): Int {
        if (mode == TravelMode.WALK || distanceKm <= 0.0) return 0
        val rate = rates[mode] ?: ModeFareRate(baseInr = 10, perKmInr = 3.0)
        val raw = (rate.baseInr + distanceKm * rate.perKmInr).roundToInt()
        val capped = rate.maxCapInr?.let { raw.coerceAtMost(it) } ?: raw
        return capped.coerceAtLeast(rate.baseInr)
    }

    fun calculateRouteCostInr(
        vehicle: TravelMode,
        steps: List<RouteStep>,
        totalDistanceKm: Double,
        variantMultiplier: Double = 1.0
    ): Int {
        if (vehicle == TravelMode.WALK) return 0
        if (vehicle != TravelMode.MIX) {
            return (calculateModeFareInr(vehicle, totalDistanceKm) * variantMultiplier).roundToInt()
        }
        val activeSteps = steps.filter { it.mode != TravelMode.WALK }
        if (activeSteps.isEmpty()) return 0
        val totalActiveMin = activeSteps.sumOf { it.minutes }.coerceAtLeast(1)
        val sumInr = activeSteps.sumOf { step ->
            val stepDist = totalDistanceKm * (step.minutes.toDouble() / totalActiveMin.toDouble())
            calculateModeFareInr(step.mode, stepDist)
        }
        return (sumInr * variantMultiplier).roundToInt()
    }
}
