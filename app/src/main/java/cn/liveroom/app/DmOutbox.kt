package cn.liveroom.app

import android.content.Context
import org.json.JSONObject
import java.util.UUID

/** Delivery memory for one DM gift. The ledger stays on Billd/Revenue; this only retries the same request. */
class DmGiftOutbox(context: Context, userId: Long) {
    data class Pending(
        val phase: String,
        val recipientId: String,
        val requestId: String,
        val giftId: String,
        val imData: String = "",
        val description: String = "",
        val extension: String = "",
    )

    private val prefs = context.getSharedPreferences("dm_gift_outbox", Context.MODE_PRIVATE)
    private val key = "${BuildConfig.BILLD_API_URL}:$userId"

    fun load(): Pending? = prefs.getString(key, null)?.let { raw ->
        val data = JSONObject(raw)
        Pending(data.getString("phase"), data.getString("recipientId"), data.getString("requestId"), data.getString("giftId"),
            data.optString("imData"), data.optString("description"), data.optString("extension"))
    }

    fun save(pending: Pending) {
        val encoded = JSONObject().put("phase", pending.phase).put("recipientId", pending.recipientId)
            .put("requestId", pending.requestId).put("giftId", pending.giftId).put("imData", pending.imData)
            .put("description", pending.description).put("extension", pending.extension).toString()
        check(prefs.edit().putString(key, encoded).commit()) { "无法保存私信礼物请求，请检查手机存储空间" }
    }

    fun clear() {
        check(prefs.edit().remove(key).commit()) { "礼物消息已处理，但本地记录未能更新" }
    }
}

class DmVideoOutbox(context: Context, userId: Long) {
    data class Pending(
        val phase: String,
        val recipientId: String,
        val requestId: String,
        val imData: String = "",
        val description: String = "",
        val extension: String = "",
    )

    private val prefs = context.getSharedPreferences("dm_video_outbox", Context.MODE_PRIVATE)
    private val key = "${BuildConfig.BILLD_API_URL}:$userId"

    fun load(): Pending? = prefs.getString(key, null)?.let { raw ->
        val data = JSONObject(raw)
        Pending(data.getString("phase"), data.getString("recipientId"), data.getString("requestId"),
            data.optString("imData"), data.optString("description"), data.optString("extension"))
    }

    fun save(pending: Pending) {
        val encoded = JSONObject().put("phase", pending.phase).put("recipientId", pending.recipientId)
            .put("requestId", pending.requestId).put("imData", pending.imData)
            .put("description", pending.description).put("extension", pending.extension).toString()
        check(prefs.edit().putString(key, encoded).commit()) { "无法保存视频消息请求，请检查手机存储空间" }
    }

    fun clear() {
        check(prefs.edit().remove(key).commit()) { "视频消息已处理，但本地记录未能更新" }
    }
}

/** One unlock request id per video so a retry cannot become a second charge. */
class DmUnlockIds(context: Context, userId: Long) {
    private val prefs = context.getSharedPreferences("dm_unlock_ids", Context.MODE_PRIVATE)
    private val prefix = "${BuildConfig.BILLD_API_URL}:$userId:"

    fun idFor(videoId: String): String {
        val key = prefix + videoId
        prefs.getString(key, null)?.let { return it }
        val created = UUID.randomUUID().toString()
        check(prefs.edit().putString(key, created).commit()) { "无法保存解锁单号" }
        return prefs.getString(key, null) ?: created
    }
}
