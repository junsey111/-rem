package cn.liveroom.app

import org.json.JSONObject
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

    @Test fun acceptedGiftWaitsUntilTheCatalogContainsIt() {
        val intent = PendingGift(
            JSONObject().put("request_id", request).put("gift_id", "CASTLE").put("quantity", 1).toString(),
            "Castle",
            999,
            "host",
        )
        assertNull(playFromAcceptedIntent(intent, emptyList()))
        val ready = playFromAcceptedIntent(intent, listOf(yyeva())) as FullscreenAdmit.Ready
        assertEquals(request, ready.play.requestId)
        assertTrue(playFromAcceptedIntent(intent, listOf(yyeva(type = EFFECT_NONE, url = ""))) is FullscreenAdmit.Skip)
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
        queue.clearSeen()
        assertEquals(GiftEffectOffer.STARTED, queue.offer(second))
    }

    @Test fun fullscreenQueueDropsPastThePendingCapWithoutStickingTheRequest() {
        val queue = GiftEffectQueue()
        assertEquals(GiftEffectOffer.STARTED, queue.offer(play(request)))
        for (index in 1..MAX_FULLSCREEN_EFFECT_PENDING) {
            assertEquals(GiftEffectOffer.QUEUED, queue.offer(play(requestId(index + 1))))
        }
        val overflow = requestId(90)
        assertEquals(GiftEffectOffer.IGNORED, queue.offer(play(overflow)))
        repeat(MAX_FULLSCREEN_EFFECT_PENDING + 1) { queue.finish(queue.current!!.requestId) }
        assertEquals(GiftEffectOffer.STARTED, queue.offer(play(overflow)))
    }

    @Test fun bannerLaneIsSerialAndKeepsQuantityOnOnePlay() {
        val queue = GiftBannerQueue()
        val first = banner(request, 3)
        val second = banner("22222222-2222-4222-8222-222222222222", 1)
        assertEquals(GiftEffectOffer.STARTED, queue.offer(first))
        assertEquals(GiftEffectOffer.QUEUED, queue.offer(second))
        assertEquals(GiftEffectOffer.IGNORED, queue.offer(first))
        assertFalse(queue.finish("someone-else"))
        assertEquals(first, queue.current)
        assertTrue(queue.finish(first.requestId))
        assertEquals(second, queue.current)
        assertEquals(1, queue.current?.quantity)
        queue.clearActive()
        assertEquals(GiftEffectOffer.IGNORED, queue.offer(second))
    }

    @Test fun broadcastCarriesSenderNameAndOneFullscreenPlay() {
        val event = parseRoomGiftBroadcast(JSONObject()
            .put("roomId", "22")
            .put("request_id", request.uppercase())
            .put("gift_id", "CASTLE")
            .put("viewer_id", 7)
            .put("quantity", 4)
            .put("sender_name", "  小柚\n  "), "22")
        assertEquals("小柚", event?.senderName)
        assertEquals(4, event?.quantity)
        val ready = playFromBroadcast(event!!, listOf(yyeva())) as FullscreenAdmit.Ready
        assertEquals(request, ready.play.requestId)
        assertEquals("用户9", giftSenderLabel(" \u0001 ", 9))
        assertEquals("12345678901234567890", sanitizeGiftSenderName("12345678901234567890EXTRA"))
        assertNull(parseRoomGiftBroadcast(JSONObject(event.let {
            JSONObject().put("roomId", "22").put("request_id", request).put("gift_id", "CASTLE")
                .put("viewer_id", 7).put("quantity", 4)
        }.toString()), "23"))
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

    private fun requestId(n: Int) = "33333333-3333-4333-8333-${n.toString().padStart(12, '0')}"

    private fun banner(id: String, quantity: Int) = GiftBannerPlay(id, "ROSE", 7, "小柚", quantity)
}
