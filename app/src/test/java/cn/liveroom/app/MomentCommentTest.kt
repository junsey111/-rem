package cn.liveroom.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MomentCommentTest {
    private val api = LivePlatformApi("http://localhost")

    private fun comment() = JSONObject("""{
        "id":"123", "user_id":7, "author":{"username":"zhang", "avatar":null},
        "content":"这是一条评论", "created_at":"2026-11-13T02:00:00Z", "is_mine":true
    }""")

    @Test fun directCommentsHaveNoReplyTarget() {
        val row = comment().put("parent_id", JSONObject.NULL).put("reply_to_username", JSONObject.NULL)
        val parsed = api.parseMomentComment(row)
        assertNull(parsed.parentId)
        assertNull(parsed.replyToUsername)
        assertNull(parsed.avatar)
        assertEquals("这是一条评论", parsed.content)
        assertNull(api.parseMomentComment(comment()).replyToUsername)
        assertNull(api.parseMomentComment(comment().put("reply_to_username", "视频作者")).replyToUsername)
    }

    @Test fun repliesRetainTheirActualTarget() {
        val parsed = api.parseMomentComment(comment().put("parent_id", "100").put("reply_to_username", "小夏"))
        assertEquals("100", parsed.parentId)
        assertEquals("小夏", parsed.replyToUsername)
    }

    @Test fun repliesWithMissingOrNullTargetsDoNotDisplayNull() {
        val row = comment().put("parent_id", "100")
        assertNull(api.parseMomentComment(row).replyToUsername)
        assertNull(api.parseMomentComment(row.put("reply_to_username", JSONObject.NULL)).replyToUsername)
        assertNull(api.parseMomentComment(row.put("reply_to_username", "")).replyToUsername)
    }

    @Test fun pictureAndEmojiCommentsRetainTheirContentAfterReload() {
        val row = comment().put("content", "图片评论😍🌸").put("media_path", "/moments/12/comments/123/media")
        val parsed = api.parseMomentComment(row)
        assertEquals("图片评论😍🌸", parsed.content)
        assertEquals("/moments/12/comments/123/media", parsed.mediaPath)
        assertEquals("", api.parseMomentComment(row.put("content", "")).content)
    }

    @Test fun commentImagePathsMustUseTheAuthenticatedCommentEndpoint() {
        for (path in listOf("null", "", "https://example.com/image.jpg", "/moments/12/media", "/moments/12/comments/../media")) {
            assertNull(api.parseMomentComment(comment().put("media_path", path)).mediaPath)
        }
        assertNull(api.parseMomentComment(comment().put("media_path", JSONObject.NULL)).mediaPath)
    }
}
