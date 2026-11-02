package cn.liveroom.app

data class DmCallInvite(val callId: String, val mode: String, val fromUserId: Long)

/** Current OpenIM hook. Replace the text send with a custom content type; do not add another chat stack. */
const val DM_CALL_SIGNAL_TRANSPORT = "openim-c2c-text"

private val dmCallInvitePattern =
    Regex("""^dm-call:v1 call_id=([0-9a-f]{32}) mode=(video|voice) from=([1-9][0-9]{0,9})$""")

fun dmCallInviteText(callId: String, mode: String, fromUserId: Long): String {
    require(callId.matches(Regex("[0-9a-f]{32}"))) { "通话号无效" }
    require(mode == "video" || mode == "voice") { "通话类型无效" }
    require(fromUserId > 0) { "账号无效" }
    return "dm-call:v1 call_id=$callId mode=$mode from=$fromUserId"
}

fun parseDmCallInvite(text: String): DmCallInvite? {
    val match = dmCallInvitePattern.matchEntire(text.trim()) ?: return null
    return DmCallInvite(match.groupValues[1], match.groupValues[2], match.groupValues[3].toLong())
}

/** Newest invite from the other person, while the 10-minute call record can still be joined. */
fun latestIncomingDmCall(
    messages: List<OpenImMessage>,
    ownUserId: String,
    nowMillis: Long = System.currentTimeMillis(),
): DmCallInvite? {
    for (message in messages.asReversed()) {
        if (message.senderId == ownUserId) continue
        val invite = parseDmCallInvite(message.content) ?: continue
        if (invite.fromUserId.toString() == ownUserId) continue
        if (message.sentAt > 0 && nowMillis - message.sentAt > 10 * 60 * 1000) return null
        return invite
    }
    return null
}

/**
 * Dial the LiveKit host baked into the APK (`backendHost` + `livekitPort`).
 * Emulator builds use 10.0.2.2:7880. VMware builds pass `-PlivekitPort=17880`.
 * The token is not bound to a host; the server URL is used only when the build field is blank.
 */
fun resolveDmCallUrl(serverUrl: String, deviceUrl: String): String {
    val device = deviceUrl.trim()
    if (device.isNotBlank()) return device
    val server = serverUrl.trim()
    require(server.isNotBlank()) { "LiveKit 地址缺失" }
    return server
}
