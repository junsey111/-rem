package cn.liveroom.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RoomDirectoryTest {
    @Test fun actualSocketRegistrationContractResolvesHostForVideoGiftsAndMessaging() {
        assertEquals(BilldRoom("11", "Real room", "Actual host", 1),
            validateLiveRoom("11", "Real room", "11", "1", "Actual host"))
    }

    @Test fun emptyTitleUsesHonestNonIdentityFallback() {
        assertEquals("主播正在直播", validateLiveRoom("11", "", "11", "1", "Actual host").title)
        assertEquals("未设置昵称", validateLiveRoom("11", "Real room", "11", "1", "").host)
    }

    @Test fun missingOrInvalidOwnerCannotBecomeFakeHostZero() {
        for (identity in listOf("", "0", "-1", "not-an-id")) {
            assertThrows(Exception::class.java) { validateLiveRoom("11", "Real room", "11", identity, "Host") }
        }
    }

    @Test fun conflictingOrInvalidRoomIdentityIsRejected() {
        assertThrows(Exception::class.java) { validateLiveRoom("12", "Real room", "11", "1", "Host") }
        assertThrows(Exception::class.java) { validateLiveRoom("../11", "Real room", "../11", "1", "Host") }
    }
}
