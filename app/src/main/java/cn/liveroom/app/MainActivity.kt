package cn.liveroom.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private val NightInk = Color(0xFF10141A)
private val NightSurface = Color(0xFF171D24)
private val NightSurfaceRaised = Color(0xFF202832)
private val NightLine = Color(0xFF303A45)
private val Fog = Color(0xFFAEB8C4)
private val Coral = Color(0xFFFF7166)
private val Mint = Color(0xFF68D5AE)
private val Amber = Color(0xFFF0B35B)
private val liveRoomColors = listOf(Color(0xFF4C5C8A), Color(0xFF2B6F70), Color(0xFF87544C), Color(0xFF596B40))

private data class LiveRoom(
    val host: String,
    val title: String,
    val creatorId: Long,
    val accent: Color,
    val stream: String,
)

private data class OpenImConversation(val userId: String, val name: String, val latestMessage: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LiveRoomApp() }
    }
}

@Composable
private fun LiveRoomApp() {
    val context = LocalContext.current
    var session by remember { mutableStateOf(readSession(context)) }
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
        if (session == null) {
            AuthScreen(onAuthenticated = {
                saveSession(context, it)
                session = it
            })
        } else {
            AuthenticatedApp(
                session = session!!,
                onLogout = {
                    saveSession(context, null)
                    session = null
                },
            )
        }
    }
}

@Composable
private fun AuthScreen(onAuthenticated: (UserSession) -> Unit) {
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()
    var registering by remember { mutableStateOf(false) }
    var accountId by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(NightInk).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("现场 Live", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(if (registering) "创建账号" else "登录", style = MaterialTheme.typography.titleMedium, color = Fog, modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))
        if (registering) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; error = null },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = accountId,
                onValueChange = { accountId = it.filter(Char::isDigit); error = null },
                label = { Text("账号 ID") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        error?.let { Text(it, color = Coral, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)) }
        Button(
            enabled = !busy && password.isNotBlank() && if (registering) username.isNotBlank() else accountId.isNotBlank(),
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    try {
                        if (registering) {
                            val created = api.register(username, password)
                            val loggedIn = api.login(created.id, password).copy(username = created.username)
                            onAuthenticated(loggedIn)
                        } else {
                            val id = accountId.toLongOrNull() ?: error("账号 ID 格式无效")
                            onAuthenticated(api.login(id, password).copy(username = "用户$id"))
                        }
                    } catch (cause: Exception) {
                        error = cause.message ?: "请求失败，请检查服务地址"
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (registering) "注册并登录" else "登录")
        }
        TextButton(onClick = { registering = !registering; error = null }) {
            Text(if (registering) "已有账号？登录" else "创建新账号")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedApp(session: UserSession, onLogout: () -> Unit) {
    val context = LocalContext.current
    val api = remember { LivePlatformApi() }
    val openImGateway = remember(context) { OpenImGateway(context) }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var rooms by remember { mutableStateOf(emptyList<LiveRoom>()) }
    var roomsLoading by remember { mutableStateOf(true) }
    var roomsStatus by remember { mutableStateOf("正在连接直播服务") }
    var gifts by remember { mutableStateOf(emptyList<Gift>()) }
    var wallet by remember { mutableStateOf<Wallet?>(null) }
    var level by remember { mutableStateOf<UserLevel?>(null) }
    var dailyEarnings by remember { mutableStateOf<Earnings?>(null) }
    var monthlyEarnings by remember { mutableStateOf<Earnings?>(null) }
    var activeRoom by remember { mutableStateOf<LiveRoom?>(null) }
    var activeRoomStatus by remember { mutableStateOf("正在连接媒体服务") }
    var roomGateway by remember { mutableStateOf<LiveKitRoomGateway?>(null) }
    var roomRegistration by remember { mutableStateOf<BilldRoomRegistration?>(null) }
    var startingLive by remember { mutableStateOf(false) }
    var giftLoading by remember { mutableStateOf(false) }
    var rankTypeContributors by remember { mutableStateOf(false) }
    var rankPeriod by remember { mutableStateOf("daily") }
    var rankingEntries by remember { mutableStateOf(emptyList<RankingEntry>()) }
    var rankingsLoading by remember { mutableStateOf(false) }
    var rankingsError by remember { mutableStateOf<String?>(null) }
    var openImStatus by remember { mutableStateOf("尚未获取 OpenIM 会话") }
    var openImConversations by remember { mutableStateOf(emptyList<OpenImConversation>()) }
    var openImRecipient by remember { mutableStateOf("") }
    var openImMessage by remember { mutableStateOf("") }
    var openImBusy by remember { mutableStateOf(false) }
    var openImReady by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val notify: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message, duration = SnackbarDuration.Short) } }

    DisposableEffect(openImGateway) { onDispose { openImGateway.close() } }

    LaunchedEffect(session.token, refreshKey) {
        roomsLoading = true
        launch {
            runCatching { api.liveRooms() }
                .onSuccess { result ->
                    rooms = result.mapIndexed { index, item ->
                        LiveRoom(item.host, item.title, item.creatorId, liveRoomColors[index % liveRoomColors.size], item.roomId)
                    }
                    roomsStatus = if (rooms.isEmpty()) "服务在线 · 当前没有在线直播" else "服务在线 · ${rooms.size} 个房间"
                }
                .onFailure { error -> roomsStatus = "直播列表读取失败 · ${error.message ?: "服务不可用"}"; rooms = emptyList() }
            roomsLoading = false
        }
        launch { runCatching { api.giftCatalog() }.onSuccess { gifts = it } }
        launch { runCatching { api.wallet(session.token) }.onSuccess { wallet = it }.onFailure { wallet = null } }
        launch { runCatching { api.level(session.token) }.onSuccess { level = it }.onFailure { level = null } }
        launch { runCatching { api.earnings(session.token, "daily") }.onSuccess { dailyEarnings = it }.onFailure { dailyEarnings = null } }
        launch { runCatching { api.earnings(session.token, "monthly") }.onSuccess { monthlyEarnings = it }.onFailure { monthlyEarnings = null } }
    }

    LaunchedEffect(session.token, rankTypeContributors, rankPeriod, selectedTab) {
        if (selectedTab != 1) return@LaunchedEffect
        rankingsLoading = true
        rankingsError = null
        runCatching {
            if (rankTypeContributors) api.contributorRankings(session.token, rankPeriod)
            else api.creatorRankings(session.token, rankPeriod)
        }.onSuccess { rankingEntries = it }
            .onFailure { rankingsError = it.message ?: "榜单读取失败"; rankingEntries = emptyList() }
        rankingsLoading = false
    }

    LaunchedEffect(activeRoom?.stream, session.token) {
        val room = activeRoom ?: return@LaunchedEffect
        val liveSessionId = room.stream.toLongOrNull()
        if (liveSessionId == null) {
            activeRoomStatus = "此房间不是 Revenue/LiveKit 会话，无法从当前服务加入"
            return@LaunchedEffect
        }
        val gateway = LiveKitRoomGateway(context)
        roomGateway = gateway
        activeRoomStatus = "正在获取房间凭证"
        runCatching {
            val credentials = api.liveKitToken(session.token, liveSessionId)
            gateway.join(credentials.url, credentials.token)
            if (room.creatorId == session.id) gateway.publishCameraAndMicrophone()
        }.onSuccess {
            activeRoomStatus = if (room.creatorId == session.id) "直播已连接 · 正在发布摄像头和麦克风" else "已加入直播间"
        }.onFailure {
            gateway.leave()
            roomGateway = null
            activeRoomStatus = "媒体连接失败 · ${it.message ?: "请检查 LiveKit 服务"}"
        }
    }

    val requestMediaPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) {
            scope.launch {
                startingLive = true
                var sessionId: Long? = null
                var registration: BilldRoomRegistration? = null
                try {
                    sessionId = api.startLive(session.token)
                    registration = BilldRoomRegistration()
                    withTimeout(15_000) {
                        registration.register(session.token, session.id, session.username, sessionId.toString(), "${session.username} 的直播间")
                    }
                    roomRegistration = registration
                    activeRoom = LiveRoom(session.username, "${session.username} 的直播间", session.id, liveRoomColors[0], sessionId.toString())
                    refreshKey++
                } catch (error: Exception) {
                    registration?.close()
                    sessionId?.let { runCatching { api.closeLive(session.token, it) } }
                    notify("开播失败 · ${error.message ?: "服务不可用"}")
                } finally {
                    startingLive = false
                }
            }
        } else {
            notify("开播需要相机和麦克风权限")
        }
    }

    fun leaveRoom() {
        val room = activeRoom ?: return
        scope.launch {
            val id = room.stream.toLongOrNull()
            val isHost = room.creatorId == session.id
            try {
                if (isHost && id != null) api.closeLive(session.token, id)
                if (isHost) roomRegistration?.close()
                roomGateway?.leave()
                activeRoom = null
                roomGateway = null
                roomRegistration = null
                activeRoomStatus = "已离开"
                if (isHost) refreshKey++
            } catch (error: Exception) {
                notify("结束直播失败 · ${error.message ?: "请求失败"}")
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(NightInk),
        containerColor = NightInk,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = NightSurface, modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                val tabs = listOf("直播", "榜单", "消息", "我的", "运营")
                val icons = listOf(Icons.Default.Home, Icons.Default.EmojiEvents, Icons.Default.ChatBubbleOutline, Icons.Default.PersonOutline, Icons.Default.Shield)
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icons[index], contentDescription = title) },
                        label = { Text(title) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TopBar(
                username = session.username,
                status = roomsStatus,
                onRefresh = { refreshKey++ },
                onLogout = onLogout,
            )
            when (selectedTab) {
                0 -> LiveScreen(
                    rooms = rooms,
                    loading = roomsLoading,
                    status = roomsStatus,
                    starting = startingLive,
                    onStart = {
                        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        ) {
                            requestMediaPermissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                        } else {
                            requestMediaPermissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                        }
                    },
                    onJoin = { activeRoom = it },
                    onRefresh = { refreshKey++ },
                )
                1 -> RankingsScreen(
                    entries = rankingEntries,
                    loading = rankingsLoading,
                    error = rankingsError,
                    contributors = rankTypeContributors,
                    period = rankPeriod,
                    onContributors = { rankTypeContributors = it },
                    onPeriod = { rankPeriod = it },
                )
                2 -> MessagesScreen(
                    status = openImStatus,
                    conversations = openImConversations,
                    recipientId = openImRecipient,
                    message = openImMessage,
                    busy = openImBusy,
                    ready = openImReady,
                    onRecipientId = { openImRecipient = it.filter(Char::isDigit) },
                    onMessage = { openImMessage = it },
                    onRefresh = {
                        scope.launch {
                            openImBusy = true
                            runCatching { openImGateway.conversations() }
                                .onSuccess { openImConversations = parseOpenImConversations(it) }
                                .onFailure { openImStatus = "会话列表读取失败 · ${it.message ?: "请求失败"}" }
                            openImBusy = false
                        }
                    },
                    onConnect = {
                        scope.launch {
                            openImBusy = true
                            openImStatus = "正在连接 OpenIM"
                            runCatching {
                                val credentials = api.openImSession(session.token)
                                openImGateway.login(credentials)
                                openImGateway.conversations()
                            }.onSuccess { raw ->
                                openImReady = true
                                openImConversations = parseOpenImConversations(raw)
                                openImStatus = "OpenIM 已登录 · ${openImConversations.size} 个会话"
                            }.onFailure {
                                openImReady = false
                                openImStatus = "OpenIM 连接失败 · ${it.message ?: "服务不可用"}"
                            }
                            openImBusy = false
                        }
                    },
                    onSend = {
                        val recipient = openImRecipient.trim()
                        val content = openImMessage.trim()
                        if (recipient.isNotBlank() && content.isNotBlank()) {
                            scope.launch {
                                openImBusy = true
                                runCatching { openImGateway.sendText(recipient, content) }
                                    .onSuccess {
                                        openImMessage = ""
                                        openImStatus = "消息已由 OpenIM 接收"
                                        runCatching { openImGateway.conversations() }.onSuccess { openImConversations = parseOpenImConversations(it) }
                                    }
                                    .onFailure { openImStatus = "消息发送失败 · ${it.message ?: "请求失败"}" }
                                openImBusy = false
                            }
                        }
                    },
                )
                3 -> ProfileScreen(
                    session = session,
                    wallet = wallet,
                    level = level,
                    daily = dailyEarnings,
                    monthly = monthlyEarnings,
                    onRefresh = { refreshKey++ },
                    onTopUp = { amount ->
                        scope.launch {
                            runCatching { api.rechargeUrl(session.token, amount) }
                                .onSuccess { url ->
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                        .onFailure { notify("无法打开支付页面 · ${it.message ?: "无可用浏览器"}") }
                                }
                                .onFailure { notify("充值暂不可用 · ${it.message ?: "请检查支付宝配置"}") }
                        }
                    },
                )
                else -> OperationsScreen(rooms, roomsLoading, roomsStatus)
            }
        }
    }

    activeRoom?.let { room ->
        ModalBottomSheet(
            onDismissRequest = { leaveRoom() },
            containerColor = NightSurface,
            dragHandle = { Surface(Modifier.padding(vertical = 10.dp).size(34.dp, 4.dp).clip(RoundedCornerShape(4.dp)), color = NightLine) {} },
        ) {
            RoomSheet(
                room = room,
                gateway = roomGateway,
                status = activeRoomStatus,
                wallet = wallet,
                gifts = gifts,
                giftLoading = giftLoading,
                isHost = room.creatorId == session.id,
                onClose = { leaveRoom() },
                onGift = { gift ->
                    val id = room.stream.toLongOrNull()
                    if (id == null || room.creatorId <= 0) {
                        notify("该房间没有可用的 Revenue 会话，无法送礼")
                    } else {
                        scope.launch {
                            giftLoading = true
                            runCatching { api.sendGift(session.token, room.creatorId, id, gift.id, 1) }
                                .onSuccess { wallet = it; notify("礼物已由服务端确认") }
                                .onFailure { notify("送礼失败 · ${it.message ?: "请求失败"}") }
                            giftLoading = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun TopBar(username: String, status: String, onRefresh: () -> Unit, onLogout: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("现场 Live", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("$username · $status", style = MaterialTheme.typography.labelMedium, color = Fog, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "刷新") }
        IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, contentDescription = "退出登录") }
    }
}

@Composable
private fun LiveScreen(
    rooms: List<LiveRoom>,
    loading: Boolean,
    status: String,
    starting: Boolean,
    onStart: () -> Unit,
    onJoin: (LiveRoom) -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = NightSurfaceRaised) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("正在发生", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = Fog)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onStart, enabled = !starting) {
                            if (starting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("开始直播")
                        }
                        OutlinedButton(onClick = onRefresh) { Text("刷新") }
                    }
                }
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Coral, trackColor = NightSurfaceRaised) }
        if (!loading && rooms.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LiveTv, contentDescription = null, tint = Fog, modifier = Modifier.size(40.dp))
                    Text("暂时没有在线直播", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (rooms.isNotEmpty()) item { Text("正在直播", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        items(rooms, key = { it.stream }) { room -> RoomTile(room, onClick = { onJoin(room) }) }
    }
}

@Composable
private fun RoomTile(room: LiveRoom, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = NightSurface) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(76.dp, 82.dp).clip(RoundedCornerShape(12.dp)).background(room.accent), contentAlignment = Alignment.BottomStart) {
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        LiveDot(true, 7)
                        Text("LIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text("直播", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(room.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("主播 ${room.host}", style = MaterialTheme.typography.bodySmall, color = Fog)
                Text("会话 ${room.stream}", style = MaterialTheme.typography.labelSmall, color = Fog)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "进入直播间", tint = Fog, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun RoomSheet(
    room: LiveRoom,
    gateway: LiveKitRoomGateway?,
    status: String,
    wallet: Wallet?,
    gifts: List<Gift>,
    giftLoading: Boolean,
    isHost: Boolean,
    onClose: () -> Unit,
    onGift: (Gift) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(room.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${room.host} · ${room.stream}", style = MaterialTheme.typography.bodySmall, color = Fog)
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = if (isHost) "结束直播" else "离开直播间") }
            }
        }
        item {
            if (gateway != null) LiveKitVideo(gateway)
            else Surface(shape = RoundedCornerShape(14.dp), color = NightSurfaceRaised) {
                Box(Modifier.fillMaxWidth().height(196.dp), contentAlignment = Alignment.Center) {
                    Text(status, color = Fog, modifier = Modifier.padding(16.dp))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Mint, modifier = Modifier.size(18.dp))
                    Text(wallet?.balance?.let { "$it 金币" } ?: "余额暂不可用", fontWeight = FontWeight.SemiBold)
                }
                Text(status, style = MaterialTheme.typography.labelSmall, color = Fog, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (gifts.isEmpty()) {
            item { Text("礼物目录读取失败或暂时为空", style = MaterialTheme.typography.bodySmall, color = Fog) }
        } else {
            item { Text("礼物", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(gifts, key = { it.id }) { gift ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(gift.name, fontWeight = FontWeight.SemiBold)
                        Text("${gift.coinPrice} 金币 · ${gift.diamonds} 钻石", style = MaterialTheme.typography.bodySmall, color = Fog)
                    }
                    OutlinedButton(onClick = { onGift(gift) }, enabled = !giftLoading && !isHost) {
                        Text(if (giftLoading) "提交中" else "赠送")
                    }
                }
                HorizontalDivider(color = NightLine)
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun LiveKitVideo(gateway: LiveKitRoomGateway) {
    AndroidView(
        factory = { context -> gateway.createVideoRenderer(context) },
        update = { gateway.attachVideoRenderer(it) },
        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black),
    )
}

@Composable
private fun RankingsScreen(
    entries: List<RankingEntry>,
    loading: Boolean,
    error: String?,
    contributors: Boolean,
    period: String,
    onContributors: (Boolean) -> Unit,
    onPeriod: (String) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(if (contributors) "贡献榜" else "主播榜", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Revenue 服务实时数据", style = MaterialTheme.typography.bodySmall, color = Fog, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!contributors, { onContributors(false) }, label = { Text("主播") })
                FilterChip(contributors, { onContributors(true) }, label = { Text("贡献者") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilterChip(period == "daily", { onPeriod("daily") }, label = { Text("日榜") })
                FilterChip(period == "monthly", { onPeriod("monthly") }, label = { Text("月榜") })
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Coral, trackColor = NightSurfaceRaised) }
        error?.let { item { Text("榜单读取失败 · $it", color = Coral, style = MaterialTheme.typography.bodySmall) } }
        if (!loading && error == null && entries.isEmpty()) item { Text("当前周期暂无榜单数据", color = Fog) }
        items(entries.indices.toList()) { index ->
            val entry = entries[index]
            Row(Modifier.fillMaxWidth().height(66.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", Modifier.width(32.dp), color = if (index < 3) Amber else Fog, fontWeight = FontWeight.Bold)
                Avatar("${entry.userId}", index)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("用户 #${entry.userId}", fontWeight = FontWeight.SemiBold)
                    Text("${entry.gifts} 件礼物", style = MaterialTheme.typography.bodySmall, color = Fog)
                }
                Text("${entry.value} ${if (contributors) "金币" else "钻石"}", color = Mint, style = MaterialTheme.typography.bodyMedium)
            }
            if (index < entries.lastIndex) HorizontalDivider(color = NightLine)
        }
    }
}

@Composable
private fun MessagesScreen(
    status: String,
    conversations: List<OpenImConversation>,
    recipientId: String,
    message: String,
    busy: Boolean,
    ready: Boolean,
    onRecipientId: (String) -> Unit,
    onMessage: (String) -> Unit,
    onRefresh: () -> Unit,
    onConnect: () -> Unit,
    onSend: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("消息", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(status, style = MaterialTheme.typography.bodySmall, color = Fog, modifier = Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                Button(onClick = onConnect, enabled = !busy) { Text(if (ready) "重新登录" else "连接 OpenIM") }
                OutlinedButton(onClick = onRefresh, enabled = ready && !busy) { Text("刷新会话") }
            }
        }
        item {
            Text("发送单聊消息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = recipientId,
                onValueChange = onRecipientId,
                label = { Text("收件人用户 ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = message,
                onValueChange = onMessage,
                label = { Text("消息内容") },
                enabled = ready,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Button(onClick = onSend, enabled = ready && !busy && recipientId.isNotBlank() && message.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) {
                Text(if (busy) "处理中" else "发送")
            }
        }
        item { Text("真实会话", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (conversations.isEmpty()) {
            item { Text(if (ready) "当前没有会话" else "连接 OpenIM 后读取会话", color = Fog) }
        } else {
            items(conversations, key = { it.userId }) { conversation ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(conversation.name, conversation.userId.hashCode())
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(conversation.name, fontWeight = FontWeight.SemiBold)
                        Text(conversation.latestMessage, style = MaterialTheme.typography.bodySmall, color = Fog, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(conversation.userId, style = MaterialTheme.typography.labelSmall, color = Fog)
                }
                HorizontalDivider(color = NightLine)
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    session: UserSession,
    wallet: Wallet?,
    level: UserLevel?,
    daily: Earnings?,
    monthly: Earnings?,
    onRefresh: () -> Unit,
    onTopUp: (Int) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(session.username, 1, 56)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(session.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("账号 ID ${session.id}", style = MaterialTheme.typography.bodySmall, color = Fog, modifier = Modifier.padding(top = 4.dp))
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "刷新账户信息") }
            }
            Text(
                level?.let { "用户等级 Lv.${it.viewerLevel} · 主播等级 Lv.${it.creatorLevel}" } ?: "等级信息暂不可用",
                style = MaterialTheme.typography.bodySmall,
                color = Fog,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = NightSurfaceRaised) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Mint, modifier = Modifier.size(20.dp))
                            Text("金币余额", color = Fog)
                        }
                        Text(wallet?.balance?.toString() ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Mint)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(100, 500, 1000).forEach { amount ->
                            OutlinedButton(onClick = { onTopUp(amount) }, modifier = Modifier.weight(1f)) { Text("+$amount") }
                        }
                    }
                    Text("到账以支付通知和服务端账本为准", style = MaterialTheme.typography.bodySmall, color = Fog)
                }
            }
        }
        item { Text("主播收益", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("今日钻石", daily?.diamonds?.toString() ?: "—", Modifier.weight(1f))
                Metric("本月钻石", monthly?.diamonds?.toString() ?: "—", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("今日礼物", daily?.gifts?.toString() ?: "—", Modifier.weight(1f))
                Metric("本月礼物", monthly?.gifts?.toString() ?: "—", Modifier.weight(1f))
            }
        }
        item {
            level?.let { Text("累计消费 ${it.viewerPoints} 积分 · 主播积分 ${it.creatorPoints}", style = MaterialTheme.typography.bodySmall, color = Fog) }
        }
    }
}

@Composable
private fun OperationsScreen(rooms: List<LiveRoom>, loading: Boolean, status: String) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("运营", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("$status\n当前后端未提供运营审核、封禁或数据汇总接口。", style = MaterialTheme.typography.bodySmall, color = Fog, modifier = Modifier.padding(top = 8.dp))
        }
        item { Text("在线直播间 · ${if (loading) "…" else rooms.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (rooms.isEmpty() && !loading) item { Text("当前没有在线直播", color = Fog) }
        items(rooms, key = { it.stream }) { room ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                LiveDot(true)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(room.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${room.host} · 会话 ${room.stream}", style = MaterialTheme.typography.bodySmall, color = Fog)
                }
            }
            HorizontalDivider(color = NightLine)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = NightSurface) {
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

private fun readSession(context: android.content.Context): UserSession? {
    val prefs = context.getSharedPreferences("live_session", android.content.Context.MODE_PRIVATE)
    val id = prefs.getLong("id", 0)
    val token = prefs.getString("token", null).orEmpty()
    return if (id > 0 && token.isNotBlank()) UserSession(id, prefs.getString("username", "用户$id").orEmpty(), token) else null
}

private fun saveSession(context: android.content.Context, session: UserSession?) {
    context.getSharedPreferences("live_session", android.content.Context.MODE_PRIVATE).edit().apply {
        if (session == null) clear() else {
            putLong("id", session.id)
            putString("username", session.username)
            putString("token", session.token)
        }
    }.apply()
}

private fun parseOpenImConversations(raw: String): List<OpenImConversation> = runCatching {
    val value = org.json.JSONTokener(raw).nextValue()
    val rows = when (value) {
        is org.json.JSONArray -> value
        is org.json.JSONObject -> value.optJSONArray("conversationList")
            ?: value.optJSONArray("conversations")
            ?: value.optJSONArray("data")
            ?: org.json.JSONArray()
        else -> org.json.JSONArray()
    }
    buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val userId = row.optString("userID").ifBlank { row.optString("groupID") }
            if (userId.isBlank()) continue
            val latest = row.optString("latestMsg")
            val message = runCatching { org.json.JSONObject(latest) }.getOrNull()
            val text = message?.optJSONObject("textElem")?.optString("content")
                ?: message?.optString("content")
                ?: latest
            add(OpenImConversation(
                userId = userId,
                name = row.optString("showName").ifBlank { "用户 $userId" },
                latestMessage = text,
            ))
        }
    }
}.getOrDefault(emptyList())
