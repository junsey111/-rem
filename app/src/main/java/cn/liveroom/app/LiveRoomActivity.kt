package cn.liveroom.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONArray

internal data class RoomActivityLine(val id: String, val username: String, val text: String, val entry: Boolean, val time: String)
internal data class LiveRoomActivity(val viewers: Int? = null, val giftCoins: Long? = null, val diamonds: Long? = null,
    val lines: List<RoomActivityLine> = emptyList())

@Composable
@Suppress("ProduceStateDoesNotAssignValue") // Both polling requests assign value below; Compose lint misses the Kotlin setter.
internal fun rememberLiveRoomActivity(session: UserSession, api: LivePlatformApi, roomId: String, isHost: Boolean): State<LiveRoomActivity> =
    produceState(LiveRoomActivity(), session.id, roomId, isHost) {
        while (true) {
            try {
                val data = api.businessRequest(session.token, "/rooms/$roomId/activity")
                val messages = data.optJSONArray("messages") ?: JSONArray()
                val entries = data.optJSONArray("entries") ?: JSONArray()
                val lines = buildList {
                    for (i in 0 until messages.length()) messages.optJSONObject(i)?.let { row ->
                        add(RoomActivityLine("message-${row.optString("id")}", row.optString("username"), row.optString("text"), false, row.optString("created_at")))
                    }
                    for (i in 0 until entries.length()) entries.optJSONObject(i)?.let { row ->
                        add(RoomActivityLine(row.optString("id"), row.optString("username"), "进入直播间", true, row.optString("created_at")))
                    }
                }.sortedBy { it.time }.takeLast(30)
                value = value.copy(viewers = data.optInt("viewer_count"), lines = lines)
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
            if (isHost) try {
                val stats = api.businessRequest(session.token, "/revenue/lives/$roomId/stats")
                value = value.copy(giftCoins = stats.getLong("gift_coins"), diamonds = stats.getLong("reward_diamonds"))
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
            delay(3000)
        }
    }

@Composable
internal fun LiveRoomComments(activity: LiveRoomActivity, welcomeText: String, announcementText: String, roomId: String) {
    val listState = rememberLazyListState()
    var entryNotice by remember(roomId) { mutableStateOf(true) }
    LaunchedEffect(roomId) { delay(12_000); entryNotice = false }
    LaunchedEffect(activity.lines.lastOrNull()?.id) {
        if (activity.lines.isNotEmpty()) listState.animateScrollToItem(activity.lines.lastIndex + if (welcomeText.isNotBlank()) 1 else 0)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    if (entryNotice) LiveEntryNotice(listOfNotNull(LIVE_ENTRY_NOTICE,
        announcementText.takeIf { it.isNotBlank() }?.let { "房间公告：$it" }).joinToString("\n"), Modifier.fillMaxWidth(.88f))
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth(.88f).heightIn(max = 140.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (welcomeText.isNotBlank()) item(key = "welcome") {
            Text("主播 · $welcomeText", Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .32f))
                .padding(horizontal = 9.dp, vertical = 5.dp), color = Color(0xFFE5D8FF), fontSize = 12.sp)
        }
        items(activity.lines, key = { it.id }) { line ->
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFFD8B8FF), fontWeight = FontWeight.SemiBold)) { append(line.username) }
                append(if (line.entry) "  ${line.text}" else "：${line.text}")
            }, Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .25f))
                .padding(horizontal = 9.dp, vertical = 5.dp), color = if (line.entry) Color(0xFFD8CEE9) else Color.White,
                fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}
}
