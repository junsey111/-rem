package cn.liveroom.app

import org.junit.Assert.*
import org.junit.Test

class LiveMosaicTest {
    @Test fun mosaicNeverLosesOrDuplicatesParticipants() {
        for (count in 1..20) {
            val rows = liveMosaicRows(count)
            assertEquals(count, rows.sum())
            assertTrue(rows.all { it in 1..3 })
        }
        assertEquals(listOf(2, 2), liveMosaicRows(4))
        assertEquals(listOf(2, 3), liveMosaicRows(5))
        assertTrue(liveMosaicRows(0).isEmpty())
    }
    @Test fun multiHostRoomsAndSpectatorsUseTheSameLiveKitFeed() {
        assertEquals(LiveMediaRoute.HOST_LIVEKIT, liveMediaRoute(true, true, null, true, null, false, "MULTI"))
        assertEquals(LiveMediaRoute.VIEWER_COHOST, liveMediaRoute(true, false, null, true, null, false, "MULTI"))
        assertEquals(LiveMediaRoute.VIEWER_COHOST, liveMediaRoute(true, false, null, true, null, true))
        assertEquals(LiveMediaRoute.HOST_RTMP, liveMediaRoute(true, true, null, true, null, false))
        assertEquals(LiveMediaRoute.VIEWER_HLS, liveMediaRoute(true, false, null, true, null, false))
        assertEquals(LiveMediaRoute.VIEWER_VOICE, liveMediaRoute(true, false, null, true, null, false, "VOICE"))
    }
}
