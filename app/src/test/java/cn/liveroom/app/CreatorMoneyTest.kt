package cn.liveroom.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreatorMoneyTest {
    private val rules = CreatorMoneyRules(10, 100, 150, 1)

    @Test fun convertsUsingConfiguredRateAndWholeFenWithoutFloatingPoint() {
        assertEquals("¥12.30", formatCreatorRmb(123, rules))
        assertEquals("¥0.06", formatCreatorRmb(2, CreatorMoneyRules(3, 10, 0, 1)))
        assertEquals("¥0.00", formatCreatorRmb(0, null))
        assertEquals("待配置", formatCreatorRmb(1, null))
        assertNull(creatorGrossFen(Long.MAX_VALUE, rules))
    }

    @Test fun parsesYuanAndRejectsInvalidPrecisionAndScientificNotation() {
        assertEquals(12345L, yuanInputFen("123.45"))
        assertEquals(50L, yuanInputFen(".50"))
        assertNull(yuanInputFen("1.001"))
        assertNull(yuanInputFen("1e3"))
        assertNull(yuanInputFen("-1.00"))
        assertNull(yuanInputFen("0.00"))
    }

    @Test fun withdrawalMatchesEnteredYuanExactlyAndHonorsMinimum() {
        assertEquals(123L, withdrawalUnitsForFen(1230, rules))
        assertNull(withdrawalUnitsForFen(1231, rules))
        assertNull(withdrawalUnitsForFen(100, rules.copy(minimumDiamonds = 20)))
        val fractional = CreatorMoneyRules(3, 10, 0, 1)
        assertEquals(2L, withdrawalUnitsForFen(6, fractional))
        assertNull(withdrawalUnitsForFen(5, fractional))
        assertEquals(18L, creatorFeeFen(1230, rules))
        assertNull(withdrawalUnitsForFen(Long.MAX_VALUE, rules))
    }
}
