package cn.liveroom.app

data class OpenImMessage(val id: String, val senderId: String, val receiverId: String, val content: String, val sentAt: Long, val status: Int)

data class OpenImHistory(
    val conversationId: String,
    val messages: List<OpenImMessage>,
    val isEnd: Boolean,
    // Keep the SDK cursor even when a page contains only deleted messages.
    val nextCursor: String = messages.firstOrNull()?.id.orEmpty(),
)

/** Re-read the loaded window, including gaps after reconnect, edits and deletions. */
suspend fun refreshHistoryWindow(
    previous: OpenImHistory?,
    fetch: suspend (String) -> OpenImHistory,
): OpenImHistory {
    var page = fetch("")
    if (previous == null || previous.messages.isEmpty()) return page
    check(page.conversationId == previous.conversationId) { "消息会话发生变化，请重试" }
    val boundary = previous.messages.first()
    val messages = linkedMapOf<String, OpenImMessage>()
    val cursors = mutableSetOf<String>()
    while (true) {
        // The first occurrence is the newer snapshot when SDK pages overlap.
        page.messages.forEach { messages.putIfAbsent(it.id, it) }
        if (page.isEnd || (messages.containsKey(previous.nextCursor) || page.nextCursor == previous.nextCursor) || page.messages.any { it.sentAt < boundary.sentAt }) break
        check(page.nextCursor.isNotEmpty() && cursors.add(page.nextCursor)) { "消息分页未能继续，请重试" }
        val next = fetch(page.nextCursor)
        check(next.conversationId == page.conversationId) { "消息会话发生变化，请重试" }
        page = next
    }
    return page.copy(messages = messages.values.sortedWith(compareBy<OpenImMessage> { it.sentAt }.thenBy { it.id }))
}

fun appendOlderHistory(current: OpenImHistory, older: OpenImHistory): OpenImHistory {
    check(current.conversationId == older.conversationId) { "消息会话发生变化，请重试" }
    check(older.isEnd || older.nextCursor.isNotBlank() && older.nextCursor != current.nextCursor) { "消息分页未能继续，请重试" }
    return older.copy(messages = (current.messages + older.messages).distinctBy { it.id }
        .sortedWith(compareBy<OpenImMessage> { it.sentAt }.thenBy { it.id }))
}
