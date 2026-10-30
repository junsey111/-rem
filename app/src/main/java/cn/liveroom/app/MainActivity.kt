package cn.liveroom.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue

private val Ink = Color(0xFF171923)
private val Panel = Color(0xFF222530)
private val Muted = Color(0xFFAAB0BF)
private val Coral = Color(0xFFFF6F61)
private val Mint = Color(0xFF62D6B0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LiveRoomApp() }
    }
}

private data class Room(val host: String, val title: String, val category: String, val viewers: String, val color: Color, val stream: String)
private data class Contact(val name: String, val message: String, val time: String)
private val contacts = listOf(
    Contact("小满", "谢谢你的支持，晚上见～", "10:42"),
    Contact("阿哲", "PK 要不要约在 8 点？", "昨天"),
    Contact("平台助手", "本周直播数据已更新", "周三"),
)

@Composable
private fun LiveRoomApp() {
    var selected by remember { mutableIntStateOf(0) }
    var role by remember { mutableStateOf("观众") }
    var coins by remember { mutableIntStateOf(1280) }
    var activeRoom by remember { mutableStateOf<Room?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var rooms by remember { mutableStateOf(emptyList<Room>()) }
    var roomsStatus by remember { mutableStateOf("正在连接 Billd 直播服务…") }
    LaunchedEffect(Unit) {
        val result = BilldApi().liveRooms()
        result.onSuccess { liveRooms ->
            rooms = liveRooms.mapIndexed { index, item ->
                Room(item.host, item.title, "直播", "在线", listOf(Color(0xFF7B5CC7), Color(0xFF247D79), Color(0xFFB15E52))[index % 3], item.roomId)
            }
            roomsStatus = if (rooms.isEmpty()) "当前没有在线直播" else "已连接 Billd · ${rooms.size} 个在线房间"
        }.onFailure { roomsStatus = "Billd 暂不可用：${it.message ?: "连接失败"}" }
    }
    val scheme = if (MaterialTheme.colorScheme.background == Color.Unspecified) lightColorScheme() else darkColorScheme(
        primary = Coral,
        secondary = Mint,
        background = Ink,
        surface = Panel,
        onBackground = Color(0xFFF4F4F6),
        onSurface = Color(0xFFF4F4F6),
        surfaceVariant = Color(0xFF303440),
        onSurfaceVariant = Muted,
    )
    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize().background(Ink).windowInsetsPadding(WindowInsets.statusBars),
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = Panel, modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                    val tabs = listOf("直播", "榜单", "消息", "我的")
                    val icons = listOf(Icons.Default.Home, Icons.Default.Star, Icons.Default.ChatBubbleOutline, Icons.Default.PersonOutline)
                    tabs.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { Icon(icons[index], contentDescription = title) },
                            label = { Text(title) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                TopBar(role = role, onRole = { role = it })
                when (selected) {
                    0 -> if (role == "运营") OperationsScreen(rooms) else LiveScreen(role, roomsStatus, rooms, onJoin = { activeRoom = it }, onAction = { notice = it })
                    1 -> RankingsScreen()
                    2 -> MessagesScreen(onOpen = { notice = "已打开与${it}的私信会话（演示）" })
                    else -> ProfileScreen(role, coins, onTopUp = { coins += it; notice = "金币已增加 ${it}（演示）" }, onAction = { notice = it })
                }
            }
        }
        activeRoom?.let { room ->
            RoomDialog(room, coins, onClose = { activeRoom = null }, onGift = { cost ->
                if (coins >= cost) { coins -= cost; notice = "礼物已送出，金币 -$cost（演示）" }
                else notice = "金币不足，请先充值"
            }, onPk = { notice = "已发起双人 PK 邀请（演示）" })
        }
        notice?.let { message ->
            AlertDialog(
                onDismissRequest = { notice = null },
                title = { Text("直播间") },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = { notice = null }) { Text("知道了") } },
            )
        }
    }
}

@Composable
private fun TopBar(role: String, onRole: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("直播间", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("发现正在发生的现场", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("观众", "主播", "运营").forEach { value ->
                    FilterChip(selected = role == value, onClick = { onRole(value) }, label = { Text(value) })
                }
            }
        }
    }
}

@Composable
private fun LiveScreen(role: String, status: String, rooms: List<Room>, onJoin: (Room) -> Unit, onAction: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (role == "主播") "我的直播" else "正在直播", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (role == "主播") Button(onClick = { onAction("已创建直播间（演示），正式开播需配置 SRS 推流地址") }) { Text("开始直播") }
            }
        }
        item { Text(status, style = MaterialTheme.typography.bodySmall, color = Muted) }
        items(rooms) { room -> RoomTile(room, onClick = { onJoin(room) }) }
        item {
            HorizontalDivider(color = Color(0xFF353946), modifier = Modifier.padding(vertical = 8.dp))
            Text("互动功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { onAction("视频连麦入口（LiveKit 服务待配置）") }, label = { Text("视频连麦") })
                AssistChip(onClick = { onAction("语音连麦入口（LiveKit 服务待配置）") }, label = { Text("语音连麦") })
                AssistChip(onClick = { onAction("PK 邀请可从直播间发起") }, label = { Text("双人 PK") })
            }
        }
    }
}

@Composable
private fun RoomTile(room: Room, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(76.dp, 88.dp).clip(RoundedCornerShape(10.dp)).background(room.color), contentAlignment = Alignment.BottomStart) {
                Text("LIVE", Modifier.padding(8.dp).clip(RoundedCornerShape(4.dp)).background(Coral).padding(horizontal = 6.dp, vertical = 3.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(room.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("主播 ${room.host} · ${room.category}", style = MaterialTheme.typography.bodySmall, color = Muted)
                Text("${room.viewers} 人正在看", style = MaterialTheme.typography.bodySmall, color = Mint)
            }
            TextButton(onClick = onClick) { Text("进入") }
        }
    }
}

@Composable
private fun RoomDialog(room: Room, coins: Int, onClose: () -> Unit, onGift: (Int) -> Unit, onPk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(room.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SrsPlayback(stream = room.stream, fallbackColor = room.color)
                Text("${room.viewers} 人在线  ·  ${coins} 金币", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("花朵" to 10, "星星" to 50, "烟花" to 100).forEach { (name, value) ->
                        AssistChip(onClick = { onGift(value) }, label = { Text("$name $value") })
                    }
                }
                OutlinedButton(onClick = onPk, modifier = Modifier.fillMaxWidth()) { Text("邀请主播 PK / 连麦") }
                Text("礼物、金币和人数为本地演示数据", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("离开房间") } },
    )
}

@Composable
private fun SrsPlayback(stream: String, fallbackColor: Color) {
    val context = LocalContext.current
    val playbackUrl = "${BuildConfig.SRS_PLAYBACK_URL}/live/$stream.m3u8"
    val player = remember(playbackUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(playbackUrl))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(fallbackColor)) {
        AndroidView(
            factory = { PlayerView(it).apply { useController = false; this.player = player } },
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$stream 的直播间", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("SRS HLS · $playbackUrl", color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RankingsScreen() {
    var period by remember { mutableStateOf("日榜") }
    val names = listOf("小满", "阿哲", "林间风", "星野", "一颗糖", "南山")
    val values = if (period == "日榜") listOf("42,860", "31,240", "26,890", "18,650", "12,340", "9,800") else listOf("386,200", "312,800", "268,900", "186,500", "132,400", "98,000")
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("主播榜单", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("日榜", "月榜").forEach { FilterChip(period == it, { period = it }, label = { Text(it) }) } }
        Spacer(Modifier.height(8.dp))
        names.forEachIndexed { index, name ->
            Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", Modifier.width(34.dp), color = if (index < 3) Coral else Muted, fontWeight = FontWeight.Bold)
                Avatar(name, index)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(name, fontWeight = FontWeight.SemiBold)
                    Text(if (index == 0) "人气主播 · Lv.18" else "人气主播 · Lv.${16 - index}", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Text("${values[index]} 票", color = Mint, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(color = Color(0xFF303440))
        }
        Spacer(Modifier.height(10.dp))
        Text("榜单数据为演示数据", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun MessagesScreen(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("私信", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        contacts.forEachIndexed { index, contact ->
            Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(contact.name, index)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(contact.name, fontWeight = FontWeight.SemiBold)
                        Text(contact.time, style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                    Text(contact.message, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
            }
            HorizontalDivider(color = Color(0xFF303440))
        }
        Spacer(Modifier.height(12.dp))
        Text("私信服务由 OpenIM 提供，演示模式下显示本地会话", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun ProfileScreen(role: String, coins: Int, onTopUp: (Int) -> Unit, onAction: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar("直播用户", 1, 54)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(if (role == "主播") "主播小满" else "直播用户", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("用户等级 Lv.12  ·  主播等级 Lv.8", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                AssistChip(onClick = { onAction("等级规则") }, label = { Text("等级") })
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("金币余额", color = Muted)
                        Text("$coins", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Mint)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(100, 500, 1000).forEach { amount -> OutlinedButton(onClick = { onTopUp(amount) }) { Text("+${amount}") } }
                    }
                    Text("充值为本地演示，不涉及真实支付", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
        item {
            Text("主播收益", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("今日收益", "¥ 286.50", Modifier.weight(1f))
                Metric("本月收益", "¥ 8,620", Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Text("礼物收入统计 · 演示数据", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        item {
            HorizontalDivider(color = Color(0xFF303440))
            listOf("我的直播间", "礼物记录", "收益明细", "主播公会", "设置").forEach { label ->
                TextButton(onClick = { onAction("$label（演示入口）") }, modifier = Modifier.fillMaxWidth()) {
                    Text(label, modifier = Modifier.weight(1f))
                    Text("›", color = Muted, fontSize = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun OperationsScreen(rooms: List<Room>) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("运营台", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("平台运行概览 · 演示数据", style = MaterialTheme.typography.bodySmall, color = Muted)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("直播中", "128", Modifier.weight(1f))
                Metric("在线用户", "24,680", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("今日礼物", "¥ 38,620", Modifier.weight(1f))
                Metric("待审核", "16", Modifier.weight(1f))
            }
        }
        item { Text("直播间管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        items(rooms) { room ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(room.title, fontWeight = FontWeight.SemiBold)
                    Text("${room.host} · ${room.viewers} 在线", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                TextButton(onClick = {}) { Text("管理") }
            }
            HorizontalDivider(color = Color(0xFF303440))
        }
        item { Text("平台数据及管理操作尚未连接业务后台", style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Panel).padding(14.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
        Spacer(Modifier.height(5.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Avatar(name: String, index: Int, size: Int = 42) {
    val colors = listOf(Color(0xFF8661C1), Color(0xFF398D84), Color(0xFFBD6E59), Color(0xFF4D75A3))
    Box(Modifier.size(size.dp).clip(CircleShape).background(colors[index % colors.size]), contentAlignment = Alignment.Center) {
        Text(name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size / 2.4f).sp)
    }
}
