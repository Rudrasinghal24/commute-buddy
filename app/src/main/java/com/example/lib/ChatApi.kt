package com.example.lib

import com.example.data.DelhiPlace
import com.example.data.DelhiPlacesData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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

/**
 * Calls the CommuteBuddy backend `POST /api/chat`.
 * Never puts any Gemini API key in the Android client.
 */
suspend fun sendMessage(
    message: String,
    context: ChatTripContext
): String = withContext(Dispatchers.IO) {
    val baseUrl = DelhiPlacesData.backendBaseUrl
    val payload = JSONObject().apply {
        put("message", message)
        val rawJourney = context.selectedRoute?.rawJourneyJson
        if (!rawJourney.isNullOrBlank()) {
            runCatching { put("journey", JSONObject(rawJourney)) }
        }
    }

    val conn = (URL("$baseUrl/api/chat").openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 5000
        readTimeout = 5000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")
    }

    conn.outputStream.bufferedWriter().use { it.write(payload.toString()) }
    val code = conn.responseCode
    if (code != 200) {
        throw IllegalStateException("Chat backend returned HTTP $code")
    }

    val body = conn.inputStream.bufferedReader().use { it.readText() }
    val json = JSONObject(body)
    val replyStr = json.optString("reply", "")
    if (replyStr.isNotBlank() && !replyStr.startsWith("{")) {
        replyStr
    } else {
        json.optJSONObject("reply")?.optString("value", "Unable to verify transit response right now.")
            ?: "Unable to verify transit response right now."
    }
}
