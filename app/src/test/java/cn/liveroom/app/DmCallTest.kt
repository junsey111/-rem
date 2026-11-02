package cn.liveroom.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class DmCallTest {
    private val callId = "a".repeat(32)

    @Test fun inviteTextRoundTripsAndCarriesNoCredential() {
        val text = dmCallInviteText(callId, "video", 7)
        assertEquals("dm-call:v1 call_id=$callId mode=video from=7", text)
        assertFalse(text.contains("secret"))
        assertFalse(text.contains("token"))
        assertEquals(DmCallInvite(callId, "video", 7), parseDmCallInvite("  $text  "))
        assertNull(parseDmCallInvite("$text extra"))
        assertNull(parseDmCallInvite("hello"))
    }

    @Test fun incomingInviteIgnoresSelfAndExpiredCalls() {
        val now = 1_000_000L
        val fresh = OpenImMessage("1", "2", "7", dmCallInviteText(callId, "voice", 2), now - 1_000, 2)
        val own = OpenImMessage("2", "7", "2", dmCallInviteText("b".repeat(32), "video", 7), now, 2)
        val chat = OpenImMessage("3", "2", "7", "在吗", now, 2)
        assertEquals(callId, latestIncomingDmCall(listOf(chat, own, fresh), "7", now)?.callId)
        val stale = fresh.copy(sentAt = now - 10 * 60 * 1000 - 1)
        assertNull(latestIncomingDmCall(listOf(stale), "7", now))
        assertNull(latestIncomingDmCall(listOf(own), "7", now))
    }

    @Test fun deviceLiveKitUrlFollowsTheApkHost() {
        assertEquals("ws://10.0.2.2:7880", resolveDmCallUrl("ws://livekit:7880", "ws://10.0.2.2:7880"))
        assertEquals(
            "ws://192.168.254.132:17880",
            resolveDmCallUrl("ws://livekit:7880", "ws://192.168.254.132:17880"),
        )
        assertEquals("ws://192.168.254.132:17880", resolveDmCallUrl("ws://192.168.254.132:17880", " "))
        try {
            resolveDmCallUrl(" ", " ")
            fail("blank LiveKit URL accepted")
        } catch (_: IllegalArgumentException) {
        }
    }
}
