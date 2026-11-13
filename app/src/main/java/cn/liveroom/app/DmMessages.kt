package cn.liveroom.app

import org.json.JSONObject

sealed class ChatBody {
    data class Text(val text: String) : ChatBody()
    data class Image(val url: String?) : ChatBody()
    data class Gift(
        val requestId: String,
        val giftOrderId: String,
        val giftId: String,
        val giftName: String,
        val quantity: Int,
        val chargedCoins: Long,
    ) : ChatBody()
    data class Video(
        val videoId: String,
        val title: String,
        val priceCoins: Long,
        val durationSec: Int,
        val paywalled: Boolean,
    ) : ChatBody()
    data class Other(val label: String) : ChatBody()
}

data class ImDelivery(val data: String, val description: String, val extension: String, val balance: Long?)

private val uuidV4 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

fun chatPreview(body: ChatBody): String = when (body) {
    is ChatBody.Text -> body.text
    is ChatBody.Image -> "[图片]"
    is ChatBody.Gift -> "礼物 ${body.giftName} × ${body.quantity}"
    is ChatBody.Video -> if (body.paywalled) "付费视频 ${body.title}" else "视频 ${body.title}"
    is ChatBody.Other -> body.label
}

fun parseOpenImMessage(row: JSONObject): OpenImMessage {
    val type = row.optInt("contentType", if (row.has("customElem")) 110 else 101)
    val custom = customDataString(row.optJSONObject("customElem"))
    val body = when (type) {
        110 -> parseCustomData(custom)
        101 -> ChatBody.Text(row.optJSONObject("textElem")?.optString("content")?.takeIf { it.isNotEmpty() } ?: row.optString("content"))
        102 -> ChatBody.Image(imageUrl(row))
        else -> ChatBody.Other("[非文字消息]")
    }
    val id = row.optString("clientMsgID").ifBlank { row.optString("serverMsgID") }
    check(id.isNotBlank()) { "消息缺少编号" }
    return OpenImMessage(
        id,
        row.optString("sendID"),
        row.optString("recvID"),
        chatPreview(body),
        row.optLong("sendTime").takeIf { it > 0 } ?: row.optLong("createTime"),
        row.optInt("status"),
        type,
        if (type == 110) custom else null,
        if (type == 102) imageUrl(row) else null,
    )
}

fun chatBody(message: OpenImMessage): ChatBody = when (message.contentType) {
    110 -> parseCustomData(message.customData)
    101 -> ChatBody.Text(message.content)
    102 -> ChatBody.Image(message.mediaUrl)
    else -> ChatBody.Other(message.content.ifBlank { "[非文字消息]" })
}

private fun imageUrl(row: JSONObject): String? {
    val picture = row.optJSONObject("pictureElem") ?: return null
    return listOf("sourcePicture", "bigPicture", "snapshotPicture")
        .firstNotNullOfOrNull { picture.optJSONObject(it)?.optString("url")?.takeIf(String::isNotBlank) }
}

fun previewLatest(latest: String): String {
    if (latest.isBlank()) return ""
    val message = runCatching { JSONObject(latest) }.getOrNull() ?: return latest
    if (!message.has("contentType") && !message.has("textElem") && !message.has("customElem")) return latest
    return chatPreview(chatBody(parseOpenImMessage(message)))
}

fun parseCustomData(raw: String?): ChatBody {
    val json = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return ChatBody.Other("[非文字消息]")
    if (json.optInt("v") != 1) return ChatBody.Other("[暂不支持的消息]")
    return when (json.optString("kind")) {
        "dm_gift" -> parseGift(json)
        "dm_video" -> parseVideo(json)
        else -> ChatBody.Other("[非文字消息]")
    }
}

private fun parseGift(json: JSONObject): ChatBody {
    val requestId = json.optString("request_id")
    val orderId = json.optString("gift_order_id")
    val giftId = json.optString("gift_id")
    val name = json.optString("gift_name")
    val quantity = json.optInt("quantity")
    if (!uuidV4.matches(requestId) || orderId.isBlank() || giftId.isBlank() || name.isBlank() || quantity !in 1..99
        || json.optString("status") != "ACCEPTED" || !json.has("charged_coins") || json.isNull("charged_coins")) {
        return ChatBody.Other("[礼物消息不完整]")
    }
    return ChatBody.Gift(requestId, orderId, giftId, name, quantity, json.optLong("charged_coins"))
}

private fun parseVideo(json: JSONObject): ChatBody {
    val videoId = json.optString("video_id")
    val title = json.optString("title")
    val duration = json.optInt("duration_sec")
    if (!uuidV4.matches(videoId) || title.isBlank() || duration !in 1..600 || !json.has("price_coins") || json.isNull("price_coins")) {
        return ChatBody.Other("[视频消息不完整]")
    }
    val price = json.optLong("price_coins")
    if (price < 0) return ChatBody.Other("[视频消息不完整]")
    return ChatBody.Video(videoId, title, price, duration, price > 0)
}

/** Send only the server-built payload. A missing receipt or a media URL is not a gift/video message. */
fun giftDelivery(data: JSONObject, requestId: String): ImDelivery? {
    val custom = data.optJSONObject("im_custom") ?: return null
    if (custom.has("media_url") || custom.has("storage_name")) return null
    val body = parseCustomData(custom.toString())
    if (body !is ChatBody.Gift || body.requestId != requestId) return null
    if (data.optString("im_extension") != "dm_gift" || data.optString("im_description").isBlank()) return null
    val balance = if (data.has("new_balance") && !data.isNull("new_balance")) data.optLong("new_balance") else null
    return ImDelivery(custom.toString(), data.optString("im_description"), "dm_gift", balance)
}

fun videoDelivery(data: JSONObject, requestId: String): ImDelivery? {
    val custom = data.optJSONObject("im_custom") ?: return null
    if (custom.has("media_url") || custom.has("storage_name") || custom.has("sha256")) return null
    val body = parseCustomData(custom.toString())
    if (body !is ChatBody.Video || data.optString("request_id") != requestId) return null
    if (data.optString("im_extension") != "dm_video" || data.optString("im_description").isBlank()) return null
    if (body.paywalled != (body.priceCoins > 0)) return null
    return ImDelivery(custom.toString(), data.optString("im_description"), "dm_video", null)
}

private fun customDataString(elem: JSONObject?): String? {
    if (elem == null) return null
    return when (val data = elem.opt("data")) {
        is String -> data
        is JSONObject -> data.toString()
        else -> null
    }
}
