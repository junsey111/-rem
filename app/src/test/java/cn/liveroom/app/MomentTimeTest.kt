package cn.liveroom.app

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class MomentTimeTest {
    private val now = Instant.parse("2026-11-09T08:00:00Z")
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test fun recentCommentsShowMinutesAndHours() {
        assertEquals("1分钟前", momentCommentTime("2026-11-09T07:59:10Z", now, zone))
        assertEquals("30分钟前", momentCommentTime("2026-11-09T07:30:00Z", now, zone))
        assertEquals("1小时前", momentCommentTime("2026-11-09T07:00:00Z", now, zone))
        assertEquals("24小时前", momentCommentTime("2026-11-08T08:00:00Z", now, zone))
        assertEquals("1分钟前", momentCommentTime("2026-11-09 15:59:10", now, zone))
        assertEquals("1小时前", momentCommentTime("2026-11-09 15:00:00", now, zone))
    }

    @Test fun olderCommentsShowLocalDate() {
        assertEquals("2026-11-08", momentCommentTime("2026-11-08T07:59:59Z", now, zone))
    }

    @Test fun timezoneFreeServerTimestampUsesBeijingTime() {
        val emulatorNow = Instant.parse("2026-11-09T17:30:00Z")
        assertEquals("1分钟前", momentCommentTime("2026-11-10 01:29:00", emulatorNow, ZoneId.of("UTC")))
        assertEquals("11分钟前", momentCommentTime("2026-11-10 01:19:00", emulatorNow, ZoneId.of("UTC")))
    }
}
