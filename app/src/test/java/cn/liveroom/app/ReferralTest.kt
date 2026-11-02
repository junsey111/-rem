package cn.liveroom.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReferralTest {
    @Test fun disabledProfileHasNoInventedCodeOrCounts() {
        val profile = parseReferralProfile(JSONObject().put("enabled", false))
        assertEquals(false, profile.enabled)
        assertNull(profile.code)
        assertNull(profile.invitedCount)
        assertNull(profile.rewardedCoins)
    }

    @Test fun enabledProfileUsesOnlyReturnedFields() {
        val profile = parseReferralProfile(JSONObject()
            .put("enabled", true)
            .put("code", "AB12CD34")
            .put("invited_count", 0)
            .put("rewarded_coins", 100))
        assertEquals("AB12CD34", profile.code)
        assertEquals(0L, profile.invitedCount)
        assertEquals(100L, profile.rewardedCoins)
        assertEquals("还没有人兑换你的邀请码", referralInviteSummary(0))
        assertEquals("已有 2 人兑换你的邀请码", referralInviteSummary(2))
    }

    @Test fun receiptTextFollowsIdempotency() {
        val created = receipt("created")
        val replayed = receipt("replayed")
        assertEquals(
            "兑换成功。你获得 50 金币，邀请人获得 100 金币。当前余额 50。",
            referralReceiptText(created),
        )
        assertEquals(
            "这笔兑换已经确认过，没有再次加币。你获得 50 金币，邀请人获得 100 金币。当前余额 50。",
            referralReceiptText(replayed),
        )
        assertNull(referralReceiptText(created.copy(accepted = false)))
    }

    @Test fun knownReferralErrorsStayReadable() {
        assertEquals("请重新登录", referralErrorMessage(401, null, "HTTP 401"))
        assertEquals("登录已失效，请重新登录", referralErrorMessage(401, null, "登录已失效，请重新登录"))
        assertEquals("不能兑换自己的邀请码", referralErrorMessage(409, "SELF_REFERRAL", "cannot redeem your own invite code"))
        assertEquals("已经兑换过邀请码，不会再次加币", referralErrorMessage(409, "REFERRAL_ALREADY_REDEEMED", "invite code already redeemed"))
        assertEquals("同一单号不能更换邀请码，请改回原来的邀请码后重试", referralErrorMessage(409, "IDEMPOTENCY_PAYLOAD_MISMATCH", "same request_id with different payload"))
        assertEquals("邀请码不存在", referralErrorMessage(404, "REFERRAL_CODE_NOT_FOUND", "invite code not found"))
        assertEquals("邀请奖励暂未开通", referralErrorMessage(403, "REFERRALS_DISABLED", "referral rewards are disabled"))
        assertEquals("兑换结果尚未确认，请用原单号重试", referralErrorMessage(503, "REFERRAL_IN_PROGRESS", "retry"))
        assertEquals("请填写邀请码", referralErrorMessage(400, null, "请填写邀请码"))
    }

    private fun receipt(idempotency: String) = ReferralReceipt(true, "7c9e6679-7425-40de-944b-e07fc1f90ae7", "AB12CD34", idempotency, 9, 100, 50, 50)
}