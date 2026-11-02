package cn.liveroom.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DmMessageTest {
    private val requestId = "11111111-1111-4111-8111-111111111111"

    private fun giftJson(charged: Long = 5) = JSONObject()
        .put("v", 1).put("kind", "dm_gift").put("request_id", requestId).put("gift_order_id", "order-1")
        .put("gift_id", "HEART").put("gift_name", "小心心").put("quantity", 1)
        .put("charged_coins", charged).put("status", "ACCEPTED")

    @Test fun textMessagesStayText() {
        val row = JSONObject().put("clientMsgID", "m1").put("sendID", "1").put("recvID", "2")
            .put("contentType", 101).put("sendTime", 4).put("status", 2)
            .put("textElem", JSONObject().put("content", "你好"))
        val message = parseOpenImMessage(row)
        assertEquals("你好", message.content)
        assertTrue(chatBody(message) is ChatBody.Text)
    }

    @Test fun giftCustomMessageRendersReceiptWithoutAMediaField() {
        val data = giftJson()
        val row = JSONObject().put("clientMsgID", "m2").put("sendID", "1").put("recvID", "2")
            .put("contentType", 110).put("sendTime", 5).put("status", 2)
            .put("customElem", JSONObject().put("data", data.toString()).put("extension", "dm_gift"))
        val message = parseOpenImMessage(row)
        val body = chatBody(message) as ChatBody.Gift
        assertEquals("礼物 小心心 × 1", message.content)
        assertEquals("order-1", body.giftOrderId)
        assertEquals(5L, body.chargedCoins)
        assertFalse(message.customData!!.contains("media_url"))
        assertEquals("礼物 小心心 × 1", previewLatest(row.toString()))
    }

    @Test fun paidVideoStaysLockedAndDropsAnyEmbeddedAddress() {
        val data = JSONObject().put("v", 1).put("kind", "dm_video").put("video_id", requestId)
            .put("title", "片段").put("price_coins", 12).put("duration_sec", 8).put("paywalled", true)
            .put("media_url", "https://secret.example/video.mp4")
        val body = parseCustomData(data.toString()) as ChatBody.Video
        assertTrue(body.paywalled)
        assertEquals(12L, body.priceCoins)
        assertFalse(body.toString().contains("secret.example"))
        val free = parseCustomData(JSONObject(data.toString()).put("price_coins", 0).put("paywalled", false).toString()) as ChatBody.Video
        assertFalse(free.paywalled)
    }

    @Test fun incompleteGiftIsNotShownAsAReceipt() {
        val data = giftJson()
        data.remove("gift_order_id")
        assertTrue(parseCustomData(data.toString()) is ChatBody.Other)
        data.put("gift_order_id", "order-1")
        data.put("status", "PENDING")
        assertTrue(parseCustomData(data.toString()) is ChatBody.Other)
    }

    @Test fun deliveryUsesTheServerPayloadOnlyWhenTheRequestMatches() {
        val response = JSONObject().put("im_extension", "dm_gift").put("im_description", "礼物 小心心 × 1")
            .put("new_balance", 95).put("im_custom", giftJson())
        val delivery = giftDelivery(response, requestId)
        assertEquals("dm_gift", delivery?.extension)
        assertEquals(95L, delivery?.balance)
        assertNull(giftDelivery(response, "22222222-2222-4222-8222-222222222222"))
        response.getJSONObject("im_custom").put("media_url", "https://secret.example/a")
        assertNull(giftDelivery(response, requestId))
    }

    @Test fun videoDeliveryRejectsPayloadsThatEmbedTheFile() {
        val custom = JSONObject().put("v", 1).put("kind", "dm_video").put("video_id", requestId)
            .put("title", "片段").put("price_coins", 0).put("duration_sec", 4).put("paywalled", false)
        val response = JSONObject().put("request_id", requestId).put("im_extension", "dm_video")
            .put("im_description", "视频 片段").put("im_custom", custom)
        assertEquals("dm_video", videoDelivery(response, requestId)?.extension)
        custom.put("storage_name", requestId)
        assertNull(videoDelivery(response, requestId))
    }
}
