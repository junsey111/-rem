package cn.liveroom.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private data class RoomRankPerson(val id: Long, val person: LiveHeaderPerson, val coins: Long)
private suspend fun roomRankPeople(token: String, roomId: String, period: String, limit: Int): List<RoomRankPerson> {
    val api = LivePlatformApi()
    val rows = api.businessRequest(token, "/revenue/lives/$roomId/contributors?limit=$limit&period=$period").getJSONArray("contributors")
    return (0 until rows.length()).map { index ->
        val row = rows.getJSONObject(index)
        val id = row.getLong("viewer_id")
        val user = api.searchUsers(token, id.toString()).firstOrNull { it.id == id }
        RoomRankPerson(id, LiveHeaderPerson(user?.username ?: "用户", user?.avatar), row.getLong("coins"))
    }
}

@Composable
@Suppress("ProduceStateDoesNotAssignValue") // The polling loop explicitly assigns value.
internal fun rememberRoomLeaders(token: String, roomId: String): State<List<LiveHeaderPerson>> = produceState(emptyList(), token, roomId) {
    while (true) {
        try { value = roomRankPeople(token, roomId, "daily", 3).map { it.person } }
        catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
        delay(15_000)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveRoomRankDialog(token: String, roomId: String, onDismiss: () -> Unit) {
    var period by remember { mutableStateOf("daily") }
    var rows by remember { mutableStateOf(emptyList<RoomRankPerson>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(period, refresh) {
        loading = true; error = null
        try { rows = roomRankPeople(token, roomId, period, 50) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "榜单暂时无法加载" } finally { loading = false }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LiveStyle.sheetSurface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("主播礼物榜", color = LiveStyle.ink, fontSize = 19.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("daily" to "日榜", "weekly" to "周榜", "monthly" to "月榜").forEach { (key, label) ->
                    FilterChip(period == key, onClick = { period = key }, label = { Text(label) })
                }
            }
            if (loading) CircularProgressIndicator(Modifier.size(24.dp))
            else if (error != null) TextButton(onClick = { refresh++ }) { Text("$error · 重试") }
            else if (rows.isEmpty()) Text("本期还没有礼物记录", color = LiveStyle.muted, modifier = Modifier.padding(vertical = 25.dp))
            else LazyColumn(Modifier.heightIn(max = 340.dp)) {
                items(rows.size) { index ->
                    val row = rows[index]
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", Modifier.width(28.dp), color = rankColor(index), fontSize = 17.sp)
                        LiveHeaderAvatar(row.person, Modifier.size(36.dp))
                        Text(row.person.name, Modifier.weight(1f).padding(start = 10.dp), color = LiveStyle.ink)
                        Text("${row.coins} 金币", color = LiveStyle.accent, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun rankColor(index: Int) = when (index) { 0 -> Color(0xFFDEAE40); 1 -> Color(0xFF9FAAC0); 2 -> Color(0xFFC48E6A); else -> LiveStyle.muted }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveRoomViewersDialog(token: String, roomId: String, ownId: Long, onDismiss: () -> Unit) {
    var users by remember { mutableStateOf(emptyList<PublicUser>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var profile by remember { mutableStateOf<PublicUser?>(null) }
    LaunchedEffect(roomId, refresh) {
        loading = true; error = false
        try {
            val api = LivePlatformApi()
            val rows = api.businessRequest(token, "/rooms/$roomId/activity").getJSONArray("viewers")
            users = (0 until rows.length()).mapNotNull { i ->
                val row = rows.getJSONObject(i); val id = row.getLong("user_id")
                api.searchUsers(token, id.toString()).firstOrNull { it.id == id }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true } finally { loading = false }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LiveStyle.sheetSurface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("房间观众", color = LiveStyle.ink, fontSize = 19.sp)
            if (loading) CircularProgressIndicator(Modifier.size(24.dp))
            else if (error) TextButton(onClick = { refresh++ }) { Text("加载失败 · 重试") }
            else if (users.isEmpty()) Text("还没有观众进入", color = LiveStyle.muted, modifier = Modifier.padding(vertical = 20.dp))
            else LazyColumn(Modifier.heightIn(max = 340.dp)) {
                items(users.size) { index -> val user = users[index]
                    Row(Modifier.fillMaxWidth().clickable { profile = user }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        QumiUserAvatar(user.avatar, user.username, Modifier.size(38.dp))
                        Text(user.username, Modifier.weight(1f).padding(start = 12.dp), color = LiveStyle.ink)
                    }
                }
            }
        }
        profile?.let { DirectChatProfile(token, it.id, ownId, it.username, onBack = { profile = null }) }
    }
}
