package cn.liveroom.app

import android.content.Context
import org.json.JSONObject

enum class WatchStatus { PREVIEW, LOCKED, UNLOCKED, FREE }

enum class WatchPhase { CHECKING, PREVIEW, OPEN, PAYWALL, SYNCING, UNAVAILABLE }

enum class WatchCommand { Retry, Unlock }

/** Server snapshot from the Billd watch-access routes. Missing or inconsistent payloads are rejected. */
data class WatchAccess(
    val freePreviewSeconds: Int,
    val watchedUnpaidSeconds: Int,
    val secondsRemaining: Int,
    val unlocked: Boolean,
    val unlockPriceCoins: Long?,
    val status: WatchStatus,
)

data class HeartbeatBatch(val requestId: String, val deltaSeconds: Int)

fun WatchPhase.allowsPlayback(): Boolean = this == WatchPhase.PREVIEW || this == WatchPhase.OPEN

internal fun previewTicksDue(elapsedSeconds: Int, consumedSeconds: Int, remaining: Int): Int {
    val fresh = (elapsedSeconds - consumedSeconds).coerceAtLeast(0)
    return fresh.coerceAtMost(remaining.coerceAtLeast(0))
}

internal fun parseWatchAccess(data: JSONObject): WatchAccess {
    val status = when (val raw = data.optString("status")) {
        "PREVIEW" -> WatchStatus.PREVIEW
        "LOCKED" -> WatchStatus.LOCKED
        "UNLOCKED" -> WatchStatus.UNLOCKED
        "FREE" -> WatchStatus.FREE
        else -> error(if (raw.isBlank()) "付费观看状态不完整" else "付费观看状态无效")
    }
    if (!data.has("free_preview_seconds") || !data.has("watched_unpaid_seconds") || !data.has("seconds_remaining") || !data.has("unlocked") || data.isNull("unlocked")) {
        error("付费观看状态不完整")
    }
    val free = data.getInt("free_preview_seconds")
    val watched = data.getInt("watched_unpaid_seconds")
    val remaining = data.getInt("seconds_remaining")
    val unlocked = data.getBoolean("unlocked")
    val price = if (!data.has("unlock_price_coins") || data.isNull("unlock_price_coins")) null else data.getLong("unlock_price_coins")
    check(free >= 0 && watched >= 0 && remaining >= 0) { "付费观看状态无效" }
    check(price == null || price >= 0) { "付费观看价格无效" }
    when (status) {
        WatchStatus.UNLOCKED -> check(unlocked) { "付费观看状态不一致" }
        WatchStatus.PREVIEW, WatchStatus.LOCKED -> check(!unlocked) { "付费观看状态不一致" }
        WatchStatus.FREE -> Unit
    }
    if (status == WatchStatus.PREVIEW) check(remaining <= free) { "付费观看状态不一致" }
    return WatchAccess(free, watched, remaining, unlocked, price, status)
}

/**
 * Idempotent preview heartbeats of 1–30 seconds.
 * An in-flight batch keeps its request id and delta until acknowledged, so a retry does not double-count.
 */
internal class PreviewHeartbeat(private val newRequestId: () -> String) {
    private var pendingId: String? = null
    private var pendingDelta: Int = 0
    private var inFlight: HeartbeatBatch? = null

    fun add(seconds: Int) {
        require(seconds >= 0) { "观看时长无效" }
        if (seconds == 0) return
        pendingDelta += seconds
        if (pendingId == null) pendingId = newRequestId()
    }

    fun current(): HeartbeatBatch? = inFlight

    fun pollNew(force: Boolean, flushAt: Int = 5): HeartbeatBatch? {
        if (inFlight != null || pendingDelta <= 0 || pendingId == null) return null
        if (!force && pendingDelta < flushAt) return null
        inFlight = HeartbeatBatch(pendingId!!, pendingDelta.coerceAtMost(30))
        return inFlight
    }

    fun acknowledge(batch: HeartbeatBatch) {
        val active = inFlight
        check(active != null && active == batch) { "心跳单号不一致" }
        check(batch.deltaSeconds in 1..30 && batch.deltaSeconds <= pendingDelta) { "心跳时长无效" }
        pendingDelta -= batch.deltaSeconds
        inFlight = null
        pendingId = if (pendingDelta == 0) null else newRequestId()
    }

    fun hasUnsent(): Boolean = pendingDelta > 0

    fun reset() {
        pendingId = null
        pendingDelta = 0
        inFlight = null
    }
}

/** Pure view of one viewer's paid-preview session. Playback stays closed until the server snapshot allows it. */
internal class WatchSession(newRequestId: () -> String) {
    private val heartbeats = PreviewHeartbeat(newRequestId)
    var access: WatchAccess? = null
        private set
    var shownRemaining: Int = 0
        private set
    var phase: WatchPhase = WatchPhase.CHECKING
        private set

    fun onUnavailable() {
        phase = WatchPhase.UNAVAILABLE
        access = null
        shownRemaining = 0
        heartbeats.reset()
    }

    fun resetToChecking() {
        phase = WatchPhase.CHECKING
        heartbeats.reset()
    }

    fun onAccess(next: WatchAccess) {
        access = next
        when (next.status) {
            WatchStatus.FREE, WatchStatus.UNLOCKED -> {
                shownRemaining = 0
                phase = WatchPhase.OPEN
                heartbeats.reset()
            }
            WatchStatus.LOCKED -> {
                shownRemaining = 0
                phase = WatchPhase.PAYWALL
                heartbeats.reset()
            }
            WatchStatus.PREVIEW -> {
                shownRemaining = next.secondsRemaining
                if (next.secondsRemaining > 0) phase = WatchPhase.PREVIEW
                else {
                    phase = WatchPhase.PAYWALL
                    heartbeats.reset()
                }
            }
        }
    }

    fun onWatchedSecond(): HeartbeatBatch? {
        check(phase == WatchPhase.PREVIEW) { "当前不在试看" }
        heartbeats.add(1)
        shownRemaining = (shownRemaining - 1).coerceAtLeast(0)
        val force = shownRemaining == 0
        val created = heartbeats.pollNew(force)
        if (force) phase = WatchPhase.SYNCING
        return created
    }

    fun currentBatch(): HeartbeatBatch? = heartbeats.current()

    fun pollNew(force: Boolean): HeartbeatBatch? = heartbeats.pollNew(force)

    fun acknowledge(batch: HeartbeatBatch) = heartbeats.acknowledge(batch)

    fun hasUnsent(): Boolean = heartbeats.hasUnsent()

    fun dropPending() = heartbeats.reset()
}

/** One unlock request id per account, backend, and live, kept until the server confirms it. */
class WatchUnlockStore(context: Context, userId: Long) {
    private val prefs = context.getSharedPreferences("watch_unlock", Context.MODE_PRIVATE)
    private val prefix = "${BuildConfig.BILLD_API_URL}:$userId:"

    fun load(liveId: Long): String? = prefs.getString(prefix + liveId, null)?.takeIf { it.isNotBlank() }

    fun save(liveId: Long, requestId: String) {
        check(requestId.isNotBlank())
        check(prefs.edit().putString(prefix + liveId, requestId).commit()) { "无法保存解锁请求，请检查手机存储空间" }
    }

    fun clear(liveId: Long) {
        check(prefs.edit().remove(prefix + liveId).commit()) { "解锁已确认，但本地单号未能清除" }
    }
}
