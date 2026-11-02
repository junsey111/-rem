package cn.liveroom.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsIngestTest {
    @Test fun publishUrlUsesRtmpOriginAppLiveAndSessionId() {
        assertEquals(
            "rtmp://10.0.2.2:1935/live/11",
            srsIngestUrl("rtmp://10.0.2.2:1935/", "11"),
        )
        assertEquals(
            "rtmps://192.168.254.132:1935/live/9001",
            srsIngestUrl("rtmps://192.168.254.132:1935", "9001"),
        )
        assertEquals("live/11", srsIngestUrl("rtmp://10.0.2.2:1935", "11")!!.substringAfter("1935/"))
    }

    @Test fun httpPlaybackOriginAndSecretsAreNotUsedAsIngest() {
        assertNull(srsIngestUrl("", "11"))
        assertNull(srsIngestUrl("   ", "11"))
        assertNull(srsIngestUrl("http://10.0.2.2:8088", "11"))
        assertNull(srsIngestUrl("http://10.0.2.2:1985", "11"))
        assertNull(srsIngestUrl("rtmp://user:secret@10.0.2.2:1935", "11"))
        assertNull(srsIngestUrl("rtmp://10.0.2.2:1935/live", "11"))
        assertNull(srsIngestUrl("rtmp://", "11"))
    }

    @Test fun roomIdMustBeTheLiveSessionId() {
        assertNull(srsIngestUrl("rtmp://10.0.2.2:1935", "0"))
        assertNull(srsIngestUrl("rtmp://10.0.2.2:1935", "../11"))
        assertNull(srsIngestUrl("rtmp://10.0.2.2:1935", "11.m3u8"))
        assertNull(srsIngestUrl("rtmp://10.0.2.2:1935", ""))
    }

    @Test fun ordinaryHostPublishesSrsAndInteractiveSessionsStayOnLiveKit() {
        assertTrue(hostPublishesOrdinarySrs(isHost = true, matchId = null, acceptedCohost = false))
        assertFalse(hostPublishesOrdinarySrs(isHost = false, matchId = null, acceptedCohost = false))
        assertFalse(hostPublishesOrdinarySrs(isHost = true, matchId = 8L, acceptedCohost = false))
        assertFalse(hostPublishesOrdinarySrs(isHost = true, matchId = null, acceptedCohost = true))
        assertFalse(hostPublishesOrdinarySrs(isHost = true, matchId = 8L, acceptedCohost = true))
    }

    @Test fun onlyActiveCohostSeatsTakeTheCameraBackFromSrs() {
        assertTrue(cohostSeatNeedsLiveKit("ACCEPTED"))
        assertTrue(cohostSeatNeedsLiveKit("MUTED"))
        assertFalse(cohostSeatNeedsLiveKit("PENDING"))
        assertFalse(cohostSeatNeedsLiveKit("REJECTED"))
        assertFalse(cohostSeatNeedsLiveKit("LEFT"))
    }
}
