package cn.liveroom.app

import org.json.JSONObject
import java.net.URI

/** Catalog effect_type. Unknown or missing values behave as none and do not play. */
const val EFFECT_NONE = "none"
const val EFFECT_YYEVA = "yyeva"
const val EFFECT_SVGA = "svga"
const val EFFECT_LOTTIE = "lottie"

const val MAX_GIFT_EFFECT_BYTES = 32L * 1024L * 1024L

private val giftRequestId = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
private val giftIdPattern = Regex("^[A-Za-z0-9_-]{1,32}$")
private val md5Pattern = Regex("^[0-9a-f]{32}$")

enum class GiftEffectTrigger {
    OUTBOX_SAVED,
    REJECTED,
    UNCONFIRMED,
    ACCEPTED,
    ROOM_BROADCAST,
}

data class YyevaSource(val url: String, val videoMode: Int?, val md5: String)

data class GiftEffectPlay(
    val requestId: String,
    val giftId: String,
    val url: String,
    val videoMode: Int?,
    val md5: String,
)

data class RoomGiftBroadcast(
    val roomId: String,
    val requestId: String,
    val giftId: String,
    val viewerId: Long,
    val quantity: Int,
)

enum class GiftEffectOffer { STARTED, QUEUED, IGNORED }

/** Single fullscreen lane. Seen request ids survive clearActive so a replay does not play twice. */
class GiftEffectQueue {
    private val pending = ArrayDeque<GiftEffectPlay>()
    private val seen = LinkedHashSet<String>()
    var current: GiftEffectPlay? = null
        private set

    fun offer(play: GiftEffectPlay): GiftEffectOffer {
        if (play.requestId.isBlank() || play.url.isBlank()) return GiftEffectOffer.IGNORED
        if (!seen.add(play.requestId)) return GiftEffectOffer.IGNORED
        while (seen.size > 300) {
            val iterator = seen.iterator()
            var removed = false
            while (iterator.hasNext()) {
                val oldest = iterator.next()
                val active = oldest == play.requestId || pending.any { it.requestId == oldest } || current?.requestId == oldest
                if (!active) {
                    iterator.remove()
                    removed = true
                    break
                }
            }
            if (!removed) break
        }
        if (current != null) {
            pending.addLast(play)
            return GiftEffectOffer.QUEUED
        }
        current = play
        return GiftEffectOffer.STARTED
    }

    fun finish(requestId: String): GiftEffectPlay? {
        if (current?.requestId != requestId) return null
        current = pending.removeFirstOrNull()
        return current
    }

    fun clearActive() {
        pending.clear()
        current = null
    }
}

fun normalizeEffectType(raw: String?): String = when (raw?.trim()) {
    EFFECT_NONE, EFFECT_YYEVA, EFFECT_SVGA, EFFECT_LOTTIE -> raw.trim()
    else -> EFFECT_NONE
}

fun normalizeEffectMd5(raw: String?): String {
    val value = raw?.trim()?.lowercase().orEmpty()
    return if (value.matches(md5Pattern)) value else ""
}

/** Empty uses the MP4's embedded layout. Other values must be a YYEVA mode integer, passed through as text. */
fun parseEffectVideoMode(raw: String?): Int? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    return text.toIntOrNull()?.takeIf { it in -2..5 }
}

fun giftFromCatalogFields(
    id: String,
    name: String,
    coinPrice: Int,
    diamonds: Int,
    effectType: String? = null,
    effectUrl: String? = null,
    effectVideoMode: String? = null,
    effectMd5: String? = null,
    iconUrl: String? = null,
    category: String? = null,
) = Gift(
    id = id,
    name = name,
    coinPrice = coinPrice,
    diamonds = diamonds,
    effectType = normalizeEffectType(effectType),
    effectUrl = effectUrl?.trim()?.take(512).orEmpty(),
    effectVideoMode = effectVideoMode?.trim()?.take(64).orEmpty(),
    effectMd5 = effectMd5?.trim()?.take(64).orEmpty(),
    iconUrl = iconUrl?.trim()?.take(512).orEmpty(),
    category = category?.takeIf { it in setOf("recommend", "heart", "party", "premium") } ?: "recommend",
)

fun parseGiftRow(row: JSONObject) = giftFromCatalogFields(
    id = row.optString("gift_id"),
    name = row.optString("display_name"),
    coinPrice = row.optInt("coin_price"),
    diamonds = row.optInt("diamond_reward"),
    effectType = if (row.has("effect_type") && !row.isNull("effect_type")) row.optString("effect_type") else null,
    effectUrl = if (row.has("effect_url") && !row.isNull("effect_url")) row.optString("effect_url") else null,
    effectVideoMode = if (row.has("effect_video_mode") && !row.isNull("effect_video_mode")) row.optString("effect_video_mode") else null,
    effectMd5 = if (row.has("effect_md5") && !row.isNull("effect_md5")) row.optString("effect_md5") else null,
    iconUrl = if (row.has("icon_url") && !row.isNull("icon_url")) row.optString("icon_url") else null,
    category = row.optString("category").takeIf { it.isNotBlank() },
)

sealed class FullscreenAdmit {
    data class Ready(val play: GiftEffectPlay) : FullscreenAdmit()
    data object Skip : FullscreenAdmit()
    data object Broken : FullscreenAdmit()
}

/**
 * Fullscreen playback is YYEVA only.
 * svga and lottie stay on the catalog for a later small-gift lane and do not enter this queue.
 */
fun Gift.yyevaSource(): YyevaSource? = when (val admit = admitFullscreen(this, "00000000-0000-4000-8000-000000000000", GiftEffectTrigger.ACCEPTED)) {
    is FullscreenAdmit.Ready -> YyevaSource(admit.play.url, admit.play.videoMode, admit.play.md5)
    else -> null
}

fun admitFullscreen(gift: Gift, requestId: String, trigger: GiftEffectTrigger): FullscreenAdmit {
    if (trigger != GiftEffectTrigger.ACCEPTED && trigger != GiftEffectTrigger.ROOM_BROADCAST) return FullscreenAdmit.Skip
    if (gift.effectType != EFFECT_YYEVA) return FullscreenAdmit.Skip
    val id = requestId.trim().lowercase()
    if (!id.matches(giftRequestId) || !gift.id.matches(giftIdPattern)) return FullscreenAdmit.Broken
    val url = httpUrlOrBlank(gift.effectUrl)
    if (url.isEmpty()) return FullscreenAdmit.Skip
    val path = runCatching { URI(url).path }.getOrNull().orEmpty()
    if (!path.lowercase().endsWith(".mp4")) return FullscreenAdmit.Broken
    if (gift.effectVideoMode.isNotBlank() && parseEffectVideoMode(gift.effectVideoMode) == null) return FullscreenAdmit.Broken
    val checksum = gift.effectMd5.trim()
    val md5 = normalizeEffectMd5(checksum)
    if (checksum.isNotEmpty() && md5.isEmpty()) return FullscreenAdmit.Broken
    return FullscreenAdmit.Ready(GiftEffectPlay(id, gift.id, url, parseEffectVideoMode(gift.effectVideoMode), md5))
}

fun playFromAcceptedIntent(intent: PendingGift, catalog: List<Gift>): FullscreenAdmit {
    val payload = JSONObject(intent.payload)
    val gift = catalog.firstOrNull { it.id == payload.optString("gift_id") } ?: return FullscreenAdmit.Skip
    return admitFullscreen(gift, payload.optString("request_id"), GiftEffectTrigger.ACCEPTED)
}

fun playFromBroadcast(event: RoomGiftBroadcast, catalog: List<Gift>): FullscreenAdmit? {
    val gift = catalog.firstOrNull { it.id == event.giftId } ?: return null
    return admitFullscreen(gift, event.requestId, GiftEffectTrigger.ROOM_BROADCAST)
}

fun parseRoomGiftBroadcast(data: JSONObject, expectedRoomId: String): RoomGiftBroadcast? {
    val roomId = data.optString("roomId")
    if (roomId != expectedRoomId || !roomId.matches(Regex("^[1-9][0-9]{0,15}$"))) return null
    val requestId = data.optString("request_id").trim().lowercase()
    if (!requestId.matches(giftRequestId)) return null
    val giftId = data.optString("gift_id")
    if (!giftId.matches(giftIdPattern)) return null
    val viewerId = data.optLong("viewer_id")
    if (viewerId <= 0L) return null
    val quantity = data.optInt("quantity")
    if (quantity !in 1..99) return null
    return RoomGiftBroadcast(roomId, requestId, giftId, viewerId, quantity)
}

fun looksLikeMp4(header: ByteArray): Boolean {
    if (header.size < 12) return false
    return header[4] == 'f'.code.toByte() &&
        header[5] == 't'.code.toByte() &&
        header[6] == 'y'.code.toByte() &&
        header[7] == 'p'.code.toByte()
}

private fun httpUrlOrBlank(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty() || value.length > 512) return ""
    val uri = runCatching { URI(value) }.getOrNull() ?: return ""
    val scheme = uri.scheme?.lowercase()
    if ((scheme != "https" && scheme != "http") || uri.host.isNullOrBlank() || !uri.userInfo.isNullOrEmpty()) return ""
    return value
}
