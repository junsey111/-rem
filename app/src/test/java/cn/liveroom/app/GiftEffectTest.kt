package cn.liveroom.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GiftEffectTest {
    private val request = "11111111-1111-4111-8111-111111111111"
    private val md5 = "0123456789abcdef0123456789abcdef"

    private fun yyeva(
        url: String = "https://cdn.example/castle.mp4",
        mode: String = "2",
        checksum: String = md5,
        type: String = EFFECT_YYEVA,
    ) = giftFromCatalogFields("CASTLE", "Castle", 999, 999, type, url, mode, checksum, "https://cdn.example/castle.png")

    @Test fun catalogFieldsKeepLowercaseContractAndDefaultToNone() {
        val bare = giftFromCatalogFields("ROSE", "Rose", 1, 1)
        assertEquals(EFFECT_NONE, bare.effectType)
        assertEquals("", bare.effectUrl)
        assertEquals("", bare.effectVideoMode)
        assertEquals("", bare.effectMd5)
        assertEquals("", bare.iconUrl)

        val gift = yyeva()
        assertEquals(EFFECT_YYEVA, gift.effectType)
        assertEquals("2", gift.effectVideoMode)
        assertEquals(md5, gift.effectMd5)
        assertEquals(EFFECT_NONE, giftFromCatalogFields("X", "X", 1, 1, "YYEVA").effectType)
        assertEquals(EFFECT_NONE, giftFromCatalogFields("X", "X", 1, 1, "vap").effectType)
        assertEquals(EFFECT_SVGA, giftFromCatalogFields("X", "X", 1, 1, "svga").effectType)
        assertEquals(EFFECT_LOTTIE, giftFromCatalogFields("X", "X", 1, 1, "lottie").effectType)
    }

    @Test fun onlyAcceptedOrBroadcastYyevaMp4EntersTheFullscreenQueue() {
        val gift = yyeva()
        assertTrue(admitFullscreen(gift, request, GiftEffectTrigger.OUTBOX_SAVED) is FullscreenAdmit.Skip)
        assertTrue(admitFullscreen(gift, request, GiftEffectTrigger.REJECTED) is FullscreenAdmit.Skip)
        assertTrue(admitFullscreen(gift, request, GiftEffectTrigger.UNCONFIRMED) is FullscreenAdmit.Skip)
        assertTrue(admitFullscreen(yyeva(type = EFFECT_LOTTIE), request, GiftEffectTrigger.ACCEPTED) is FullscreenAdmit.Skip)
        assertTrue(admitFullscreen(yyeva(type = EFFECT_SVGA), request, GiftEffectTrigger.ACCEPTED) is FullscreenAdmit.Skip)
        assertTrue(admitFullscreen(yyeva(type = EFFECT_NONE, url = ""), request, GiftEffectTrigger.ACCEPTED) is FullscreenAdmit.Skip)
        assertTrue(admitFullscreen(yyeva(url = ""), request, GiftEffectTrigger.ROOM_BROADCAST) is FullscreenAdmit.Skip)
        assertTrue(admitFullscreen(yyeva(url = "https://cdn.example/castle.json"), request, GiftEffectTrigger.ACCEPTED) is FullscreenAdmit.Broken)
        assertTrue(admitFullscreen(yyeva(mode = "sideways"), request, GiftEffectTrigger.ACCEPTED) is FullscreenAdmit.Broken)
        assertTrue(admitFullscreen(yyeva(checksum = "not-a-checksum"), request, GiftEffectTrigger.ACCEPTED) is FullscreenAdmit.Broken)

        val ready = admitFullscreen(gift, request, GiftEffectTrigger.ACCEPTED) as FullscreenAdmit.Ready
        assertEquals(request, ready.play.requestId)
        assertEquals(2, ready.play.videoMode)
        assertEquals(md5, ready.play.md5)
        assertNull(parseEffectVideoMode(""))
        assertEquals(1, parseEffectVideoMode("1"))
    }

    @Test fun fullscreenQueueIsSerialAndDoesNotReplayARequest() {
        val queue = GiftEffectQueue()
        val first = play(request)
        val second = play("22222222-2222-4222-8222-222222222222")
        assertEquals(GiftEffectOffer.STARTED, queue.offer(first))
        assertEquals(GiftEffectOffer.QUEUED, queue.offer(second))
        assertEquals(GiftEffectOffer.IGNORED, queue.offer(first))
        assertEquals(second, queue.finish(first.requestId))
        assertNull(queue.finish(first.requestId))
        assertNull(queue.finish(second.requestId))
        queue.clearActive()
        assertEquals(GiftEffectOffer.IGNORED, queue.offer(second))
    }

    @Test fun effectFileMustBeAnMp4AndMatchMd5WhenProvided() {
        val good = File.createTempFile("gift-effect", ".mp4")
        val bad = File.createTempFile("gift-effect", ".txt")
        try {
            good.writeBytes(byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 0, 0, 0, 0))
            bad.writeBytes("not a video".toByteArray())
            assertTrue(giftEffectFileUsable(good, ""))
            assertFalse(giftEffectFileUsable(bad, ""))
            assertFalse(giftEffectFileUsable(good, md5))
            assertTrue(looksLikeMp4(good.readBytes().copyOf(12)))
        } finally {
            good.delete()
            bad.delete()
        }
    }

    private fun play(id: String) = (admitFullscreen(yyeva(checksum = ""), id, GiftEffectTrigger.ACCEPTED) as FullscreenAdmit.Ready).play
}
