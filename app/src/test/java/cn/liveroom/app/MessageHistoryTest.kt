package cn.liveroom.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MessageHistoryTest {
    private fun message(n: Int, content: String = "message $n") = OpenImMessage("m$n", "a", "b", content, n.toLong(), 2)
    private fun page(start: Int, end: Int, isEnd: Boolean = false) = OpenImHistory("chat", (start..end).map { message(it) }, isEnd)

    @Test fun incomingMessagesRetainAllLoadedPages() = runBlocking {
        val requests = mutableListOf<String>()
        val pages = mapOf("" to page(62, 91), "m62" to page(32, 61), "m32" to page(2, 31), "m2" to page(1, 1, true))
        val result = refreshHistoryWindow(page(1, 60, true)) { cursor -> requests.add(cursor); pages.getValue(cursor) }
        assertEquals(listOf("", "m62", "m32", "m2"), requests)
        assertEquals((1..91).map { "m$it" }, result.messages.map { it.id })
        assertTrue(result.isEnd)
    }

    @Test fun refreshedRangeRemovesDeletedMessagesAndUsesEditedContent() = runBlocking {
        val previous = page(1, 50, true)
        val newest = page(25, 54).copy(messages = (25..54).filter { it != 40 }.map { message(it, if (it == 41) "edited" else "message $it") })
        val oldest = page(1, 24, true).copy(messages = (1..24).filter { it != 1 }.map { message(it) }, nextCursor = "m1")
        val result = refreshHistoryWindow(previous) { if (it.isEmpty()) newest else oldest }
        assertFalse(result.messages.any { it.id == "m1" || it.id == "m40" })
        assertEquals("edited", result.messages.first { it.id == "m41" }.content)
        assertTrue(result.isEnd)
        assertEquals("m1", result.nextCursor)
    }

    @Test fun emptyDeletedPageUsesSdkCursorAndDoesNotStopEarly() = runBlocking {
        val requests = mutableListOf<String>()
        val result = refreshHistoryWindow(page(1, 30, true)) { cursor ->
            requests.add(cursor)
            when (cursor) {
                "" -> page(61, 90)
                "m61" -> OpenImHistory("chat", emptyList(), false, "m31")
                else -> page(1, 30, true)
            }
        }
        assertEquals(listOf("", "m61", "m31"), requests)
        assertEquals(60, result.messages.size)
        assertEquals("m1", result.messages.first().id)
    }

    @Test fun overlappingPagesDoNotDuplicateOrReplaceNewerSnapshots() = runBlocking {
        val result = refreshHistoryWindow(page(1, 30, true)) {
            if (it.isEmpty()) page(25, 54).copy(messages = page(25, 54).messages.map { m -> if (m.id == "m25") m.copy(content = "updated") else m })
            else page(1, 25, true)
        }
        assertEquals(54, result.messages.size)
        assertEquals("updated", result.messages.first { it.id == "m25" }.content)
    }

    @Test fun stalledCursorFailsInsteadOfReplacingHistoryWithTruncatedWindow() = runBlocking {
        val previous = page(1, 30, true)
        try {
            refreshHistoryWindow(previous) { page(40, 69) }
            fail("stalled pagination accepted")
        } catch (_: IllegalStateException) { }
        assertEquals(30, previous.messages.size)
    }

    @Test fun olderPaginationKeepsCurrentEditsAndRejectsWrongConversation() {
        val current = page(20, 49).copy(messages = page(20, 49).messages.map { if (it.id == "m20") it.copy(content = "new") else it })
        val result = appendOlderHistory(current, page(1, 20, true))
        assertEquals(49, result.messages.size)
        assertEquals("new", result.messages.first { it.id == "m20" }.content)
        assertTrue(result.isEnd)
        try { appendOlderHistory(current, page(1, 19).copy(conversationId = "other")); fail("wrong conversation accepted") }
        catch (_: IllegalStateException) { }
    }

    @Test fun failedFetchLeavesPreviousHistoryIntact() = runBlocking {
        val previous = page(1, 60, true)
        try {
            refreshHistoryWindow(previous) { if (it.isEmpty()) page(50, 79) else error("network unavailable") }
            fail("fetch failure ignored")
        } catch (_: IllegalStateException) { }
        assertEquals((1..60).map { "m$it" }, previous.messages.map { it.id })
    }
}
