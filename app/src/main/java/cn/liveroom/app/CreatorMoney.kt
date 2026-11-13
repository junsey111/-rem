package cn.liveroom.app

import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger

internal data class CreatorMoneyRules(
    val diamondUnits: Long,
    val fiatFen: Long,
    val platformFeeBps: Long,
    val minimumDiamonds: Long,
)

internal fun parseCreatorMoneyRules(value: JSONObject?): CreatorMoneyRules? {
    value ?: return null
    val units = value.optLong("diamond_units")
    val fen = value.optLong("fiat_fen")
    val fee = value.optLong("platform_fee_bps", -1)
    val minimum = value.optLong("minimum_withdraw_diamonds")
    if (units !in 1..1_000_000_000L || fen !in 1..1_000_000_000L || fee !in 0..10_000L || minimum !in 1..1_000_000_000_000L) return null
    return CreatorMoneyRules(units, fen, fee, minimum)
}

internal fun creatorGrossFen(units: Long?, rules: CreatorMoneyRules?): Long? {
    if (units == 0L) return 0
    if (units == null || units < 0 || rules == null || rules.diamondUnits <= 0 || rules.fiatFen <= 0) return null
    return runCatching {
        BigInteger.valueOf(units).multiply(BigInteger.valueOf(rules.fiatFen))
            .divide(BigInteger.valueOf(rules.diamondUnits)).checkedLong()
    }.getOrNull()
}

internal fun formatRmb(fen: Long?): String = fen?.let { "¥${BigDecimal.valueOf(it, 2).toPlainString()}" } ?: "待配置"

internal fun formatCreatorRmb(units: Long?, rules: CreatorMoneyRules?): String = formatRmb(creatorGrossFen(units, rules))

internal fun yuanInputFen(text: String): Long? {
    if (!text.matches(Regex("(?:[0-9]{1,12}(?:\\.[0-9]{0,2})?|\\.[0-9]{1,2})"))) return null
    return runCatching { BigDecimal(text).movePointRight(2).longValueExact().takeIf { it > 0 } }.getOrNull()
}

// Select the smallest internal unit amount that quotes exactly the entered yuan amount.
// Reject unrepresentable amounts instead of silently rounding the withdrawal up or down.
internal fun withdrawalUnitsForFen(fen: Long?, rules: CreatorMoneyRules?): Long? {
    if (fen == null || fen <= 0 || rules == null || rules.diamondUnits <= 0 || rules.fiatFen <= 0) return null
    return runCatching {
        val divisor = BigInteger.valueOf(rules.fiatFen)
        val numerator = BigInteger.valueOf(fen).multiply(BigInteger.valueOf(rules.diamondUnits))
        val units = numerator.add(divisor).subtract(BigInteger.ONE).divide(divisor).checkedLong()
        units.takeIf { it in rules.minimumDiamonds..1_000_000_000_000L && creatorGrossFen(it, rules) == fen }
    }.getOrNull()
}

internal fun creatorFeeFen(grossFen: Long, rules: CreatorMoneyRules): Long =
    BigInteger.valueOf(grossFen).multiply(BigInteger.valueOf(rules.platformFeeBps))
        .divide(BigInteger.valueOf(10_000)).checkedLong()

// BigInteger.longValueExact is unavailable on Android 8–11; retain its overflow check.
private fun BigInteger.checkedLong(): Long {
    if (this < BigInteger.valueOf(Long.MIN_VALUE) || this > BigInteger.valueOf(Long.MAX_VALUE)) throw ArithmeticException("Long overflow")
    return toLong()
}
