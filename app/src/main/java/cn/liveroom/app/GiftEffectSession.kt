package cn.liveroom.app

import android.content.Context
import android.util.Log
import com.yy.yyeva.view.EvaAnimViewV3
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.coroutineContext

private data class HeldAcceptedGift(
    val intent: PendingGift,
    val roomId: String,
    val senderName: String,
    val senderId: Long,
)

/**
 * One fullscreen YYEVA lane and one gift banner lane.
 * Callers enqueue only after the server accepts the debit, or from a room broadcast.
 * The same request id plays once on this device even if both paths deliver it.
 */
class GiftEffectSession(context: Context) {
    private val cache = GiftEffectCache(File(context.cacheDir, "gift-effects"))
    private val player = YyevaGiftEffectPlayer()
    private val queue = GiftEffectQueue()
    private val banners = GiftBannerQueue()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = Any()
    private val unresolved = ArrayDeque<RoomGiftBroadcast>()
    private val unresolvedAccepted = ArrayDeque<HeldAcceptedGift>()
    private var catalog: List<Gift> = emptyList()
    private var joinedRoom: String? = null
    private var generation = 0
    private var pumping = false
    private var waitingForSurface = false
    private var prefetchJob: Job? = null

    private val _failure = MutableStateFlow<String?>(null)
    val failure: StateFlow<String?> = _failure

    private val _banner = MutableStateFlow<GiftBannerPlay?>(null)
    val banner: StateFlow<GiftBannerPlay?> = _banner

    fun updateCatalog(gifts: List<Gift>) {
        catalog = gifts
        prefetchJob?.cancel()
        val snapshot = gifts
        prefetchJob = scope.launch(Dispatchers.IO) { cache.prefetch(snapshot) }
        val waiting = unresolved.toList()
        unresolved.clear()
        waiting.forEach { offerBroadcast(it) }
        val accepted = unresolvedAccepted.toList()
        unresolvedAccepted.clear()
        accepted.forEach { offerAccepted(it.intent, it.roomId, it.senderName, it.senderId) }
    }

    fun enterRoom(roomId: String) {
        joinedRoom = roomId
        generation++
        resetStage()
        prefetchJob?.cancel()
        val snapshot = catalog
        prefetchJob = scope.launch(Dispatchers.IO) { cache.prefetch(snapshot) }
    }

    fun leaveRoom() {
        joinedRoom = null
        generation++
        resetStage()
    }

    fun close() {
        joinedRoom = null
        generation++
        resetStage()
        scope.cancel()
    }

    fun offerAccepted(intent: PendingGift, roomId: String?, senderName: String, senderId: Long) {
        scope.launch {
            if (roomId == null || roomId != joinedRoom || senderId <= 0L) return@launch
            val payload = runCatching { JSONObject(intent.payload) }.getOrNull() ?: return@launch
            val requestId = payload.optString("request_id").trim().lowercase()
            val giftId = payload.optString("gift_id")
            val quantity = payload.optInt("quantity", 1)
            val gift = catalog.firstOrNull { it.id == giftId }
            if (gift == null) {
                holdAccepted(HeldAcceptedGift(intent, roomId, senderName, senderId), requestId)
                return@launch
            }
            when (val admit = playFromAcceptedIntent(intent, catalog)) {
                is FullscreenAdmit.Ready -> enqueue(admit.play)
                FullscreenAdmit.Broken -> _failure.value = FAILURE
                FullscreenAdmit.Skip, null -> Unit
            }
            presentBanner(GiftBannerPlay(requestId, gift.id, senderId, giftSenderLabel(senderName, senderId), quantity))
        }
    }

    fun offerBroadcast(event: RoomGiftBroadcast) {
        scope.launch {
            if (event.roomId != joinedRoom) return@launch
            val gift = catalog.firstOrNull { it.id == event.giftId }
            if (gift == null) {
                if (unresolved.none { it.requestId == event.requestId } && unresolved.size < MAX_GIFT_BANNER_PENDING) {
                    unresolved.addLast(event)
                }
                return@launch
            }
            when (val admit = playFromBroadcast(event, catalog)) {
                is FullscreenAdmit.Ready -> enqueue(admit.play)
                FullscreenAdmit.Broken -> _failure.value = FAILURE
                FullscreenAdmit.Skip, null -> Unit
            }
            presentBanner(
                GiftBannerPlay(
                    event.requestId,
                    gift.id,
                    event.viewerId,
                    giftSenderLabel(event.senderName, event.viewerId),
                    event.quantity,
                ),
            )
        }
    }

    fun finishBanner(requestId: String) {
        scope.launch {
            synchronized(gate) {
                if (!banners.finish(requestId)) return@launch
                _banner.value = banners.current
            }
        }
    }

    fun attach(surface: EvaAnimViewV3) {
        player.attach(surface)
    }

    fun onSurfaceReady() {
        if (waitingForSurface) {
            waitingForSurface = false
            kick()
        }
    }

    fun detach(surface: EvaAnimViewV3) {
        player.detach(surface)
    }

    private fun resetStage() {
        unresolved.clear()
        unresolvedAccepted.clear()
        waitingForSurface = false
        synchronized(gate) {
            queue.clearActive()
            queue.clearSeen()
            banners.clearActive()
            banners.clearSeen()
        }
        _banner.value = null
        _failure.value = null
        player.stop()
    }

    private fun holdAccepted(held: HeldAcceptedGift, requestId: String) {
        if (requestId.isBlank()) return
        if (unresolvedAccepted.any { requestIdOf(it.intent) == requestId }) return
        if (unresolvedAccepted.size >= MAX_GIFT_BANNER_PENDING) return
        unresolvedAccepted.addLast(held)
    }

    private fun requestIdOf(intent: PendingGift): String = runCatching {
        JSONObject(intent.payload).optString("request_id").trim().lowercase()
    }.getOrDefault("")

    private fun presentBanner(play: GiftBannerPlay) {
        val started = synchronized(gate) {
            banners.offer(play) == GiftEffectOffer.STARTED
        }
        if (started) _banner.value = play
    }

    private suspend fun enqueue(play: GiftEffectPlay) {
        synchronized(gate) { queue.offer(play) }
        kick()
    }

    private fun kick() {
        if (pumping) return
        pumping = true
        val gen = generation
        scope.launch {
            try {
                pump(gen)
            } finally {
                pumping = false
                val more = synchronized(gate) { queue.current } != null
                if (!waitingForSurface && more) kick()
            }
        }
    }

    private suspend fun pump(gen: Int) {
        while (gen == generation) {
            coroutineContext.ensureActive()
            val play = synchronized(gate) { queue.current } ?: return
            val file = withContext(Dispatchers.IO) {
                runCatching { cache.prepare(YyevaSource(play.url, play.videoMode, play.md5)) }
                    .onFailure { Log.w(TAG, "gift effect not ready gift=${play.giftId}", it) }
                    .getOrNull()
            }
            if (gen != generation) return
            if (file == null) {
                _failure.value = FAILURE
                synchronized(gate) { queue.finish(play.requestId) }
                continue
            }
            val finished = CompletableDeferred<Boolean>()
            val started = player.play(file, play.videoMode) { success ->
                finished.complete(success)
            }
            if (!started) {
                if (gen == generation) waitingForSurface = true
                return
            }
            if (gen != generation) {
                player.stop()
                return
            }
            val success = withTimeoutOrNull(FULLSCREEN_EFFECT_TIMEOUT_MS) { finished.await() }
            if (gen != generation) return
            if (success == null) player.stop()
            if (gen != generation) return
            _failure.value = if (success == true) null else FAILURE
            synchronized(gate) { queue.finish(play.requestId) }
        }
    }

    private companion object {
        const val TAG = "GiftEffect"
        const val FAILURE = "礼物特效播放失败"
    }
}
