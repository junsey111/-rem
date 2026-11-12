package cn.liveroom.app

import org.junit.Assert.*
import org.junit.Test

class CallRecordTest {
    @Test fun receiptsDistinguishWhoCancelledOrRejected() {
        assertEquals("已取消", CallRecord("video", "cancelled").label(true))
        assertEquals("对方已取消", CallRecord("video", "cancelled").label(false))
        assertEquals("对方已拒绝", CallRecord("voice", "rejected").label(true))
        assertEquals("已拒绝", CallRecord("voice", "rejected").label(false))
        assertEquals("对方未接听", CallRecord("voice", "no_answer").label(true))
        assertEquals("未接听", CallRecord("voice", "no_answer").label(false))
    }

    @Test fun connectedZeroSecondAndLongCallsKeepCorrectDurationAfterStorage() {
        for (mode in listOf("voice", "video")) {
            val zero = CallRecord(mode, "ended", 0, true)
            assertEquals("通话时长 00:00", zero.label(true))
            val call = CallRecord(mode, "ended", 15, true)
            assertEquals(call, parseCallRecord(call.encode()))
            assertEquals("通话时长 00:15", parseCallRecord(call.encode())?.label(true))
        }
        assertEquals("1:01:01", formatCallDuration(3661))
        assertEquals("00:00", formatCallDuration(-1))
        assertFalse(CallRecord("voice", "connected").terminal)
        assertTrue(CallRecord("voice", "cancelled").terminal)
    }

    @Test fun existingDemoCallHistoryUpgradesWithoutLosingModeOrStatus() {
        val old = DemoChatMessage("1", true, "视频通话 · 通话时长 0:15", 1, "call")
        assertEquals("video", demoCallRecord(old).mode)
        assertEquals("通话时长 00:15", demoCallRecord(old).label(true))
        assertEquals("已取消", demoCallRecord(old.copy(text = "语音通话 · 已取消 · 未接通")).label(true))
        assertNull(parseCallRecord("not-json"))
    }
}
