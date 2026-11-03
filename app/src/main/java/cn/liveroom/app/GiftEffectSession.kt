package cn.liveroom.app

import android.content.Context
import android.util.Log
import com.yy.yyeva.view.EvaAnimViewV3
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Plays at most one fullscreen effect. GiftOutbox is not consulted here:
 * callers enqueue only after ACCEPTED or a server room broadcast.
 */
class GiftEffectSession(context: Context) {
    private val cache = GiftEffectCache(File(context.cacheDir, "gift-effects"))
    private val player = YyevaGiftEffectPlayer()
    private val queue = GiftEffectQueue()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val unresolved = ArrayDeque<RoomGiftBroadcast>()
    private var catalog: List<Gift> = emptyList()
    private var joinedRoom: String? = null
    private var generation = 0
    private var pumping = false
    private var waitingForSurface = false
    private var prefetchJob: Job? = null

    private val _failure = MutableStateFlow<String?>(null)
    val failure: StateFlow<String?> = _failure

    fun updateCatalog(gifts: List<Gift>) {
        catalog = gifts
        prefetchJob?.cancel()
        val snapshot = gifts
        prefetchJob = scope.launch(Dispatchers.IO) { cache.prefetch(snapshot) }
        val waiting = unresolved.toList()
        unresolved.clear()
        waiting.forEach { offerBroadcast(it) }
    }

    fun enterRoom(roomId: String) {
        joinedRoom = roomId
        generation++
        unresolved.clear()
        waitingForSurface = false
        scope.launch {
            mutex.withLock { queue.clearActive() }
            player.stop()
            _failure.value = null
        }
        prefetchJob?.cancel()
        val snapshot = catalog
        prefetchJob = scope.launch(Dispatchers.IO) { cache.prefetch(snapshot) }
    }

    fun leaveRoom() {
        joinedRoom = null
        generation++
        unresolved.clear()
        waitingForSurface = false
        scope.launch {
            mutex.withLock { queue.clearActive() }
            player.stop()
            _failure.value = null
        }
    }

    fun close() {
        joinedRoom = null
        generation++
        unresolved.clear()
        waitingForSurface = false
        queue.clearActive()
        player.stop()
        _failure.value = null
        scope.cancel()
    }

    fun offerAccepted(intent: PendingGift, roomId: String?) {
        scope.launch {
            if (roomId == null || roomId != joinedRoom) return@launch
            when (val admit = playFromAcceptedIntent(intent, catalog)) {
                is FullscreenAdmit.Ready -> enqueue(admit.play)
                FullscreenAdmit.Broken -> _failure.value = FAILURE
                FullscreenAdmit.Skip -> Unit
            }
        }
    }

    fun offerBroadcast(event: RoomGiftBroadcast) {
        scope.launch {
            if (event.roomId != joinedRoom) return@launch
            when (val admit = playFromBroadcast(event, catalog)) {
                null -> {
                    if (unresolved.none { it.requestId == event.requestId } && unresolved.size < 20) unresolved.addLast(event)
                }
                is FullscreenAdmit.Ready -> enqueue(admit.play)
                FullscreenAdmit.Broken -> _failure.value = FAILURE
                FullscreenAdmit.Skip -> Unit
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

    private suspend fun enqueue(play: GiftEffectPlay) {
        mutex.withLock { queue.offer(play) }
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
            }
        }
    }

    private suspend fun pump(gen: Int) {
        while (gen == generation) {
            coroutineContext.ensureActive()
            val play = mutex.withLock { queue.current } ?: return
            val file = withContext(Dispatchers.IO) {
                runCatching { cache.prepare(YyevaSource(play.url, play.videoMode, play.md5)) }
                    .onFailure { Log.w(TAG, "gift effect not ready gift=${play.giftId}", it) }
                    .getOrNull()
            }
            if (gen != generation) return
            if (file == null) {
                _failure.value = FAILURE
                mutex.withLock { queue.finish(play.requestId) }
                continue
            }
            val finished = CompletableDeferred<Boolean>()
            val started = player.play(file, play.videoMode) { success ->
                finished.complete(success)
            }
            if (!started) {
                waitingForSurface = true
                return
            }
            val success = finished.await()
            if (gen != generation) return
            _failure.value = if (success) null else FAILURE
            mutex.withLock { queue.finish(play.requestId) }
        }
    }

    private companion object {
        const val TAG = "GiftEffect"
        const val FAILURE = "礼物特效播放失败"
    }
}
