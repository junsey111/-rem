package cn.liveroom.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatPresenceTest {
    @Test fun onlyConfirmedOnlineUsersReceiveAnIndicator() {
        assertEquals(setOf("12"), parseOnlineChatUsers("""[
            {"userID":"12","status":1,"platformIDs":[1]},
            {"userID":"13","status":0},
            {"userID":"14"},
            {"userID":"group_15","status":1}
        ]"""))
    }

    @Test fun missingOrEmptyResponsesNeverInventOnlineUsers() {
        assertEquals(emptySet<String>(), parseOnlineChatUsers("[]"))
        assertEquals(emptySet<String>(), parseOnlineChatUsers("{}"))
        assertEquals(setOf("21"), parseOnlineChatUsers("""{"userStatusList":[{"userID":"21","status":1}]}"""))
    }
}
