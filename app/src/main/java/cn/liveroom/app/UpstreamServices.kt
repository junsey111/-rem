package cn.liveroom.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import io.socket.client.IO
import io.socket.client.Socket
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class UpstreamServices(
    val billdApiUrl: String = BuildConfig.BILLD_API_URL,
    val srsUrl: String = BuildConfig.SRS_URL,
    val srsPlaybackUrl: String = BuildConfig.SRS_PLAYBACK_URL,
    val liveKitUrl: String = BuildConfig.LIVEKIT_URL,
    val openImApiUrl: String = BuildConfig.OPENIM_API_URL,
    val openImWsUrl: String = BuildConfig.OPENIM_WS_URL,
)

data class UserSession(val id: Long, val username: String, val token: String)
data class BilldRoom(val roomId: String, val title: String, val host: String, val creatorId: Long)
data class Gift(val id: String, val name: String, val coinPrice: Int, val diamonds: Int)
data class Wallet(val balance: Long)
data class UserLevel(val viewerLevel: Int, val creatorLevel: Int, val viewerPoints: Long, val creatorPoints: Long)
data class Earnings(val diamonds: Long, val gifts: Long)
data class RankingEntry(val userId: Long, val value: Long, val gifts: Long)
data class LiveKitCredentials(val url: String, val token: String)
data class OpenImCredentials(val userId: String, val token: String, val apiUrl: String, val wsUrl: String)

/** Authenticated mobile API for the Billd application gateway and its Revenue service. */
class LivePlatformApi(private val baseUrl: String = BuildConfig.BILLD_API_URL) {
    suspend fun register(username: String, password: String): UserSession = withContext(Dispatchers.IO) {
        val data = request("/user/create", "POST", body = JSONObject()
            .put("username", username.trim())
            .put("password", password))
        val id = data.optLong("id")
        check(id > 0) { "注册成功，但服务端没有返回账号 ID" }
        UserSession(id, data.optString("username", username.trim()), "")
    }

    suspend fun login(userId: Long, password: String): UserSession = withContext(Dispatchers.IO) {
        val value = requestValue("/user/login", "POST", body = JSONObject()
            .put("id", userId)
            .put("password", password))
        val token = value as? String ?: error("登录响应中没有 JWT")
        check(token.isNotBlank()) { "登录响应中没有 JWT" }
        UserSession(userId, "用户$userId", token)
    }

    suspend fun liveRooms(): List<BilldRoom> = withContext(Dispatchers.IO) {
        val data = request("/live/list")
        val rows = data.optJSONArray("rows") ?: JSONArray()
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val details = jsonObject(row.opt("data"))
                val roomId = row.optString("roomId").ifBlank { details.optString("roomId") }
                if (roomId.isNotBlank()) add(
                    BilldRoom(
                        roomId = roomId,
                        title = details.optString("roomName").ifBlank { "直播间 $roomId" },
                        host = details.optString("username").ifBlank { "主播" },
                        creatorId = details.optLong("userId", row.optLong("userId")),
                    )
                )
            }
        }
    }

    suspend fun giftCatalog(): List<Gift> = withContext(Dispatchers.IO) {
        val rows = request("/revenue/gifts").optJSONArray("gifts") ?: JSONArray()
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                add(Gift(
                    id = row.optString("gift_id"),
                    name = row.optString("display_name"),
                    coinPrice = row.optInt("coin_price"),
                    diamonds = row.optInt("diamond_reward"),
                ))
            }
        }
    }

    suspend fun wallet(token: String): Wallet = withContext(Dispatchers.IO) {
        Wallet(request("/revenue/wallet", token = token).optLong("balance"))
    }

    suspend fun level(token: String): UserLevel = withContext(Dispatchers.IO) {
        val data = request("/revenue/level", token = token)
        UserLevel(
            viewerLevel = data.optInt("viewer_level"),
            creatorLevel = data.optInt("creator_level"),
            viewerPoints = data.optLong("viewer_points"),
            creatorPoints = data.optLong("creator_points"),
        )
    }

    suspend fun earnings(token: String, period: String): Earnings = withContext(Dispatchers.IO) {
        val data = request("/revenue/earnings?period=$period", token = token)
        Earnings(data.optLong("diamonds"), data.optLong("gifts"))
    }

    suspend fun creatorRankings(token: String, period: String): List<RankingEntry> = withContext(Dispatchers.IO) {
        val rows = request("/revenue/rankings/creators?period=$period", token = token).optJSONArray("rankings") ?: JSONArray()
        rows.toRankings("creator_id", "diamonds")
    }

    suspend fun contributorRankings(token: String, period: String): List<RankingEntry> = withContext(Dispatchers.IO) {
        val rows = request("/revenue/rankings/contributors?period=$period", token = token).optJSONArray("rankings") ?: JSONArray()
        rows.toRankings("viewer_id", "coins")
    }

    suspend fun sendGift(token: String, creatorId: Long, liveSessionId: Long, giftId: String, quantity: Int): Wallet = withContext(Dispatchers.IO) {
        val data = request("/revenue/gifts/send", "POST", token, JSONObject()
            .put("creator_id", creatorId)
            .put("live_session_id", liveSessionId)
            .put("gift_id", giftId)
            .put("quantity", quantity))
        Wallet(data.optLong("new_balance"))
    }

    suspend fun startLive(token: String): Long = withContext(Dispatchers.IO) {
        request("/revenue/lives/start", "POST", token, JSONObject())
            .optLong("live_session_id")
            .also { check(it > 0) { "开播接口没有返回直播会话 ID" } }
    }

    suspend fun closeLive(token: String, liveSessionId: Long) = withContext(Dispatchers.IO) {
        request("/revenue/lives/$liveSessionId/close", "POST", token, JSONObject())
    }

    suspend fun liveKitToken(token: String, liveSessionId: Long): LiveKitCredentials = withContext(Dispatchers.IO) {
        val data = request("/livekit/token", "POST", token, JSONObject().put("live_session_id", liveSessionId))
        LiveKitCredentials(data.optString("url"), data.optString("token")).also {
            check(it.url.isNotBlank() && it.token.isNotBlank()) { "LiveKit token 响应不完整" }
        }
    }

    suspend fun rechargeUrl(token: String, coins: Int): String = withContext(Dispatchers.IO) {
        val data = request("/alipay/recharge?coins=$coins", token = token)
        data.optString("payUrl").takeIf(String::isNotBlank) ?: error("充值接口没有返回支付地址")
    }

    suspend fun openImSession(token: String): OpenImCredentials = withContext(Dispatchers.IO) {
        val data = request("/openim/session", "POST", token, JSONObject())
        OpenImCredentials(
            data.optString("user_id"), data.optString("token"),
            data.optString("api_url"), data.optString("ws_url"),
        )
    }

    private fun request(path: String, method: String = "GET", token: String? = null, body: JSONObject? = null): JSONObject =
        jsonObject(requestValue(path, method, token, body))

    private fun requestValue(path: String, method: String = "GET", token: String? = null, body: JSONObject? = null): Any? {
        val connection = URL("${baseUrl.trimEnd('/')}$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Accept", "application/json")
        if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val root = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                val message = root.optString("message").ifBlank { root.optString("msg") }.ifBlank { "HTTP $status" }
                error(message)
            }
            return root.opt("data")?.takeUnless { it == JSONObject.NULL } ?: root
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONArray.toRankings(idKey: String, valueKey: String): List<RankingEntry> = buildList {
        for (i in 0 until length()) {
            val row = optJSONObject(i) ?: continue
            add(RankingEntry(row.optLong(idKey), row.optLong(valueKey), row.optLong("gifts")))
        }
    }

    private fun jsonObject(value: Any?): JSONObject = when (value) {
        is JSONObject -> value
        is String -> runCatching { JSONObject(value) }.getOrDefault(JSONObject())
        else -> JSONObject()
    }
}

/** Registers a live room in Billd's Socket.IO directory and stays connected until the host ends it. */
class BilldRoomRegistration(private val baseUrl: String = BuildConfig.BILLD_API_URL) {
    private var socket: Socket? = null

    suspend fun register(token: String, userId: Long, username: String, roomId: String, roomName: String) {
        val activeSocket = IO.socket(baseUrl, IO.Options.builder()
            .setAuth(mapOf("token" to token))
            .setReconnection(false)
            .build())
        socket = activeSocket
        suspendCancellableCoroutine { continuation ->
            val finished = AtomicBoolean(false)
            activeSocket.once(Socket.EVENT_CONNECT) {
                val payload = JSONObject()
                    .put("roomId", roomId)
                    .put("roomName", roomName)
                    .put("isAdmin", true)
                    .put("data", JSONObject().put("userId", userId).put("username", username))
                activeSocket.emit("join", payload)
            }
            activeSocket.once("joined") {
                if (finished.compareAndSet(false, true)) continuation.resume(Unit)
            }
            activeSocket.once(Socket.EVENT_CONNECT_ERROR) { args ->
                if (finished.compareAndSet(false, true)) {
                    continuation.resumeWithException(IllegalStateException(args.firstOrNull()?.toString() ?: "Socket.IO 连接失败"))
                }
            }
            continuation.invokeOnCancellation { close() }
            activeSocket.connect()
        }
    }

    fun close() {
        socket?.let {
            it.emit("leave", JSONObject())
            it.disconnect()
            it.close()
        }
        socket = null
    }
}
