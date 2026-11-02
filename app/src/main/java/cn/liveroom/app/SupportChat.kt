package cn.liveroom.app

/** Billd route that resolves `OPENIM_SUPPORT_USER_ID`. Empty config responds 404. */
const val SUPPORT_CHAT_PATH = "/openim/support"

data class SupportChatTarget(val userId: String, val name: String)

/**
 * Open the configured customer-service conversation.
 *
 * Profile, referral, and message-list buttons should call this and then show the existing
 * direct-message sheet for [SupportChatTarget.userId]. It reuses the Billd session token and
 * the already logged-in [OpenImGateway]. It does not log in again, send a canned reply, or
 * create a local inbox. A 404 means support is not configured and the button should stay hidden.
 */
suspend fun openSupportConversation(
    api: LivePlatformApi,
    gateway: OpenImGateway,
    token: String,
): SupportChatTarget {
    val contact = api.openImSupport(token)
    check(contact.userId.isNotBlank()) { "客服暂未开通" }
    gateway.ensureDirectConversation(contact.userId)
    return SupportChatTarget(contact.userId, contact.name.ifBlank { "客服" })
}
