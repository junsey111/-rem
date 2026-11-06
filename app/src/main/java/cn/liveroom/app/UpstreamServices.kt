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
data class Gift(
    val id: String,
    val name: String,
    val coinPrice: Int,
    val diamonds: Int,
    val effectType: String = EFFECT_NONE,
    val effectUrl: String = "",
    val effectVideoMode: String = "",
    val effectMd5: String = "",
    val iconUrl: String = "",
)
data class Wallet(val balance: Long)
data class UserLevel(
    val viewerLevel: Int,
    val creatorLevel: Int,
    val viewerPoints: Long,
    val creatorPoints: Long,
    val viewerNextLevelPoints: Long,
    val creatorNextLevelPoints: Long,
)
data class Earnings(val diamonds: Long, val gifts: Long)
data class CreatorLiveSession(val id: String, val status: String, val startedAt: String, val durationSeconds: Long)
data class CreatorSupporter(val userId: Long, val giftCount: Long, val coins: Long)
data class RankingEntry(val userId: Long, val value: Long, val gifts: Long)
data class LiveKitCredentials(val url: String, val token: String)
data class DmCallCredentials(
    val callId: String,
    val room: String,
    val mode: String,
    val callerId: String,
    val peerId: String,
    val url: String,
    val token: String,
    val expiresIn: Int,
    val signalTransport: String,
    val signalText: String,
)
data class OpenImCredentials(val userId: String, val token: String, val apiUrl: String, val wsUrl: String)
data class ChatRecipient(val userId: String, val name: String)
data class MatchScore(val matchId: Long, val creatorId: Long, val points: Long)
data class RechargeOrder(val id: String, val coins: Int, val amountFen: Int, val status: String)
data class GiftReceipt(val id: String, val requestId: String, val creatorId: Long, val liveSessionId: Long, val matchId: Long, val giftId: String, val giftName: String, val quantity: Int, val chargedCoins: Long, val status: String, val rejectCode: String, val createdAt: String)
data class GiftHistory(val orders: List<GiftReceipt>, val nextCursor: String)
class PlatformHttpException(val statusCode: Int, message: String, val errorCode: String? = null) : IllegalStateException(message)
class ModerationRejectedException(message: String) : IllegalStateException(message)

data class UserProfile(val age: Int?, val region: String?, val source: String, val gender: String? = null)
data class IdentityVerificationStatus(val enabled: Boolean, val requestId: String?, val status: String?)

data class OperationsSummary(val users: Long, val disabledUsers: Long, val listedRooms: Long, val pendingOrders: Long, val creditedOrders: Long, val observedAt: String)
data class OperationsUser(val id: Long, val username: String, val status: Int)
data class OperationsUsers(val users: List<OperationsUser>, val total: Long, val page: Int, val pageSize: Int)
data class PublicUser(val id: Long, val username: String, val avatar: String?, val description: String?, val isFollowing: Boolean = false)
data class UserRelations(val users: List<PublicUser>, val nextCursor: String)
data class MomentPost(val id: String, val userId: Long, val username: String, val content: String, val createdAt: String, val likeCount: Long, val isLiked: Boolean, val commentCount: Long = 0)
data class MomentComment(val id: String, val userId: Long, val username: String, val content: String, val createdAt: String, val isMine: Boolean)
data class MomentDailyProgress(val published: Int, val liked: Int, val commented: Int)
data class DailyCheckin(val today: String, val checkedIn: Boolean, val streakDays: Int, val rewardConfigured: Boolean)
data class CloseFriendUser(val id: Long, val username: String, val avatar: String?)
data class CloseFriendRelations(val friends: List<CloseFriendUser>, val incoming: List<CloseFriendUser>, val outgoing: List<CloseFriendUser>)
data class ProfileVisitor(val user: PublicUser, val lastSeenAt: String)
data class NotificationPreferences(val messages: Boolean, val visitors: Boolean, val announcements: Boolean, val marketing: Boolean)
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

    suspend fun searchUsers(token: String, query: String): List<PublicUser> = withContext(Dispatchers.IO) {
        val rows = request("/user/search", "POST", token, JSONObject().put("query", query)).getJSONArray("users")
        (0 until rows.length()).mapNotNull { index -> rows.optJSONObject(index)?.let { user ->
            PublicUser(user.getLong("id"), user.getString("username"), user.optString("avatar").takeIf(String::isNotBlank),
                user.optString("desc").takeIf(String::isNotBlank), user.optBoolean("is_following"))
        } }
    }

    suspend fun recommendedUsers(token: String, gender: String? = null, ageMin: Int? = null, ageMax: Int? = null, sameRegion: Boolean = false): List<PublicUser> = withContext(Dispatchers.IO) {
        val query = buildList {
            gender?.let { add("gender=${URLEncoder.encode(it, "UTF-8")}") }
            ageMin?.let { add("age_min=$it") }
            ageMax?.let { add("age_max=$it") }
            if (sameRegion) add("region=same")
        }.joinToString("&")
        val rows = request("/user/recommendations" + if (query.isEmpty()) "" else "?$query", token = token).getJSONArray("users")
        (0 until rows.length()).mapNotNull { index -> rows.optJSONObject(index)?.let { user ->
            PublicUser(user.getLong("id"), user.getString("username"), user.optString("avatar").takeIf(String::isNotBlank),
                user.optString("desc").takeIf(String::isNotBlank), user.optBoolean("is_following"))
        } }
    }

    suspend fun recordProfileVisit(token: String, userId: Long) = withContext(Dispatchers.IO) {
        request("/user/visits", "POST", token, JSONObject().put("user_id", userId))
        Unit
    }

    suspend fun profileVisitors(token: String): List<ProfileVisitor> = withContext(Dispatchers.IO) {
        val rows = request("/user/visitors", token = token).getJSONArray("visitors")
        (0 until rows.length()).mapNotNull { index -> rows.optJSONObject(index)?.let { row ->
            val user = row.optJSONObject("user") ?: return@let null
            ProfileVisitor(PublicUser(user.getLong("id"), user.getString("username"), user.optString("avatar").takeIf(String::isNotBlank), null), row.optString("last_seen_at"))
        } }
    }

    suspend fun relations(token: String, type: String, beforeId: String = ""): UserRelations = withContext(Dispatchers.IO) {
        require(type in setOf("following", "followers", "friends"))
        require(beforeId.isBlank() || beforeId.matches(Regex("[1-9][0-9]{0,19}")))
        val path = "/user/relations?type=$type" + if (beforeId.isBlank()) "" else "&before_id=$beforeId"
        val data = request(path, token = token)
        val rows = data.getJSONArray("users")
        UserRelations((0 until rows.length()).mapNotNull { index -> rows.optJSONObject(index)?.let { user ->
            PublicUser(user.getLong("id"), user.getString("username"), user.optString("avatar").takeIf(String::isNotBlank),
                user.optString("desc").takeIf(String::isNotBlank))
        } }, data.optString("next_before_id"))
    }

    suspend fun setFollowing(token: String, userId: Long, following: Boolean) = withContext(Dispatchers.IO) {
        request("/user/${if (following) "follow" else "unfollow"}", "POST", token, JSONObject().put("user_id", userId))
        Unit
    }

    suspend fun blockedUsers(token: String): List<PublicUser> = withContext(Dispatchers.IO) {
        val rows = request("/user/blocks", token = token).getJSONArray("users")
        (0 until rows.length()).mapNotNull { index -> rows.optJSONObject(index)?.optJSONObject("user")?.let { user ->
            PublicUser(user.getLong("id"), user.getString("username"), user.optString("avatar").takeIf(String::isNotBlank), user.optString("desc").takeIf(String::isNotBlank))
        } }
    }

    suspend fun setBlocked(token: String, userId: Long, blocked: Boolean) = withContext(Dispatchers.IO) {
        request(if (blocked) "/user/blocks" else "/user/blocks/$userId", if (blocked) "POST" else "DELETE", token,
            if (blocked) JSONObject().put("user_id", userId) else null)
        Unit
    }

    suspend fun notificationPreferences(token: String): NotificationPreferences = withContext(Dispatchers.IO) {
        parseNotificationPreferences(request("/user/notification-preferences", token = token).getJSONObject("preferences"))
    }

    suspend fun saveNotificationPreference(token: String, key: String, enabled: Boolean): NotificationPreferences = withContext(Dispatchers.IO) {
        require(key in setOf("messages", "visitors", "announcements", "marketing"))
        parseNotificationPreferences(request("/user/notification-preferences", "PUT", token, JSONObject().put(key, enabled)).getJSONObject("preferences"))
    }

    private fun parseNotificationPreferences(data: JSONObject) = NotificationPreferences(
        data.getBoolean("messages"), data.getBoolean("visitors"), data.getBoolean("announcements"), data.getBoolean("marketing"),
    )

    suspend fun moments(token: String, scope: String, beforeId: String = "", userId: Long? = null): Pair<List<MomentPost>, String> = withContext(Dispatchers.IO) {
        require(scope in setOf("all", "following", "mine", "user"))
        require((scope == "user") == (userId != null) && (userId == null || userId > 0))
        val path = "/moments?scope=$scope" + (userId?.let { "&user_id=$it" } ?: "") + if (beforeId.isBlank()) "" else "&before_id=$beforeId"
        val data = request(path, token = token)
        val rows = data.getJSONArray("moments")
        val posts = (0 until rows.length()).mapNotNull { index -> rows.optJSONObject(index)?.let { row ->
            val author = row.optJSONObject("author") ?: return@let null
            MomentPost(row.getString("id"), row.getLong("user_id"), author.getString("username"), row.getString("content"),
                row.optString("created_at"), row.optLong("like_count"), row.optBoolean("is_liked"), row.optLong("comment_count"))
        } }
        posts to data.optString("next_before_id")
    }

    suspend fun momentDailyProgress(token: String): MomentDailyProgress = withContext(Dispatchers.IO) {
        val data = request("/moments/tasks/today", token = token)
        MomentDailyProgress(data.optInt("published"), data.optInt("liked"), data.optInt("commented"))
    }

    suspend fun dailyCheckin(token: String): DailyCheckin = withContext(Dispatchers.IO) {
        parseDailyCheckin(request("/moments/tasks/checkin", token = token))
    }

    suspend fun submitDailyCheckin(token: String): DailyCheckin = withContext(Dispatchers.IO) {
        parseDailyCheckin(request("/moments/tasks/checkin", "POST", token, JSONObject()))
    }

    private fun parseDailyCheckin(data: JSONObject) = DailyCheckin(data.getString("today"), data.getBoolean("checked_in"),
        data.getInt("streak_days"), data.optBoolean("reward_configured"))

    suspend fun closeFriendRelations(token: String): CloseFriendRelations = withContext(Dispatchers.IO) {
        val data = request("/close-friends", token = token)
        fun parse(key: String): List<CloseFriendUser> {
            val rows = data.optJSONArray(key) ?: return emptyList()
            return (0 until rows.length()).mapNotNull { index -> rows.optJSONObject(index)?.optJSONObject("user")?.let { user ->
                CloseFriendUser(user.getLong("id"), user.getString("username"), user.optString("avatar").takeIf(String::isNotBlank))
            } }
        }
        CloseFriendRelations(parse("friends"), parse("incoming"), parse("outgoing"))
    }

    suspend fun inviteCloseFriend(token: String, userId: Long) = withContext(Dispatchers.IO) {
        require(userId > 0)
        request("/close-friends", "POST", token, JSONObject().put("user_id", userId))
        Unit
    }

    suspend fun acceptCloseFriend(token: String, userId: Long) = withContext(Dispatchers.IO) {
        require(userId > 0)
        request("/close-friends/$userId/accept", "POST", token, JSONObject())
        Unit
    }

    suspend fun removeCloseFriend(token: String, userId: Long) = withContext(Dispatchers.IO) {
        require(userId > 0)
        request("/close-friends/$userId", "DELETE", token)
        Unit
    }

    suspend fun publishMoment(token: String, content: String): MomentPost = withContext(Dispatchers.IO) {
        val row = request("/moments", "POST", token, JSONObject().put("content", content))
        MomentPost(row.getString("id"), row.getLong("user_id"), "我", row.getString("content"), row.optString("created_at"), 0, false)
    }

    suspend fun deleteMoment(token: String, id: String) = withContext(Dispatchers.IO) {
        request("/moments/$id", "DELETE", token)
        Unit
    }

    suspend fun likeMoment(token: String, id: String, liked: Boolean) = withContext(Dispatchers.IO) {
        request("/moments/$id/like", if (liked) "PUT" else "DELETE", token)
        Unit
    }

    suspend fun momentComments(token: String, momentId: String, beforeId: String = ""): Pair<List<MomentComment>, String> = withContext(Dispatchers.IO) {
        require(momentId.matches(Regex("[1-9][0-9]{0,19}")))
        val data = request("/moments/$momentId/comments" + if (beforeId.isBlank()) "" else "?before_id=$beforeId", token = token)
        val rows = data.getJSONArray("comments")
        val comments = (0 until rows.length()).mapNotNull { index -> rows.optJSONObject(index)?.let { row ->
            val author = row.optJSONObject("author") ?: return@let null
            MomentComment(row.getString("id"), row.getLong("user_id"), author.getString("username"), row.getString("content"),
                row.optString("created_at"), row.optBoolean("is_mine"))
        } }
        comments to data.optString("next_before_id")
    }

    suspend fun publishMomentComment(token: String, momentId: String, content: String): MomentComment = withContext(Dispatchers.IO) {
        require(momentId.matches(Regex("[1-9][0-9]{0,19}")))
        val row = request("/moments/$momentId/comments", "POST", token, JSONObject().put("content", content)).getJSONObject("comment")
        MomentComment(row.getString("id"), row.getLong("user_id"), row.getJSONObject("author").getString("username"),
            row.getString("content"), row.optString("created_at"), row.optBoolean("is_mine"))
    }

    suspend fun deleteMomentComment(token: String, momentId: String, commentId: String) = withContext(Dispatchers.IO) {
        require(momentId.matches(Regex("[1-9][0-9]{0,19}")) && commentId.matches(Regex("[1-9][0-9]{0,19}")))
        request("/moments/$momentId/comments/$commentId", "DELETE", token)
        Unit
    }

    suspend fun profile(token: String): UserProfile = withContext(Dispatchers.IO) {
        parseProfile(request("/revenue/profile", token = token))
    }

    suspend fun saveProfile(token: String, age: Int, region: String, gender: String): UserProfile = withContext(Dispatchers.IO) {
        parseProfile(request("/revenue/profile", "PUT", token, JSONObject().put("age_years", age).put("region_code", region).put("gender", gender)))
    }

    suspend fun identityVerificationStatus(token: String): IdentityVerificationStatus = withContext(Dispatchers.IO) {
        val data = request("/verification/me", token = token)
        val verification = data.optJSONObject("verification")
        IdentityVerificationStatus(data.optBoolean("enabled"), verification?.optString("request_id"), verification?.optString("status"))
    }

    suspend fun startIdentityVerification(token: String, requestId: String): String = withContext(Dispatchers.IO) {
        request("/verification/start", "POST", token, JSONObject().put("request_id", requestId)).getString("verification_url")
    }

    private fun parseProfile(data: JSONObject) = UserProfile(
        if (data.isNull("age_years")) null else data.optInt("age_years"),
        if (data.isNull("region_code")) null else data.optString("region_code"), data.optString("source"),
        if (data.isNull("gender")) null else data.optString("gender"),
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
                add(parseGiftRow(row))
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
            viewerNextLevelPoints = data.optLong("viewer_next_level_points"),
            creatorNextLevelPoints = data.optLong("creator_next_level_points"),
        )
    }

    suspend fun referralProfile(token: String): ReferralProfile = withContext(Dispatchers.IO) {
        parseReferralProfile(request("/revenue/referrals/me", token = token))
    }

    suspend fun redeemReferral(token: String, requestId: String, code: String): ReferralReceipt = withContext(Dispatchers.IO) {
        parseReferralReceipt(request("/revenue/referrals/redeem", "POST", token, JSONObject()
            .put("request_id", requestId)
            .put("code", code.trim())))
    }

    suspend fun earnings(token: String, period: String): Earnings = withContext(Dispatchers.IO) {
        val data = request("/revenue/earnings?period=$period", token = token)
        Earnings(data.optLong("diamonds"), data.optLong("gifts"))
    }

    suspend fun creatorSessions(token: String): List<CreatorLiveSession> = withContext(Dispatchers.IO) {
        val rows = request("/revenue/sessions", token = token).getJSONArray("sessions")
        (0 until rows.length()).mapNotNull { index -> rows.optJSONObject(index)?.let { row ->
            CreatorLiveSession(row.getString("live_session_id"), row.getString("status"), row.getString("started_at"), row.getLong("duration_seconds"))
        } }
    }

    suspend fun creatorSupporters(token: String): List<CreatorSupporter> = withContext(Dispatchers.IO) {
        val rows = request("/revenue/supporters", token = token).getJSONArray("supporters")
        (0 until rows.length()).mapNotNull { index -> rows.optJSONObject(index)?.let { row ->
            CreatorSupporter(row.getLong("user_id"), row.getLong("gift_count"), row.getLong("coins"))
        } }
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

    suspend fun srsPublishToken(token: String, liveSessionId: Long): String = withContext(Dispatchers.IO) {
        request("/srs/publish-token", "POST", token, JSONObject().put("live_session_id", liveSessionId)).getString("token")
    }

    suspend fun liveKitToken(token: String, liveSessionId: Long, matchId: Long? = null): LiveKitCredentials = withContext(Dispatchers.IO) {
        val data = request("/livekit/token", "POST", token, JSONObject().put("live_session_id", liveSessionId).apply { if (matchId != null) put("match_id", matchId) })
        LiveKitCredentials(data.optString("url"), data.optString("token")).also {
            check(it.url.isNotBlank() && it.token.isNotBlank()) { "LiveKit token 响应不完整" }
        }
    }

    suspend fun startDmCall(token: String, peerId: Long, mode: String): DmCallCredentials = withContext(Dispatchers.IO) {
        parseDmCall(request("/livekit/calls", "POST", token, JSONObject().put("peer_id", peerId).put("mode", mode)))
    }

    suspend fun joinDmCall(token: String, callId: String): DmCallCredentials = withContext(Dispatchers.IO) {
        parseDmCall(request("/livekit/calls/join", "POST", token, JSONObject().put("call_id", callId)))
    }

    suspend fun endDmCall(token: String, callId: String) = withContext(Dispatchers.IO) {
        request("/livekit/calls/end", "POST", token, JSONObject().put("call_id", callId))
    }

    private fun parseDmCall(data: JSONObject): DmCallCredentials {
        val serverUrl = data.optString("url")
        val mediaToken = data.optString("token")
        val callId = data.optString("call_id")
        val mode = data.optString("mode")
        check(serverUrl.isNotBlank() && mediaToken.isNotBlank() && callId.isNotBlank() && (mode == "video" || mode == "voice")) {
            "通话凭证不完整"
        }
        return DmCallCredentials(
            callId = callId,
            room = data.optString("room"),
            mode = mode,
            callerId = data.optString("caller_id"),
            peerId = data.optString("peer_id"),
            url = resolveDmCallUrl(serverUrl, BuildConfig.LIVEKIT_URL),
            token = mediaToken,
            expiresIn = data.optInt("expires_in"),
            signalTransport = data.optString("signal_transport"),
            signalText = data.optString("signal_text"),
        )
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

    suspend fun openImSupport(token: String): ChatRecipient = withContext(Dispatchers.IO) {
        val data = request(SUPPORT_CHAT_PATH, token = token)
        ChatRecipient(data.getString("user_id"), data.getString("name"))
    }

    suspend fun sendDmGift(token: String, recipientId: String, giftId: String, requestId: String): JSONObject = withContext(Dispatchers.IO) {
        request("/openim/dm-gifts", "POST", token, JSONObject()
            .put("request_id", requestId).put("recipient_id", recipientId).put("gift_id", giftId).put("quantity", 1))
    }

    suspend fun verifyDmGift(token: String, requestId: String): JSONObject = withContext(Dispatchers.IO) {
        request("/openim/dm-gifts/${URLEncoder.encode(requestId, "UTF-8")}", token = token)
    }

    suspend fun uploadDmVideo(token: String, recipientId: String, requestId: String, title: String, priceCoins: Int, durationSec: Int, bytes: ByteArray): JSONObject = withContext(Dispatchers.IO) {
        require(bytes.size in 1..(12 * 1024 * 1024)) { "视频不能超过 12MB" }
        val meta = JSONObject().put("request_id", requestId).put("recipient_id", recipientId).put("title", title)
            .put("price_coins", priceCoins).put("duration_sec", durationSec)
        val connection = URL("${baseUrl.trimEnd('/')}/openim/dm-videos").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setRequestProperty("X-Dm-Video", android.util.Base64.encodeToString(meta.toString().toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP))
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            val raw = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val root = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                val message = root.optString("message").ifBlank { "上传失败" }
                if (root.optString("settlement") == "UNAVAILABLE") throw SettlementUnavailableException(message)
                throw PlatformHttpException(status, message)
            }
            root.getJSONObject("data")
        } finally { connection.disconnect() }
    }

    suspend fun dmVideo(token: String, videoId: String): JSONObject = withContext(Dispatchers.IO) {
        request("/openim/dm-videos/${URLEncoder.encode(videoId, "UTF-8")}", token = token)
    }

    suspend fun unlockDmVideo(token: String, videoId: String, requestId: String): JSONObject = withContext(Dispatchers.IO) {
        request("/openim/dm-videos/${URLEncoder.encode(videoId, "UTF-8")}/unlock", "POST", token, JSONObject().put("request_id", requestId))
    }

    suspend fun downloadDmVideo(token: String, videoId: String, dest: java.io.File) = withContext(Dispatchers.IO) {
        require(videoId.matches(Regex("^[0-9a-fA-F-]{36}$"))) { "视频不存在" }
        val connection = URL("${baseUrl.trimEnd('/')}/openim/dm-videos/${URLEncoder.encode(videoId, "UTF-8")}/media").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            val status = connection.responseCode
            if (status !in 200..299) {
                val raw = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val root = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
                val message = root.optString("message").ifBlank { "暂时无法播放" }
                if (root.optString("settlement") == "LOCKED") throw PlatformHttpException(status, message)
                if (root.optString("settlement") == "UNAVAILABLE") throw SettlementUnavailableException(message)
                throw PlatformHttpException(status, message)
            }
            val type = connection.contentType.orEmpty()
            require(type.startsWith("video/")) { "视频格式无法播放" }
            dest.parentFile?.mkdirs()
            val partial = java.io.File(dest.parentFile, dest.name + ".partial")
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= 12 * 1024 * 1024) { "视频不能超过 12MB" }
                        output.write(buffer, 0, n)
                    }
                }
            }
            if (!partial.renameTo(dest)) {
                partial.copyTo(dest, overwrite = true)
                partial.delete()
            }
        } finally { connection.disconnect() }
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
                val errorCode = (root.opt("code") as? String)?.ifBlank { null }
                    ?: (root.opt("errorCode") as? String)?.ifBlank { null }
                if (root.optString("gift_outcome") == "REJECTED") throw GiftRejectedException(message)
                if (root.optString("settlement") == "UNAVAILABLE") throw SettlementUnavailableException(message)
                if (status in listOf(400, 404) && root.optString("moderation_outcome") == "REJECTED") throw ModerationRejectedException(message)
                throw PlatformHttpException(status, message, errorCode)
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
