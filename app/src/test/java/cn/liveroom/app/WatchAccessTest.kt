package cn.liveroom.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class WatchAccessTest {
    private fun payload(
        status: String,
        free: Int = 60,
        watched: Int = 0,
        remaining: Int = 60,
        unlocked: Boolean = false,
        price: Long? = 30,
    ) = JSONObject()
        .put("free_preview_seconds", free)
        .put("watched_unpaid_seconds", watched)
        .put("seconds_remaining", remaining)
        .put("unlocked", unlocked)
        .put("status", status)
        .apply { if (price != null) put("unlock_price_coins", price) else put("unlock_price_coins", JSONObject.NULL) }

    private fun access(status: WatchStatus, remaining: Int = 60, price: Long? = 30L) = WatchAccess(
        freePreviewSeconds = 60,
        watchedUnpaidSeconds = 60 - remaining,
        secondsRemaining = remaining,
        unlocked = status == WatchStatus.UNLOCKED,
        unlockPriceCoins = price,
        status = status,
    )

    @Test fun parsesPreviewLockedUnlockedAndFreeWithoutInventingPrice() {
        val preview = parseWatchAccess(payload("PREVIEW", watched = 15, remaining = 45, price = 80))
        assertEquals(WatchStatus.PREVIEW, preview.status)
        assertEquals(60, preview.freePreviewSeconds)
        assertEquals(15, preview.watchedUnpaidSeconds)
        assertEquals(45, preview.secondsRemaining)
        assertEquals(80L, preview.unlockPriceCoins)
        assertFalse(preview.unlocked)

        val locked = parseWatchAccess(payload("LOCKED", watched = 60, remaining = 0))
        assertEquals(WatchStatus.LOCKED, locked.status)
        assertEquals(WatchPhase.PAYWALL, WatchSession { "id" }.apply { onAccess(locked) }.phase)

        val unlocked = parseWatchAccess(payload("UNLOCKED", watched = 60, remaining = 0, unlocked = true))
        assertTrue(unlocked.unlocked)
        val free = parseWatchAccess(payload("FREE", remaining = 0, unlocked = false, price = null))
        assertNull(free.unlockPriceCoins)
        assertEquals(WatchStatus.FREE, free.status)
    }

    @Test fun rejectsUnknownInconsistentOrIncompleteAccess() {
        assertThrows(IllegalStateException::class.java) { parseWatchAccess(payload("OPEN")) }
        assertThrows(IllegalStateException::class.java) { parseWatchAccess(payload("UNLOCKED", unlocked = false)) }
        assertThrows(IllegalStateException::class.java) { parseWatchAccess(payload("PREVIEW", unlocked = true, remaining = 10)) }
        assertThrows(IllegalStateException::class.java) { parseWatchAccess(payload("PREVIEW", free = 60, remaining = 90)) }
        assertThrows(IllegalStateException::class.java) { parseWatchAccess(payload("LOCKED").apply { remove("unlocked") }) }
        assertThrows(IllegalStateException::class.java) { parseWatchAccess(payload("PREVIEW", price = -1)) }
    }

    @Test fun playbackFollowsServerStatusAndStopsWhenPreviewReachesZero() {
        val session = WatchSession { "id" }
        assertFalse(session.phase.allowsPlayback())
        session.onAccess(access(WatchStatus.FREE, remaining = 0, price = null))
        assertTrue(session.phase.allowsPlayback())
        session.onAccess(access(WatchStatus.PREVIEW, remaining = 2))
        assertTrue(session.phase.allowsPlayback())
        assertEquals(2, session.shownRemaining)
        assertNull(session.onWatchedSecond())
        assertEquals(WatchPhase.PREVIEW, session.phase)
        assertEquals(2, session.onWatchedSecond()?.deltaSeconds)
        assertEquals(WatchPhase.SYNCING, session.phase)
        assertEquals(0, session.shownRemaining)
        assertFalse(session.phase.allowsPlayback())
        session.onAccess(access(WatchStatus.LOCKED, remaining = 12))
        assertEquals(WatchPhase.PAYWALL, session.phase)
        assertFalse(session.phase.allowsPlayback())
        session.onAccess(access(WatchStatus.UNLOCKED, remaining = 0))
        assertEquals(WatchPhase.OPEN, session.phase)
        assertTrue(session.phase.allowsPlayback())
    }

    @Test fun previewHeartbeatRetriesTheSameBatchThenDrainsAtMostThirtySeconds() {
        val ids = ArrayDeque(listOf("a", "b", "c", "d"))
        val session = WatchSession { ids.removeFirst() }
        session.onAccess(access(WatchStatus.PREVIEW, remaining = 60))
        var created: HeartbeatBatch? = null
        repeat(60) {
            if (session.phase == WatchPhase.PREVIEW) session.onWatchedSecond()?.let { created = it }
        }
        assertEquals(HeartbeatBatch("a", 5), created)
        assertEquals(created, session.currentBatch())
        assertEquals(WatchPhase.SYNCING, session.phase)
        session.acknowledge(created!!)
        val second = session.pollNew(force = true)
        assertEquals(HeartbeatBatch("b", 30), second)
        session.acknowledge(second!!)
        val third = session.pollNew(force = true)
        assertEquals(HeartbeatBatch("c", 25), third)
        session.acknowledge(third!!)
        assertNull(session.pollNew(force = true))
        assertFalse(session.hasUnsent())
    }

    @Test fun previewTicksNeverRunPastTheServerRemainder() {
        assertEquals(0, previewTicksDue(elapsedSeconds = 0, consumedSeconds = 0, remaining = 40))
        assertEquals(1, previewTicksDue(elapsedSeconds = 4, consumedSeconds = 3, remaining = 40))
        assertEquals(2, previewTicksDue(elapsedSeconds = 12, consumedSeconds = 0, remaining = 2))
        assertEquals(0, previewTicksDue(elapsedSeconds = 5, consumedSeconds = 5, remaining = 0))
    }
}
