package com.example.lib

import com.example.data.DelhiPlace
import kotlinx.coroutines.delay

data class ChatTripContext(
    val start: DelhiPlace?,
    val end: DelhiPlace?,
    val vehicle: TravelMode,
    val selectedRoute: Route?,
    val reachByTime: String? = null
)

data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean
)

// Later call our backend /api/chat, never put the Gemini key in the app.
suspend fun sendMessage(
    message: String,
    context: ChatTripContext
): String {
    // Later call our backend /api/chat, never put the Gemini key in the app.
    delay(800L)

    val startName = context.start?.name ?: "your starting point"
    val endName = context.end?.name ?: "your destination"
    val route = context.selectedRoute
    val duration = route?.minutes ?: 24
    val cost = route?.costInr ?: 35
    val modeLabel = context.vehicle.label
    val normalized = message.trim().lowercase()

    return when {
        normalized.contains("miss") && normalized.contains("metro") -> {
            "Next DMRC train from $startName toward $endName departs in 6 min. Total trip time only shifts to ${duration + 6} min with the same ₹$cost fare."
        }
        normalized.contains("safe") && normalized.contains("night") -> {
            "Stations between $startName and $endName have CCTV, CISF security, and lit exits until 11:30 PM. Prefer the first coach and main gate exits after 9:30 PM."
        }
        normalized.contains("leave by") || normalized.contains("what time") -> {
            val leaveBy = CommuteFormatters.formatLeaveBy(context.reachByTime ?: "09:30 AM", duration)
                ?: "Leave 5 min before departure"
            "$leaveBy from $startName ($duration min $modeLabel route plus 5 min station buffer) to reach $endName on time."
        }
        route != null -> {
            "Your ${route.label.display} $modeLabel route from $startName to $endName takes ${route.minutes} min (₹${route.costInr}, ${route.transfers} transfers, ${route.walkMeters} m walk, Reliable ${route.reliability}/10)."
        }
        context.start != null && context.end != null -> {
            "For $startName to $endName by $modeLabel, typical travel time is $duration min and costs around ₹$cost."
        }
        else -> {
            "Select your trip start and destination in Delhi NCR to get specific metro frequency, night safety, and departure buffer details."
        }
    }
}
