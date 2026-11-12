package cn.liveroom.app

import org.json.JSONObject

internal data class CallRecord(val mode: String, val status: String, val durationSeconds: Long = 0, val connected: Boolean = false) {
    val terminal get() = status !in listOf("ringing", "connected")
    fun label(isCaller: Boolean): String = when (status) {
        "ringing" -> if (isCaller) "等待接通" else "等待接听"
        "connected" -> "通话中 ${formatCallDuration(durationSeconds)}"
        "rejected" -> if (isCaller) "对方已拒绝" else "已拒绝"
        "cancelled" -> if (isCaller) "已取消" else "对方已取消"
        "no_answer" -> if (isCaller) "对方未接听" else "未接听"
        "busy" -> if (isCaller) "对方忙线" else "忙线未接通"
        "failed" -> "连接失败"
        else -> if (connected || durationSeconds > 0) "通话时长 ${formatCallDuration(durationSeconds)}" else "未接通"
    }
    fun encode(): String = JSONObject().put("mode", mode).put("status", status)
        .put("duration_seconds", durationSeconds.coerceAtLeast(0)).put("connected", connected).toString()
}

internal fun formatCallDuration(seconds: Long): String {
    val value = seconds.coerceAtLeast(0)
    val minutes = (value / 60).toString().padStart(2, '0')
    val remainder = (value % 60).toString().padStart(2, '0')
    return if (value < 3600) "$minutes:$remainder"
        else "${value / 3600}:${((value / 60) % 60).toString().padStart(2, '0')}:$remainder"
}

internal fun parseCallRecord(encoded: String): CallRecord? = runCatching {
    val row = JSONObject(encoded)
    val mode = row.getString("mode").takeIf { it == "voice" || it == "video" } ?: return null
    CallRecord(mode, row.getString("status"), row.optLong("duration_seconds").coerceAtLeast(0), row.optBoolean("connected"))
}.getOrNull()

/** Upgrades existing local demo receipts instead of leaving older bubbles as plain text. */
internal fun demoCallRecord(message: DemoChatMessage): CallRecord {
    parseCallRecord(message.media)?.let { return it }
    val mode = if (message.text.contains("视频")) "video" else "voice"
    val status = when {
        message.text.contains("通话时长") -> "ended"
        message.text.contains("已拒绝") -> "rejected"
        message.text.contains("取消") -> "cancelled"
        message.text.contains("未接听") || message.text.contains("无人接听") -> "no_answer"
        else -> "ended"
    }
    val clock = Regex("""(\d+):(\d{2})(?::(\d{2}))?""").find(message.text)
    val seconds = clock?.let { val a = it.groupValues[1].toLongOrNull() ?: 0
        val b = it.groupValues[2].toLongOrNull() ?: 0; val c = it.groupValues[3].toLongOrNull()
        if (c == null) a * 60 + b else a * 3600 + b * 60 + c } ?: 0
    return CallRecord(mode, status, seconds, status == "ended" && message.text.contains("通话时长"))
}
