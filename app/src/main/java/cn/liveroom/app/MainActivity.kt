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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch

private val NightInk = Color(0xFF10141A)
private val NightSurface = Color(0xFF171D24)
private val NightSurfaceRaised = Color(0xFF202832)
private val NightLine = Color(0xFF303A45)
private val Fog = Color(0xFFAEB8C4)
private val Coral = Color(0xFFFF7166)
private val Mint = Color(0xFF68D5AE)
private val Amber = Color(0xFFF0B35B)

private val liveRoomColors = listOf(
    Color(0xFF4C5C8A),
    Color(0xFF2B6F70),
    Color(0xFF87544C),
    Color(0xFF596B40),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LiveRoomApp() }
    }
}

private data class Room(
    val host: String,
    val title: String,
    val category: String,
    val viewers: String,
    val accent: Color,
    val stream: String,
)

private data class Contact(val name: String, val message: String, val time: String)

private val contacts = listOf(
    Contact("小满", "谢谢你的支持，晚上见～", "10:42"),
    Contact("阿哲", "PK 要不要约在 8 点？", "昨天"),
    Contact("平台助手", "本周直播数据已更新", "周三"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveRoomApp() {
    var selected by remember { mutableIntStateOf(0) }
    var role by remember { mutableStateOf("观众") }
    var coins by remember { mutableIntStateOf(1280) }
    var activeRoom by remember { mutableStateOf<Room?>(null) }
    var rooms by remember { mutableStateOf(emptyList<Room>()) }
    var roomsStatus by remember { mutableStateOf("正在连接 Billd 直播服务…") }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { message ->
        scope.launch { snackbar.showSnackbar(message, duration = SnackbarDuration.Short) }
    }

    LaunchedEffect(refreshKey) {
        loading = true
        BilldApi().liveRooms()
            .onSuccess { liveRooms ->
                rooms = liveRooms.mapIndexed { index, item ->
                    Room(
                        host = item.host,
                        title = item.title,
                        category = "直播",
                        viewers = "在线",
                        accent = liveRoomColors[index % liveRoomColors.size],
                        stream = item.roomId,
                    )
                }
                roomsStatus = if (rooms.isEmpty()) "当前没有在线直播" else "服务在线 · ${rooms.size} 个房间"
            }
            .onFailure { error ->
                roomsStatus = "连接失败 · ${error.message ?: "请检查服务地址"}"
                rooms = emptyList()
            }
        loading = false
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Coral,
            onPrimary = Color.White,
            secondary = Mint,
            onSecondary = NightInk,
            tertiary = Amber,
            background = NightInk,
            onBackground = Color(0xFFF5F7FA),
            surface = NightSurface,
            onSurface = Color(0xFFF5F7FA),
            surfaceVariant = NightSurfaceRaised,
            onSurfaceVariant = Fog,
            outline = NightLine,
        ),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().background(NightInk),
            containerColor = NightInk,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(
                    containerColor = NightSurface,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    val tabs = listOf("直播", "榜单", "消息", "我的")
                    val icons = listOf(Icons.Default.Home, Icons.Default.EmojiEvents, Icons.Default.ChatBubbleOutline, Icons.Default.PersonOutline)
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
            Column(
                Modifier.fillMaxSize()
                    .padding(padding),
            ) {
                TopBar(
                    role = role,
                    status = roomsStatus,
                    onRole = { role = it },
                    onRefresh = { refreshKey++ },
                )
                when (selected) {
                    0 -> if (role == "运营") {
                        OperationsScreen(rooms, loading, onAction = notify)
                    } else {
                        LiveScreen(
                            role = role,
                            status = roomsStatus,
                            rooms = rooms,
                            loading = loading,
                            onJoin = { activeRoom = it },
                            onRefresh = { refreshKey++ },
                            onAction = notify,
                        )
                    }
                    1 -> RankingsScreen()
                    2 -> MessagesScreen(onOpen = { notify("已打开与 $it 的私信会话") })
                    else -> ProfileScreen(
                        role = role,
                        coins = coins,
                        onTopUp = { amount -> coins += amount; notify("金币余额已更新") },
                        onAction = notify,
                    )
                }
            }
        }

        activeRoom?.let { room ->
            ModalBottomSheet(
                onDismissRequest = { activeRoom = null },
                containerColor = NightSurface,
                dragHandle = { Surface(Modifier.padding(vertical = 10.dp).size(34.dp, 4.dp).clip(RoundedCornerShape(4.dp)), color = NightLine) {} },
            ) {
                RoomSheet(
                    room = room,
                    coins = coins,
                    onClose = { activeRoom = null },
                    onGift = { cost ->
                        if (coins >= cost) {
                            coins -= cost
                            notify("礼物已送出，金币 -$cost")
                        } else {
                            notify("金币不足，请先充值")
                        }
                    },
                    onAction = notify,
                )
            }
        }
    }
}

@Composable
private fun TopBar(role: String, status: String, onRole: (String) -> Unit, onRefresh: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("现场 Live", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LiveDot(active = status.startsWith("服务在线"))
                Text(status, style = MaterialTheme.typography.labelMedium, color = Fog, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "刷新直播") }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.ManageAccounts, contentDescription = "切换角色") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                listOf("观众", "主播", "运营").forEach { value ->
                    DropdownMenuItem(
                        text = { Text(if (value == role) "$value · 当前" else value) },
                        onClick = { onRole(value); menuOpen = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveScreen(
    role: String,
    status: String,
    rooms: List<Room>,
    loading: Boolean,
    onJoin: (Room) -> Unit,
    onRefresh: () -> Unit,
    onAction: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            LiveSummary(
                role = role,
                status = status,
                roomCount = rooms.size,
                onRefresh = onRefresh,
                onStart = { onAction("已创建开播入口，推流地址由 SRS 服务提供") },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = true, onClick = {}, label = { Text("全部") })
                FilterChip(selected = false, onClick = { onAction("分类筛选将在接入房间标签后启用") }, label = { Text("才艺") })
                FilterChip(selected = false, onClick = { onAction("分类筛选将在接入房间标签后启用") }, label = { Text("游戏") })
            }
        }
        if (loading) {
            item {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Coral, trackColor = NightSurfaceRaised)
            }
        } else if (rooms.isEmpty()) {
            item { EmptyLiveState(role = role, onRefresh = onRefresh, onAction = onAction) }
        } else {
            item { Text("正在直播", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(rooms, key = { it.stream }) { room -> RoomTile(room, onClick = { onJoin(room) }) }
        }
        item { InteractionSection(onAction) }
    }
}

@Composable
private fun LiveSummary(role: String, status: String, roomCount: Int, onRefresh: () -> Unit, onStart: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = NightSurfaceRaised, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(if (role == "主播") "准备好开播了吗？" else "现在正在发生", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(if (role == "主播") "打开摄像头，和观众开始互动" else "找到一个房间，马上加入现场", style = MaterialTheme.typography.bodyMedium, color = Fog)
                }
                Surface(shape = RoundedCornerShape(12.dp), color = if (status.startsWith("服务在线")) Mint.copy(alpha = .14f) else Coral.copy(alpha = .14f)) {
                    Text("${roomCount} 场", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = if (status.startsWith("服务在线")) Mint else Coral, fontWeight = FontWeight.Bold)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveDot(active = status.startsWith("服务在线"))
                Text(status, style = MaterialTheme.typography.bodySmall, color = Fog)
                Spacer(Modifier.weight(1f))
                if (role == "主播") {
                    Button(onClick = onStart, contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp)) { Text("开始直播") }
                } else {
                    TextButton(onClick = onRefresh, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) { Text("重新连接") }
                }
            }
        }
    }
}

@Composable
private fun EmptyLiveState(role: String, onRefresh: () -> Unit, onAction: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = NightSurface) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.LiveTv, contentDescription = null, tint = Fog, modifier = Modifier.size(42.dp))
            Text(if (role == "主播") "还没有你的直播间" else "暂时没有在线直播", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (role == "主播") "创建房间后，观众就能在这里找到你" else "刷新一下，或者稍后再来看看", style = MaterialTheme.typography.bodyMedium, color = Fog)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefresh) { Text("刷新") }
                if (role == "主播") Button(onClick = { onAction("开播入口已准备，正式推流需连接 SRS") }) { Text("创建房间") }
            }
        }
    }
}

@Composable
private fun RoomTile(room: Room, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = NightSurface, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(88.dp, 98.dp).clip(RoundedCornerShape(13.dp)).background(room.accent), contentAlignment = Alignment.BottomStart) {
                Column(Modifier.fillMaxSize().padding(9.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        LiveDot(active = true, size = 7)
                        Text("LIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(room.category, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .78f))
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(room.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("主播 ${room.host}", style = MaterialTheme.typography.bodySmall, color = Fog, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Coral, modifier = Modifier.size(14.dp))
                    Text("${room.viewers} 人正在看", style = MaterialTheme.typography.bodySmall, color = Mint)
                }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "进入直播间", tint = Fog, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun InteractionSection(onAction: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = NightLine, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("互动工具", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("更多", style = MaterialTheme.typography.labelMedium, color = Fog)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { onAction("视频连麦入口由 LiveKit 提供") }, label = { Text("视频连麦") }, leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp)) })
            AssistChip(onClick = { onAction("语音连麦入口由 LiveKit 提供") }, label = { Text("语音连麦") }, leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp)) })
        }
        AssistChip(
            onClick = { onAction("PK 邀请可从直播间发起") },
            label = { Text("双人 PK") },
            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) },
            colors = AssistChipDefaults.assistChipColors(containerColor = Amber.copy(alpha = .13f), labelColor = Amber, leadingIconContentColor = Amber),
        )
    }
}

@Composable
private fun RoomSheet(room: Room, coins: Int, onClose: () -> Unit, onGift: (Int) -> Unit, onAction: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(room.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${room.host} · ${room.viewers} 在线", style = MaterialTheme.typography.bodySmall, color = Fog)
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "关闭直播间") }
        }
        SrsPlayback(stream = room.stream, fallbackColor = room.accent)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Mint, modifier = Modifier.size(18.dp))
                Text("$coins 金币", fontWeight = FontWeight.SemiBold)
            }
            Text("直播互动", style = MaterialTheme.typography.labelMedium, color = Fog)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("花朵" to 10, "星星" to 50, "烟花" to 100).forEach { (name, value) ->
                AssistChip(onClick = { onGift(value) }, label = { Text("$name $value") }, modifier = Modifier.weight(1f))
            }
        }
        OutlinedButton(onClick = { onAction("已发起双人 PK / 连麦邀请") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("邀请主播 PK / 连麦")
        }
        Text("礼物与金币会由业务后台校验，当前客户端保留演示反馈。", style = MaterialTheme.typography.bodySmall, color = Fog)
    }
}

@Composable
private fun SrsPlayback(stream: String, fallbackColor: Color) {
    val context = LocalContext.current
    val playbackUrl = "${BuildConfig.SRS_PLAYBACK_URL}/live/$stream.m3u8"
    val player = remember(playbackUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(playbackUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(Modifier.fillMaxWidth().height(196.dp).clip(RoundedCornerShape(16.dp)).background(fallbackColor)) {
        AndroidView(
            factory = { PlayerView(it).apply { useController = true; this.player = player } },
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${stream} 的直播间", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Text("SRS HLS", color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RankingsScreen() {
    var period by remember { mutableStateOf("日榜") }
    val names = listOf("小满", "阿哲", "林间风", "星野", "一颗糖", "南山")
    val values = if (period == "日榜") listOf("42,860", "31,240", "26,890", "18,650", "12,340", "9,800") else listOf("386,200", "312,800", "268,900", "186,500", "132,400", "98,000")
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("主播榜单", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("按礼物贡献实时更新", style = MaterialTheme.typography.bodySmall, color = Fog, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("日榜", "月榜").forEach { FilterChip(period == it, { period = it }, label = { Text(it) }) } }
        }
        items(names.indices.toList()) { index ->
            Row(Modifier.fillMaxWidth().height(66.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", Modifier.width(32.dp), color = if (index < 3) Amber else Fog, fontWeight = FontWeight.Bold)
                Avatar(names[index], index)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(names[index], fontWeight = FontWeight.SemiBold)
                    Text("人气主播 · Lv.${18 - index}", style = MaterialTheme.typography.bodySmall, color = Fog)
                }
                Text("${values[index]} 票", color = Mint, style = MaterialTheme.typography.bodyMedium)
            }
            if (index < names.lastIndex) HorizontalDivider(color = NightLine)
        }
    }
}

@Composable
private fun MessagesScreen(onOpen: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
        item {
            Text("消息", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("和主播保持联系", style = MaterialTheme.typography.bodySmall, color = Fog, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        }
        items(contacts) { contact ->
            Surface(onClick = { onOpen(contact.name) }, color = Color.Transparent) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(contact.name, contact.name.length)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(contact.name, fontWeight = FontWeight.SemiBold)
                            Text(contact.time, style = MaterialTheme.typography.labelSmall, color = Fog)
                        }
                        Text(contact.message, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = Fog, modifier = Modifier.padding(top = 4.dp))
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "打开会话", tint = Fog, modifier = Modifier.size(18.dp).padding(start = 4.dp))
                }
            }
            HorizontalDivider(color = NightLine)
        }
        item { Text("私信服务由 OpenIM 提供", style = MaterialTheme.typography.bodySmall, color = Fog, modifier = Modifier.padding(top = 14.dp)) }
    }
}

@Composable
private fun ProfileScreen(role: String, coins: Int, onTopUp: (Int) -> Unit, onAction: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(if (role == "主播") "小满" else "直播用户", 1, 56)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(if (role == "主播") "主播小满" else "直播用户", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("用户等级 Lv.12 · 主播等级 Lv.8", style = MaterialTheme.typography.bodySmall, color = Fog, modifier = Modifier.padding(top = 4.dp))
                }
                AssistChip(onClick = { onAction("等级规则即将开放") }, label = { Text("Lv.12") })
            }
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = NightSurfaceRaised) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Mint, modifier = Modifier.size(20.dp))
                            Text("金币余额", color = Fog)
                        }
                        Text("$coins", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Mint)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(100, 500, 1000).forEach { amount -> OutlinedButton(onClick = { onTopUp(amount) }, modifier = Modifier.weight(1f)) { Text("+$amount") } }
                    }
                    Text("充值入口由服务端支付回调确认", style = MaterialTheme.typography.bodySmall, color = Fog)
                }
            }
        }
        item {
            Text("主播收益", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("今日收益", "¥ 286.50", Modifier.weight(1f))
                Metric("本月收益", "¥ 8,620", Modifier.weight(1f))
            }
        }
        item {
            Text("快捷入口", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            listOf("我的直播间" to Icons.Default.LiveTv, "礼物记录" to Icons.Default.Favorite, "收益明细" to Icons.Default.BarChart, "设置" to Icons.Default.Menu).forEach { (label, icon) ->
                TextButton(onClick = { onAction("${label}功能正在接入业务后台") }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 11.dp)) {
                    Icon(icon, contentDescription = null, tint = Fog, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(label, modifier = Modifier.weight(1f), color = Color(0xFFF5F7FA))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Fog, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun OperationsScreen(rooms: List<Room>, loading: Boolean, onAction: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("运营台", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("平台运行概览", style = MaterialTheme.typography.bodySmall, color = Fog, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("直播中", if (loading) "…" else rooms.size.toString(), Modifier.weight(1f))
                Metric("在线用户", "24,680", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("今日礼物", "¥ 38,620", Modifier.weight(1f))
                Metric("待审核", "16", Modifier.weight(1f))
            }
        }
        item { Text("直播间管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (rooms.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = NightSurface) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Amber, modifier = Modifier.size(22.dp))
                        Text("暂无待管理的在线房间", Modifier.padding(start = 12.dp), color = Fog)
                    }
                }
            }
        } else {
            items(rooms, key = { it.stream }) { room ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(active = true)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(room.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${room.host} · ${room.viewers} 在线", style = MaterialTheme.typography.bodySmall, color = Fog)
                    }
                    TextButton(onClick = { onAction("已打开 ${room.title} 的审核面板") }) { Text("管理") }
                }
                HorizontalDivider(color = NightLine)
            }
        }
        item { Text("运营数据需要从业务后台读取，当前只展示页面结构。", style = MaterialTheme.typography.bodySmall, color = Fog) }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = NightSurface) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Fog)
            Spacer(Modifier.height(5.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LiveDot(active: Boolean, size: Int = 8) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(if (active) Mint else Coral))
}

@Composable
private fun Avatar(name: String, index: Int, size: Int = 42) {
    val colors = listOf(Color(0xFF8661C1), Color(0xFF398D84), Color(0xFFBD6E59), Color(0xFF4D75A3))
    Box(Modifier.size(size.dp).clip(CircleShape).background(colors[index % colors.size]), contentAlignment = Alignment.Center) {
        Text(name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size / 2.4f).sp)
    }
}
