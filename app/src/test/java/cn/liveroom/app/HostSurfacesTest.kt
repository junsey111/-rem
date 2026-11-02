package cn.liveroom.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostSurfacesTest {
    private val level = UserLevel(viewerLevel = 2, creatorLevel = 4, viewerPoints = 10, creatorPoints = 40)

    @Test fun otherHostLevelStaysUnpublished() {
        assertEquals("主播等级暂未公开", hostLevelLabel(9, 1, level))
        assertEquals("主播等级暂未公开", hostLevelLabel(9, 1, null))
    }

    @Test fun ownHostLevelUsesTheAccountEndpointOnly() {
        assertEquals("主播等级 Lv.4", hostLevelLabel(1, 1, level))
        assertEquals("等级信息暂不可用", hostLevelLabel(1, 1, null))
    }

    @Test fun customerServiceIdMustBeARealAccountId() {
        assertNull(customerServiceUserId(""))
        assertNull(customerServiceUserId(" "))
        assertNull(customerServiceUserId("0"))
        assertNull(customerServiceUserId("01"))
        assertNull(customerServiceUserId("cs-agent"))
        assertEquals("42", customerServiceUserId(" 42 "))
    }
}