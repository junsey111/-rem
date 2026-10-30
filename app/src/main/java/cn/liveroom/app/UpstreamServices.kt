package cn.liveroom.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Runtime endpoints for the GitHub based media and messaging services. */
data class UpstreamServices(
    val billdApiUrl: String = BuildConfig.BILLD_API_URL,
    val srsUrl: String = BuildConfig.SRS_URL,
    val srsPlaybackUrl: String = BuildConfig.SRS_PLAYBACK_URL,
    val liveKitUrl: String = BuildConfig.LIVEKIT_URL,
    val openImApiUrl: String = BuildConfig.OPENIM_API_URL,
    val openImWsUrl: String = BuildConfig.OPENIM_WS_URL,
)

/** Small adapter for the already-running GitHub Billd server. */
class BilldApi(private val baseUrl: String = BuildConfig.BILLD_API_URL) {
    suspend fun liveRooms(): Result<List<BilldRoom>> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL("${baseUrl.trimEnd('/')}/live/list").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000
            connection.readTimeout = 5000
            try {
                if (connection.responseCode !in 200..299) error("Billd HTTP ${connection.responseCode}")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val data = JSONObject(body).optJSONObject("data") ?: JSONObject()
                val rows = data.optJSONArray("rows") ?: JSONArray()
                buildList {
                    for (index in 0 until rows.length()) {
                        val row = rows.optJSONObject(index) ?: continue
                        val raw = row.optString("data", "{}")
                        val details = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
                        val roomId = row.optString("roomId").ifBlank { details.optString("roomId") }
                        if (roomId.isNotBlank()) add(
                            BilldRoom(
                                roomId = roomId,
                                title = details.optString("roomName", "直播间 $roomId"),
                                host = details.optString("username", "主播"),
                            )
                        )
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}

data class BilldRoom(val roomId: String, val title: String, val host: String)

/** Tokens must be issued by the application API; secrets never belong in the APK. */
interface SessionTokenProvider {
    suspend fun liveKitToken(roomId: String): String
    suspend fun openImToken(userId: String): String
}
