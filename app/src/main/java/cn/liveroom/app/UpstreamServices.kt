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
    val srsIngestUrl: String = BuildConfig.SRS_RTMP_URL,
    val liveKitUrl: String = BuildConfig.LIVEKIT_URL,
    val openImApiUrl: String = BuildConfig.OPENIM_API_URL,
    val openImWsUrl: String = BuildConfig.OPENIM_WS_URL,
)

data class UserSession(val id: Long, val username: String, val token: String)
data class BilldRoom(val roomId: String, val title: String, val host: String, val creatorId: Long, val coverPath: String? = null)
data class Gift(val id: String, val name: String, val coinPrice: Int, val diamonds: Int)
data class Wallet(val balance: Long)
data class UserLevel(val viewerLevel: Int, val creatorLevel: Int, val viewerPoints: Long, val creatorPoints: Long)
data class Earnings(val diamonds: Long, val gifts: Long)
data class RankingEntry(val userId: Long, val value: Long, val gifts: Long)
data class LiveKitCredentials(val url: String, val token: String)
data class OpenImCredentials(val userId: String, val token: String, val apiUrl: String, val wsUrl: String)
data class ChatRecipient(val userId: String, val name: String)
data class MatchScore(val matchId: Long, val creatorId: Long, val points: Long)
data class RechargeOrder(val id: String, val coins: Int, val amountFen: Int, val status: String)
data class GiftReceipt(val id: String, val requestId: String, val creatorId: Long, val liveSessionId: Long, val matchId: Long, val giftId: String, val giftName: String, val quantity: Int, val chargedCoins: Long, val status: String, val rejectCode: String, val createdAt: String)
data class GiftHistory(val orders: List<GiftReceipt>, val nextCursor: String)
class PlatformHttpException(val statusCode: Int, message: String) : IllegalStateException(message)
class ModerationRejectedException(message: String) : IllegalStateException(message)

data class UserProfile(val age: Int?, val region: String?, val source: String)

data class OperationsSummary(val users: Long, val disabledUsers: Long, val listedRooms: Long, val pendingOrders: Long, val creditedOrders: Long, val observedAt: String)
data class OperationsUser(val id: Long, val username: String, val status: Int)
data class OperationsUsers(val users: List<OperationsUser>, val total: Long, val page: Int, val pageSize: Int)
data class ModerationLive(val id: String, val creatorId: String, val startedAt: String)
data class ModerationLives(val lives: List<ModerationLive>, val nextCursor: String)
data class ModerationAction(val id: String, val requestId: String, val operatorId: String, val liveId: String, val reason: String, val createdAt: String)
data class ModerationActions(val actions: List<ModerationAction>, val nextCursor: String)

data class MatchInvitation(val invitationId: Long, val matchId: Long, val liveSessionId: Long, val opponentLiveSessionId: Long, val creatorId: Long, val opponentCreatorId: Long, val status: String)

/** Authenticated mobile API for the Billd application gateway and its Revenue service. */
class LivePlatformApi(private val baseUrl: String = BuildConfig.BILLD_API_URL) {
    suspend fun liveRecipient(token: String, roomId: String): ChatRecipient = withContext(Dispatchers.IO) {
        val data = request("/openim/rooms/${URLEncoder.encode(roomId, "UTF-8")}/recipient", "POST", token, JSONObject())
        ChatRecipient(data.getString("user_id"), data.getString("name"))
    }
    suspend fun moderationLives(token: String, before: String = ""): ModerationLives = withContext(Dispatchers.IO) {
        val data = request("/operations/lives" + if (before.isBlank()) "" else "?before_id=$before", token = token)
        val rows = data.getJSONArray("lives")
        ModerationLives((0 until rows.length()).map { i -> rows.getJSONObject(i).let {
            ModerationLive(it.getString("live_session_id"), it.getString("creator_id"), it.getString("started_at"))
        } }, data.getString("next_before_id"))
    }

    private fun parseModeration(data: JSONObject) = ModerationAction(data.getString("action_id"), data.getString("request_id"),
        data.getString("operator_id"), data.getString("live_session_id"), data.getString("reason"), data.getString("created_at"))

    suspend fun moderationActions(token: String, before: String = ""): ModerationActions = withContext(Dispatchers.IO) {
        val data = request("/operations/actions" + if (before.isBlank()) "" else "?before_id=$before", token = token)
        val rows = data.getJSONArray("actions")
        ModerationActions((0 until rows.length()).map { parseModeration(rows.getJSONObject(it)) }, data.getString("next_before_id"))
    }

    suspend fun moderationReceipt(token: String, requestId: String): ModerationAction? = withContext(Dispatchers.IO) {
        try { parseModeration(request("/operations/actions/${URLEncoder.encode(requestId, "UTF-8")}", token = token)) }
        catch (error: PlatformHttpException) { if (error.statusCode == 404) null else throw error }
    }

    suspend fun forceCloseLive(token: String, liveId: String, requestId: String, reason: String): ModerationAction = withContext(Dispatchers.IO) {
        parseModeration(request("/operations/lives/$liveId/close", "POST", token,
            JSONObject().put("request_id", requestId).put("reason", reason)))
    }

    suspend fun canManageOperations(token: String): Boolean = withContext(Dispatchers.IO) {
        request("/operations/access", token = token).getBoolean("can_manage")
    }

    suspend fun operationsSummary(token: String): OperationsSummary = withContext(Dispatchers.IO) {
        val data = request("/operations/summary", token = token)
        OperationsSummary(data.getLong("users"), data.getLong("disabled_users"), data.getLong("listed_rooms"),
            data.getLong("pending_orders"), data.getLong("credited_orders"), data.getString("observed_at"))
    }

    suspend fun operationsUsers(token: String, page: Int): OperationsUsers = withContext(Dispatchers.IO) {
        val data = request("/operations/users?page=$page", token = token)
        val rows = data.getJSONArray("users")
        OperationsUsers((0 until rows.length()).map { index ->
            val user = rows.getJSONObject(index)
            OperationsUser(user.getLong("id"), user.getString("username"), user.getInt("status"))
        }, data.getLong("total"), data.getInt("page"), data.getInt("page_size"))
    }

    suspend fun profile(token: String): UserProfile = withContext(Dispatchers.IO) {
        parseProfile(request("/revenue/profile", token = token))
    }

    suspend fun saveProfile(token: String, age: Int, region: String): UserProfile = withContext(Dispatchers.IO) {
        parseProfile(request("/revenue/profile", "PUT", token, JSONObject().put("age_years", age).put("region_code", region)))
    }

    private fun parseProfile(data: JSONObject) = UserProfile(
        if (data.isNull("age_years")) null else data.optInt("age_years"),
        if (data.isNull("region_code")) null else data.optString("region_code"), data.optString("source"),
    )
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
        currentAccount(token).also { check(it.id == userId) { "账号信息不符，请重新登录" } }
    }

    suspend fun currentAccount(token: String): UserSession = withContext(Dispatchers.IO) {
        val data = request("/user/me", token = token)
        UserSession(data.getLong("id"), data.getString("username"), token)
    }

    suspend fun changePassword(token: String, oldPassword: String, newPassword: String) = withContext(Dispatchers.IO) {
        request("/user/password", "POST", token, JSONObject().put("oldpwd", oldPassword).put("newpwd", newPassword))
        Unit
    }

    suspend fun logout(token: String) = withContext(Dispatchers.IO) {
        request("/user/logout", "POST", token, JSONObject())
        Unit
    }

    suspend fun liveRooms(): List<BilldRoom> = withContext(Dispatchers.IO) {
        parseLiveDirectory(request("/live/list"))
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

    suspend fun giftHistory(token: String, before: String = ""): GiftHistory = withContext(Dispatchers.IO) {
        val data = request("/revenue/gift-orders" + if (before.isBlank()) "" else "?before_id=$before", token = token)
        val rows = data.getJSONArray("orders")
        GiftHistory((0 until rows.length()).map { parseGiftReceipt(rows.getJSONObject(it)) }, data.getString("next_before_id"))
    }

    private fun parseGiftReceipt(data: JSONObject) = GiftReceipt(data.getString("gift_order_id"), data.getString("request_id"),
        data.getString("creator_id").toLong(), data.getString("live_session_id").toLong(), data.getString("match_id").toLong(), data.getString("gift_id"), data.getString("gift_name"),
        data.getInt("quantity"), data.getLong("charged_coins"), data.getString("status"), data.getString("reject_code"), data.getString("created_at"))

    suspend fun giftReceipt(token: String, requestId: String): GiftReceipt? = withContext(Dispatchers.IO) {
        try { parseGiftReceipt(request("/revenue/gift-orders/${URLEncoder.encode(requestId, "UTF-8")}", token = token))
        } catch (error: PlatformHttpException) { if (error.statusCode == 404) null else throw error }
    }

    suspend fun recoverGift(token: String, submission: PendingGift): Wallet {
        val payload = JSONObject(submission.payload)
        val receipt = giftReceipt(token, payload.getString("request_id"))
        if (receipt != null) {
            check(receipt.creatorId == payload.getLong("creator_id") && receipt.liveSessionId == payload.getLong("live_session_id") &&
                receipt.matchId == payload.optLong("match_id") && receipt.giftId == payload.getString("gift_id") && receipt.quantity == payload.getInt("quantity")) { "送礼记录与待确认请求不符，请保留单号联系客服" }
            if (receipt.status == "REJECTED") throw GiftRejectedException("服务端记录：本次未赠送、未扣币")
            check(receipt.status == "ACCEPTED") { "送礼结果尚未确认" }
            return wallet(token)
        }
        // A missing row is inconclusive. Resubmit the exact original idempotent intent.
        return sendGift(token, submission)
    }

    suspend fun sendGift(token: String, submission: PendingGift): Wallet = withContext(Dispatchers.IO) {
        val data = request("/revenue/gifts/send", "POST", token, JSONObject(submission.payload))
        check(data.optBoolean("accepted") && data.has("new_balance")) { "送礼结果尚未确认，请重试核实" }
        Wallet(data.getLong("new_balance"))
    }

    suspend fun activeMatch(token: String, liveSessionId: Long): Long? = withContext(Dispatchers.IO) {
        request("/revenue/lives/$liveSessionId/match", token = token).optLong("match_id").takeIf { it > 0 }
    }

    suspend fun closeMatch(token: String, matchId: Long) = withContext(Dispatchers.IO) {
        request("/revenue/matches/$matchId/close", "POST", token, JSONObject())
    }

    suspend fun inviteMatch(token: String, liveSessionId: Long, opponentLiveSessionId: Long): MatchInvitation = withContext(Dispatchers.IO) {
        val data = request("/revenue/match-invitations", "POST", token, JSONObject()
            .put("live_session_id", liveSessionId)
            .put("opponent_live_session_id", opponentLiveSessionId))
        MatchInvitation(data.optLong("invitation_id"), data.optLong("match_id"), liveSessionId, opponentLiveSessionId, 0, 0, data.optString("status"))
    }

    suspend fun matchInvitations(token: String): List<MatchInvitation> = withContext(Dispatchers.IO) {
        val rows = request("/revenue/match-invitations", token = token).optJSONArray("invitations") ?: JSONArray()
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                add(MatchInvitation(row.optLong("invitation_id"), row.optLong("match_id"), row.optLong("live_session_id"), row.optLong("opponent_live_session_id"), row.optLong("creator_id"), row.optLong("opponent_creator_id"), row.optString("status")))
            }
        }
    }

    suspend fun respondMatchInvitation(token: String, invitationId: Long, accept: Boolean): MatchInvitation = withContext(Dispatchers.IO) {
        val data = request("/revenue/match-invitations/$invitationId/respond", "POST", token, JSONObject().put("accept", accept))
        MatchInvitation(data.optLong("invitation_id"), data.optLong("match_id"), data.optLong("live_session_id"), data.optLong("opponent_live_session_id"), data.optLong("creator_id"), data.optLong("opponent_creator_id"), data.optString("status"))
    }

    suspend fun matchScore(token: String, matchId: Long): List<MatchScore> = withContext(Dispatchers.IO) {
        val rows = request("/revenue/matches/$matchId/score", token = token).optJSONArray("scores") ?: JSONArray()
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                add(MatchScore(matchId, row.optLong("creator_id"), row.optLong("points")))
            }
        }
    }

    suspend fun startLive(token: String): Long = withContext(Dispatchers.IO) {
        request("/revenue/lives/start", "POST", token, JSONObject())
            .optLong("live_session_id")
            .also { check(it > 0) { "开播接口没有返回直播会话 ID" } }
    }

    suspend fun closeLive(token: String, liveSessionId: Long) = withContext(Dispatchers.IO) {
        request("/revenue/lives/$liveSessionId/close", "POST", token, JSONObject())
    }

    suspend fun watchAccess(token: String, liveId: Long): WatchAccess = withContext(Dispatchers.IO) {
        parseWatchAccess(request("/revenue/lives/$liveId/watch-access", token = token))
    }

    suspend fun watchHeartbeat(token: String, liveId: Long, requestId: String, deltaSeconds: Int): WatchAccess = withContext(Dispatchers.IO) {
        require(requestId.isNotBlank()) { "观看心跳单号无效" }
        require(deltaSeconds in 1..30) { "观看心跳时长无效" }
        parseWatchAccess(request("/revenue/lives/$liveId/watch-heartbeat", "POST", token,
            JSONObject().put("request_id", requestId).put("delta_seconds", deltaSeconds)))
    }

    suspend fun watchUnlock(token: String, liveId: Long, requestId: String): WatchAccess = withContext(Dispatchers.IO) {
        require(requestId.isNotBlank()) { "解锁单号无效" }
        parseWatchAccess(request("/revenue/lives/$liveId/watch-unlock", "POST", token,
            JSONObject().put("request_id", requestId)))
    }

    suspend fun liveKitToken(token: String, liveSessionId: Long, matchId: Long? = null): LiveKitCredentials = withContext(Dispatchers.IO) {
        val data = request("/livekit/token", "POST", token, JSONObject().put("live_session_id", liveSessionId).apply { if (matchId != null) put("match_id", matchId) })
        LiveKitCredentials(data.optString("url"), data.optString("token")).also {
            check(it.url.isNotBlank() && it.token.isNotBlank()) { "LiveKit token 响应不完整" }
        }
    }

    suspend fun rechargeUrl(token: String, coins: Int): String = withContext(Dispatchers.IO) {
        val data = request("/alipay/recharge?coins=$coins", token = token)
        data.optString("payUrl").takeIf(String::isNotBlank) ?: error("充值接口没有返回支付地址")
    }

    suspend fun rechargeOrders(token: String): List<RechargeOrder> = withContext(Dispatchers.IO) {
        val rows = request("/alipay/orders", token = token).optJSONArray("orders") ?: JSONArray()
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                add(RechargeOrder(row.optString("requestId"), row.optInt("coins"), row.optInt("amountFen"), row.optString("status")))
            }
        }
    }

    suspend fun cohosts(token: String, liveId: Long): List<CohostRequest> = withContext(Dispatchers.IO) {
        val data = request("/revenue/cohosts/$liveId", token = token)
        val rows = data.optJSONArray("requests") ?: JSONArray()
        buildList { for (i in 0 until rows.length()) { val row=rows.getJSONObject(i); add(CohostRequest(row.getLong("cohost_id"),row.getString("request_id"),row.getLong("live_session_id"),row.getLong("user_id"),row.getString("mode"),row.getString("status"))) } }
    }
    suspend fun requestCohost(token: String, liveId: Long, mode: String) = withContext(Dispatchers.IO) { request("/revenue/cohosts/$liveId", "POST", token, JSONObject().put("request_id", UUID.randomUUID().toString()).put("mode", mode)) }
    suspend fun cohostAction(token: String, liveId: Long, id: Long, action: String) = withContext(Dispatchers.IO) { request("/revenue/cohosts/$liveId/$id", "POST", token, JSONObject().put("request_id", UUID.randomUUID().toString()).put("action", action)) }

    suspend fun uploadCover(token: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        require(bytes.size <= 3*1024*1024){"图片不能超过 3MB"}
        val connection=URL("${baseUrl.trimEnd('/')}/live/cover").openConnection() as HttpURLConnection
        try{connection.requestMethod="PUT";connection.connectTimeout=8000;connection.readTimeout=15000;connection.doOutput=true
            connection.setRequestProperty("Authorization","Bearer $token");connection.setRequestProperty("Content-Type","application/octet-stream")
            connection.outputStream.use{it.write(bytes)}
            val status=connection.responseCode
            val raw=(if(status in 200..299)connection.inputStream else connection.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
            val result=JSONObject(raw);if(status !in 200..299)throw PlatformHttpException(status,result.optString("message","上传失败"))
            result.getJSONObject("data").getString("cover_path")
        }finally{connection.disconnect()}
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

    suspend fun businessRequest(token: String, path: String, method: String = "GET", body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        require(path.startsWith("/revenue/") || path.startsWith("/verification/") || path.startsWith("/rooms/"))
        request(path, method, token, body)
    }

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
                if (root.optString("gift_outcome") == "REJECTED") throw GiftRejectedException(message)
                if (status in listOf(400, 404) && root.optString("moderation_outcome") == "REJECTED") throw ModerationRejectedException(message)
                throw PlatformHttpException(status, message)
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
            activeSocket.once("joinRejected") { args ->
                if (finished.compareAndSet(false, true)) {
                    val message = (args.firstOrNull() as? JSONObject)?.optString("message").orEmpty()
                    continuation.resumeWithException(IllegalStateException(message.ifBlank { "直播间注册未通过，请重试" }))
                }
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
