package cn.liveroom.app

import org.json.JSONObject

/** GET /revenue/referrals/me. Counters are absent while invites are disabled. */
data class ReferralProfile(
    val enabled: Boolean,
    val code: String?,
    val invitedCount: Long?,
    val rewardedCoins: Long?,
    val rechargeBps: Int = 0,
    val withdrawalBps: Int = 0,
    val todayInvitedCount: Long = 0,
    val todayRechargeRewardCoins: Long = 0,
    val todayWithdrawalRewardCoins: Long = 0,
    val totalRechargeRewardCoins: Long = 0,
    val totalWithdrawalRewardCoins: Long = 0,
    val inviterId: Long? = null,
    val todayInvitees: List<ReferralInvite> = emptyList(),
)

data class ReferralInvite(val userId: Long, val createdAt: String)
data class ReferralReward(val id: String, val source: String, val inviteeId: Long,
    val sourceAmount: Long, val rewardCoins: Long, val rateBps: Int, val createdAt: String)

/** POST /revenue/referrals/redeem receipt. Amounts are the server's, not a local estimate. */
data class ReferralReceipt(
    val accepted: Boolean,
    val requestId: String,
    val code: String,
    val idempotency: String,
    val inviterId: Long,
    val inviterCoins: Long,
    val inviteeCoins: Long,
    val inviteeBalance: Long,
)

internal fun parseReferralProfile(data: JSONObject): ReferralProfile {
    val code = data.optString("code").takeIf { data.has("code") && !data.isNull("code") && it.isNotBlank() }
    val invitees = data.optJSONArray("today_invitees")
    return ReferralProfile(
        enabled = data.optBoolean("enabled"),
        code = code,
        invitedCount = data.optionalLong("invited_count"),
        rewardedCoins = data.optionalLong("rewarded_coins"),
        rechargeBps = data.optInt("recharge_bps"),
        withdrawalBps = data.optInt("withdrawal_bps"),
        todayInvitedCount = data.optLong("today_invited_count"),
        todayRechargeRewardCoins = data.optLong("today_recharge_reward_coins"),
        todayWithdrawalRewardCoins = data.optLong("today_withdrawal_reward_coins"),
        totalRechargeRewardCoins = data.optLong("total_recharge_reward_coins"),
        totalWithdrawalRewardCoins = data.optLong("total_withdrawal_reward_coins"),
        inviterId = data.optionalLong("inviter_id"),
        todayInvitees = (0 until (invitees?.length() ?: 0)).mapNotNull { index ->
            invitees?.optJSONObject(index)?.let { ReferralInvite(it.optLong("user_id"), it.optString("created_at")) }
        },
    )
}

internal fun parseReferralReceipt(data: JSONObject): ReferralReceipt {
    val required = listOf("accepted", "request_id", "code", "idempotency", "inviter_id", "inviter_coins", "invitee_coins", "invitee_balance")
    check(required.all { data.has(it) && !data.isNull(it) }) { "兑换结果尚未确认，请用原单号重试" }
    return ReferralReceipt(
        accepted = data.getBoolean("accepted"),
        requestId = data.getString("request_id"),
        code = data.getString("code"),
        idempotency = data.getString("idempotency"),
        inviterId = data.getLong("inviter_id"),
        inviterCoins = data.getLong("inviter_coins"),
        inviteeCoins = data.getLong("invitee_coins"),
        inviteeBalance = data.getLong("invitee_balance"),
    )
}

internal fun referralErrorMessage(status: Int, errorCode: String?, serverMessage: String?): String {
    val message = serverMessage?.takeIf { it.isNotBlank() && !it.startsWith("HTTP ") }
    if (status == 401) return message ?: "请重新登录"
    return when (errorCode) {
        "SELF_REFERRAL" -> "不能兑换自己的邀请码"
        "REFERRAL_ALREADY_REDEEMED" -> "已经兑换过邀请码，不会再次加币"
        "IDEMPOTENCY_PAYLOAD_MISMATCH" -> "同一单号不能更换邀请码，请改回原来的邀请码后重试"
        "REFERRAL_CODE_NOT_FOUND" -> "邀请码不存在"
        "REFERRALS_DISABLED" -> "邀请奖励暂未开通"
        "REFERRAL_IN_PROGRESS" -> "兑换结果尚未确认，请用原单号重试"
        else -> message ?: "兑换没有完成，请稍后重试"
    }
}

/** Null when the server did not accept the receipt. Coin figures come only from the receipt. */
internal fun referralReceiptText(receipt: ReferralReceipt): String? {
    if (!receipt.accepted) return null
    val amounts = "你获得 ${receipt.inviteeCoins} 金币，邀请人获得 ${receipt.inviterCoins} 金币。当前余额 ${receipt.inviteeBalance}。"
    return when (receipt.idempotency) {
        "created" -> "兑换成功。$amounts"
        "replayed" -> "这笔兑换已经确认过，没有再次加币。$amounts"
        else -> "服务端已确认这笔兑换。$amounts"
    }
}

internal fun referralInviteSummary(count: Long): String =
    if (count == 0L) "还没有人兑换你的邀请码" else "已有 $count 人兑换你的邀请码"

private fun JSONObject.optionalLong(key: String): Long? =
    if (has(key) && !isNull(key)) getLong(key) else null
