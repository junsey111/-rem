package cn.liveroom.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SrsPlaybackTest {
    @Test fun playlistUsesPlaybackOriginAppLiveAndSessionId() {
        assertEquals(
            "http://10.0.2.2:8088/live/11.m3u8",
            srsPlaybackUrl("http://10.0.2.2:8088/", "11"),
        )
        assertEquals(
            "https://media.example:18088/live/9001.m3u8",
            srsPlaybackUrl("https://media.example:18088", "9001"),
        )
    }

    @Test fun missingOrNonHttpOriginDoesNotInventAUrl() {
        assertNull(srsPlaybackUrl("", "11"))
        assertNull(srsPlaybackUrl("   ", "11"))
        assertNull(srsPlaybackUrl("rtmp://10.0.2.2/live", "11"))
        assertNull(srsPlaybackUrl("http://", "11"))
    }

    @Test fun roomIdMustMatchDirectoryIdentity() {
        assertNull(srsPlaybackUrl("http://10.0.2.2:8088", "0"))
        assertNull(srsPlaybackUrl("http://10.0.2.2:8088", "../11"))
        assertNull(srsPlaybackUrl("http://10.0.2.2:8088", "11.m3u8"))
        assertNull(srsPlaybackUrl("http://10.0.2.2:8088", ""))
    }

    @Test fun ordinaryViewerUsesSrsAndInteractivePathsStayOnLiveKit() {
        assertEquals(true, viewerUsesSrsPlayback(isHost = false, matchId = null, cohostMode = null))
        assertEquals(false, viewerUsesSrsPlayback(isHost = true, matchId = null, cohostMode = null))
        assertEquals(false, viewerUsesSrsPlayback(isHost = false, matchId = 8L, cohostMode = null))
        assertEquals(false, viewerUsesSrsPlayback(isHost = false, matchId = null, cohostMode = "VIDEO"))
        assertEquals(false, viewerUsesSrsPlayback(isHost = false, matchId = null, cohostMode = "AUDIO"))
    }

    @Test fun ordinaryHostDoesNotLiveKitPublishTheCamera() {
        val ordinary = liveMediaRoute(true, isHost = true, matchId = null, viewerCohostKnown = false, viewerCohostMode = null, hostCohostSeat = false)
        assertEquals(LiveMediaRoute.HOST_RTMP, ordinary)
        assertEquals(false, liveKitPublishesHostCamera(ordinary))
        assertEquals(LiveMediaRoute.HOST_LIVEKIT, liveMediaRoute(true, true, 8L, false, null, false))
        assertEquals(true, liveKitPublishesHostCamera(LiveMediaRoute.HOST_LIVEKIT))
        assertEquals(LiveMediaRoute.HOST_LIVEKIT, liveMediaRoute(true, true, null, false, null, hostCohostSeat = true))
        assertEquals(LiveMediaRoute.VIEWER_HLS, liveMediaRoute(true, false, null, viewerCohostKnown = true, viewerCohostMode = null, hostCohostSeat = false))
        assertEquals(LiveMediaRoute.VIEWER_PK, liveMediaRoute(true, false, 8L, true, "VIDEO", false))
        assertEquals(LiveMediaRoute.VIEWER_COHOST, liveMediaRoute(true, false, null, true, "AUDIO", false))
        assertEquals(LiveMediaRoute.PENDING, liveMediaRoute(true, false, null, viewerCohostKnown = false, viewerCohostMode = null, hostCohostSeat = false))
    }
}
