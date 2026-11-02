package cn.liveroom.app

import android.content.Context
import org.json.JSONObject
import java.util.UUID

data class PendingGift(val payload: String, val giftName: String, val coins: Int, val hostName: String) {
    companion object {
        fun create(creatorId: Long, liveId: Long, gift: Gift, matchId: Long?, hostName: String) = PendingGift(
            JSONObject().put("request_id", UUID.randomUUID().toString())
                .put("creator_id", creatorId).put("live_session_id", liveId)
                .put("gift_id", gift.id).put("quantity", 1)
                .apply { if (matchId != null) put("match_id", matchId) }.toString(),
            gift.name, gift.coinPrice, hostName,
        )
    }
}

class GiftRejectedException(message: String) : IllegalStateException(message)

/** One unresolved payment intent per account and backend, retained across restarts. */
class GiftOutbox(context: Context, userId: Long) {
    private val prefs = context.getSharedPreferences("gift_outbox", Context.MODE_PRIVATE)
    private val key = "${BuildConfig.BILLD_API_URL}:$userId"

    fun load(): PendingGift? = prefs.getString(key, null)?.let {
        val data = JSONObject(it)
        PendingGift(data.getString("payload"), data.getString("giftName"), data.getInt("coins"), data.getString("hostName"))
    }

    fun save(gift: PendingGift) {
        check(load() == null) { "请先核实上一笔送礼" }
        check(prefs.edit().putString(key, JSONObject().put("payload", gift.payload)
            .put("giftName", gift.giftName).put("coins", gift.coins).put("hostName", gift.hostName).toString()).commit()) {
            "无法保存送礼请求，请检查手机存储空间"
        }
    }

    fun clear() {
        check(prefs.edit().remove(key).commit()) { "送礼结果已确认，但本地记录未能更新，请再次核实" }
    }
}
