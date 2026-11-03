package cn.liveroom.app

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

private val NightInk = Color(0xFFF7F8FA)
private val NightSurface = Color.White
private val NightSurfaceRaised = Color(0xFFF0F2F5)
private val NightLine = Color(0xFFE2E5EA)
private val Fog = Color(0xFF667085)
private val Coral = Color(0xFFCF343D)
private val Mint = Color(0xFF08785E)
private val Amber = Color(0xFF995600)
private val Ink = Color(0xFF17191C)

private data class LiveRoom(
    val host: String,
    val title: String,
    val creatorId: Long,
    val stream: String,
    val coverPath: String? = null,
)

private data class WatchGateUi(
    val phase: WatchPhase,
    val shownRemaining: Int,
    val priceCoins: Long?,
    val error: String?,
    val busy: Boolean,
)

private data class OpenImConversation(val userId: String, val name: String, val latestMessage: String)
private data class PendingDmCall(val peerId: String, val peerName: String?, val requestedMode: String, val callId: String?)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LiveRoomApp() }
    }
}

@Composable
private fun LiveRoomApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(readSession(context)) }
    var loggingOut by remember { mutableStateOf(false) }
    var logoutError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val api = LivePlatformApi()
        while (true) {
            try { flushLogout(context, api) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Durable pending logout retries while the app is running. */ }
            kotlinx.coroutines.delay(10_000)
        }
    }
    LaunchedEffect(session?.token) {
        val token = session?.token ?: return@LaunchedEffect
        val api = LivePlatformApi()
        while (true) {
            try {
                val account = api.currentAccount(token)
                if (session?.token == token) {
                    saveSession(context, account)
                    session = account
                }
            } catch (e: CancellationException) { throw e
            } catch (e: PlatformHttpException) {
                if (e.statusCode == 401 && session?.token == token) {
                    saveSession(context, null)
                    session = null
                    return@LaunchedEffect
                }
            } catch (_: Exception) { /* A temporary outage does not prove the session is invalid. */ }
            kotlinx.coroutines.delay(30_000)
        }
    }
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Coral,
            onPrimary = Color.White,
            secondary = Mint,
            onSecondary = Color.White,
            tertiary = Amber,
            background = NightInk,
            onBackground = Ink,
            surface = NightSurface,
            onSurface = Ink,
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
                    val token = session?.token
                    if (!loggingOut && token != null) {
                        loggingOut = true
                        scope.launch {
                            try {
                                queueLogout(context, token)
                                if (session?.token == token) session = null
                                try { flushLogout(context, LivePlatformApi()) }
                                catch (error: CancellationException) { throw error }
                                catch (_: Exception) { /* Retried from the durable pending record. */ }
                            } catch (error: CancellationException) { throw error
                            } catch (error: Exception) { logoutError = error.message ?: "退出未完成，请重试"
                            } finally { loggingOut = false }
                        }
                    }
                },
            )
        }
        logoutError?.let { message ->
            AlertDialog(onDismissRequest = { logoutError = null }, title = { Text("退出未完成") },
                text = { Text(message) }, confirmButton = { TextButton(onClick = { logoutError = null }) { Text("知道了") } })
        }
    }
}

@Composable
private fun AuthScreen(onAuthenticated: (UserSession) -> Unit) {
    val context = LocalContext.current
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
                        try { flushLogout(context, api) }
                        catch (cause: CancellationException) { throw cause }
                        catch (_: Exception) { error("上次退出尚未同步，请联网后重试") }
                        if (registering) {
                            val created = api.register(username, password)
                            val loggedIn = api.login(created.id, password)
                            onAuthenticated(loggedIn)
                        } else {
                            val id = accountId.toLongOrNull() ?: error("账号 ID 格式无效")
                            onAuthenticated(api.login(id, password))
                        }
                    } catch (cause: CancellationException) { throw cause
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
    val giftOutbox = remember(session.id) { GiftOutbox(context, session.id) }
    val effectSession = remember(session.id) { GiftEffectSession(context) }
    DisposableEffect(effectSession) { onDispose { effectSession.close() } }
    val openImGateway = remember(context) { OpenImGateway(context) }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var canManageOperations by remember { mutableStateOf(false) }
    var operationsSummary by remember { mutableStateOf<OperationsSummary?>(null) }
    var operationsUsers by remember { mutableStateOf<OperationsUsers?>(null) }
    var showModeration by remember { mutableStateOf(false) }
    var showPasswordChange by remember { mutableStateOf(false) }
    var showCoverUpload by remember { mutableStateOf(false) }
    var showIdentity by remember { mutableStateOf(false) }
    var showRoomChat by remember { mutableStateOf(false) }
    var showBusiness by remember { mutableStateOf<String?>(null) }
    var showCohost by remember { mutableStateOf(false) }
    var cohostMode by remember { mutableStateOf<String?>(null) }
    var cohostKnown by remember { mutableStateOf(false) }
    var operationsPage by remember { mutableIntStateOf(1) }
    var operationsRefresh by remember { mutableIntStateOf(0) }
    var operationsLoading by remember { mutableStateOf(false) }
    var operationsError by remember { mutableStateOf<String?>(null) }

    var refreshKey by remember { mutableIntStateOf(0) }
    var rooms by remember { mutableStateOf(emptyList<LiveRoom>()) }
    var roomsLoading by remember { mutableStateOf(true) }
    var roomsError by remember { mutableStateOf<String?>(null) }
    var gifts by remember { mutableStateOf(emptyList<Gift>()) }
    var wallet by remember { mutableStateOf<Wallet?>(null) }
    var level by remember { mutableStateOf<UserLevel?>(null) }
    var dailyEarnings by remember { mutableStateOf<Earnings?>(null) }
    var monthlyEarnings by remember { mutableStateOf<Earnings?>(null) }
    var activeRoom by remember { mutableStateOf<LiveRoom?>(null) }
    var activeRoomStatus by remember { mutableStateOf("正在连接媒体服务") }
    var roomGateway by remember { mutableStateOf<LiveKitRoomGateway?>(null) }
    var srsIngest by remember { mutableStateOf<SrsIngestPublisher?>(null) }
    val cameraHandoff = remember { Mutex() }
    var hostCohostLiveKit by remember { mutableStateOf(false) }
    var roomRegistration by remember { mutableStateOf<BilldRoomRegistration?>(null) }
    var activeMatchId by remember { mutableStateOf<Long?>(null) }
    var matchScores by remember { mutableStateOf(emptyList<MatchScore>()) }
    var matchLoading by remember { mutableStateOf(false) }
    var matchInvitations by remember { mutableStateOf(emptyList<MatchInvitation>()) }
    var startingLive by remember { mutableStateOf(false) }
    var giftLoading by remember { mutableStateOf(false) }
    var showGiftHistory by remember { mutableStateOf(false) }
    var giftHistory by remember { mutableStateOf<GiftHistory?>(null) }
    var giftHistoryLoading by remember { mutableStateOf(false) }
    var giftHistoryError by remember { mutableStateOf<String?>(null) }

    var pendingGift by remember(session.id) { mutableStateOf(giftOutbox.load()) }
    var rankTypeContributors by remember { mutableStateOf(false) }
    var rankPeriod by remember { mutableStateOf("daily") }
    var rankingEntries by remember { mutableStateOf(emptyList<RankingEntry>()) }
    var rankingsLoading by remember { mutableStateOf(false) }
    var rankingsError by remember { mutableStateOf<String?>(null) }
    var openImStatus by remember { mutableStateOf("正在连接消息服务") }
    var messageConnectionAttempt by remember { mutableIntStateOf(0) }
    var openImConversations by remember { mutableStateOf(emptyList<OpenImConversation>()) }
    var openImRecipient by remember { mutableStateOf("") }
    var openImRecipientName by remember { mutableStateOf<String?>(null) }
    var openImMessage by remember { mutableStateOf("") }
    var openImBusy by remember { mutableStateOf(false) }
    var openImReady by remember { mutableStateOf(false) }
    var activeCall by remember { mutableStateOf<PendingDmCall?>(null) }
    val messageVersion by openImGateway.messageVersion.collectAsState()
    val messageConnection by openImGateway.connection.collectAsState()
    var history by remember { mutableStateOf<OpenImHistory?>(null) }
    var historyRecipient by remember { mutableStateOf("") }
    val historyMutex = remember { Mutex() }
    var historyError by remember { mutableStateOf<String?>(null) }
    var historyLoading by remember { mutableStateOf(false) }
    var historyRefresh by remember { mutableIntStateOf(0) }
    var chatVisible by remember { mutableStateOf(false) }
    var chatOpenSignal by remember { mutableIntStateOf(0) }
    val dmGiftOutbox = remember(session.id) { DmGiftOutbox(context, session.id) }
    val dmVideoOutbox = remember(session.id) { DmVideoOutbox(context, session.id) }
    val dmUnlockIds = remember(session.id) { DmUnlockIds(context, session.id) }
    var pendingDmGift by remember(session.id) { mutableStateOf(dmGiftOutbox.load()) }
    var pendingDmVideo by remember(session.id) { mutableStateOf(dmVideoOutbox.load()) }
    var supportContact by remember { mutableStateOf<ChatRecipient?>(null) }
    var playableVideos by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var playingVideo by remember { mutableStateOf<java.io.File?>(null) }
    var rechargeBusy by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var profileBusy by remember { mutableStateOf(false) }
    var rechargeOrders by remember { mutableStateOf(emptyList<RechargeOrder>()) }
    var rechargeOrdersError by remember { mutableStateOf<String?>(null) }
    var roomFeedback by remember { mutableStateOf<String?>(null) }
    var playbackStream by remember(activeRoom?.stream) { mutableStateOf<String?>(null) }
    var viewerGate by remember(activeRoom?.stream) {
        mutableStateOf(if (activeRoom != null && activeRoom?.creatorId != session.id) WatchGateUi(WatchPhase.CHECKING, 0, null, null, false) else null)
    }
    val unlockStore = remember(session.id) { WatchUnlockStore(context, session.id) }
    val watchCommands = remember(activeRoom?.stream) { Channel<WatchCommand>(capacity = 8) }
    val snackbar = remember { SnackbarHostState() }
    val notify: (String) -> Unit = { message ->
        if (activeRoom != null) roomFeedback = message
        else scope.launch { snackbar.showSnackbar(message, duration = SnackbarDuration.Short) }
    }

    val loadGiftHistory: (Boolean) -> Unit = { older ->
        if (!giftHistoryLoading) {
            giftHistoryLoading = true
            giftHistoryError = null
            scope.launch {
                try {
                    val current = giftHistory
                    val cursor = if (older) current?.nextCursor.orEmpty() else ""
                    val page = api.giftHistory(session.token, cursor)
                    giftHistory = if (older && current != null) page.copy(orders = (current.orders + page.orders).distinctBy { it.id }) else page
                } catch (error: CancellationException) { throw error
                } catch (_: Exception) { giftHistoryError = "送礼记录暂时无法加载，请重试"
                } finally { giftHistoryLoading = false }
            }
        }
    }

    val submitGift: (PendingGift) -> Unit = { intent ->
        if (!giftLoading) {
            giftLoading = true
            scope.launch {
                try {
                    val recovering = pendingGift != null
                    if (pendingGift == null) {
                        giftOutbox.save(intent)
                        pendingGift = intent
                    }
                    check(pendingGift == intent) { "请先核实上一笔送礼" }
                    wallet = if (recovering) api.recoverGift(session.token, intent) else api.sendGift(session.token, intent)
                    giftOutbox.clear()
                    pendingGift = null
                    effectSession.offerAccepted(intent, activeRoom?.stream)
                    notify("礼物已由服务端确认")
                    // A replay returns the original post-debit balance; refresh the current wallet.
                    runCatching { api.wallet(session.token) }.onSuccess { wallet = it }
                } catch (error: GiftRejectedException) {
                    runCatching { giftOutbox.clear() }.onSuccess { pendingGift = null }
                    notify(error.message ?: "服务端已拒绝，本次未扣币")
                } catch (error: CancellationException) { throw error
                } catch (error: Exception) {
                    notify(if (pendingGift != null) "结果未确认，请重试核实原送礼。重试不会重复扣币。" else error.message ?: "无法保存送礼请求")
                } finally { giftLoading = false }
            }
        }
    }

    DisposableEffect(openImGateway) { onDispose { openImGateway.close() } }

    LaunchedEffect(session.token, selectedTab, refreshKey) {
        do {
            try {
                canManageOperations = api.canManageOperations(session.token)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { canManageOperations = false }
            if (!canManageOperations) {
                operationsSummary = null
                operationsUsers = null
                if (selectedTab == 4) { selectedTab = 3; notify("运营权限暂不可用，请刷新后重试") }
            } else if (selectedTab == 4) operationsRefresh++
            if (selectedTab != 4) break
            kotlinx.coroutines.delay(15_000)
        } while (true)
    }

    LaunchedEffect(canManageOperations, selectedTab, operationsPage, operationsRefresh) {
        if (!canManageOperations || selectedTab != 4) return@LaunchedEffect
        operationsLoading = true
        operationsError = null
        // Clear the previous page while changing page, rather than label old rows as new.
        if (operationsUsers?.page != operationsPage) operationsUsers = null
        try {
            val summary = api.operationsSummary(session.token)
            val users = api.operationsUsers(session.token, operationsPage)
            operationsSummary = summary
            operationsUsers = users
        } catch (error: CancellationException) { throw error
        } catch (_: Exception) {
            operationsSummary = null
            operationsUsers = null
            operationsError = "运营数据暂不可用，请检查权限或连接后重试"
        } finally { operationsLoading = false }
    }


    LaunchedEffect(session.token, messageConnectionAttempt) {
        openImBusy = true
        openImStatus = "正在连接消息服务"
        try {
            withTimeout(20_000) {
                openImGateway.login(api.openImSession(session.token))
                openImGateway.connection.first { it == MessageConnection.CONNECTED }
                openImReady = true
                openImConversations = parseOpenImConversations(openImGateway.conversations())
            }
            openImStatus = "消息已连接"
            supportContact = try { api.openImSupport(session.token) } catch (error: CancellationException) { throw error } catch (_: Exception) { null }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            openImReady = false
            openImStatus = "连接超时，请重试"
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            openImReady = false
            openImStatus = "消息暂不可用，请重试"
        } finally {
            openImBusy = false
        }
    }

    LaunchedEffect(messageConnection) {
        if (messageConnection != MessageConnection.CONNECTED) {
            openImReady = false
            openImStatus = when (messageConnection) {
                MessageConnection.CONNECTING -> "正在重新连接消息服务"
                MessageConnection.SESSION_EXPIRED -> "消息登录已失效，请重新连接"
                else -> "消息连接已断开，正在等待恢复"
            }
        } else {
            openImReady = true
            openImStatus = "消息已连接"
        }
    }
    LaunchedEffect(history, openImReady, session.token) {
        if (!openImReady) return@LaunchedEffect
        val ids = history?.messages.orEmpty().mapNotNull { message ->
            (chatBody(message) as? ChatBody.Video)?.videoId
        }.distinct()
        if (ids.isEmpty()) return@LaunchedEffect
        val next = playableVideos.toMutableMap()
        for (id in ids) {
            val info = try { api.dmVideo(session.token, id) } catch (error: CancellationException) { throw error } catch (_: Exception) { null }
            if (info != null) next[id] = info.optBoolean("playable")
        }
        playableVideos = next
    }

    val deliverCustom: suspend (String, String, String, String) -> Unit = { recipient, data, extension, description ->
        openImGateway.sendCustom(recipient, data, extension, description)
        historyRefresh++
        runCatching { openImGateway.conversations() }.onSuccess { openImConversations = parseOpenImConversations(it) }
    }
    val sendDmGift: (String) -> Unit = { giftId ->
        val recipient = openImRecipient.trim()
        val pending = pendingDmGift
        if (recipient.isBlank() || openImBusy) return@sendDmGift
        if (pending != null && (pending.recipientId != recipient || (pending.phase == "settle" && pending.giftId != giftId))) {
            openImStatus = "请先完成上一笔私信礼物"
            return@sendDmGift
        }
        scope.launch {
            openImBusy = true
            try {
                val requestId = pending?.requestId ?: java.util.UUID.randomUUID().toString()
                var current = pending ?: DmGiftOutbox.Pending("settle", recipient, requestId, giftId).also {
                    dmGiftOutbox.save(it)
                    pendingDmGift = it
                }
                if (current.phase != "deliver") {
                    val data = api.sendDmGift(session.token, recipient, giftId, requestId)
                    val delivery = giftDelivery(data, requestId) ?: error("结算回执无法识别，未发送礼物消息")
                    if (delivery.balance != null) wallet = Wallet(delivery.balance) else runCatching { wallet = api.wallet(session.token) }
                    current = DmGiftOutbox.Pending("deliver", recipient, requestId, giftId, delivery.data, delivery.description, delivery.extension)
                    dmGiftOutbox.save(current)
                    pendingDmGift = current
                }
                deliverCustom(recipient, current.imData, current.extension, current.description)
                dmGiftOutbox.clear()
                pendingDmGift = null
                openImStatus = "礼物消息已发送"
            } catch (error: SettlementUnavailableException) {
                runCatching { dmGiftOutbox.clear() }.onSuccess { pendingDmGift = null }
                openImStatus = error.message ?: "私信送礼结算尚未接通，本次未扣币，也未发送礼物消息"
            } catch (error: GiftRejectedException) {
                runCatching { dmGiftOutbox.clear() }.onSuccess { pendingDmGift = null }
                openImStatus = error.message ?: "本次未扣币"
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                openImStatus = error.message ?: "礼物结果未确认，请重试同一笔，不要重新赠送"
            } finally { openImBusy = false }
        }
    }
    val sendDmVideo: (String, Int, ByteArray, Int) -> Unit = { title, price, bytes, duration ->
        val recipient = openImRecipient.trim()
        val pending = pendingDmVideo
        if (recipient.isBlank() || openImBusy) return@sendDmVideo
        if (pending != null && pending.recipientId != recipient) {
            openImStatus = "请先完成上一条视频消息"
            return@sendDmVideo
        }
        scope.launch {
            openImBusy = true
            try {
                val requestId = pending?.requestId ?: java.util.UUID.randomUUID().toString()
                var current = pending ?: DmVideoOutbox.Pending("upload", recipient, requestId).also {
                    dmVideoOutbox.save(it)
                    pendingDmVideo = it
                }
                if (current.phase != "deliver") {
                    val data = api.uploadDmVideo(session.token, recipient, requestId, title, price, duration, bytes)
                    val delivery = videoDelivery(data, requestId) ?: error("视频回执无法识别，未发送视频消息")
                    current = DmVideoOutbox.Pending("deliver", recipient, requestId, delivery.data, delivery.description, delivery.extension)
                    dmVideoOutbox.save(current)
                    pendingDmVideo = current
                }
                deliverCustom(recipient, current.imData, current.extension, current.description)
                dmVideoOutbox.clear()
                pendingDmVideo = null
                openImStatus = if (price == 0) "免费视频已发送" else "付费视频已发送，对方解锁后才扣费"
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                openImStatus = error.message ?: "视频消息未完成，请用同一单号重试"
            } finally { openImBusy = false }
        }
    }
    val unlockDmVideo: (String) -> Unit = { videoId ->
        if (!openImBusy) scope.launch {
            openImBusy = true
            try {
                val data = api.unlockDmVideo(session.token, videoId, dmUnlockIds.idFor(videoId))
                if (data.has("new_balance") && !data.isNull("new_balance")) wallet = Wallet(data.getLong("new_balance"))
                else runCatching { wallet = api.wallet(session.token) }
                playableVideos = playableVideos + (videoId to true)
                openImStatus = "视频已解锁"
            } catch (error: SettlementUnavailableException) {
                openImStatus = error.message ?: "付费视频解锁结算尚未接通，本次未扣币"
            } catch (error: GiftRejectedException) {
                openImStatus = error.message ?: "本次未扣币"
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                openImStatus = error.message ?: "解锁结果未确认，请用同一单号重试"
            } finally { openImBusy = false }
        }
    }
    val playDmVideo: (String) -> Unit = { videoId ->
        if (!openImBusy) scope.launch {
            openImBusy = true
            try {
                val dir = java.io.File(context.cacheDir, "dm-video").apply { mkdirs() }
                val dest = java.io.File(dir, "$videoId.mp4")
                api.downloadDmVideo(session.token, videoId, dest)
                playingVideo = dest
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { openImStatus = error.message ?: "暂时无法播放"
            } finally { openImBusy = false }
        }
    }
    val verifyDmGift: (ChatBody.Gift) -> Unit = { gift ->
        if (!openImBusy) scope.launch {
            openImBusy = true
            try {
                val data = api.verifyDmGift(session.token, gift.requestId)
                val charged = data.optLong("charged_coins")
                openImStatus = if (charged == gift.chargedCoins) "结算已确认扣费 $charged 金币" else "结算记录为 $charged 金币，与这条消息不一致"
            } catch (error: SettlementUnavailableException) {
                openImStatus = error.message ?: "私信送礼核对尚未接通"
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { openImStatus = error.message ?: "暂时无法核对回执"
            } finally { openImBusy = false }
        }
    }

    LaunchedEffect(messageVersion, openImReady) {
        if (openImReady) runCatching { withTimeout(10_000) { openImGateway.conversations() } }
            .onSuccess { openImConversations = parseOpenImConversations(it) }
    }
    LaunchedEffect(openImRecipient, openImReady, messageVersion, historyRefresh, chatVisible) {
        if (historyRecipient != openImRecipient) {
            history = null
            historyRecipient = openImRecipient
            historyError = null
        }
        if (!openImReady || openImRecipient.isBlank() || !chatVisible) return@LaunchedEffect
        val recipient = openImRecipient
        historyMutex.withLock {
            historyLoading = true
            historyError = null
            try {
                val updated = withTimeout(15_000) {
                    refreshHistoryWindow(history) { cursor -> openImGateway.history(recipient, cursor) }
                }
                if (openImRecipient == recipient && chatVisible) {
                    history = updated
                    runCatching { withTimeout(5_000) { openImGateway.markRead(updated.conversationId) } }
                }
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                historyError = "消息更新超时，已有记录已保留，请重试"
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { historyError = "消息暂时无法更新，已有记录已保留，请重试"
            } finally { historyLoading = false }
        }
    }

    LaunchedEffect(gifts) { effectSession.updateCatalog(gifts) }

    LaunchedEffect(activeRoom?.stream, session.token) {
        val roomId = activeRoom?.stream ?: return@LaunchedEffect
        effectSession.enterRoom(roomId)
        val channel = RoomGiftChannel()
        try {
            channel.listen(session.token, roomId) { effectSession.offerBroadcast(it) }
            awaitCancellation()
        } finally {
            channel.close()
            effectSession.leaveRoom()
        }
    }

    LaunchedEffect(session.token, refreshKey) {
        roomsLoading = true
        roomsError = null
        launch {
            try {
                val result = api.liveRooms()
                rooms = result.map { item -> LiveRoom(item.host, item.title, item.creatorId, item.roomId,item.coverPath) }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                roomsError = if (rooms.isEmpty()) "暂时无法加载直播，请检查网络后重试" else "更新失败，以下是上次加载的直播，可能已结束"
            } finally { roomsLoading = false }
        }
        launch { runCatching { api.giftCatalog() }.onSuccess { gifts = it } }
        launch { runCatching { api.profile(session.token) }.onSuccess { profile = it } }
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

    LaunchedEffect(selectedTab, session.token, refreshKey) {
        if (selectedTab != 3) return@LaunchedEffect
        while (true) {
            try {
                rechargeOrders = api.rechargeOrders(session.token)
                rechargeOrdersError = null
                wallet = api.wallet(session.token)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { rechargeOrdersError = "充值记录暂时无法加载，请刷新重试" }
            kotlinx.coroutines.delay(5_000)
        }
    }

    val mediaRoute = liveMediaRoute(
        hasRoom = activeRoom != null,
        isHost = activeRoom?.creatorId == session.id,
        matchId = activeMatchId,
        viewerCohostKnown = cohostKnown,
        viewerCohostMode = cohostMode,
        hostCohostSeat = hostCohostLiveKit,
    )
    LaunchedEffect(activeRoom?.stream, session.token) {
        playbackStream = null
        viewerGate = WatchGateUi(WatchPhase.CHECKING, 0, null, null, false)
        val room = activeRoom
        if (room == null) {
            viewerGate = null
            return@LaunchedEffect
        }
        if (room.creatorId == session.id) {
            playbackStream = room.stream
            viewerGate = null
            return@LaunchedEffect
        }
        val liveId = room.stream.toLongOrNull()
        if (liveId == null) {
            viewerGate = WatchGateUi(WatchPhase.UNAVAILABLE, 0, null, "暂时无法确认是否需要付费观看", false)
            return@LaunchedEffect
        }
        val watch = WatchSession { UUID.randomUUID().toString() }
        var errorText: String? = null
        var anchor = SystemClock.elapsedRealtime()
        var consumed = 0
        var lastDeliverFailed = false
        var nextRetryAt = 0L
        fun publish(busy: Boolean = false) {
            val phase = watch.phase
            playbackStream = if (phase.allowsPlayback() && activeRoom?.stream == room.stream) room.stream else null
            viewerGate = WatchGateUi(phase, watch.shownRemaining, watch.access?.unlockPriceCoins, errorText, busy)
            if (!phase.allowsPlayback()) {
                activeRoomStatus = when (phase) {
                    WatchPhase.PAYWALL -> "免费试看已结束"
                    WatchPhase.SYNCING -> "正在核对观看时长"
                    WatchPhase.CHECKING -> "正在确认观看权限"
                    WatchPhase.UNAVAILABLE -> errorText ?: "无法确认付费观看状态"
                    else -> activeRoomStatus
                }
            }
        }
        fun applyAccess(access: WatchAccess) {
            watch.onAccess(access)
            if (watch.phase == WatchPhase.OPEN) runCatching { unlockStore.clear(liveId) }
            if (watch.phase == WatchPhase.PREVIEW) {
                anchor = SystemClock.elapsedRealtime()
                consumed = 0
            }
            lastDeliverFailed = false
            errorText = null
        }
        suspend fun unlockNow() {
            publish(busy = true)
            try {
                val existing = unlockStore.load(liveId)
                val requestId = existing ?: UUID.randomUUID().toString()
                if (existing == null) unlockStore.save(liveId, requestId)
                val updated = try {
                    api.watchUnlock(session.token, liveId, requestId)
                } catch (error: PlatformHttpException) {
                    throw error
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // A 200 with an unusable body is not success. Re-read access before playing.
                    api.watchAccess(session.token, liveId)
                }
                if (updated.status == WatchStatus.UNLOCKED || updated.status == WatchStatus.FREE) {
                    runCatching { unlockStore.clear(liveId) }
                    runCatching { wallet = api.wallet(session.token) }.onFailure { wallet = null }
                    applyAccess(updated)
                } else {
                    watch.onAccess(updated)
                    if (watch.phase == WatchPhase.PREVIEW) {
                        anchor = SystemClock.elapsedRealtime()
                        consumed = 0
                    }
                    errorText = if (watch.phase == WatchPhase.PAYWALL) "服务端尚未确认解锁，未继续播放" else null
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: PlatformHttpException) {
                errorText = if (error.statusCode == 404) "解锁接口暂不可用，未确认扣款" else error.message ?: "解锁未完成，未继续播放"
            } catch (error: IllegalStateException) {
                errorText = error.message ?: "无法保存解锁请求，未继续播放"
            } catch (_: Exception) {
                errorText = "解锁结果未确认，请重试。重试使用同一单号，不会重复扣款。"
            } finally {
                publish()
            }
        }
        suspend fun deliver(batch: HeartbeatBatch, startedAt: Long) {
            val updated = api.watchHeartbeat(session.token, liveId, batch.requestId, batch.deltaSeconds)
            watch.acknowledge(batch)
            if (updated.status != WatchStatus.PREVIEW) watch.dropPending()
            if (updated.status != WatchStatus.PREVIEW || !watch.hasUnsent()) {
                applyAccess(updated)
                if (watch.phase == WatchPhase.PREVIEW) anchor = startedAt
            }
            lastDeliverFailed = false
            errorText = null
        }
        publish()
        while (isActive && activeRoom?.stream == room.stream) {
            when (watch.phase) {
                WatchPhase.CHECKING, WatchPhase.UNAVAILABLE -> {
                    if (watch.phase == WatchPhase.UNAVAILABLE) {
                        if (watchCommands.receive() != WatchCommand.Retry) continue
                    }
                    errorText = null
                    watch.resetToChecking()
                    publish()
                    try {
                        applyAccess(api.watchAccess(session.token, liveId))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: PlatformHttpException) {
                        watch.onUnavailable()
                        errorText = if (error.statusCode == 404) "付费观看暂不可用，请稍后重试" else error.message ?: "无法确认付费观看状态"
                    } catch (_: Exception) {
                        watch.onUnavailable()
                        errorText = "无法确认付费观看状态，请稍后重试"
                    }
                    publish()
                }
                WatchPhase.PREVIEW -> {
                    if (withTimeoutOrNull(1_000) { watchCommands.receive() } == WatchCommand.Retry) {
                        watch.resetToChecking()
                        continue
                    }
                    if (watch.phase != WatchPhase.PREVIEW) continue
                    val elapsed = ((SystemClock.elapsedRealtime() - anchor) / 1000L).toInt()
                    val ticks = previewTicksDue(elapsed, consumed, watch.shownRemaining)
                    var created: HeartbeatBatch? = null
                    repeat(ticks) {
                        if (watch.phase == WatchPhase.PREVIEW) watch.onWatchedSecond()?.let { created = it }
                    }
                    consumed += ticks
                    publish()
                    val now = SystemClock.elapsedRealtime()
                    val batch = created ?: watch.currentBatch()?.takeIf { lastDeliverFailed && now >= nextRetryAt }
                    if (batch != null) {
                        val started = SystemClock.elapsedRealtime()
                        try {
                            deliver(batch, started)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: PlatformHttpException) {
                            lastDeliverFailed = true
                            nextRetryAt = SystemClock.elapsedRealtime() + 3_000
                            errorText = if (error.statusCode == 404) "观看时长接口暂不可用，画面已暂停" else "观看时长未能同步，画面已暂停"
                        } catch (_: Exception) {
                            lastDeliverFailed = true
                            nextRetryAt = SystemClock.elapsedRealtime() + 3_000
                            errorText = "观看时长未能同步，画面已暂停"
                        }
                        publish()
                    }
                }
                WatchPhase.SYNCING -> {
                    if (lastDeliverFailed) {
                        val wait = (nextRetryAt - SystemClock.elapsedRealtime()).coerceAtLeast(1L)
                        when (withTimeoutOrNull(wait) { watchCommands.receive() }) {
                            WatchCommand.Unlock -> {
                                unlockNow()
                                continue
                            }
                            WatchCommand.Retry -> nextRetryAt = 0L
                            null -> Unit
                        }
                        if (watch.phase != WatchPhase.SYNCING) continue
                    }
                    val batch = watch.currentBatch() ?: watch.pollNew(force = true)
                    if (batch == null) {
                        watch.resetToChecking()
                        continue
                    }
                    val started = SystemClock.elapsedRealtime()
                    try {
                        deliver(batch, started)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: PlatformHttpException) {
                        lastDeliverFailed = true
                        nextRetryAt = SystemClock.elapsedRealtime() + 3_000
                        errorText = if (error.statusCode == 404) "观看时长接口暂不可用，画面已暂停" else "观看时长未能同步，画面已暂停"
                    } catch (_: Exception) {
                        lastDeliverFailed = true
                        nextRetryAt = SystemClock.elapsedRealtime() + 3_000
                        errorText = "观看时长未能同步，画面已暂停"
                    }
                    publish()
                }
                WatchPhase.PAYWALL -> {
                    when (watchCommands.receive()) {
                        WatchCommand.Retry -> watch.resetToChecking()
                        WatchCommand.Unlock -> unlockNow()
                    }
                }
                WatchPhase.OPEN -> awaitCancellation()
            }
        }
    }

    LaunchedEffect(activeRoom?.stream, session.token, mediaRoute, cohostMode, playbackStream) {
        val room = activeRoom ?: return@LaunchedEffect
        roomFeedback = null
        // LiveKit starts only after watch-access allows this room. A future SRS player must use the same gate.
        if (playbackStream != room.stream) return@LaunchedEffect
        val liveSessionId = room.stream.toLongOrNull()
        if (liveSessionId == null) {
            activeRoomStatus = "暂时无法加入此直播间，请返回刷新"
            return@LaunchedEffect
        }
        if (mediaRoute == LiveMediaRoute.PENDING) {
            activeRoomStatus = "正在连接直播"
            return@LaunchedEffect
        }
        if (mediaRoute == LiveMediaRoute.VIEWER_HLS) {
            roomGateway = null
            activeRoomStatus = if (srsPlaybackUrl(BuildConfig.SRS_PLAYBACK_URL, room.stream) == null) {
                "播放地址不可用，请返回刷新"
            } else {
                "正在连接直播"
            }
            return@LaunchedEffect
        }
        val isHost = room.creatorId == session.id
        // Ordinary host video is the SRS RTMP publish. LiveKit camera starts only after that publish stops.
        val ordinarySrs = mediaRoute == LiveMediaRoute.HOST_RTMP
        val gateway = LiveKitRoomGateway(context)
        roomGateway = gateway
        if (!ordinarySrs) activeRoomStatus = "正在连接直播"
        try {
            withTimeout(20_000) {
                val credentials = api.liveKitToken(session.token, liveSessionId, activeMatchId)
                gateway.join(credentials.url, credentials.token)
                if (isHost && !ordinarySrs) cameraHandoff.withLock { gateway.publishCameraAndMicrophone() }
                else if (!isHost && activeMatchId == null && cohostMode != null) gateway.publishCohost(cohostMode == "VIDEO")
            }
            if (!ordinarySrs) activeRoomStatus = if (isHost) "正在直播" else "已加入直播间"
            awaitCancellation()
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            if (!ordinarySrs) activeRoomStatus = "连接超时，请退出后重试"
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!ordinarySrs) activeRoomStatus = "直播连接失败，请退出后重试"
        } finally {
            gateway.leave()
            if (roomGateway === gateway) roomGateway = null
        }
    }

    LaunchedEffect(activeRoom?.stream, session.id, activeMatchId, hostCohostLiveKit) {
        val room = activeRoom ?: return@LaunchedEffect
        if (!hostPublishesOrdinarySrs(room.creatorId == session.id, activeMatchId, hostCohostLiveKit)) return@LaunchedEffect
        val url = srsIngestUrl(BuildConfig.SRS_RTMP_URL, room.stream)
        if (url == null) {
            activeRoomStatus = "推流地址不可用，观众将看不到画面"
            return@LaunchedEffect
        }
        cameraHandoff.withLock {
            val publisher = SrsIngestPublisher(context)
            srsIngest = publisher
            try {
                activeRoomStatus = "正在连接直播"
                publisher.onDropped = { activeRoomStatus = "推流已断开，观众暂时看不到画面" }
                withTimeout(15_000) { publisher.start(url) }
                activeRoomStatus = "正在直播"
                awaitCancellation()
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                activeRoomStatus = "推流失败，观众暂时看不到画面"
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                activeRoomStatus = "推流失败，观众暂时看不到画面"
            } finally {
                publisher.onDropped = null
                publisher.stop()
                if (srsIngest === publisher) srsIngest = null
            }
        }
    }

    val ordinaryHostIngest by rememberUpdatedState(
        activeRoom?.let { hostPublishesOrdinarySrs(it.creatorId == session.id, activeMatchId, hostCohostLiveKit) } == true,
    )
    LaunchedEffect(roomGateway, playbackStream) {
        val gateway = roomGateway ?: return@LaunchedEffect
        if (playbackStream == null) return@LaunchedEffect
        gateway.connection.collect { state ->
            if (ordinaryHostIngest) return@collect
            when (state) {
                MediaConnectionState.CONNECTED -> activeRoomStatus = if (activeRoom?.creatorId == session.id) "正在直播" else "已加入直播间"
                MediaConnectionState.RECONNECTING -> activeRoomStatus = "连接中断，正在尝试恢复"
                MediaConnectionState.DISCONNECTED -> activeRoomStatus = "直播连接已断开，请返回后重新进入"
                MediaConnectionState.IDLE -> Unit
            }
        }
    }

    LaunchedEffect(activeRoom?.stream, session.token, session.id, activeMatchId) {
        hostCohostLiveKit = false
        val room = activeRoom ?: return@LaunchedEffect
        if (room.creatorId != session.id || activeMatchId != null) return@LaunchedEffect
        val liveId = room.stream.toLongOrNull() ?: return@LaunchedEffect
        while (true) {
            try {
                hostCohostLiveKit = api.cohosts(session.token, liveId).any { cohostSeatNeedsLiveKit(it.status) }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { }
            kotlinx.coroutines.delay(3_000)
        }
    }

    LaunchedEffect(activeRoom?.stream, session.token, activeMatchId) {
        cohostMode = null
        cohostKnown = false
        val room = activeRoom ?: return@LaunchedEffect
        if (room.creatorId == session.id) return@LaunchedEffect
        val liveId = room.stream.toLongOrNull() ?: return@LaunchedEffect
        while (true) {
            try { cohostMode = if (activeMatchId == null) api.cohosts(session.token,liveId).firstOrNull { it.userId==session.id && it.status=="ACCEPTED" }?.mode else null }
            catch(e:CancellationException){throw e}catch(_:Exception){cohostMode=null}
            cohostKnown = true
            kotlinx.coroutines.delay(3000)
        }
    }

    LaunchedEffect(activeRoom?.stream, session.token) {
        val liveId = activeRoom?.stream?.toLongOrNull() ?: return@LaunchedEffect
        while (true) {
            try {
                val current = api.activeMatch(session.token, liveId)
                if (current != activeMatchId) {
                    activeMatchId = current
                    matchScores = emptyList()
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { /* Keep the last confirmed state during a temporary outage. */ }
            kotlinx.coroutines.delay(3_000)
        }
    }

    LaunchedEffect(activeMatchId, session.token) {
        val matchId = activeMatchId ?: return@LaunchedEffect
        while (true) {
            runCatching { api.matchScore(session.token, matchId) }.onSuccess { matchScores = it }
            kotlinx.coroutines.delay(3_000)
        }
    }

    LaunchedEffect(selectedTab, activeRoom?.creatorId, session.token) {
        if (selectedTab != 4 && activeRoom?.creatorId != session.id) return@LaunchedEffect
        while (true) {
            try { matchInvitations = api.matchInvitations(session.token)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { }
            kotlinx.coroutines.delay(3_000)
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
                    activeRoom = LiveRoom(session.username, "${session.username} 的直播间", session.id, sessionId.toString())
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
        showRoomChat=false
        showCohost=false
        showBusiness=null
        cohostMode=null
        val room = activeRoom ?: return
        scope.launch {
            val id = room.stream.toLongOrNull()
            val isHost = room.creatorId == session.id
            try {
                if (!isHost && id != null) {
                    for (request in api.cohosts(session.token, id).filter { it.status in listOf("PENDING", "ACCEPTED", "MUTED") }) {
                        api.cohostAction(session.token, id, request.id, "LEAVE")
                    }
                }
                if (isHost && id != null) api.closeLive(session.token, id)
                if (isHost) roomRegistration?.close()
                activeMatchId = null
                matchScores = emptyList()
                activeRoom = null
                roomGateway = null
                roomRegistration = null
                activeRoomStatus = "已离开"
                if (isHost) refreshKey++
            } catch (error: Exception) {
                notify("离开直播失败 · ${error.message ?: "请求失败"}")
            }
        }
    }

    val respondInvitation: (MatchInvitation, Boolean) -> Unit = { invitation, accept ->
        if (!matchLoading) scope.launch {
            matchLoading = true
            try {
                val result = api.respondMatchInvitation(session.token, invitation.invitationId, accept)
                matchInvitations = matchInvitations.filterNot { it.invitationId == invitation.invitationId }
                if (accept && activeRoom?.stream?.toLongOrNull() in listOf(result.liveSessionId, result.opponentLiveSessionId)) {
                    activeMatchId = result.matchId
                    notify("已接受 PK，正在连接对方主播")
                } else notify(if (accept) "已接受 PK，请进入自己的直播间" else "已婉拒 PK 邀请")
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { notify("未能处理邀请，请刷新后重试")
            } finally { matchLoading = false }
        }
    }

    fun beginCall(peerId: String, peerName: String?, requestedMode: String, callId: String?) {
        if (activeCall != null) {
            notify("请先挂断当前通话")
            return
        }
        if (activeRoom != null) {
            notify("请先离开直播间，再发起通话")
            return
        }
        if (callId == null) {
            if (peerId.isBlank() || peerId == session.id.toString() || peerId.toLongOrNull() == null) {
                notify("请先选择对方账号")
                return
            }
            if (requestedMode != "video" && requestedMode != "voice") return
        } else if (!callId.matches(Regex("[0-9a-f]{32}"))) {
            notify("通话号无效")
            return
        }
        activeCall = PendingDmCall(peerId, peerName, requestedMode, callId)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(NightInk),
        containerColor = NightInk,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = NightSurface) {
                val tabs = listOf("直播", "榜单", "消息", "我的") + if (canManageOperations) listOf("运营") else emptyList()
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
            TopBar(title = listOf("现场", "榜单", "消息", "我的", "运营")[selectedTab], onRefresh = { refreshKey++ })
            pendingGift?.let { pending ->
                PendingGiftNotice(pending, giftLoading, onRetry = { submitGift(pending) })
            }
            when (selectedTab) {
                0 -> LiveScreen(
                    rooms = rooms,
                    loading = roomsLoading,
                    error = roomsError,
                    starting = startingLive,
                    onStart = {
                        val age = profile?.age
                        val minimumAge = if (profile?.region == "KR") 19 else 18
                        if (profile?.source != "SELF_REPORTED") {
                            selectedTab = 3
                            notify("请先在“我的”填写年龄和地区，再开播")
                        } else if (age == null || age < minimumAge) {
                            notify("当前年龄未达到开播要求")
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
                    ownUserId = session.id.toString(),
                    onChatVisibility = { chatVisible = it },
                    history = history.takeIf { historyRecipient == openImRecipient },
                    historyLoading = historyLoading,
                    historyError = historyError,
                    onHistoryRetry = { historyRefresh++ },
                    onOlderMessages = {
                        val recipient = openImRecipient
                        if (!historyLoading) scope.launch {
                            historyMutex.withLock {
                                val current = history
                                if (current != null && historyRecipient == recipient && !current.isEnd && openImRecipient == recipient && chatVisible) {
                                    historyLoading = true
                                    historyError = null
                                    try {
                                        check(current.nextCursor.isNotBlank()) { "缺少消息分页位置" }
                                        val older = withTimeout(15_000) { openImGateway.history(recipient, current.nextCursor) }
                                        if (openImRecipient == recipient && chatVisible) history = appendOlderHistory(current, older)
                                    } catch (error: kotlinx.coroutines.TimeoutCancellationException) { if (openImRecipient == recipient && chatVisible) historyError = "加载超时，请重试"
                                    } catch (error: CancellationException) { throw error
                                    } catch (error: Exception) { if (openImRecipient == recipient && chatVisible) historyError = "更早消息加载失败，请重试"
                                    } finally { historyLoading = false }
                                }
                            }
                        }
                    },
                    conversations = openImConversations,
                    recipientId = openImRecipient,
                    recipientName = openImRecipientName,
                    message = openImMessage,
                    busy = openImBusy,
                    ready = openImReady,
                    onRecipientId = { openImRecipient = it.filter(Char::isDigit); openImRecipientName = null },
                    onMessage = { openImMessage = it },
                    onRefresh = {
                        scope.launch {
                            openImBusy = true
                            runCatching { openImGateway.conversations() }
                                .onSuccess { openImConversations = parseOpenImConversations(it) }
                                .onFailure { openImStatus = "会话暂时无法加载，请刷新重试" }
                            openImBusy = false
                        }
                    },
                    onConnect = { messageConnectionAttempt++ },
                    onVideoCall = { beginCall(openImRecipient, openImRecipientName, "video", null) },
                    onVoiceCall = { beginCall(openImRecipient, openImRecipientName, "voice", null) },
                    onJoinCall = { beginCall(openImRecipient, openImRecipientName, "", it) },
                    onAcceptCall = { invite ->
                        beginCall(invite.fromUserId.toString(), openImRecipientName ?: "用户 ${invite.fromUserId}", invite.mode, invite.callId)
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
                                        openImStatus = "消息已发送"
                                        historyRefresh++
                                        runCatching { openImGateway.conversations() }.onSuccess { openImConversations = parseOpenImConversations(it) }
                                    }
                                    .onFailure { openImStatus = "发送失败，内容已保留，请重试" }
                                openImBusy = false
                            }
                        }
                    },
                    support = supportContact,
                    gifts = gifts,
                    balance = wallet?.balance,
                    playable = playableVideos,
                    pendingGift = pendingDmGift?.takeIf { it.recipientId == openImRecipient },
                    pendingVideoPhase = pendingDmVideo?.takeIf { it.recipientId == openImRecipient }?.phase,
                    chatOpenSignal = chatOpenSignal,
                    onOpenChat = { id, name -> openImRecipient = id; openImRecipientName = name },
                    onOpenSupport = {
                        if (!openImBusy) scope.launch {
                            openImBusy = true
                            try {
                                val target = openSupportConversation(api, openImGateway, session.token)
                                openImRecipient = target.userId
                                openImRecipientName = target.name
                                supportContact = ChatRecipient(target.userId, target.name)
                                chatOpenSignal++
                                selectedTab = 2
                            } catch (error: CancellationException) { throw error
                            } catch (error: Exception) { openImStatus = error.message ?: "客服暂时无法连接"
                            } finally { openImBusy = false }
                        }
                    },
                    onSendGift = { gift -> sendDmGift(gift.id) },
                    onRetryGift = { pendingDmGift?.let { sendDmGift(it.giftId) } },
                    onSendVideo = sendDmVideo,
                    onUnlock = unlockDmVideo,
                    onPlay = playDmVideo,
                    onVerifyGift = verifyDmGift,
                )
                3 -> ProfileScreen(
                    session = session,
                    wallet = wallet,
                    level = level,
                    daily = dailyEarnings,
                    monthly = monthlyEarnings,
                    rechargeBusy = rechargeBusy,
                    rechargeOrders = rechargeOrders,
                    rechargeOrdersError = rechargeOrdersError,
                    profile = profile,
                    profileBusy = profileBusy,
                    onSaveProfile = { age, region ->
                        if (!profileBusy) scope.launch {
                            profileBusy = true
                            try { profile = api.saveProfile(session.token, age, region); notify("资料已保存，来源为本人填写")
                            } catch (error: CancellationException) { throw error
                            } catch (_: Exception) { notify("资料未能保存，请检查填写内容后重试")
                            } finally { profileBusy = false }
                        }
                    },
                    onGiftHistory = { showGiftHistory = true; loadGiftHistory(false) },
                    onChangePassword = { showPasswordChange = true },
                    onBusiness = { showBusiness = "withdrawals" },
                    onCoverUpload = { showCoverUpload = true },
                    onIdentity = { showIdentity = true },
                    onLogout = onLogout,
                    onRefresh = { refreshKey++ },
                    onTopUp = { amount ->
                        if (!rechargeBusy) scope.launch {
                            rechargeBusy = true
                            try {
                                val url = api.rechargeUrl(session.token, amount)
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                refreshKey++
                            } catch (error: CancellationException) { throw error
                            } catch (_: Exception) { notify("暂时无法发起支付，请刷新充值记录后重试")
                            } finally { rechargeBusy = false }
                        }
                    },
                )
                else -> if (canManageOperations) OperationsScreen(
                    summary = operationsSummary, users = operationsUsers, page = operationsPage,
                    loading = operationsLoading, error = operationsError,
                    onRefresh = { operationsRefresh++ }, onPage = { operationsPage = it },
                    onModeration = { showModeration = true },
                )
            }
        }
    }

    activeCall?.let { call ->
        DmCallSheet(
            session = session,
            api = api,
            peerId = call.peerId,
            peerName = call.peerName,
            requestedMode = call.requestedMode,
            callId = call.callId,
            onSignal = { creds ->
                // OpenIM hook: one C2C text on the existing conversation. Swap this send for a custom
                // call message later; the media path still works if the callee joins with call_id.
                if (!openImReady) false
                else if (creds.signalTransport.isNotBlank() && creds.signalTransport != DM_CALL_SIGNAL_TRANSPORT) false
                else {
                    val peer = if (creds.callerId == session.id.toString()) creds.peerId else creds.callerId
                    val text = creds.signalText.takeIf { parseDmCallInvite(it) != null }
                        ?: dmCallInviteText(creds.callId, creds.mode, session.id)
                    runCatching { openImGateway.sendText(peer, text) }.isSuccess
                }
            },
            onDismiss = { activeCall = null },
        )
    }
    if (showPasswordChange) PasswordSheet(session.token, onDismiss = { showPasswordChange = false }, onChanged = onLogout)
    if (showCoverUpload) CoverUploadSheet(session,api,onDismiss={showCoverUpload=false;refreshKey++})
    if (showIdentity) IdentitySheet(session,api,onDismiss={showIdentity=false})
    if(activeRoom==null)showBusiness?.let { BusinessSheet(session,api,it,onDismiss={showBusiness=null}) }
    if (showModeration && canManageOperations) ModerationPanel(session, onDismiss = { showModeration = false; operationsRefresh++ })

    playingVideo?.let { file -> DmVideoPlayerDialog(file) { playingVideo = null } }
    if (showGiftHistory) GiftHistorySheet(
        history = giftHistory, loading = giftHistoryLoading, error = giftHistoryError,
        onDismiss = { showGiftHistory = false }, onRefresh = { loadGiftHistory(false) }, onOlder = { loadGiftHistory(true) },
    )

    activeRoom?.let { room ->
        Dialog(
            onDismissRequest = { leaveRoom() },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            MaterialTheme(colorScheme = darkColorScheme(primary = Coral, onPrimary = Color.White, primaryContainer = Color(0xFF51232A), onPrimaryContainer = Color.White, surface = Color(0xFF202126), background = Color.Black)) {
            RoomSheet(
                room = room,
                gateway = roomGateway,
                playbackUrl = if (mediaRoute == LiveMediaRoute.VIEWER_HLS) {
                    srsPlaybackUrl(BuildConfig.SRS_PLAYBACK_URL, room.stream)
                } else null,
                onPlaybackStatus = { activeRoomStatus = it },
                ingest = srsIngest,
                watchGate = if (room.creatorId == session.id) null else viewerGate ?: WatchGateUi(WatchPhase.CHECKING, 0, null, null, false),
                onUnlock = { watchCommands.trySend(WatchCommand.Unlock) },
                onRetryWatch = { watchCommands.trySend(WatchCommand.Retry) },
                status = activeRoomStatus,
                feedback = roomFeedback,
                wallet = wallet,
                gifts = gifts,
                giftLoading = giftLoading,
                pendingGift = pendingGift,
                onRetryGift = { pendingGift?.let(submitGift) },
                isHost = room.creatorId == session.id,
                matchId = activeMatchId,
                scores = matchScores,
                onMessageHost = {
                    scope.launch {
                        try {
                            val recipient = api.liveRecipient(session.token, room.stream)
                            openImRecipient = recipient.userId
                            openImRecipientName = recipient.name
                            openImMessage = ""
                            history = null
                            historyRecipient = recipient.userId
                            chatOpenSignal++
                            leaveRoom()
                            selectedTab = 2
                        } catch (error: CancellationException) { throw error }
                        catch (error: Exception) { notify(error.message ?: "暂时无法给主播发私信，请稍后重试") }
                    }
                },
                onCohost = { showCohost = true },
                onRoomChat = { showRoomChat = true },
                onReport = { showBusiness = "reports" },
                matchLoading = matchLoading,
                opponents = rooms.filter { it.creatorId != session.id },
                onClose = { leaveRoom() },
                invitations = matchInvitations.filter { it.opponentLiveSessionId == room.stream.toLongOrNull() },
                onRespondInvitation = respondInvitation,
                effectSession = effectSession,
                onEndMatch = {
                    val matchId = activeMatchId
                    if (matchId != null && !matchLoading) scope.launch {
                        matchLoading = true
                        try {
                            api.closeMatch(session.token, matchId)
                            activeMatchId = null
                            matchScores = emptyList()
                            notify("PK 已结束，继续单人直播")
                        } catch (error: CancellationException) { throw error
                        } catch (_: Exception) { notify("未能结束 PK，请重试")
                        } finally { matchLoading = false }
                    }
                },
                onGift = { gift ->
                    val id = room.stream.toLongOrNull()
                    if (id == null || room.creatorId <= 0) {
                        notify("暂时无法向这位主播送礼，请退出后重试")
                    } else {
                        if (pendingGift != null) notify("请先重试核实上一笔送礼")
                        else submitGift(PendingGift.create(room.creatorId, id, gift, activeMatchId, room.host))
                    }
                },
                onInviteMatch = { opponentRoom ->
                    scope.launch {
                        val own = room.stream.toLongOrNull()
                        val opponent = opponentRoom.stream.toLongOrNull()
                        if (own == null || opponent == null || opponent <= 0) { notify("该主播暂时无法接受邀请，请刷新后重试"); return@launch }
                        matchLoading = true
                        runCatching { api.inviteMatch(session.token, own, opponent) }
                            .onSuccess { notify("PK 邀请已发送，等待对方接受") }
                            .onFailure { notify("PK 邀请失败 · ${it.message ?: "请求失败"}") }
                        matchLoading = false
                    }
                },
            )
            showBusiness?.let { BusinessSheet(session,api,it,room.creatorId,onDismiss={showBusiness=null}) }
            if(showCohost)room.stream.toLongOrNull()?.let { liveId -> CohostSheet(session,liveId,room.creatorId==session.id,api,onDismiss={showCohost=false},onChanged={}) }
            if(showRoomChat)RoomChatSheet(session,api,room.stream,onDismiss={showRoomChat=false})
            }
        }
    }
}

@Composable
private fun PendingGiftNotice(pending: PendingGift, busy: Boolean, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("给 ${pending.hostName} 的${pending.giftName} · ${pending.coins} 金币", style = MaterialTheme.typography.bodyMedium)
        Text("结果尚未确认。重试会核实原送礼，不会重复扣币。", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRetry, enabled = !busy) { Text(if (busy) "正在核实…" else "重试并核实原送礼") }
    }
}

@Composable
private fun TopBar(title: String, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "刷新内容") }
    }
}

@Composable
private fun LiveScreen(
    rooms: List<LiveRoom>,
    loading: Boolean,
    error: String?,
    starting: Boolean,
    onStart: () -> Unit,
    onJoin: (LiveRoom) -> Unit,
    onRefresh: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("发现直播", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("点进直播间，和主播聊聊", style = MaterialTheme.typography.bodyMedium, color = Fog)
                }
                Button(onClick = onStart, enabled = !starting, shape = RoundedCornerShape(12.dp)) {
                    if (starting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (starting) "开播中" else "开播")
                }
            }
        }
        if (loading) item(span = { GridItemSpan(maxLineSpan) }) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Coral, trackColor = NightSurfaceRaised)
        }
        if (error != null) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (rooms.isEmpty()) "直播暂时没加载出来" else "直播列表未更新", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(error, style = MaterialTheme.typography.bodyMedium, color = Fog)
                OutlinedButton(onClick = onRefresh, enabled = !loading) { Text(if (loading) "正在重试…" else "重新加载") }
            }
        }
        if (!loading && error == null && rooms.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.LiveTv, contentDescription = null, tint = Fog, modifier = Modifier.size(40.dp))
                Text("现在还没有直播", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("稍后再来看看，也可以自己开播", style = MaterialTheme.typography.bodyMedium, color = Fog)
                OutlinedButton(onClick = onRefresh) { Text("刷新看看") }
            }
        }
        items(rooms, key = { it.stream }) { room -> RoomTile(room, onClick = { onJoin(room) }) }
    }
}

@Composable
private fun RoomTile(room: LiveRoom, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = NightSurface) {
        Column {
            // The directory has no cover field yet. Never substitute an unrelated host photo.
            Box(Modifier.fillMaxWidth().aspectRatio(0.85f).background(Color(0xFF252A32))) {
                room.coverPath?.let { LiveCoverImage(it) }
                Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if(room.coverPath==null)Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color(0xFFCCD2DC), modifier = Modifier.size(32.dp))
                    if(room.coverPath==null)Text("暂无封面", style = MaterialTheme.typography.bodySmall, color = Color(0xFFCCD2DC))
                }
                Row(Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF77E2B9)))
                    Text("直播", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(room.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(room.host, style = MaterialTheme.typography.bodySmall, color = Fog, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SrsHostStage(ingest: SrsIngestPublisher, gateway: LiveKitRoomGateway?, room: LiveRoom) {
    Box(Modifier.fillMaxSize()) {
        SrsHostPreview(ingest, Modifier.fillMaxSize())
        if (gateway == null) return@Box
        val videoIds by gateway.videoIdentities.collectAsState()
        val remotes = videoIds.mapNotNull { it.toLongOrNull() }.filter { it != room.creatorId }
        if (remotes.isEmpty()) return@Box
        Row(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).width(140.dp).height(200.dp)) {
            remotes.take(2).forEach { participantId ->
                androidx.compose.runtime.key(gateway, participantId) {
                    AndroidView(
                        factory = { context -> gateway.createVideoRenderer(context, participantId.toString()) },
                        update = { gateway.attachVideoRenderer(it) },
                        onRelease = { gateway.releaseVideoRenderer(it); it.release() },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchBlockedOverlay(gate: WatchGateUi, wallet: Wallet?, onUnlock: () -> Unit, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)).padding(top = 72.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (gate.phase) {
                WatchPhase.CHECKING -> {
                    CircularProgressIndicator(color = Color.White)
                    Text("正在确认观看权限", color = Color.White)
                }
                WatchPhase.SYNCING -> {
                    val price = gate.priceCoins
                    val short = price != null && wallet != null && wallet.balance < price
                    Text(if (gate.error == null) "正在核对观看时长" else "免费试看已结束", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(gate.error ?: "画面已暂停。服务器确认后才会继续播放或要求解锁。", color = Color(0xFFDDDEE2))
                    if (price != null) Text("解锁价格 $price 金币", color = Color.White)
                    if (gate.error != null && short) Text("余额不足，请到「我的」充值后再解锁", color = Color(0xFFFFB4AB))
                    if (gate.error != null) {
                        Button(onClick = onUnlock, enabled = !gate.busy && !short, modifier = Modifier.fillMaxWidth()) {
                            Text(if (gate.busy) "正在解锁…" else if (price != null && price > 0) "支付 $price 金币解锁" else "解锁继续观看")
                        }
                        TextButton(onClick = onRetry, enabled = !gate.busy) { Text("重试核对") }
                    }
                }
                WatchPhase.UNAVAILABLE -> {
                    Text("暂时无法确认是否需要付费", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(gate.error ?: "请稍后重试", color = Color(0xFFDDDEE2))
                    Button(onClick = onRetry, enabled = !gate.busy, modifier = Modifier.fillMaxWidth()) { Text("重试") }
                }
                WatchPhase.PAYWALL -> {
                    val price = gate.priceCoins
                    val short = price != null && wallet != null && wallet.balance < price
                    Text("免费试看已结束", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (price != null) Text("解锁价格 $price 金币", color = Color.White) else Text("解锁后才能继续观看", color = Color(0xFFDDDEE2))
                    Text(wallet?.let { "余额 ${it.balance} 金币" } ?: "余额暂不可用，解锁结果以服务器为准", color = Color(0xFFDDDEE2), style = MaterialTheme.typography.bodySmall)
                    if (short) Text("余额不足，请到「我的」充值后再解锁", color = Color(0xFFFFB4AB))
                    gate.error?.let { Text(it, color = Color(0xFFFFB4AB)) }
                    Button(onClick = onUnlock, enabled = !gate.busy && !short, modifier = Modifier.fillMaxWidth()) {
                        Text(if (gate.busy) "正在解锁…" else if (price != null && price > 0) "支付 $price 金币解锁" else "解锁继续观看")
                    }
                }
                WatchPhase.PREVIEW, WatchPhase.OPEN -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomSheet(
    room: LiveRoom,
    gateway: LiveKitRoomGateway?,
    playbackUrl: String?,
    onPlaybackStatus: (String) -> Unit,
    ingest: SrsIngestPublisher?,
    watchGate: WatchGateUi?,
    onUnlock: () -> Unit,
    onRetryWatch: () -> Unit,
    status: String,
    feedback: String?,
    wallet: Wallet?,
    gifts: List<Gift>,
    giftLoading: Boolean,
    pendingGift: PendingGift?,
    onRetryGift: () -> Unit,
    isHost: Boolean,
    matchId: Long?,
    scores: List<MatchScore>,
    onMessageHost: () -> Unit,
    onCohost: () -> Unit,
    onRoomChat: () -> Unit,
    onReport: () -> Unit,
    matchLoading: Boolean,
    opponents: List<LiveRoom>,
    onClose: () -> Unit,
    invitations: List<MatchInvitation>,
    onRespondInvitation: (MatchInvitation, Boolean) -> Unit,
    onEndMatch: () -> Unit,
    effectSession: GiftEffectSession,
    onGift: (Gift) -> Unit,
    onInviteMatch: (LiveRoom) -> Unit,
) {
    var panel by remember(room.stream) { mutableStateOf<String?>(null) }
    var selectedGiftId by remember(room.stream) { mutableStateOf<String?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    var confirmEndMatch by remember { mutableStateOf(false) }
    val selectedGift = gifts.firstOrNull { it.id == selectedGiftId }
    val effectFailure by effectSession.failure.collectAsState()
    val requestClose = { if (isHost) confirmClose = true else onClose() }
    val gate = watchGate
    val watchBlocked = gate != null && !gate.phase.allowsPlayback()
    androidx.activity.compose.BackHandler { requestClose() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (playbackUrl != null && !watchBlocked) {
            SrsPlaybackView(playbackUrl, onPlaybackStatus, Modifier.fillMaxSize())
        } else if (ingest != null && isHost && matchId == null) {
            SrsHostStage(ingest, gateway, room)
        } else if (gateway != null && !watchBlocked) {
            val videoIds by gateway.videoIdentities.collectAsState()
            val opponentId = scores.firstOrNull { it.creatorId != room.creatorId }?.creatorId
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                val visibleIds = if (matchId != null) listOf(room.creatorId, opponentId) else (listOf(room.creatorId) + videoIds.mapNotNull { it.toLongOrNull() }).distinct().take(4)
                visibleIds.forEach { participantId ->
                    androidx.compose.runtime.key(gateway, participantId, matchId) {
                        if (participantId == null) Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Text("正在等待对方画面", color = Color.White)
                        } else AndroidView(
                            factory = { context -> gateway.createVideoRenderer(context, participantId.toString()) },
                            update = { gateway.attachVideoRenderer(it) },
                            onRelease = { gateway.releaseVideoRenderer(it); it.release() },
                            modifier = Modifier.weight(1f).then(if (matchId != null) Modifier.fillMaxHeight(0.6f) else Modifier.fillMaxHeight()),
                        )
                    }
                }
            }
        }
        GiftEffectOverlay(effectSession, Modifier.fillMaxSize())
        if (gate != null && watchBlocked) WatchBlockedOverlay(gate, wallet, onUnlock, onRetryWatch)
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(room.host, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(room.title, color = Color(0xFFDDDEE2), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!isHost) TextButton(onClick = onMessageHost) { Text("私信") }
                if (!isHost) TextButton(onClick = onReport) { Text("举报") }
                IconButton(onClick = requestClose) {
                    Icon(Icons.Default.Close, contentDescription = if (isHost) "结束直播" else "离开直播间", tint = Color.White)
                }
            }
            if (watchGate?.phase == WatchPhase.PREVIEW) {
                Text(
                    "免费试看还剩 ${watchGate.shownRemaining} 秒",
                    color = Color(0xFFFFC46B),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                )
                watchGate.error?.let { Text(it, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) }
            }
            if (matchId != null) {
                Text("PK 进行中", color = Color.White, modifier = Modifier.padding(8.dp))
                scores.forEach { score ->
                    Text("${if (score.creatorId == room.creatorId) room.host else "对方主播"}  ${score.points} 分", color = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }
        if (!watchBlocked) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            feedback?.let { Text(it, color = Color.White, style = MaterialTheme.typography.bodyMedium) }
            effectFailure?.let { Text(it, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodyMedium) }
            Text(status, color = Color(0xFFDDDEE2), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick=onCohost,enabled=matchId==null){Text(if(isHost) "连麦管理" else "申请连麦")}
            TextButton(onClick=onRoomChat){Text("直播公屏")}
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isHost) "把此刻分享给观众" else "正在观看 ${room.host}", color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Button(onClick = { panel = if (isHost) "pk" else "gifts" }, enabled = !giftLoading && !matchLoading) {
                    Text(if (isHost && matchId != null) "PK 管理" else if (isHost && invitations.isNotEmpty()) "PK 邀请（${invitations.size}）" else if (isHost) "邀请 PK" else "送礼物")
                }
            }
        }
    }
    if (confirmEndMatch) AlertDialog(
        onDismissRequest = { confirmEndMatch = false },
        title = { Text("结束本场 PK？") },
        text = { Text("双方将返回各自直播间，已收到的礼物和比分会保留。") },
        confirmButton = { TextButton(onClick = { confirmEndMatch = false; onEndMatch() }, enabled = !matchLoading) { Text("结束 PK") } },
        dismissButton = { TextButton(onClick = { confirmEndMatch = false }) { Text("继续 PK") } },
    )
    if (confirmClose) AlertDialog(
        onDismissRequest = { confirmClose = false },
        title = { Text("结束本场直播？") },
        text = { Text("结束后，观众将无法继续观看。") },
        confirmButton = { TextButton(onClick = { confirmClose = false; onClose() }) { Text("结束直播") } },
        dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("继续直播") } },
    )
    if (panel != null) ModalBottomSheet(onDismissRequest = { if (!giftLoading) panel = null }) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (panel == "gifts") {
                pendingGift?.let { PendingGiftNotice(it, giftLoading, onRetryGift) }
                Text("送给 ${room.host}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                feedback?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(wallet?.balance?.let { "余额 $it 金币" } ?: "余额暂不可用，请稍后重试")
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (gifts.isEmpty()) item { Text("暂时没有可赠送的礼物") }
                    items(gifts, key = { it.id }) { gift ->
                        Surface(
                            onClick = { if (!giftLoading) selectedGiftId = gift.id },
                            color = if (selectedGiftId == gift.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(gift.name, fontWeight = FontWeight.SemiBold)
                                Text("${gift.coinPrice} 金币")
                            }
                        }
                    }
                }
                val enoughBalance = selectedGift != null && wallet != null && wallet.balance >= selectedGift.coinPrice
                Text(selectedGift?.let { "${it.name} × 1 · 合计 ${it.coinPrice} 金币" } ?: "选择一份礼物", style = MaterialTheme.typography.bodyMedium)
                if (selectedGift != null && wallet != null && !enoughBalance) Text("余额不足，请到「我的」充值后再送礼。")
                Button(onClick = { selectedGift?.let(onGift) }, enabled = enoughBalance && !giftLoading && pendingGift == null, modifier = Modifier.fillMaxWidth()) {
                    Text(if (giftLoading) "正在赠送…" else "确认赠送")
                }
            } else {
                Text("邀请主播 PK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (matchId == null) "选择一位正在直播的主播" else "当前已有 PK，请先完成本场比赛。")
                if (matchId != null) Button(onClick = { panel = null; confirmEndMatch = true }, enabled = !matchLoading, modifier = Modifier.fillMaxWidth()) { Text("结束 PK，继续直播") }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(invitations, key = { "invite-${it.invitationId}" }) { invitation ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text("${opponents.firstOrNull { it.creatorId == invitation.creatorId }?.host ?: "一位主播"} 邀请你 PK", fontWeight = FontWeight.SemiBold)
                            Row {
                                TextButton(onClick = { onRespondInvitation(invitation, false) }, enabled = !matchLoading) { Text("婉拒") }
                                TextButton(onClick = { onRespondInvitation(invitation, true); panel = null }, enabled = !matchLoading && matchId == null) { Text("接受 PK") }
                            }
                        }
                    }
                    if (opponents.isEmpty()) item { Text("暂无可邀请的主播，请稍后再来。", modifier = Modifier.padding(vertical = 20.dp)) }
                    items(opponents, key = { it.stream }) { opponent ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(opponent.host, fontWeight = FontWeight.SemiBold)
                                Text(opponent.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = { onInviteMatch(opponent); panel = null }, enabled = matchId == null && !matchLoading) { Text("邀请") }
                        }
                    }
                }
            }
        }
    }
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
            Text("按本期实际礼物记录排序", style = MaterialTheme.typography.bodySmall, color = Fog, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessagesScreen(
    status: String,
    ownUserId: String,
    onChatVisibility: (Boolean) -> Unit,
    history: OpenImHistory?,
    historyLoading: Boolean,
    historyError: String?,
    onHistoryRetry: () -> Unit,
    onOlderMessages: () -> Unit,
    conversations: List<OpenImConversation>,
    recipientId: String,
    recipientName: String?,
    message: String,
    busy: Boolean,
    ready: Boolean,
    onRecipientId: (String) -> Unit,
    onMessage: (String) -> Unit,
    onRefresh: () -> Unit,
    onConnect: () -> Unit,
    onVideoCall: () -> Unit,
    onVoiceCall: () -> Unit,
    onJoinCall: (String) -> Unit,
    onAcceptCall: (DmCallInvite) -> Unit,
    onSend: () -> Unit,
    support: ChatRecipient?,
    gifts: List<Gift>,
    balance: Long?,
    playable: Map<String, Boolean>,
    pendingGift: DmGiftOutbox.Pending?,
    pendingVideoPhase: String?,
    chatOpenSignal: Int,
    onOpenChat: (String, String?) -> Unit,
    onOpenSupport: () -> Unit,
    onSendGift: (Gift) -> Unit,
    onRetryGift: () -> Unit,
    onSendVideo: (String, Int, ByteArray, Int) -> Unit,
    onUnlock: (String) -> Unit,
    onPlay: (String) -> Unit,
    onVerifyGift: (ChatBody.Gift) -> Unit,
) {
    var composeOpen by remember { mutableStateOf(false) }
    var enteringAccount by remember { mutableStateOf(false) }
    var giftSheet by remember { mutableStateOf(false) }
    var videoSheet by remember { mutableStateOf(false) }
    LaunchedEffect(composeOpen) { onChatVisibility(composeOpen) }
    LaunchedEffect(chatOpenSignal) {
        if (chatOpenSignal > 0 && recipientId.isNotBlank()) {
            enteringAccount = false
            composeOpen = true
        }
    }
    DisposableEffect(Unit) { onDispose { onChatVisibility(false) } }
    val listed = conversations.filter { it.userId != support?.userId }
    val recipient = conversations.firstOrNull { it.userId == recipientId }
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("消息", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = { onRecipientId(""); enteringAccount = true; composeOpen = true }, enabled = ready && !busy) { Text("新消息") }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = Fog)
            if (!ready) TextButton(onClick = onConnect, enabled = !busy) { Text(if (busy) "正在连接…" else "重新连接") }
            else TextButton(onClick = onRefresh, enabled = !busy) { Text("刷新会话") }
        }
        support?.let { contact ->
            item {
                val preview = conversations.firstOrNull { it.userId == contact.userId }?.latestMessage
                Row(Modifier.fillMaxWidth().clickable(enabled = ready && !busy) {
                    onOpenSupport()
                }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(contact.name, contact.userId.hashCode())
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(contact.name, fontWeight = FontWeight.SemiBold)
                        Text(preview?.ifBlank { "平台客服" } ?: "平台客服", style = MaterialTheme.typography.bodyMedium, color = Fog, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(color = NightLine)
            }
        }
        if (listed.isEmpty() && support == null) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = Fog, modifier = Modifier.size(36.dp))
                    Text(if (ready) "还没有聊天" else "暂时无法读取消息", fontWeight = FontWeight.SemiBold)
                    Text(if (ready) "点击「新消息」，给认识的人发个招呼。" else "连接恢复后，你的会话会显示在这里。", color = Fog, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(listed, key = { it.userId }) { conversation ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    onOpenChat(conversation.userId, conversation.name)
                    enteringAccount = false
                    composeOpen = true
                }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(conversation.name, conversation.userId.hashCode())
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(conversation.name, fontWeight = FontWeight.SemiBold)
                    Text(conversation.latestMessage, style = MaterialTheme.typography.bodyMedium, color = Fog, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "发消息", tint = Fog, modifier = Modifier.size(18.dp))
            }
            HorizontalDivider(color = NightLine)
        }
    }
    if (composeOpen) ModalBottomSheet(onDismissRequest = { composeOpen = false }) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text((recipientName ?: recipient?.name)?.let { "发给 $it" } ?: "新消息", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (recipientId != ownUserId) DmCallActions(
                canStart = recipientId.isNotBlank() && recipientId.toLongOrNull() != null,
                incoming = history?.let { latestIncomingDmCall(it.messages, ownUserId) },
                onVideo = onVideoCall,
                onVoice = onVoiceCall,
                onJoin = onJoinCall,
                onAccept = onAcceptCall,
            )
            if (enteringAccount) OutlinedTextField(
                value = recipientId, onValueChange = onRecipientId, label = { Text("对方账号 ID") },
                supportingText = { Text("可在对方的「我的」页面查看账号。") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (historyLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            historyError?.let {
                Text(it, color = Coral)
                TextButton(onClick = onHistoryRetry, enabled = !historyLoading) { Text("重新加载") }
            }
            if (history != null) {
                LazyColumn(Modifier.fillMaxWidth().height(240.dp), reverseLayout = true, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(history.messages.asReversed(), key = { it.id }) { chat ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (chat.senderId == ownUserId) Arrangement.End else Arrangement.Start) {
                            Surface(color = if (chat.senderId == ownUserId) NightSurfaceRaised else NightSurface, shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    val videoId = (chatBody(chat) as? ChatBody.Video)?.videoId
                                    DmMessageBubble(chat, videoId?.let { playable[it] }, busy, onUnlock, onPlay, onVerifyGift)
                                    if (chat.senderId == ownUserId) Text(when (chat.status) {
                                        1 -> "发送中"
                                        2 -> "已发送"
                                        3 -> "发送失败"
                                        else -> "状态待确认"
                                    }, color = Fog, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    if (!history.isEnd) item { TextButton(onClick = onOlderMessages, enabled = !historyLoading) { Text("加载更早消息") } }
                    if (history.isEnd && history.messages.isEmpty()) item { Text("还没有消息，打个招呼吧。", color = Fog) }
                }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = Fog)
            pendingGift?.let {
                Text(if (it.phase == "deliver") "礼物已由结算确认，消息还没送达" else "上一笔私信礼物还没确认，请重试同一单", color = Coral, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRetryGift, enabled = ready && !busy) { Text("重试这笔礼物") }
            }
            if (giftSheet) DmGiftSheet(gifts, balance, busy, onDismiss = { giftSheet = false }) { gift ->
                giftSheet = false
                onSendGift(gift)
            } else if (videoSheet) DmVideoSheet(busy, pendingVideoPhase == "upload", onDismiss = { videoSheet = false }) { title, price, bytes, duration ->
                videoSheet = false
                onSendVideo(title, price, bytes, duration)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { giftSheet = true }, enabled = ready && !busy && recipientId.isNotBlank() && pendingGift == null) { Text("礼物") }
                    if (pendingVideoPhase == "deliver") TextButton(onClick = { onSendVideo("", 0, ByteArray(0), 1) }, enabled = ready && !busy) { Text("重试发送视频") }
                    else TextButton(onClick = { videoSheet = true }, enabled = ready && !busy && recipientId.isNotBlank()) { Text(if (pendingVideoPhase == "upload") "重试上传" else "视频") }
                }
                OutlinedTextField(value = message, onValueChange = onMessage, label = { Text("说点什么…") }, enabled = ready && !busy, maxLines = 5, modifier = Modifier.fillMaxWidth())
                Button(onClick = onSend, enabled = ready && !busy && recipientId.isNotBlank() && message.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "正在发送…" else "发送")
                }
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
    rechargeBusy: Boolean,
    rechargeOrders: List<RechargeOrder>,
    rechargeOrdersError: String?,
    profile: UserProfile?,
    profileBusy: Boolean,
    onSaveProfile: (Int, String) -> Unit,
    onLogout: () -> Unit,
    onChangePassword: () -> Unit,
    onBusiness: () -> Unit,
    onCoverUpload: () -> Unit,
    onIdentity: () -> Unit,
    onGiftHistory: () -> Unit,
    onRefresh: () -> Unit,
    onTopUp: (Int) -> Unit,
) {
    var confirmRecharge by remember { mutableStateOf<Int?>(null) }
    var ageText by remember(profile) { mutableStateOf(profile?.age?.toString().orEmpty()) }
    var regionText by remember(profile) { mutableStateOf(profile?.region.orEmpty()) }
    confirmRecharge?.let { coins ->
        AlertDialog(
            onDismissRequest = { confirmRecharge = null },
            title = { Text("充值 $coins 金币") },
            text = { Text("实付 ¥${String.format(java.util.Locale.ROOT, "%.2f", coins / 100.0)}。确认后前往支付宝，到账结果请在充值记录查看。") },
            confirmButton = { TextButton(onClick = { confirmRecharge = null; onTopUp(coins) }, enabled = !rechargeBusy) { Text("前往支付") } },
            dismissButton = { TextButton(onClick = { confirmRecharge = null }) { Text("取消") } },
        )
    }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("个人资料", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (profile?.source == "SELF_REPORTED") "来源：本人填写，未经身份核验" else "请填写真实资料，系统不会自动替你填写年龄和地区。", style = MaterialTheme.typography.bodySmall, color = Fog)
                OutlinedTextField(value = ageText, onValueChange = { ageText = it.filter(Char::isDigit).take(3) }, label = { Text("当前年龄") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = regionText, onValueChange = { regionText = it.uppercase(java.util.Locale.ROOT).take(2) }, label = { Text("地区代码，例如 CN、US、KR") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { ageText.toIntOrNull()?.let { onSaveProfile(it, regionText) } }, enabled = !profileBusy && ageText.toIntOrNull() in 1..120 && regionText.length == 2, modifier = Modifier.fillMaxWidth()) {
                    Text(if (profileBusy) "正在保存…" else "保存本人资料")
                }
            }
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
                            OutlinedButton(onClick = { confirmRecharge = amount }, enabled = !rechargeBusy, modifier = Modifier.weight(1f)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$amount 金币")
                                    Text("¥${amount / 100}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Text(if (rechargeBusy) "正在创建充值订单…" else "付款返回后自动核实到账，请勿重复付款", style = MaterialTheme.typography.bodySmall, color = Fog)
                }
            }
        }
        item { OutlinedButton(onClick = onGiftHistory, modifier = Modifier.fillMaxWidth()) { Text("查看送礼记录") } }
        item {
            Text("充值记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            rechargeOrdersError?.let { Text(it, color = Fog, modifier = Modifier.padding(top = 8.dp)) }
            if (rechargeOrders.isEmpty() && rechargeOrdersError == null) Text("还没有充值记录", color = Fog, modifier = Modifier.padding(top = 8.dp))
        }
        items(rechargeOrders, key = { it.id }) { order ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("${order.coins} 金币 · ¥${String.format(java.util.Locale.ROOT, "%.2f", order.amountFen / 100.0)}")
                    Text(order.id, style = MaterialTheme.typography.bodySmall, color = Fog, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (order.status == "CREDITED") "已到账" else "待支付确认", color = if (order.status == "CREDITED") Mint else Fog)
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
        item { TextButton(onClick = onChangePassword) { Text("修改密码") } }
        item { OutlinedButton(onClick=onBusiness,modifier=Modifier.fillMaxWidth()){Text("提现、退款与主播申请")}}
        item { TextButton(onClick=onCoverUpload){Text("设置直播封面")}}
        item { TextButton(onClick=onIdentity){Text("实名认证")}}
        item { TextButton(onClick = onLogout) { Text("退出登录") } }
        item {
            level?.let { Text("累计消费 ${it.viewerPoints} 积分 · 主播积分 ${it.creatorPoints}", style = MaterialTheme.typography.bodySmall, color = Fog) }
        }
    }
}

@Composable
private fun OperationsScreen(
    summary: OperationsSummary?, users: OperationsUsers?, page: Int,
    loading: Boolean, error: String?, onRefresh: () -> Unit, onPage: (Int) -> Unit,
    onModeration: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("运营概览", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = onRefresh, enabled = !loading) { Text("刷新") }
            }
            Text("账号与充值订单", style = MaterialTheme.typography.bodySmall, color = Fog)
            OutlinedButton(onClick = onModeration) { Text("直播管理与处置记录") }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            error?.let { Text(it, color = Coral) }
        }
        summary?.let { data ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("账号总数" to data.users, "已禁用账号" to data.disabledUsers,
                        "登记中的直播间" to data.listedRooms, "待支付确认订单" to data.pendingOrders,
                        "已到账充值订单" to data.creditedOrders).forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, color = Fog)
                            Text(value.toString(), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    val updated = runCatching { java.time.Instant.parse(data.observedAt).atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) }.getOrNull()
                    updated?.let { Text("统计时间 $it", style = MaterialTheme.typography.bodySmall, color = Fog) }
                }
            }
        }
        item {
            HorizontalDivider(color = NightLine)
            Text(users?.let { "账号列表 · 共 ${it.total} 个" } ?: "账号列表", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        users?.let { data ->
            items(data.users, key = { it.id }) { user ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(user.username, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("账号 ${user.id}", style = MaterialTheme.typography.bodySmall, color = Fog)
                    }
                    Text(when (user.status) { 1 -> "正常"; 2 -> "已禁用"; else -> "状态 ${user.status}" }, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (data.users.isEmpty()) item { Text("这一页没有账号", color = Fog) }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onPage(page - 1) }, enabled = !loading && page > 1) { Text("上一页") }
                Text("第 $page 页", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onPage(page + 1) }, enabled = !loading && users != null && users.page.toLong() * users.pageSize < users.total) { Text("下一页") }
            }
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
        if (session == null) {
            remove("id"); remove("username"); remove("token")
        } else {
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
            val text = previewLatest(latest).ifBlank { "暂无消息" }
            add(OpenImConversation(
                userId = userId,
                name = row.optString("showName").ifBlank { "用户 $userId" },
                latestMessage = text,
            ))
        }
    }
}.getOrDefault(emptyList())
