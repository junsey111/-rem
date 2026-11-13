package cn.liveroom.app

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.painterResource
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

private val NightInk = LiveStyle.canvas
private val NightSurface = LiveStyle.surface
private val NightSurfaceRaised = LiveStyle.raised
private val NightLine = LiveStyle.line
private val Fog = LiveStyle.muted
private val Coral = LiveStyle.live
private val Mint = LiveStyle.teal
private val Amber = LiveStyle.gold
private val Ink = LiveStyle.ink
private val LivePlaceholderCovers = listOf(
    R.drawable.live_placeholder_1,
    R.drawable.live_placeholder_2,
    R.drawable.live_placeholder_3,
    R.drawable.live_placeholder_4,
)
private val SampleRoomTitles = listOf("晚间聊天", "心动点歌", "一起听歌", "舞台时刻")
private val DemoLivePosters = listOf(
    R.drawable.qumi_demo_live_51287,
    R.drawable.qumi_demo_live_48036,
    R.drawable.qumi_demo_live_47971,
    R.drawable.qumi_demo_live_41181,
)
private val VideoPlaceholderCovers = listOf(
    R.drawable.video_placeholder_1,
    R.drawable.video_placeholder_2,
    R.drawable.video_placeholder_3,
    R.drawable.video_placeholder_4,
)

private data class LiveRoom(
    val host: String,
    val title: String,
    val creatorId: Long,
    val stream: String,
    val coverPath: String? = null,
    val roomType: String = "SINGLE",
    val welcomeText: String = "",
    val backgroundId: Int = 0,
    val announcementText: String = "",
)

private data class FinishedLiveRoom(val room: LiveRoom, val startedAtMillis: Long,
    val localDurationSeconds: Long, val session: CreatorLiveSession? = null, val loading: Boolean = true)

private data class WatchGateUi(
    val phase: WatchPhase,
    val shownRemaining: Int,
    val priceCoins: Long?,
    val error: String?,
    val busy: Boolean,
)

private data class OpenImConversation(val userId: String, val name: String, val latestMessage: String, val avatar: String = "")
private data class PendingDmCall(val peerId: String, val peerName: String?, val requestedMode: String, val callId: String?, val offeredPrice: Int? = null)

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
    val requestNotificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var session by remember { mutableStateOf(if (BuildConfig.PREVIEW_QUMI_LOGIN) null else readSession(context)) }
    var loggingOut by remember { mutableStateOf(false) }
    var logoutError by remember { mutableStateOf<String?>(null) }
    var appUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf(false) }
    var dismissedVersion by remember { mutableIntStateOf(0) }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            val bars = WindowCompat.getInsetsController(window, view)
            bars.isAppearanceLightStatusBars = session != null
            bars.isAppearanceLightNavigationBars = session != null
            window.statusBarColor = if (session == null) android.graphics.Color.rgb(20, 25, 29) else android.graphics.Color.WHITE
            window.navigationBarColor = if (session == null) android.graphics.Color.rgb(20, 25, 29) else android.graphics.Color.WHITE
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            try {
                availableAppUpdate(context)?.let { if (it.versionCode != dismissedVersion) appUpdate = it }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { /* Local update service may be offline. */ }
            kotlinx.coroutines.delay(3_600_000)
        }
    }
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
    LiveAppTheme {
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
                onRequestNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }
                },
            )
        }
        logoutError?.let { message ->
            AlertDialog(onDismissRequest = { logoutError = null }, title = { Text("退出未完成") },
                text = { Text(message) }, confirmButton = { TextButton(onClick = { logoutError = null }) { Text("知道了") } })
        }
        appUpdate?.let { update ->
            AlertDialog(onDismissRequest = {
                if (!updating) { dismissedVersion = update.versionCode; appUpdate = null }
            }, title = { Text("发现新版本 ${update.versionName}") },
                text = { Text(updateError ?: "新版已自动同步，可直接下载安装。") },
                confirmButton = { TextButton(onClick = {
                    if (updating) return@TextButton
                    updating = true
                    updateError = null
                    scope.launch {
                        try {
                            val apk = downloadAppUpdate(context, update)
                            if (installAppUpdate(context, apk)) appUpdate = null
                            else updateError = "请在系统页面允许安装，然后返回重试。"
                        } catch (error: CancellationException) { throw error
                        } catch (error: Exception) {
                            updateError = error.message ?: "下载更新失败，请重试"
                        } finally { updating = false }
                    }
                }, enabled = !updating) { Text(if (updating) "下载中…" else "安装更新") } },
                dismissButton = { TextButton(onClick = {
                    dismissedVersion = update.versionCode
                    appUpdate = null
                }, enabled = !updating) { Text("稍后") } })
        }
    }
}

@Composable
private fun AuthScreen(onAuthenticated: (UserSession) -> Unit) {
    val context = LocalContext.current
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()
    var registering by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var emailCode by remember { mutableStateOf("") }
    var codeLogin by remember { mutableStateOf(false) }
    var codeBusy by remember { mutableStateOf(false) }
    var codeCooldown by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(codeCooldown) {
        if (codeCooldown > 0) {
            kotlinx.coroutines.delay(1_000)
            codeCooldown--
        }
    }

    QumiLoginFrame(
        registering = registering,
        codeLogin = codeLogin,
        account = account,
        username = username,
        password = password,
        email = email,
        emailCode = emailCode,
        codeBusy = codeBusy,
        codeCooldown = codeCooldown,
        error = error,
        busy = busy,
        onAccount = { account = it; error = null },
        onUsername = { username = it; error = null },
        onPassword = { password = it; error = null },
        onEmail = { email = it; emailCode = ""; codeCooldown = 0; error = null },
        onEmailCode = { emailCode = it.filter(Char::isDigit).take(6); error = null },
        onToggleLoginMode = { codeLogin = !codeLogin; error = null },
        onSendCode = {
            scope.launch {
                codeBusy = true
                error = null
                try {
                    api.sendEmailCode(email, if (registering) "register" else "login")
                    codeCooldown = 60
                } catch (cause: CancellationException) { throw cause
                } catch (cause: Exception) { error = cause.message ?: "验证码发送失败" }
                finally { codeBusy = false }
            }
        },
        onSubmit = {
                scope.launch {
                    busy = true
                    error = null
                    try {
                        try { flushLogout(context, api) }
                        catch (cause: CancellationException) { throw cause }
                        catch (_: Exception) { error("上次退出尚未同步，请联网后重试") }
                        if (registering) {
                            val created = api.register(username, password, email, emailCode)
                            val loggedIn = api.login(created.username, password)
                            onAuthenticated(loggedIn)
                        } else if (codeLogin) {
                            onAuthenticated(api.loginWithEmailCode(email, emailCode))
                        } else {
                            onAuthenticated(api.login(account, password))
                        }
                    } catch (cause: CancellationException) { throw cause
                    } catch (cause: Exception) {
                        error = if (cause is java.net.ConnectException) {
                            "无法连接直播服务，请确认服务已启动后重试"
                        } else {
                            cause.message ?: "请求失败，请检查服务地址"
                        }
                    } finally {
                        busy = false
                    }
                }
        },
        onToggleRegister = { registering = !registering; emailCode = ""; codeCooldown = 0; error = null },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedApp(session: UserSession, onLogout: () -> Unit, onRequestNotifications: () -> Unit) {
    val context = LocalContext.current
    val api = remember { LivePlatformApi() }
    val demoChatState = remember(session.id) { DemoChatState(context, session.id) }
    var demoPeerId by remember { mutableStateOf<String?>(null) }
    var demoPeer by remember { mutableStateOf<DemoProfile?>(null) }
    val demoConversations = remember(session.id) { DemoConversations(context, session.id) }
    val peerDemoChatState = remember(session.id, demoPeer, demoPeerId) { DemoChatState(context, session.id, demoPeer) { preview ->
        demoPeerId?.let { demoConversations.update(it, preview) }
    } }
    val giftOutbox = remember(session.id) { GiftOutbox(context, session.id) }
    val effectSession = remember(session.id) { GiftEffectSession(context) }
    DisposableEffect(effectSession) { onDispose { effectSession.close() } }
    val openImGateway = remember(context) { OpenImGateway(context) }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var momentComposerVisible by remember { mutableStateOf(false) }
    var accountPage by remember { mutableStateOf(QumiAccountPage.MINE) }
    var searchReturnTab by remember { mutableIntStateOf(0) }
    var searchReturnPage by remember { mutableStateOf(QumiAccountPage.MINE) }
    var profileReturnTab by remember { mutableIntStateOf(0) }
    var profileReturnPage by remember { mutableStateOf(QumiAccountPage.MINE) }
    var publicProfileId by remember { mutableStateOf<Long?>(null) }
    var publicProfileFallbackName by remember { mutableStateOf("") }
    var openImMessagePage by remember { mutableStateOf(false) }
    val interactionPrefs = remember(session.id) {
        context.getSharedPreferences("moment_interactions", android.content.Context.MODE_PRIVATE)
    }
    val interactionReadKey = "seen_${session.id}"
    var seenInteractionIds by remember(session.id) {
        mutableStateOf(interactionPrefs.getStringSet(interactionReadKey, emptySet())?.toSet() ?: emptySet())
    }
    var latestInteractionIds by remember(session.id) { mutableStateOf(emptyList<String>()) }
    fun markInteractionsViewed(ids: List<String>) {
        val updated = (ids + seenInteractionIds).distinct().take(500).toSet()
        seenInteractionIds = updated
        interactionPrefs.edit().putStringSet(interactionReadKey, updated).apply()
    }
    LaunchedEffect(session.id, selectedTab, accountPage, openImMessagePage) {
        if (selectedTab == 3 && accountPage == QumiAccountPage.MINE && !openImMessagePage) {
            while (true) {
                try {
                    latestInteractionIds = api.momentInteractions(session.token, limit = 100).first.map { it.id }
                } catch (error: CancellationException) { throw error
                } catch (_: Exception) { }
                kotlinx.coroutines.delay(30_000)
            }
        }
    }
    var billsCategory by remember { mutableIntStateOf(0) }
    var billsReturnPage by remember { mutableStateOf(QumiAccountPage.WALLET) }
    var contactTab by remember { mutableIntStateOf(0) }
    var announcementIndex by remember { mutableIntStateOf(0) }
    var announcementTitle by remember { mutableStateOf("绿色直播文明公约") }
    var announcementReturnTab by remember { mutableIntStateOf(3) }
    fun openAnnouncements() {
        announcementReturnTab = selectedTab
        openImMessagePage = false
        selectedTab = 3
        accountPage = QumiAccountPage.EVENTS
    }
    fun closeAnnouncements() {
        selectedTab = announcementReturnTab
        accountPage = QumiAccountPage.MINE
    }
    fun openPublicProfile(id: String, name: String) {
        val targetId = id.toLongOrNull()?.takeIf { it > 0 } ?: return
        profileReturnTab = selectedTab
        profileReturnPage = accountPage
        publicProfileId = targetId
        publicProfileFallbackName = name
        selectedTab = 4
        accountPage = QumiAccountPage.PUBLIC_PROFILE
    }
    BackHandler(enabled = (selectedTab == 4 && accountPage != QumiAccountPage.MINE) || (selectedTab == 3 && (openImMessagePage || accountPage != QumiAccountPage.MINE))) {
        accountPage = when {
            selectedTab == 3 && openImMessagePage -> { openImMessagePage = false; QumiAccountPage.MINE }
            selectedTab == 3 && accountPage == QumiAccountPage.ANNOUNCEMENT_DETAIL -> QumiAccountPage.EVENTS
            selectedTab == 3 && accountPage == QumiAccountPage.EVENTS -> {
                closeAnnouncements()
                QumiAccountPage.MINE
            }
            selectedTab == 3 && accountPage == QumiAccountPage.SECRETARY_PROFILE -> QumiAccountPage.SECRETARY_SETTINGS
            selectedTab == 3 && accountPage == QumiAccountPage.SECRETARY_SETTINGS -> QumiAccountPage.SECRETARY_CHAT
            selectedTab == 3 && accountPage == QumiAccountPage.SECRETARY_CHAT -> QumiAccountPage.MINE
            selectedTab == 3 -> QumiAccountPage.MINE
            else -> when (accountPage) {
                QumiAccountPage.BILLS -> billsReturnPage
                QumiAccountPage.PROFILE_EDIT -> QumiAccountPage.PROFILE
                QumiAccountPage.YOUTH, QumiAccountPage.ABOUT, QumiAccountPage.BLACKLIST, QumiAccountPage.QUALIFICATION -> QumiAccountPage.SETTINGS
                QumiAccountPage.SEARCH -> { selectedTab = searchReturnTab; searchReturnPage }
                QumiAccountPage.PUBLIC_PROFILE -> { selectedTab = profileReturnTab; profileReturnPage }
                QumiAccountPage.LEVEL -> QumiAccountPage.MINE
                QumiAccountPage.TASKS, QumiAccountPage.GUARD, QumiAccountPage.HOST,
                QumiAccountPage.IDENTITY, QumiAccountPage.CONTACTS, QumiAccountPage.EVENTS,
                QumiAccountPage.ANNOUNCEMENT_DETAIL, QumiAccountPage.VISITORS -> QumiAccountPage.MINE
                else -> QumiAccountPage.MINE
            }
        }
    }
    var discoverySection by remember { mutableIntStateOf(0) }
    var discoveryComposerVisible by remember { mutableStateOf(false) }
    var approvedVideoPublisher by remember(session.token) { mutableStateOf(false) }
    var sampleRoomIndex by remember { mutableStateOf<Int?>(null) }
    var sampleLivePreview by remember { mutableStateOf(false) }
    var openVideoFromLiveSignal by remember { mutableIntStateOf(0) }
    var handledVideoOpenSignal by remember { mutableIntStateOf(0) }
    var closeVideoForLiveSignal by remember { mutableIntStateOf(0) }
    var switchToVideoAfterRoom by remember { mutableStateOf(false) }
    var canManageOperations by remember { mutableStateOf(false) }
    var operationsSummary by remember { mutableStateOf<OperationsSummary?>(null) }
    var operationsUsers by remember { mutableStateOf<OperationsUsers?>(null) }
    var showModeration by remember { mutableStateOf(false) }
    var showPasswordChange by remember { mutableStateOf(false) }
    var showCoverUpload by remember { mutableStateOf(false) }
    var showRoomChat by remember { mutableStateOf(false) }
    var showBusiness by remember { mutableStateOf<String?>(null) }
    var hostProfile by remember { mutableStateOf<LiveRoom?>(null) }
    var showReferral by remember { mutableStateOf(false) }
    var referralRequestId by remember(session.id) { mutableStateOf<String?>(null) }
    var customerServiceNotice by remember { mutableStateOf<String?>(null) }
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
    var giftCelebration by remember { mutableStateOf<Pair<String, GiftSendPresentation>?>(null) }
    var giftCelebrationNonce by remember { mutableIntStateOf(0) }
    var dmCelebration by remember { mutableStateOf<Gift?>(null) }
    var dmCelebrationNonce by remember { mutableIntStateOf(0) }
    val celebratedGiftRequests = remember { LinkedHashSet<String>() }
    var wallet by remember { mutableStateOf<Wallet?>(null) }
    var level by remember { mutableStateOf<UserLevel?>(null) }
    var dailyEarnings by remember { mutableStateOf<Earnings?>(null) }
    var monthlyEarnings by remember { mutableStateOf<Earnings?>(null) }
    var activeRoom by remember { mutableStateOf<LiveRoom?>(null) }
    var previewRoom by remember { mutableStateOf<LiveRoom?>(null) }
    var switchToLiveAfterViewer by remember { mutableStateOf(false) }
    LaunchedEffect(activeRoom?.stream, previewRoom?.stream, sampleRoomIndex, switchToLiveAfterViewer) {
        if (switchToLiveAfterViewer && (activeRoom != null || previewRoom != null || sampleRoomIndex != null)) {
            withFrameNanos { }
            discoverySection = 1
            withFrameNanos { }
            closeVideoForLiveSignal++
            switchToLiveAfterViewer = false
        }
    }
    var switchingRoom by remember { mutableStateOf(false) }
    var activeRoomStatus by remember { mutableStateOf("正在连接媒体服务") }
    var roomGateway by remember { mutableStateOf<LiveKitRoomGateway?>(null) }
    var srsIngest by remember { mutableStateOf<SrsIngestPublisher?>(null) }
    var cameraBeauty by remember(session.id) { mutableStateOf(CameraBeautyPreferences.load(context, session.id)) }
    val cameraHandoff = remember { Mutex() }
    var hostCohostLiveKit by remember { mutableStateOf(false) }
    var roomRegistration by remember { mutableStateOf<BilldRoomRegistration?>(null) }
    var liveStartedAtMillis by remember { mutableStateOf(0L) }
    var finishedLiveRoom by remember { mutableStateOf<FinishedLiveRoom?>(null) }
    var activeMatchId by remember { mutableStateOf<Long?>(null) }
    var matchScores by remember { mutableStateOf(emptyList<MatchScore>()) }
    var matchRemainingSeconds by remember { mutableStateOf<Int?>(null) }
    var matchLoading by remember { mutableStateOf(false) }
    var matchInvitations by remember { mutableStateOf(emptyList<MatchInvitation>()) }
    var startingLive by remember { mutableStateOf(false) }
    var showCreateRoom by remember(session.id) { mutableStateOf(false) }
    var showCreateRoomBeauty by remember(session.id) { mutableStateOf(false) }
    var pendingRoomConfig by remember(session.id) { mutableStateOf<RoomCreateConfig?>(null) }
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
    val demoCallSession = remember(session.id) { DemoCallSession() }
    var showDmCallPrice by remember { mutableStateOf(false) }
    var incomingCall by remember { mutableStateOf<JSONObject?>(null) }
    var incomingCallBusy by remember { mutableStateOf(false) }
    var showGiftRecharge by remember { mutableStateOf(false) }
    var showGiftRechargeQr by remember { mutableStateOf(false) }
    val callAppForeground = rememberChatForeground()
    LaunchedEffect(session.token, callAppForeground, activeCall != null) {
        if (!callAppForeground || activeCall != null) { incomingCall = null; return@LaunchedEffect }
        while (true) {
            try { incomingCall = api.incomingDmCall(session.token)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { incomingCall = null }
            kotlinx.coroutines.delay(3_000)
        }
    }
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
    val onlineChatUsers = rememberChatOnlineUsers(openImGateway,
        openImConversations.map { it.userId } + openImRecipient,
        enabled = selectedTab == 3 && openImReady && messageConnection == MessageConnection.CONNECTED)
    fun openDirectMessage(id: String, name: String, greeting: Boolean = false) {
        demoProfileForUser(context, id)?.let { peer ->
            demoPeerId = id; demoPeer = peer
            demoConversations.touch(id, peer)
            selectedTab = 3; accountPage = QumiAccountPage.DEMO_CHAT; openImMessagePage = false
            return
        }
        openImRecipient = id
        openImRecipientName = name
        openImMessage = if (greeting) "你好，看到你的动态啦，想和你聊聊～" else ""
        selectedTab = 3
        accountPage = QumiAccountPage.MINE
        openImMessagePage = true
        chatOpenSignal++
    }
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
    var blockTarget by remember { mutableStateOf<PublicUser?>(null) }
    var blockBusy by remember { mutableStateOf(false) }
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
    fun openContactLive(user: PublicUser) {
        scope.launch {
            try {
                val fresh = api.liveRooms()
                val room = fresh.firstOrNull { it.creatorId == user.id }
                if (room != null) activeRoom = LiveRoom(room.host, room.title, room.creatorId, room.roomId,
                    room.coverPath, room.roomType, room.welcomeText, room.backgroundId, room.announcementText)
                else {
                    val demo = demoLiveIndex(user)
                    if (demo != null) sampleRoomIndex = demo else notify("主播已下播")
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                val demo = demoLiveIndex(user)
                if (demo != null) sampleRoomIndex = demo else notify("直播状态暂时无法更新，请重试")
            }
        }
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

    fun showConfirmedGift(roomId: String, requestId: String, giftId: String, senderId: Long = session.id, quantity: Int = 1) {
        if (activeRoom?.stream != roomId || requestId.isBlank()) return
        val gift = gifts.firstOrNull { it.id == giftId } ?: return
        if (!celebratedGiftRequests.add(requestId)) return
        if (celebratedGiftRequests.size > 200) {
            celebratedGiftRequests.remove(celebratedGiftRequests.first())
        }
        giftCelebration = roomId to GiftSendPresentation(gift,
            if (senderId == session.id) session.username else "用户$senderId",
            if (senderId == session.id) session.avatar else null, quantity)
        giftCelebrationNonce++
        val nonce = giftCelebrationNonce
        if (senderId != session.id) scope.launch {
            runCatching { api.searchUsers(session.token, senderId.toString()).firstOrNull { it.id == senderId } }
                .onSuccess { sender ->
                    if (sender != null && giftCelebrationNonce == nonce && activeRoom?.stream == roomId) {
                        giftCelebration = roomId to GiftSendPresentation(gift, sender.username, sender.avatar, quantity)
                    }
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
                    val giftPayload = JSONObject(intent.payload)
                    activeRoom?.stream?.let { roomId ->
                        showConfirmedGift(roomId, giftPayload.optString("request_id"),
                            giftPayload.optString("gift_id"), quantity = giftPayload.optInt("quantity", 1))
                    }
                    notify("礼物已由服务端确认")
                    // A replay returns the original post-debit balance; refresh the current wallet.
                    runCatching { api.wallet(session.token) }.onSuccess { wallet = it }
                } catch (error: GiftRejectedException) {
                    runCatching { giftOutbox.clear() }.onSuccess { pendingGift = null }
                    if (isInsufficientGiftBalance(error)) showGiftRecharge = true
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
                if (selectedTab == 5) { selectedTab = 4; notify("运营权限暂不可用，请刷新后重试") }
            } else if (selectedTab == 5) operationsRefresh++
            if (selectedTab != 5) break
            kotlinx.coroutines.delay(15_000)
        } while (true)
    }

    LaunchedEffect(canManageOperations, selectedTab, operationsPage, operationsRefresh) {
        if (!canManageOperations || selectedTab != 5) return@LaunchedEffect
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


    LaunchedEffect(session.token) {
        supportContact = try { api.openImSupport(session.token) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
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
    val sendDmGift: (String) -> Unit = fun(giftId) {
        if (pendingDmGift == null && wallet?.balance?.let { balance -> gifts.firstOrNull { it.id == giftId }?.coinPrice?.let { it > balance } } == true) {
            showGiftRecharge = true
            return
        }
        val recipient = openImRecipient.trim()
        val pending = pendingDmGift
        if (recipient.isBlank() || openImBusy) return
        if (pending != null && (pending.recipientId != recipient || (pending.phase == "settle" && pending.giftId != giftId))) {
            openImStatus = "请先完成上一笔私信礼物"
            return
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
                dmCelebration = gifts.firstOrNull { it.id == giftId }
                dmCelebrationNonce++
            } catch (error: SettlementUnavailableException) {
                runCatching { dmGiftOutbox.clear() }.onSuccess { pendingDmGift = null }
                openImStatus = error.message ?: "私信送礼结算尚未接通，本次未扣币，也未发送礼物消息"
            } catch (error: GiftRejectedException) {
                runCatching { dmGiftOutbox.clear() }.onSuccess { pendingDmGift = null }
                if (isInsufficientGiftBalance(error)) showGiftRecharge = true
                openImStatus = error.message ?: "本次未扣币"
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (isInsufficientGiftBalance(error)) {
                    showGiftRecharge = true
                    runCatching { dmGiftOutbox.clear() }.onSuccess { pendingDmGift = null }
                }
                openImStatus = error.message ?: "礼物结果未确认，请重试同一笔，不要重新赠送"
            } finally { openImBusy = false }
        }
    }
    val sendDmVideo: (String, Int, ByteArray, Int) -> Unit = fun(title, price, bytes, duration) {
        val recipient = openImRecipient.trim()
        val pending = pendingDmVideo
        if (recipient.isBlank() || openImBusy) return
        if (pending != null && pending.recipientId != recipient) {
            openImStatus = "请先完成上一条视频消息"
            return
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
    val sendDmImage: (Uri) -> Unit = { uri ->
        val recipient = openImRecipient.trim()
        if (recipient.isNotBlank() && !openImBusy) scope.launch {
            openImBusy = true
            try {
                openImGateway.sendImage(recipient, uri)
                openImStatus = "图片已发送"
                historyRefresh++
                runCatching { openImGateway.conversations() }
                    .onSuccess { openImConversations = parseOpenImConversations(it) }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { openImStatus = error.message ?: "图片发送失败，请重试"
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
            channel.listen(session.token, roomId) { broadcast ->
                effectSession.offerBroadcast(broadcast)
                scope.launch {
                    showConfirmedGift(broadcast.roomId, broadcast.requestId, broadcast.giftId, broadcast.viewerId, broadcast.quantity)
                }
            }
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
                rooms = result.map { item -> LiveRoom(item.host, item.title, item.creatorId, item.roomId,
                    item.coverPath, item.roomType, item.welcomeText, item.backgroundId, item.announcementText) }
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

    LaunchedEffect(session.token, selectedTab, discoverySection) {
        if (selectedTab != 0 || discoverySection != 0) return@LaunchedEffect
        approvedVideoPublisher = false
        try {
            approvedVideoPublisher = api.creatorApplications(session.token).any { it.status == "APPROVED" }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { approvedVideoPublisher = false }
    }

    LaunchedEffect(session.token, selectedTab, discoveryComposerVisible) {
        if (selectedTab != 0 || discoveryComposerVisible) return@LaunchedEffect
        while (true) {
            try {
                rooms = api.liveRooms().map { item -> LiveRoom(item.host, item.title, item.creatorId, item.roomId,
                    item.coverPath, item.roomType, item.welcomeText, item.backgroundId, item.announcementText) }
                roomsError = null
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { roomsError = "直播状态暂时无法更新" }
            kotlinx.coroutines.delay(10_000)
        }
    }

    LaunchedEffect(hostProfile?.creatorId, session.token) {
        val viewedUserId = hostProfile?.creatorId ?: return@LaunchedEffect
        if (viewedUserId != session.id && viewedUserId > 0) runCatching { api.recordProfileVisit(session.token, viewedUserId) }
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
        if (selectedTab != 4) return@LaunchedEffect
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
        roomType = activeRoom?.roomType ?: "SINGLE",
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
        val gateway = LiveKitRoomGateway(context, cameraBeauty)
        roomGateway = gateway
        if (!ordinarySrs) activeRoomStatus = "正在连接直播"
        try {
            withTimeout(20_000) {
                val credentials = api.liveKitToken(session.token, liveSessionId, activeMatchId)
                gateway.join(credentials.url, credentials.token)
                if (isHost && !ordinarySrs) cameraHandoff.withLock {
                    if (room.roomType == "VOICE") gateway.publishCohost(false)
                    else gateway.publishCameraAndMicrophone()
                }
                else if (!isHost && activeMatchId == null && cohostMode != null)
                    gateway.publishCohost(room.roomType != "VOICE" && cohostMode == "VIDEO")
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

    LaunchedEffect(activeRoom?.stream, session.id, session.token, activeMatchId, hostCohostLiveKit) {
        val room = activeRoom ?: return@LaunchedEffect
        if (room.roomType in setOf("VOICE", "MULTI") || !hostPublishesOrdinarySrs(room.creatorId == session.id, activeMatchId, hostCohostLiveKit)) return@LaunchedEffect
        val url = srsIngestUrl(BuildConfig.SRS_RTMP_URL, room.stream)
        if (url == null) {
            activeRoomStatus = "推流地址不可用，观众将看不到画面"
            return@LaunchedEffect
        }
        cameraHandoff.withLock {
            val publisher = SrsIngestPublisher(context, cameraBeauty)
            srsIngest = publisher
            try {
                activeRoomStatus = "正在连接直播"
                publisher.onDropped = { activeRoomStatus = "推流已断开，观众暂时看不到画面" }
                withTimeout(15_000) {
                    val ticket = api.srsPublishToken(session.token, room.stream.toLong())
                    publisher.start("$url?ticket=${java.net.URLEncoder.encode(ticket, "UTF-8")}")
                }
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
        activeRoom?.let { it.roomType !in setOf("VOICE", "MULTI") && hostPublishesOrdinarySrs(it.creatorId == session.id, activeMatchId, hostCohostLiveKit) } == true,
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
        if (activeMatchId != null) return@LaunchedEffect
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
                    matchRemainingSeconds = null
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { /* Keep the last confirmed state during a temporary outage. */ }
            kotlinx.coroutines.delay(3_000)
        }
    }

    LaunchedEffect(activeMatchId, session.token) {
        val matchId = activeMatchId ?: return@LaunchedEffect
        while (true) {
            runCatching { api.matchScore(session.token, matchId) }.onSuccess {
                matchScores = it.scores
                matchRemainingSeconds = it.remainingSeconds
            }
            kotlinx.coroutines.delay(3_000)
        }
    }
    LaunchedEffect(activeMatchId) {
        while (activeMatchId != null) {
            kotlinx.coroutines.delay(1_000)
            matchRemainingSeconds = matchRemainingSeconds?.let { (it - 1).coerceAtLeast(0) }
        }
    }

    LaunchedEffect(selectedTab, activeRoom?.creatorId, session.token) {
        if (selectedTab != 2 && activeRoom?.creatorId != session.id) return@LaunchedEffect
        while (true) {
            try { matchInvitations = api.matchInvitations(session.token)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { }
            kotlinx.coroutines.delay(3_000)
        }
    }

    val requestMediaPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) {
            val draft = pendingRoomConfig ?: return@rememberLauncherForActivityResult
            scope.launch {
                startingLive = true
                var sessionId: Long? = null
                var registration: BilldRoomRegistration? = null
                try {
                    sessionId = api.startLive(session.token)
                    api.setWatchPrice(session.token, sessionId, draft.watchPriceCoins)
                    registration = BilldRoomRegistration()
                    withTimeout(15_000) {
                        registration.register(session.token, session.id, session.username, sessionId.toString(), draft)
                    }
                    roomRegistration = registration
                    activeRoom = LiveRoom(session.username, draft.title, session.id, sessionId.toString(),
                        draft.coverPath, draft.roomType, draft.welcomeText, draft.backgroundId, draft.announcementText)
                    liveStartedAtMillis = System.currentTimeMillis()
                    showCreateRoom = false
                    pendingRoomConfig = null
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
            notify(if (pendingRoomConfig?.roomType == "VOICE") "语音房需要麦克风权限" else "开播需要相机和麦克风权限")
        }
    }

    fun leaveRoom() {
        giftCelebration = null
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
                if (isHost) {
                    val startedAt = liveStartedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
                    finishedLiveRoom = FinishedLiveRoom(room, startedAt,
                        ((System.currentTimeMillis() - startedAt) / 1_000).coerceAtLeast(0))
                    liveStartedAtMillis = 0L
                    refreshKey++
                    val recorded = runCatching { api.creatorSessions(session.token)
                        .firstOrNull { it.id == room.stream } }.getOrNull()
                    finishedLiveRoom = finishedLiveRoom?.takeIf { it.room.stream == room.stream }
                        ?.copy(session = recorded, loading = false)
                }
            } catch (error: Exception) {
                notify("离开直播失败 · ${error.message ?: "请求失败"}")
            }
        }
    }

    fun switchRoom(direction: Int) {
        val current = activeRoom ?: return
        if (current.creatorId == session.id || switchingRoom) return
        switchingRoom = true
        scope.launch {
            try {
                val fresh = api.liveRooms().map { LiveRoom(it.host, it.title, it.creatorId, it.roomId, it.coverPath) }
                rooms = fresh
                val currentIndex = fresh.indexOfFirst { it.stream == current.stream }
                val next = when {
                    fresh.size < 2 && currentIndex >= 0 -> null
                    fresh.isEmpty() -> null
                    currentIndex < 0 -> if (direction > 0) fresh.first() else fresh.last()
                    else -> fresh[Math.floorMod(currentIndex + direction, fresh.size)]
                }
                if (next == null || next.stream == current.stream) {
                    notify("暂无其他正在直播的房间")
                    return@launch
                }
                val liveId = current.stream.toLongOrNull()
                if (liveId != null) {
                    val seats = try { api.cohosts(session.token, liveId) }
                    catch (error: Exception) {
                        if (cohostMode != null) throw error
                        emptyList()
                    }
                    for (request in seats.filter { it.userId == session.id && it.status in listOf("PENDING", "ACCEPTED", "MUTED") }) {
                        api.cohostAction(session.token, liveId, request.id, "LEAVE")
                    }
                }
                if (activeRoom?.stream != current.stream) return@launch
                showRoomChat = false
                showCohost = false
                cohostMode = null
                cohostKnown = false
                activeMatchId = null
                matchScores = emptyList()
                playbackStream = null
                activeRoomStatus = "正在连接直播"
                activeRoom = next
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                notify(error.message ?: "切换直播失败，请重试")
            } finally { switchingRoom = false }
        }
    }

    fun openSupportChat() {
        if (openImBusy) {
            notify("正在连接消息服务")
            return
        }
        scope.launch {
            openImBusy = true
            try {
                val target = openSupportConversation(api, openImGateway, session.token)
                openImRecipient = target.userId
                openImRecipientName = target.name
                supportContact = ChatRecipient(target.userId, target.name)
                selectedTab = 3
                accountPage = QumiAccountPage.MINE
                openImMessagePage = true
                chatOpenSignal++
            } catch (error: CancellationException) { throw error
            } catch (error: PlatformHttpException) {
                if (error.statusCode == 404) supportContact = null
                else customerServiceNotice = error.message ?: "客服暂时无法连接"
            } catch (error: Exception) {
                customerServiceNotice = error.message ?: "客服暂时无法连接"
            } finally { openImBusy = false }
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

    fun beginCall(peerId: String, peerName: String?, requestedMode: String, callId: String?, offeredPrice: Int? = null) {
        if (activeCall != null || demoCallSession.active != null) {
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
        activeCall = PendingDmCall(peerId, peerName, requestedMode, callId, offeredPrice)
    }

    var checkingCreator by remember { mutableStateOf(false) }
    var showCreatorGate by remember { mutableStateOf(false) }
    var creatorGateStatus by remember { mutableStateOf<String?>(null) }
    var creatorGateError by remember { mutableStateOf<String?>(null) }
    val startLive: () -> Unit = {
        if (!checkingCreator && !startingLive) {
            checkingCreator = true
            scope.launch {
                try {
                    val applications = api.creatorApplications(session.token)
                    creatorGateStatus = (applications.firstOrNull { it.status == "APPROVED" } ?: applications.firstOrNull())?.status
                    creatorGateError = null
                    if (creatorGateStatus == "APPROVED") {
                        val age = profile?.age
                        val minimumAge = if (profile?.region == "KR") 19 else 18
                        if (profile?.source != "SELF_REPORTED") {
                            selectedTab = 4
                            notify("请先在“我的”填写年龄和性别，再开播")
                        } else if (age == null || age < minimumAge) {
                            notify("当前年龄未达到开播要求")
                        } else {
                            showCreateRoom = true
                        }
                    } else showCreatorGate = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    creatorGateError = failure.message ?: "认证状态暂时无法读取"
                    showCreatorGate = true
                } finally {
                    checkingCreator = false
                }
            }
        }
    }

    if (showCreatorGate) Dialog(onDismissRequest = { showCreatorGate = false }) {
        Surface(shape = RoundedCornerShape(22.dp), color = LiveStyle.dialogSurface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { showCreatorGate = false }, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = LiveStyle.muted)
                }
                Box(Modifier.padding(top = 8.dp).size(108.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(LiveStyle.accentSoft, Color(0xFFE3D5FF)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = LiveStyle.accent, modifier = Modifier.size(72.dp))
                }
                Text(when {
                    creatorGateError != null -> "暂无法确认主播身份"
                    creatorGateStatus == "PENDING" -> "主播认证审核中"
                    creatorGateStatus == "REJECTED" -> "请重新认证为主播"
                    else -> "完成主播认证"
                }, Modifier.padding(top = 22.dp), color = LiveStyle.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(when {
                    creatorGateError != null -> creatorGateError ?: "请稍后重试"
                    creatorGateStatus == "PENDING" -> "审核通过后即可开播"
                    else -> "认证审核通过后即可开播"
                }, Modifier.padding(top = 8.dp), color = LiveStyle.muted, fontSize = 13.sp)
                Button(onClick = {
                    showCreatorGate = false
                    if (creatorGateError != null) startLive() else { selectedTab = 4; accountPage = QumiAccountPage.IDENTITY }
                }, modifier = Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 4.dp).height(48.dp), shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = LiveStyle.accent)) {
                    Text(if (creatorGateError != null) "重试" else if (creatorGateStatus == "PENDING") "查看审核进度" else "立即认证", fontSize = 17.sp)
                }
            }
        }
    }

    if (showCreateRoom) Dialog(onDismissRequest = {
        if (showCreateRoomBeauty) showCreateRoomBeauty = false else if (!startingLive) showCreateRoom = false
    }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize()) {
        QumiCreateRoomScreen(
            token = session.token,
            username = session.username,
            api = api,
            starting = startingLive,
            onBack = { showCreateRoom = false },
            onBeauty = { showCreateRoomBeauty = true },
            onCreate = { draft ->
                pendingRoomConfig = draft
                requestMediaPermissions.launch(if (draft.roomType == "VOICE")
                    arrayOf(Manifest.permission.RECORD_AUDIO)
                else arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            },
        )
        if (showCreateRoomBeauty) QumiBeautyScreen(
            settings = cameraBeauty,
            onSettingsChange = { settings ->
                cameraBeauty = settings
                CameraBeautyPreferences.save(context, session.id, settings)
            },
            onBack = { showCreateRoomBeauty = false },
        )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(NightInk),
        containerColor = NightInk,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!momentComposerVisible && !discoveryComposerVisible && ((selectedTab != 3 && selectedTab != 4) || accountPage == QumiAccountPage.MINE)) {
                val titles = listOf("发现", "动态", "直播", "消息", "我的") + if (canManageOperations) listOf("运营") else emptyList()
                val icons = listOf(Icons.Rounded.Explore, Icons.Rounded.RocketLaunch, Icons.Rounded.PlayArrow, Icons.Outlined.Forum, Icons.Rounded.AccountCircle, Icons.Rounded.Shield)
                Row(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().height(66.dp), verticalAlignment = Alignment.CenterVertically) {
                    titles.forEachIndexed { index, title ->
                        Column(Modifier.weight(1f).fillMaxHeight().clickable {
                            selectedTab = index; accountPage = QumiAccountPage.MINE; openImMessagePage = false
                        }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Box(Modifier.size(33.dp).then(if (index == 2) Modifier.shadow(5.dp, RoundedCornerShape(8.dp),
                                ambientColor = Color(0xFF9A79F8), spotColor = Color(0xFF9A79F8)).clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF9A79F8), Color(0xFF6233E3)))) else Modifier),
                                contentAlignment = Alignment.Center) {
                                Icon(icons[index], contentDescription = title, modifier = Modifier.size(if (index == 2) 25.dp else 23.dp),
                                    tint = if (index == 2) Color.White else if (selectedTab == index) Color(0xFF7044D8) else Color(0xFFBFC0CB))
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(title, color = if (index == 2 || selectedTab == index) Color(0xFF2F2C37) else Color(0xFF676975), fontSize = 10.sp)
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (selectedTab == 0 && discoveryComposerVisible) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                QumiMomentComposer(session.token,
                    onBack = { discoveryComposerVisible = false },
                    onPublished = { discoveryComposerVisible = false },
                    videoOnly = true)
            }
        } else {
        val discoveryFadeHeight = with(LocalDensity.current) { 250.dp.toPx() }
        val discoveryBackdrop = if (selectedTab == 0) Modifier
            .background(Brush.horizontalGradient(listOf(Color(0xFFE3DDFF), Color(0xFFD7E9FF))))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.White), endY = discoveryFadeHeight))
        else Modifier
        Column(Modifier.fillMaxSize().padding(padding).background(Color.White).then(discoveryBackdrop)) {
            if (selectedTab == 0) {
                QumiDiscoveryHeader(
                    section = discoverySection,
                    onSection = { discoverySection = it },
                    showPublishVideo = approvedVideoPublisher,
                    onPublishVideo = { discoveryComposerVisible = true },
                )
            } else if (selectedTab > 4) TopBar(title = "运营", onRefresh = { refreshKey++ })
            pendingGift?.let { pending ->
                PendingGiftNotice(pending, giftLoading, onRetry = { submitGift(pending) })
            }
            when (selectedTab) {
                0 -> Box(Modifier.fillMaxSize()) {
                  if (discoverySection == 1) DiscoveryLiveScreen(
                    rooms = rooms,
                    loading = roomsLoading,
                    error = roomsError,
                    onJoin = { activeRoom = it },
                    onHost = { hostProfile = it },
                    onSample = { sampleLivePreview = false; sampleRoomIndex = it },
                    onRefresh = { refreshKey++ },
                  )
                  QumiVideoFeed(
                    token = session.token,
                    currentUserId = session.id,
                    liveCreatorIds = if (roomsError == null) rooms.map { it.creatorId }.toSet() else emptySet(),
                    onOpenUser = { id, name -> openPublicProfile(id, name) },
                    onGoMoments = { selectedTab = 1 },
                    openFirstSignal = openVideoFromLiveSignal,
                    consumedOpenSignal = handledVideoOpenSignal,
                    onOpenFirstConsumed = { handledVideoOpenSignal = it },
                    closeViewerSignal = closeVideoForLiveSignal,
                    showGrid = discoverySection == 0,
                    onViewerOpened = {
                        sampleRoomIndex = null
                        previewRoom = null
                        if (switchToVideoAfterRoom) {
                            switchToVideoAfterRoom = false
                            leaveRoom()
                        }
                    },
                    onViewerUnavailable = {
                        sampleRoomIndex = null
                        previewRoom = null
                        if (switchToVideoAfterRoom) {
                            switchToVideoAfterRoom = false
                            leaveRoom()
                        }
                    },
                    onOpenLive = {
                        switchToLiveAfterViewer = true
                        if (rooms.isNotEmpty()) previewRoom = rooms.first() else { sampleLivePreview = true; sampleRoomIndex = 0 }
                    },
                    onOpenCreatorLive = { creatorId ->
                        scope.launch {
                            try {
                                val fresh = api.liveRooms().map { item -> LiveRoom(item.host, item.title, item.creatorId, item.roomId,
                                    item.coverPath, item.roomType, item.welcomeText, item.backgroundId, item.announcementText) }
                                rooms = fresh
                                roomsError = null
                                val creatorRoom = fresh.firstOrNull { it.creatorId == creatorId }
                                if (creatorRoom == null) notify("主播已下播")
                                else {
                                    switchToLiveAfterViewer = true
                                    previewRoom = creatorRoom
                                }
                            } catch (cancelled: CancellationException) { throw cancelled
                            } catch (_: Exception) { notify("直播状态暂时无法更新，请重试") }
                        }
                    },
                  )
                }
                1 -> QumiMomentsScreen(
                    token = session.token,
                    currentUserId = session.id,
                    onMessages = { selectedTab = 3; accountPage = QumiAccountPage.MINE },
                    onOpenAnnouncements = { openAnnouncements() },
                    onOpenUser = { id, name -> openPublicProfile(id, name) },
                    onMessageUser = { id, name, greeting -> openDirectMessage(id, name, greeting) },
                    onComposerVisible = { momentComposerVisible = it },
                )
                2 -> LiveScreen(
                    rooms = rooms,
                    loading = roomsLoading,
                    error = roomsError,
                    starting = startingLive,
                    onStart = startLive,
                    onJoin = { activeRoom = it },
                    onHost = { hostProfile = it },
                    onSample = { sampleLivePreview = false; sampleRoomIndex = it },
                    showStartButton = true,
                    onRefresh = { refreshKey++ },
                )
                3 -> when (accountPage) {
                    QumiAccountPage.SEARCH -> QumiUserSearchScreen(
                        token = session.token,
                        onBack = { selectedTab = searchReturnTab; accountPage = searchReturnPage },
                        onOpenUser = { id, name -> openPublicProfile(id, name) },
                        onMessageUser = { id, name -> openDirectMessage(id, name) },
                        onBlockUser = { id -> scope.launch { runCatching { api.searchUsers(session.token, id.toString()).firstOrNull() }.onSuccess { blockTarget = it }.onFailure { notify(it.message ?: "读取用户失败") } } },
                    )
                    QumiAccountPage.CONTACTS -> QumiContactsScreen(token = session.token, initialTab = contactTab, onOpenUser = { id, name -> openPublicProfile(id, name) }, onMessageUser = { id, name -> openDirectMessage(id, name) }, onOpenLive = ::openContactLive, onBack = { accountPage = QumiAccountPage.MINE }, onSearch = { tab ->
                        contactTab = tab
                        searchReturnTab = selectedTab; searchReturnPage = QumiAccountPage.CONTACTS; accountPage = QumiAccountPage.SEARCH
                    })
                    QumiAccountPage.SECRETARY_PROFILE -> QumiSecretaryProfileScreen(
                        onBack = { accountPage = QumiAccountPage.SECRETARY_SETTINGS },
                    )
                    QumiAccountPage.SECRETARY_CHAT -> QumiSecretaryChatScreen(
                        token = session.token,
                        onBack = { accountPage = QumiAccountPage.MINE },
                        onSettings = { accountPage = QumiAccountPage.SECRETARY_SETTINGS },
                    )
                    QumiAccountPage.DEMO_CHAT -> DemoDirectChatScreen(if (demoPeer == null) demoChatState else peerDemoChatState,
                        session.username, session.avatar, onBack = { accountPage = QumiAccountPage.MINE }, token = session.token, peerId = demoPeerId, ownId = session.id,
                        onStartCall = { chat, mode, price -> if (activeCall == null) demoCallSession.start(chat, mode, price) else notify("请先挂断当前通话") })
                    QumiAccountPage.SECRETARY_SETTINGS -> QumiSecretarySettingsScreen(
                        onBack = { accountPage = QumiAccountPage.SECRETARY_CHAT },
                        onOpenProfile = { accountPage = QumiAccountPage.SECRETARY_PROFILE },
                    )
                    QumiAccountPage.VISITORS -> QumiVisitorsScreen(
                        token = session.token,
                        onBack = { accountPage = QumiAccountPage.MINE },
                        onOpenUser = { id, name -> openPublicProfile(id, name) },
                    )
                    QumiAccountPage.INTERACTIONS -> QumiInteractionsScreen(
                        token = session.token,
                        onBack = { accountPage = QumiAccountPage.MINE },
                        onOpenUser = { id, name -> openPublicProfile(id, name) },
                        onViewed = { markInteractionsViewed(it) },
                    )
                    QumiAccountPage.EVENTS -> QumiAnnouncementListScreen(
                        onBack = { closeAnnouncements() },
                        onOpenDetail = { index, title -> announcementIndex = index; announcementTitle = title; accountPage = QumiAccountPage.ANNOUNCEMENT_DETAIL },
                    )
                    QumiAccountPage.ANNOUNCEMENT_DETAIL -> QumiAnnouncementDetailScreen(
                        title = announcementTitle,
                        index = announcementIndex,
                        onBack = { accountPage = QumiAccountPage.EVENTS },
                    )
                    QumiAccountPage.NOTIFICATIONS -> QumiNotificationSettingsScreen(
                        token = session.token,
                        onBack = { accountPage = QumiAccountPage.MINE },
                    )
                    else -> if (!openImMessagePage) QumiMessagesHomeScreen(
                        token = session.token,
                        ready = openImReady,
                        busy = openImBusy,
                        conversations = demoConversations.rows.map { Triple(it.id, "${it.name}（演示）", it.preview) } + openImConversations.filterNot { real -> demoConversations.rows.any { it.id == real.userId } }.map { Triple(it.userId, it.name, it.latestMessage) },
                        conversationAvatars = openImConversations.associate { it.userId to it.avatar } + demoConversations.rows.associate { it.id to it.avatar },
                        onlineUserIds = onlineChatUsers,
                        demoPreview = demoChatState.messages.lastOrNull()?.text.orEmpty(),
                        onOpenDemo = { demoPeer = null; demoPeerId = null; accountPage = QumiAccountPage.DEMO_CHAT },
                        supportName = supportContact?.name,
                        supportPreview = supportContact?.let { contact -> openImConversations.firstOrNull { it.userId == contact.userId }?.latestMessage },
                        onNewMessage = { openImMessagePage = true },
                        onContacts = { accountPage = QumiAccountPage.CONTACTS },
                        onConnect = { messageConnectionAttempt++ },
                        onOpenAnnouncements = { openAnnouncements() },
                        onOpenVisitors = { accountPage = QumiAccountPage.VISITORS },
                        onOpenInteractions = {
                            markInteractionsViewed(latestInteractionIds)
                            accountPage = QumiAccountPage.INTERACTIONS
                        },
                        interactionUnread = latestInteractionIds.count { it !in seenInteractionIds },
                        onOpenSecretaryChat = { accountPage = QumiAccountPage.SECRETARY_CHAT },
                        onOpenConversation = { id, name ->
                            openDirectMessage(id, name)
                        },
                        onOpenSupport = { openSupportChat(); openImMessagePage = true },
                        onOpenNotifications = { accountPage = QumiAccountPage.NOTIFICATIONS },
                        onRequestNotifications = onRequestNotifications,
                    ) else MessagesScreen(
                    onExit = { openImMessagePage = false },
                    status = openImStatus,
                    ownUserId = session.id.toString(),
                    ownAvatar = session.avatar,
                    ownName = session.username,
                    celebrationGift = dmCelebration, celebrationNonce = dmCelebrationNonce,
                    onCelebrationFinished = { dmCelebration = null },
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
                                    } catch (_: Exception) { if (openImRecipient == recipient && chatVisible) historyError = "更早消息加载失败，请重试"
                                    } finally { historyLoading = false }
                                }
                            }
                        }
                    },
                    conversations = openImConversations,
                    recipientId = openImRecipient,
                    recipientName = openImRecipientName,
                    onlineUserIds = onlineChatUsers,
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
                    onPaidVideoCall = { price -> beginCall(openImRecipient, openImRecipientName, "video", null, price) },
                    onVoiceCall = { beginCall(openImRecipient, openImRecipientName, "voice", null) },
                    onJoinCall = { beginCall(openImRecipient, openImRecipientName, "", it) },
                    onAcceptCall = { invite -> beginCall(invite.fromUserId.toString(), openImRecipientName ?: "用户 ${invite.fromUserId}", invite.mode, invite.callId) },
                    onCallPriceSettings = { showDmCallPrice = true },
                    onSend = {
                        val recipient = openImRecipient.trim()
                        val content = openImMessage.trim()
                        if (recipient.isNotBlank() && content.isNotBlank()) scope.launch {
                            openImBusy = true
                            runCatching { openImGateway.sendText(recipient, content) }
                                .onSuccess {
                                    openImMessage = ""; openImStatus = "消息已发送"; historyRefresh++
                                    runCatching { openImGateway.conversations() }.onSuccess { openImConversations = parseOpenImConversations(it) }
                                }
                                .onFailure { openImStatus = "发送失败，内容已保留，请重试" }
                            openImBusy = false
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
                    onOpenSupport = { openSupportChat() },
                    onOpenVisitors = { accountPage = QumiAccountPage.VISITORS },
                    onOpenAnnouncements = { openAnnouncements() },
                    onOpenNotifications = { openImMessagePage = false; accountPage = QumiAccountPage.NOTIFICATIONS },
                    onSendGift = { gift -> sendDmGift(gift.id) },
                    onRetryGift = { pendingDmGift?.let { sendDmGift(it.giftId) } },
                    onSendVideo = sendDmVideo,
                    onSendImage = sendDmImage,
                    token = session.token,
                    onCreatorVerification = { openImMessagePage = false; selectedTab = 4; accountPage = QumiAccountPage.IDENTITY },
                    onUnlock = unlockDmVideo,
                    onPlay = playDmVideo,
                    onVerifyGift = verifyDmGift,
                    )
                }
                4 -> when (accountPage) {
                    QumiAccountPage.PUBLIC_PROFILE -> publicProfileId?.let { targetId ->
                        QumiPublicProfileScreen(
                            token = session.token,
                            userId = targetId,
                            currentUserId = session.id,
                            fallbackName = publicProfileFallbackName,
                            onBack = { selectedTab = profileReturnTab; accountPage = profileReturnPage },
                            onMessage = { id, name -> openDirectMessage(id, name) },
                            onBlock = { id -> scope.launch {
                                runCatching { api.searchUsers(session.token, id.toString()).firstOrNull() }
                                    .onSuccess { blockTarget = it }
                                    .onFailure { notify(it.message ?: "读取用户失败") }
                            } },
                        )
                    } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { selectedTab = profileReturnTab; accountPage = profileReturnPage }) { Text("返回") }
                    }
                    QumiAccountPage.SEARCH -> QumiUserSearchScreen(
                        token = session.token,
                        onBack = { selectedTab = searchReturnTab; accountPage = searchReturnPage },
                        onOpenUser = { id, name -> openPublicProfile(id, name) },
                        onMessageUser = { id, name -> openDirectMessage(id, name) },
                        onBlockUser = { id -> scope.launch { runCatching { api.searchUsers(session.token, id.toString()).firstOrNull() }.onSuccess { blockTarget = it }.onFailure { notify(it.message ?: "读取用户失败") } } },
                    )
                    QumiAccountPage.MINE -> QumiMineScreen(
                        session = session, wallet = wallet, level = level, profile = profile,
                        onProfile = { accountPage = QumiAccountPage.PROFILE },
                        onWallet = { accountPage = QumiAccountPage.WALLET },
                        onBalanceDetails = { accountPage = QumiAccountPage.BALANCE },
                        onSettings = { accountPage = QumiAccountPage.SETTINGS },
                        onBusiness = { accountPage = QumiAccountPage.HOST },
                        onReferral = { accountPage = QumiAccountPage.REFERRAL },
                        onLevel = { accountPage = QumiAccountPage.LEVEL },
                        onMyPosts = { accountPage = QumiAccountPage.POSTS },
                        onMyLikes = { accountPage = QumiAccountPage.LIKES },
                        onMyFavorites = { accountPage = QumiAccountPage.FAVORITES },
                        onIdentity = { accountPage = QumiAccountPage.IDENTITY },
                        onContacts = {
                            accountPage = QumiAccountPage.CONTACTS
                            contactTab = it.coerceIn(0, 1)
                        },
                        onNoble = { accountPage = QumiAccountPage.NOBLE },
                    )
                    QumiAccountPage.PROFILE -> QumiPersonalHomeScreen(
                        session = session, profile = profile, level = level, coverRevision = refreshKey,
                        onBack = { accountPage = QumiAccountPage.MINE },
                        onEdit = { accountPage = QumiAccountPage.PROFILE_EDIT },
                        onRefresh = { refreshKey++ },
                    )
                    QumiAccountPage.PROFILE_EDIT -> QumiProfileEditScreen(
                        session = session, profile = profile, busy = profileBusy,
                        onBack = { accountPage = QumiAccountPage.PROFILE },
                        onCoverUpload = { showCoverUpload = true },
                        onSave = { age, gender ->
                            if (!profileBusy) scope.launch {
                                profileBusy = true
                                try { profile = api.saveProfile(session.token, age, gender); notify("资料已保存，来源为本人填写")
                                } catch (error: CancellationException) { throw error
                                } catch (_: Exception) { notify("资料未能保存，请检查填写内容后重试")
                                } finally { profileBusy = false }
                            }
                        },
                    )
                    QumiAccountPage.BALANCE -> QumiBalanceScreen(
                        wallet = wallet, rechargeOrders = rechargeOrders, rechargeOrdersError = rechargeOrdersError,
                        onBack = { accountPage = QumiAccountPage.MINE }, onRefresh = { refreshKey++ },
                    )
                    QumiAccountPage.WALLET -> QumiCreatorWalletGatewayScreen(
                        session = session, daily = dailyEarnings, monthly = monthlyEarnings,
                        onBack = { accountPage = QumiAccountPage.MINE }, onApply = { accountPage = QumiAccountPage.IDENTITY },
                        onRefresh = { refreshKey++ },
                        onBills = { billsCategory = it; billsReturnPage = QumiAccountPage.WALLET; accountPage = QumiAccountPage.BILLS },
                        onWithdraw = { showBusiness = "withdrawals" },
                    )
                    QumiAccountPage.BILLS -> QumiBillsScreen(
                        session = session, api = api, initialCategory = billsCategory,
                        rechargeOrders = rechargeOrders, rechargeOrdersError = rechargeOrdersError,
                        giftHistory = giftHistory, giftHistoryLoading = giftHistoryLoading, giftHistoryError = giftHistoryError,
                        onBack = { accountPage = billsReturnPage }, onRefresh = { refreshKey++ },
                        onLoadGiftHistory = { loadGiftHistory(false) },
                    )
                    QumiAccountPage.GUILD -> QumiGuildScreen(onBack = { accountPage = QumiAccountPage.MINE })
                    QumiAccountPage.NOBLE -> QumiNobleScreen(session, onBack = { accountPage = QumiAccountPage.MINE })
                    QumiAccountPage.FRIENDS -> QumiCloseFriendsScreen(
                        session,
                        onBack = { accountPage = QumiAccountPage.MINE },
                        onFindUser = {
                            searchReturnTab = selectedTab
                            searchReturnPage = QumiAccountPage.FRIENDS
                            accountPage = QumiAccountPage.SEARCH
                        },
                    )
                    QumiAccountPage.BEAUTY -> QumiBeautyScreen(
                        settings = cameraBeauty,
                        onSettingsChange = { settings ->
                            cameraBeauty = settings
                            CameraBeautyPreferences.save(context, session.id, settings)
                            srsIngest?.updateBeauty(settings)
                            roomGateway?.updateBeauty(settings)
                        },
                        onBack = { accountPage = QumiAccountPage.MINE },
                    )
                    QumiAccountPage.BLACKLIST -> QumiBlacklistScreen(token = session.token, onBack = { accountPage = QumiAccountPage.SETTINGS })
                    QumiAccountPage.QUALIFICATION -> QumiQualificationScreen(onBack = { accountPage = QumiAccountPage.SETTINGS })
                    QumiAccountPage.HELP -> QumiHelpScreen(
                        onBack = { accountPage = QumiAccountPage.MINE },
                        onSupport = { if (supportContact != null) openSupportChat() else notify("客服暂不可用，请稍后重试") },
                    )
                    QumiAccountPage.ABOUT -> QumiAboutScreen(onBack = { accountPage = QumiAccountPage.SETTINGS })
                    QumiAccountPage.SETTINGS -> QumiSettingsScreen(
                        token = session.token,
                        onBack = { accountPage = QumiAccountPage.MINE },
                        onBlacklist = { accountPage = QumiAccountPage.BLACKLIST },
                        onChangePassword = { showPasswordChange = true },
                        onLogout = onLogout,
                    )
                    QumiAccountPage.NOTIFICATIONS -> QumiNotificationSettingsScreen(
                        token = session.token,
                        onBack = { accountPage = QumiAccountPage.MINE },
                    )
                    QumiAccountPage.REFERRAL -> QumiReferralPage(
                        token = session.token,
                        api = api,
                        onBack = { accountPage = QumiAccountPage.MINE },
                        onRedeem = { showReferral = true },
                    )
                    QumiAccountPage.POSTS -> QumiMyPostsScreen(
                        token = session.token,
                        onBack = { accountPage = QumiAccountPage.MINE },
                    )
                    QumiAccountPage.LIKES, QumiAccountPage.FAVORITES -> QumiMySavedScreen(
                        token = session.token,
                        likes = accountPage == QumiAccountPage.LIKES,
                        onBack = { accountPage = QumiAccountPage.MINE },
                        onOpenUser = { id, name -> openPublicProfile(id, name) },
                    )
                    QumiAccountPage.COSTUME -> QumiCostumeShopScreen(
                        onBack = { accountPage = QumiAccountPage.MINE },
                    )
                    QumiAccountPage.YOUTH -> QumiYouthModeScreen(
                        accountId = session.id,
                        onBack = { accountPage = QumiAccountPage.SETTINGS },
                    )
                    QumiAccountPage.LEVEL -> QumiLevelScreen(
                        token = session.token,
                        level = level,
                        onBack = { accountPage = QumiAccountPage.MINE },
                        onRefresh = { refreshKey++ },
                    )
                    QumiAccountPage.TASKS -> QumiTaskCenterScreen(
                        token = session.token,
                        onBack = { accountPage = QumiAccountPage.MINE },
                        onOpenMoments = { accountPage = QumiAccountPage.MINE; selectedTab = 1 },
                    )
                    QumiAccountPage.GUARD -> QumiGuardScreen(
                        token = session.token,
                        onBack = { accountPage = QumiAccountPage.MINE },
                    )
                    QumiAccountPage.HOST -> QumiCreatorGatewayScreen(
                        session = session,
                        onBack = { accountPage = QumiAccountPage.MINE },
                        onApply = { accountPage = QumiAccountPage.IDENTITY },
                    )
                    QumiAccountPage.IDENTITY -> QumiCreatorVerificationScreen(
                        session = session,
                        onBack = { accountPage = QumiAccountPage.MINE },
                        onHostBackend = { accountPage = QumiAccountPage.HOST },
                    )
                    QumiAccountPage.CONTACTS -> QumiContactsScreen(
                        token = session.token,
                        initialTab = contactTab,
                        onOpenLive = ::openContactLive,
                        onOpenUser = { id, name -> openPublicProfile(id, name) },
                        onMessageUser = { id, name -> openDirectMessage(id, name) },
                        onBack = { accountPage = QumiAccountPage.MINE },
                        onSearch = { tab ->
                            contactTab = tab
                            searchReturnTab = selectedTab; searchReturnPage = QumiAccountPage.CONTACTS; accountPage = QumiAccountPage.SEARCH
                        },
                    )
                    QumiAccountPage.VISITORS -> QumiVisitorsScreen(
                        token = session.token,
                        onBack = { accountPage = QumiAccountPage.MINE },
                        onOpenUser = { id, name -> openPublicProfile(id, name) },
                    )
                    QumiAccountPage.EVENTS -> QumiAnnouncementListScreen(
                        onBack = { accountPage = QumiAccountPage.MINE },
                        onOpenDetail = { index, title -> announcementIndex = index; announcementTitle = title; accountPage = QumiAccountPage.ANNOUNCEMENT_DETAIL },
                    )
                    QumiAccountPage.ANNOUNCEMENT_DETAIL -> QumiAnnouncementDetailScreen(
                        title = announcementTitle,
                        index = announcementIndex,
                        onBack = { accountPage = QumiAccountPage.EVENTS },
                    )
                    QumiAccountPage.INTERACTIONS, QumiAccountPage.SECRETARY_CHAT,
                    QumiAccountPage.SECRETARY_SETTINGS, QumiAccountPage.SECRETARY_PROFILE, QumiAccountPage.DEMO_CHAT -> QumiMineScreen(
                        session = session, wallet = wallet, level = level, profile = profile,
                        onProfile = { accountPage = QumiAccountPage.PROFILE },
                        onWallet = { accountPage = QumiAccountPage.WALLET },
                        onBalanceDetails = { accountPage = QumiAccountPage.BALANCE },
                        onSettings = { accountPage = QumiAccountPage.SETTINGS }, onBusiness = { accountPage = QumiAccountPage.HOST },
                        onReferral = { accountPage = QumiAccountPage.REFERRAL },
                        onLevel = { accountPage = QumiAccountPage.LEVEL },
                        onMyPosts = { accountPage = QumiAccountPage.POSTS },
                        onMyLikes = { accountPage = QumiAccountPage.LIKES },
                        onMyFavorites = { accountPage = QumiAccountPage.FAVORITES },
                        onIdentity = { accountPage = QumiAccountPage.IDENTITY },
                        onContacts = { accountPage = QumiAccountPage.CONTACTS },
                        onNoble = { accountPage = QumiAccountPage.NOBLE },
                    )
                }
                else -> if (canManageOperations) OperationsScreen(
                    summary = operationsSummary, users = operationsUsers, page = operationsPage,
                    loading = operationsLoading, error = operationsError,
                    onRefresh = { operationsRefresh++ }, onPage = { operationsPage = it },
                    onModeration = { showModeration = true },
                )
            }
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
            offeredPrice = call.offeredPrice,
            gifts = gifts, balance = wallet?.balance, giftBusy = openImBusy,
            celebrationGift = dmCelebration, celebrationNonce = dmCelebrationNonce,
            onCelebrationFinished = { dmCelebration = null },
            onGift = { peerId, peerName, gift ->
                openDirectMessage(peerId, peerName ?: "用户")
                sendDmGift(gift.id)
            },
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
    DemoCallOverlay(demoCallSession, session.username, session.avatar)
    if (showDmCallPrice) DmCallPriceSheet(session, api, onDismiss = { showDmCallPrice = false })
    incomingCall?.takeIf { activeCall == null }?.let { invite ->
        DmIncomingCallPrompt(invite.optString("caller_name", "用户"), invite.optString("caller_avatar"), invite.optString("mode"), incomingCallBusy,
            onAccept = {
                val caller = invite.optString("caller_id")
                incomingCall = null
                beginCall(caller, invite.optString("caller_name"), invite.optString("mode"), invite.optString("call_id"))
            }, onReject = {
                if (!incomingCallBusy) scope.launch {
                    incomingCallBusy = true
                    try {
                        val rejectedId = invite.optString("call_id")
                        api.endDmCall(session.token, rejectedId, "rejected")
                        incomingCall = null
                        runCatching { cacheDmCallReceipt(context, session.id.toString(), rejectedId, invite.optString("mode"),
                            api.dmCallStatus(session.token, rejectedId)) }
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (_: Exception) { notify("拒接未成功，请重试")
                    } finally { incomingCallBusy = false }
                }
            })
    }
    if (showGiftRecharge) GiftRechargeDialog(onDismiss = { showGiftRecharge = false }, onRecharge = {
        showGiftRecharge = false; showGiftRechargeQr = true
    })
    if (showGiftRechargeQr) DisplayQrDialog(DisplayQrKind.SUPPORT, onDismiss = { showGiftRechargeQr = false })
    if (showPasswordChange) PasswordSheet(session.token, onDismiss = { showPasswordChange = false }, onChanged = onLogout)
    blockTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!blockBusy) blockTarget = null },
            title = { Text("屏蔽 ${target.username}？") },
            text = { Text("该用户会加入黑名单。你可以之后在设置中解除屏蔽。") },
            confirmButton = { TextButton(enabled = !blockBusy, onClick = {
                blockBusy = true
                scope.launch {
                    runCatching { api.setBlocked(session.token, target.id, true) }
                        .onSuccess {
                            blockTarget = null
                            notify("已屏蔽 ${target.username}")
                            if (accountPage == QumiAccountPage.PUBLIC_PROFILE) {
                                selectedTab = profileReturnTab
                                accountPage = profileReturnPage
                            }
                        }
                        .onFailure { notify(it.message ?: "屏蔽失败，请重试") }
                    blockBusy = false
                }
            }) { Text(if (blockBusy) "处理中" else "屏蔽") } },
            dismissButton = { TextButton(enabled = !blockBusy, onClick = { blockTarget = null }) { Text("取消") } },
        )
    }
    if (showCoverUpload) ProfileCoverUploadSheet(session,api,onDismiss={showCoverUpload=false;refreshKey++})
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
            LiveAppTheme(colorScheme = darkColorScheme(primary = LiveStyle.accent, onPrimary = Color.White, primaryContainer = Color(0xFF3D2869), onPrimaryContainer = Color.White, surface = Color(0xFF202126), background = Color.Black)) {
            RoomSheet(
                session = session,
                api = api,
                room = room,
                gateway = roomGateway,
                playbackUrl = if (mediaRoute == LiveMediaRoute.VIEWER_HLS) {
                    srsPlaybackUrl(BuildConfig.SRS_PLAYBACK_URL, room.stream)
                } else null,
                playbackToken = session.token,
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
                remainingSeconds = matchRemainingSeconds,
                onHostProfile = { hostProfile = room },
                onMessageParticipant = { id, name -> leaveRoom(); openDirectMessage(id, name) },
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
                            selectedTab = 3
                            accountPage = QumiAccountPage.MINE
                            openImMessagePage = true
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
                onOpenVideo = {
                    switchToVideoAfterRoom = true
                    selectedTab = 0
                    discoverySection = 0
                    openVideoFromLiveSignal++
                },
                onSwitchRoom = { direction -> switchRoom(direction) },
                invitations = matchInvitations.filter { it.opponentLiveSessionId == room.stream.toLongOrNull() },
                onRespondInvitation = respondInvitation,
                effectSession = effectSession,
                celebrationGift = giftCelebration?.takeIf { it.first == room.stream }?.second,
                celebrationNonce = giftCelebrationNonce,
                onCelebrationFinished = { giftCelebration = null },
                onEndMatch = {
                    val matchId = activeMatchId
                    if (matchId != null && !matchLoading) scope.launch {
                        matchLoading = true
                        try {
                            api.closeMatch(session.token, matchId)
                            activeMatchId = null
                            matchScores = emptyList()
                            matchRemainingSeconds = null
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
                        else if (wallet?.balance?.let { it < gift.coinPrice } == true) showGiftRecharge = true
                        else submitGift(PendingGift.create(room.creatorId, id, gift, activeMatchId, room.host))
                    }
                },
                onInviteMatch = { opponentRoom, durationMinutes ->
                    scope.launch {
                        val own = room.stream.toLongOrNull()
                        val opponent = opponentRoom.stream.toLongOrNull()
                        if (own == null || opponent == null || opponent <= 0) { notify("该主播暂时无法接受邀请，请刷新后重试"); return@launch }
                        matchLoading = true
                        runCatching { api.inviteMatch(session.token, own, opponent, durationMinutes) }
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
    previewRoom?.let { preview ->
        Dialog(onDismissRequest = { previewRoom = null }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            val previewRooms = remember(rooms, preview.stream) {
                if (rooms.any { it.stream == preview.stream }) rooms else listOf(preview) + rooms
            }
            val previewPager = rememberPagerState(
                initialPage = previewRooms.indexOfFirst { it.stream == preview.stream }.coerceAtLeast(0),
                pageCount = { previewRooms.size },
            )
            VerticalPager(previewPager, Modifier.fillMaxSize(), key = { previewRooms[it].stream }) { index ->
                val room = previewRooms[index]
                LiveRoomPreview(room, onBack = { previewRoom = null }, onVideo = {
                    selectedTab = 0; discoverySection = 0; openVideoFromLiveSignal++
                }, onEnter = { activeRoom = room; previewRoom = null })
            }
        }
    }
    sampleRoomIndex?.let { index ->
        QumiSampleLiveViewer(index, session.id, session.token, initialPreview = sampleLivePreview,
            senderName = session.username, senderAvatar = session.avatar,
            onBack = { sampleRoomIndex = null },
            onMessage = { id, name -> sampleRoomIndex = null; openDirectMessage(id, name) },
            onOpenVideo = {
                selectedTab = 0
                discoverySection = 0
                openVideoFromLiveSignal++
            },
        )
    }
    finishedLiveRoom?.let { finished -> Dialog(onDismissRequest = { finishedLiveRoom = null },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        QumiLiveEndedScreen(
            username = finished.room.host,
            coverPath = finished.room.coverPath,
            roomType = finished.room.roomType,
            startedAtMillis = finished.startedAtMillis,
            session = finished.session,
            durationSeconds = finished.localDurationSeconds,
            loading = finished.loading,
            onClose = { finishedLiveRoom = null },
            onMore = { finishedLiveRoom = null; selectedTab = 4; accountPage = QumiAccountPage.HOST },
        )
    } }
    if (showReferral) ReferralSheet(
        token = session.token,
        api = api,
        pendingRequestId = referralRequestId,
        onPendingRequestId = { referralRequestId = it },
        onInviteeBalance = { wallet = Wallet(it) },
        onDismiss = { showReferral = false },
    )
    customerServiceNotice?.let { message ->
        CustomerServiceUnavailableSheet(message, onDismiss = { customerServiceNotice = null })
    }
    hostProfile?.let { room ->
        HostIntroductionPage(
            name = room.host,
            creatorId = room.creatorId,
            coverPath = room.coverPath,
            roomTitle = room.title,
            levelLabel = hostLevelLabel(room.creatorId, session.id, level),
            onDismiss = { hostProfile = null },
            onEnterRoom = if (activeRoom?.stream == room.stream) null else {
                {
                    hostProfile = null
                    activeRoom = room
                }
            },
        )
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
        IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, contentDescription = "刷新内容") }
    }
}

@Composable
private fun QumiDiscoveryHeader(
    section: Int,
    onSection: (Int) -> Unit,
    showPublishVideo: Boolean,
    onPublishVideo: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("视频", "直播").forEachIndexed { index, title ->
                Column(
                    Modifier.clickable { onSection(index) }.padding(end = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(title, fontSize = if (section == index) 21.sp else 17.sp,
                        fontWeight = if (section == index) FontWeight.Bold else FontWeight.Medium,
                        color = if (section == index) Ink else Fog)
                    Spacer(Modifier.height(7.dp))
                    Spacer(Modifier.width(26.dp).height(3.dp).clip(CircleShape)
                        .background(if (section == index) Color(0xFF7044D8) else Color.Transparent))
                }
            }
            Spacer(Modifier.weight(1f))
            if (section == 0 && showPublishVideo) Button(
                onClick = onPublishVideo,
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 11.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF7044D8)),
            ) {
                Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("发布视频", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DiscoveryLiveScreen(
    rooms: List<LiveRoom>,
    loading: Boolean,
    error: String?,
    onJoin: (LiveRoom) -> Unit,
    onHost: (LiveRoom) -> Unit,
    onSample: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (loading) item(span = { GridItemSpan(maxLineSpan) }) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFF7044D8))
        }
        if (error != null) item(span = { GridItemSpan(maxLineSpan) }) {
            TextButton(onClick = onRefresh) { Text("直播列表暂时无法更新，点击重试") }
        }
        if (!loading && rooms.isEmpty()) items(LivePlaceholderCovers.size) { index -> SampleRoomTile(index) { onSample(index) } }
        items(rooms, key = { it.stream }) { room ->
            RoomTile(room, onClick = { onJoin(room) }, onHost = { onHost(room) })
        }
    }
}

@Composable
private fun LiveStartPill(onStart: () -> Unit, starting: Boolean) {
    Row(Modifier.height(30.dp).clip(CircleShape)
        .background(Brush.horizontalGradient(listOf(Color(0xFF9CEEFF), Color(0xFF9DF0C9))))
        .clickable(enabled = !starting, onClick = onStart)
        .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (starting) CircularProgressIndicator(Modifier.size(17.dp), color = Color(0xFF157A86), strokeWidth = 2.dp)
        else Icon(Icons.Rounded.Videocam, contentDescription = null, tint = Color(0xFF0DABD1), modifier = Modifier.size(19.dp))
        Text(if (starting) "检查中" else "开播", color = Color(0xFF1A2A31), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
    onHost: (LiveRoom) -> Unit,
    onSample: (Int) -> Unit,
    showStartButton: Boolean,
    onRefresh: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color(0xFFDEDAFF),
            0.22f to Color(0xFFEBE9FF),
            0.42f to NightInk,
            1f to NightInk,
        )),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("正在直播", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Rounded.Refresh, contentDescription = "刷新直播") }
                if (showStartButton) LiveStartPill(onStart, starting)
            }
        }
        if (loading) item(span = { GridItemSpan(maxLineSpan) }) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Coral, trackColor = NightSurfaceRaised)
        }
        if (error != null) item(span = { GridItemSpan(maxLineSpan) }) {
            if (rooms.isEmpty()) {
                Text("直播服务暂时无法连接，可点右上角刷新", Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = Fog)
            } else {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("直播列表未更新", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = Fog)
                }
            }
        }
        if (!loading && rooms.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("发现主播", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("暂无可进入的直播间", style = MaterialTheme.typography.bodySmall, color = Fog)
                }
            }
            items(LivePlaceholderCovers.size) { index -> SampleRoomTile(index) { onSample(index) } }
        }
        items(rooms, key = { it.stream }) { room -> RoomTile(room, onClick = { onJoin(room) }, onHost = { onHost(room) }) }
    }
}

@Composable
private fun RoomTile(room: LiveRoom, onClick: () -> Unit, onHost: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(0.78f).clip(RoundedCornerShape(6.dp))
        .background(Color(0xFF424B59)).clickable(onClick = onClick)) {
        Image(painterResource(LivePlaceholderCovers[Math.floorMod(room.stream.hashCode(), LivePlaceholderCovers.size)]),
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        room.coverPath?.let { LiveCoverImage(it) }
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.42f).align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))))
        Row(Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(5.dp))
            .background(Coral).padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
            Text("直播中", style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
        IconButton(onClick = onHost, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(42.dp)
            .clip(CircleShape).background(Color.White)) {
            Box(Modifier.fillMaxSize().padding(2.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                Image(painterResource(LivePlaceholderCovers[Math.floorMod(room.stream.hashCode(), LivePlaceholderCovers.size)]),
                    contentDescription = "查看${room.host}的介绍", contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
                room.coverPath?.let { LiveCoverImage(it) }
            }
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(room.host, color = Color.White, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(room.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
}

@Composable
private fun SampleRoomTile(index: Int, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(0.78f).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick)) {
        Image(painterResource(DemoLivePosters[index]), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.42f).align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))))
        Text("示例", Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 4.dp),
            color = Color.White, style = MaterialTheme.typography.labelSmall)
        Text(SampleRoomTitles[index], Modifier.align(Alignment.BottomStart).padding(12.dp),
            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
    session: UserSession,
    api: LivePlatformApi,
    room: LiveRoom,
    gateway: LiveKitRoomGateway?,
    playbackUrl: String?,
    playbackToken: String,
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
    onHostProfile: () -> Unit,
    onMessageParticipant: (String, String) -> Unit,
    isHost: Boolean,
    matchId: Long?,
    scores: List<MatchScore>,
    remainingSeconds: Int?,
    onMessageHost: () -> Unit,
    onCohost: () -> Unit,
    onRoomChat: () -> Unit,
    onReport: () -> Unit,
    matchLoading: Boolean,
    opponents: List<LiveRoom>,
    onClose: () -> Unit,
    onOpenVideo: () -> Unit,
    onSwitchRoom: (Int) -> Unit,
    invitations: List<MatchInvitation>,
    onRespondInvitation: (MatchInvitation, Boolean) -> Unit,
    onEndMatch: () -> Unit,
    effectSession: GiftEffectSession,
    celebrationGift: GiftSendPresentation?,
    celebrationNonce: Int,
    onCelebrationFinished: () -> Unit,
    onGift: (Gift) -> Unit,
    onInviteMatch: (LiveRoom, Int) -> Unit,
) {
    val roomScope = rememberCoroutineScope()
    var participantProfile by remember(room.stream) { mutableStateOf<Long?>(null) }
    val participantNames = remember(room.stream) { mutableStateMapOf<Long, String>() }
    var hostUser by remember(room.stream) { mutableStateOf<PublicUser?>(null) }
    var followBusy by remember(room.stream) { mutableStateOf(false) }
    var followNotice by remember(room.stream) { mutableStateOf<String?>(null) }
    var showLiveRank by remember(room.stream) { mutableStateOf(false) }
    var showLiveViewers by remember(room.stream) { mutableStateOf(false) }
    var showAnnouncement by remember(room.stream) { mutableStateOf(false) }
    val roomLeaders by rememberRoomLeaders(session.token, room.stream)
    LaunchedEffect(room.stream, participantProfile) {
        runCatching { api.searchUsers(session.token, room.creatorId.toString()).firstOrNull { it.id == room.creatorId } }
            .onSuccess { hostUser = it }
    }
    var panel by remember(room.stream) { mutableStateOf<String?>(null) }
    var selectedGiftId by remember(room.stream) { mutableStateOf<String?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    var confirmEndMatch by remember { mutableStateOf(false) }
    var selectedMatchMinutes by remember(room.stream) { mutableIntStateOf(5) }
    val selectedGift = gifts.firstOrNull { it.id == selectedGiftId }
    val effectFailure by effectSession.failure.collectAsState()
    val activity by rememberLiveRoomActivity(session, api, room.stream, isHost)
    val requestClose = { if (isHost) confirmClose = true else onClose() }
    val gate = watchGate
    val watchBlocked = gate != null && !gate.phase.allowsPlayback()
    androidx.activity.compose.BackHandler { requestClose() }
    Box(Modifier.fillMaxSize().background(roomBackgroundBrush(room.backgroundId))
        .then(if (isHost || panel != null) Modifier else Modifier.pointerInput(room.stream) {
            var dragDistance = 0f
            val threshold = 80.dp.toPx()
            detectVerticalDragGestures(
                onDragStart = { dragDistance = 0f },
                onDragCancel = { dragDistance = 0f },
                onDragEnd = {
                    if (kotlin.math.abs(dragDistance) >= threshold) onSwitchRoom(if (dragDistance < 0) 1 else -1)
                    dragDistance = 0f
                },
                onVerticalDrag = { change, amount -> change.consume(); dragDistance += amount },
            )
        })) {
        if (playbackUrl != null && !watchBlocked) {
            SrsPlaybackView(playbackUrl, playbackToken, onPlaybackStatus, Modifier.fillMaxSize())
        } else if (ingest != null && isHost && matchId == null) {
            SrsHostStage(ingest, gateway, room)
        } else if (gateway != null && !watchBlocked && room.roomType != "VOICE") {
            val videoIds by gateway.videoIdentities.collectAsState()
            val opponentId = scores.firstOrNull { it.creatorId != room.creatorId }?.creatorId
            val visibleIds = if (matchId != null) listOfNotNull(room.creatorId, opponentId)
                else (listOf(room.creatorId) + videoIds.mapNotNull { it.toLongOrNull() }).distinct()
            LiveMosaic(visibleIds, identity = { it }, onProfile = { participantProfile = it },
                modifier = if (visibleIds.size > 1) Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 155.dp).fillMaxWidth().fillMaxHeight(.48f)
                    else Modifier.fillMaxSize()) { participantId ->
                androidx.compose.runtime.key(gateway, participantId, matchId) {
                    AndroidView(factory = { context -> gateway.createVideoRenderer(context, participantId.toString()) },
                        update = { gateway.attachVideoRenderer(it) },
                        onRelease = { gateway.releaseVideoRenderer(it); it.release() }, modifier = Modifier.fillMaxSize())
                }
                Text(participantNames[participantId] ?: if (participantId == room.creatorId) room.host else "连麦主播",
                    Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = .3f)).padding(7.dp, 4.dp), color = Color.White, fontSize = 12.sp)
            }
            LaunchedEffect(videoIds) {
                visibleIds.forEach { id -> if (!participantNames.containsKey(id)) {
                    runCatching { api.searchUsers(session.token, id.toString()).firstOrNull { it.id == id } }
                        .getOrNull()?.let { participantNames[id] = it.username }
                } }
            }
        }
        if (room.roomType == "VOICE" && !watchBlocked) Column(
            Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(98.dp).clip(CircleShape).background(Color.White.copy(alpha = .16f)),
                contentAlignment = Alignment.Center) {
                Image(painterResource(LivePlaceholderCovers[Math.floorMod(room.stream.hashCode(), LivePlaceholderCovers.size)]),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(4.dp).clip(CircleShape))
                room.coverPath?.let { LiveCoverImage(it) }
            }
            Text("语音房 · ${room.host}", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        GiftEffectOverlay(effectSession, Modifier.fillMaxSize())
        if (gate != null && watchBlocked) WatchBlockedOverlay(gate, wallet, onUnlock, onRetryWatch)
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            LiveRoomHeader(
                host = LiveHeaderPerson(room.host, hostUser?.avatar), subtitle = room.title,
                followed = hostUser?.isFollowing == true, followBusy = followBusy || hostUser == null,
                showFollow = !isHost, viewers = activity.viewers?.toString() ?: "…", leaders = roomLeaders,
                onProfile = { participantProfile = room.creatorId }, onFollow = {
                    val user = hostUser
                    if (user != null && !followBusy) roomScope.launch {
                        followBusy = true
                        try { api.setFollowing(session.token, room.creatorId, !user.isFollowing)
                            hostUser = user.copy(isFollowing = !user.isFollowing)
                            followNotice = if (user.isFollowing) "已取消关注" else "已关注"
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { followNotice = "关注失败，请重试" }
                        finally { followBusy = false }
                    }
                }, onRank = { showLiveRank = true }, onViewers = { showLiveViewers = true }, onClose = requestClose,
                onAnnouncement = { showAnnouncement = true })
            if (isHost) Text("本场礼物 " + (activity.giftCoins?.toString() ?: "…") + " 金币", Modifier.padding(top = 6.dp),
                color = Color(0xFFFFDEA2), fontSize = 12.sp)
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
                val ownPoints = scores.firstOrNull { it.creatorId == room.creatorId }?.points ?: 0L
                val rivalPoints = scores.firstOrNull { it.creatorId != room.creatorId }?.points ?: 0L
                val totalPoints = ownPoints + rivalPoints
                val targetShare = if (totalPoints == 0L) .5f else
                    (ownPoints.toFloat() / totalPoints.toFloat()).coerceIn(.08f, .92f)
                val redShare by animateFloatAsState(targetShare, tween(650), label = "pkScoreShare")
                Column(Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("PK  ${remainingSeconds?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "--:--"}",
                        Modifier.align(Alignment.CenterHorizontally), color = Color.White,
                        fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.fillMaxWidth().height(24.dp).clip(CircleShape)
                        .background(Color(0xFF258BEF))) {
                        Box(Modifier.fillMaxWidth(redShare).fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(Color(0xFFE92C6C), Color(0xFFFF4C8B)))))
                        Row(Modifier.fillMaxSize().padding(horizontal = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("$ownPoints", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("$rivalPoints", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
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
            LiveRoomComments(activity, room.welcomeText, room.announcementText, room.stream)
            Text(status, color = Color(0xFFDDDEE2), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).clip(CircleShape).background(Color.White.copy(alpha = .15f))
                    .clickable(onClick = onRoomChat).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text("说点什么…", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                }
                TextButton(onClick = onCohost, enabled = matchId == null, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text(if (isHost) "连麦管理" else "连麦", color = Color(0xFFE5D4FF), fontSize = 13.sp)
                }
                Button(onClick = { panel = if (isHost) "pk" else "gifts" }, enabled = !giftLoading && !matchLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7951ED)), contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Text(if (isHost && matchId != null) "PK 管理" else if (isHost && invitations.isNotEmpty()) "PK 邀请（${invitations.size}）" else if (isHost) "邀请 PK" else "送礼物")
                }
            }
        }
        celebrationGift?.let { event ->
            QumiGiftCelebration(event.gift, celebrationNonce, demo = false,
                senderName = event.senderName, senderAvatar = event.senderAvatar, quantity = event.quantity,
                onFinished = onCelebrationFinished)
        }
    }
    participantProfile?.let { id -> DirectChatProfile(session.token, id, session.id,
        participantNames[id] ?: if (id == room.creatorId) room.host else "主播", onBack = { participantProfile = null },
        onMessage = onMessageParticipant) }
    if (showAnnouncement) LiveRoomAnnouncementDialog(room.host,
        room.announcementText.ifBlank { room.welcomeText }, onDismiss = { showAnnouncement = false })
    if (showLiveRank) LiveRoomRankDialog(session.token, room.stream, onDismiss = { showLiveRank = false })
    if (showLiveViewers) LiveRoomViewersDialog(session.token, room.stream, session.id, onDismiss = { showLiveViewers = false })
    QumiActionNotice(followNotice) { followNotice = null }
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
    if (panel != null) ModalBottomSheet(onDismissRequest = { if (!giftLoading) panel = null },
        containerColor = if (panel == "gifts") Color(0xFF171820) else LiveStyle.sheetSurface) {
        if (panel == "gifts") {
            pendingGift?.let { PendingGiftNotice(it, giftLoading, onRetryGift) }
            QumiGiftPicker(gifts, selectedGiftId, wallet?.balance, giftLoading || pendingGift != null,
                demo = false, onSelect = { selectedGiftId = it },
                onSend = { selectedGift?.let { panel = null; onGift(it) } },
                onDismiss = { panel = null })
        } else Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("邀请主播 PK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (matchId == null) "选择时长和正在直播的主播" else "当前已有 PK，请先完成本场比赛。")
                if (matchId == null) Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(2, 3, 5, 10).forEach { minutes ->
                        FilterChip(selected = selectedMatchMinutes == minutes,
                            onClick = { selectedMatchMinutes = minutes },
                            label = { Text("${minutes}分钟") })
                    }
                }
                if (matchId != null) Button(onClick = { panel = null; confirmEndMatch = true }, enabled = !matchLoading, modifier = Modifier.fillMaxWidth()) { Text("结束 PK，继续直播") }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(invitations, key = { "invite-${it.invitationId}" }) { invitation ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text("${opponents.firstOrNull { it.creatorId == invitation.creatorId }?.host ?: "一位主播"} 邀请你 PK · ${invitation.durationMinutes}分钟", fontWeight = FontWeight.SemiBold)
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
                            TextButton(onClick = { onInviteMatch(opponent, selectedMatchMinutes); panel = null }, enabled = matchId == null && !matchLoading) { Text("邀请") }
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
                FilterChip(period == "weekly", { onPeriod("weekly") }, label = { Text("周榜") })
                FilterChip(period == "monthly", { onPeriod("monthly") }, label = { Text("月榜") })
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Coral, trackColor = NightSurfaceRaised) }
        error?.let { item { Text("榜单读取失败 · $it", color = Coral, style = MaterialTheme.typography.bodySmall) } }
        if (!loading && error == null && entries.isEmpty()) item { Text("当前周期暂无榜单数据", color = Fog) }
        items(entries.indices.toList()) { index ->
            val entry = entries[index]
            val periodName = when (period) { "daily" -> "今日"; "weekly" -> "本周"; else -> "本月" }
            val rankTitle = when (index) { 0 -> "${periodName}榜首"; 1 -> "${periodName}榜眼"; 2 -> "${periodName}探花"; else -> null }
            Row(Modifier.fillMaxWidth().height(66.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", Modifier.width(32.dp), color = if (index < 3) Amber else Fog, fontWeight = FontWeight.Bold)
                Avatar("${entry.userId}", index)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("用户 #${entry.userId}", fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(rankTitle?.let { "专属头衔：$it" }, "${entry.gifts} 件礼物").joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = if (rankTitle == null) Fog else Amber,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    onExit: () -> Unit,
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
    onlineUserIds: Set<String>,
    message: String,
    busy: Boolean,
    ready: Boolean,
    onRecipientId: (String) -> Unit,
    onMessage: (String) -> Unit,
    onRefresh: () -> Unit,
    onConnect: () -> Unit,
    onVideoCall: () -> Unit,
    onPaidVideoCall: (Int) -> Unit,
    onVoiceCall: () -> Unit,
    onJoinCall: (String) -> Unit,
    onAcceptCall: (DmCallInvite) -> Unit,
    onCallPriceSettings: () -> Unit,
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
    onOpenVisitors: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenNotifications: () -> Unit,
    onSendGift: (Gift) -> Unit,
    onRetryGift: () -> Unit,
    onSendVideo: (String, Int, ByteArray, Int) -> Unit,
    onSendImage: (Uri) -> Unit,
    token: String,
    onCreatorVerification: () -> Unit,
    onUnlock: (String) -> Unit,
    onPlay: (String) -> Unit,
    onVerifyGift: (ChatBody.Gift) -> Unit,
    ownAvatar: String? = null,
    ownName: String,
    celebrationGift: Gift?, celebrationNonce: Int, onCelebrationFinished: () -> Unit,
) {
    var composeOpen by remember { mutableStateOf(false) }
    var enteringAccount by remember { mutableStateOf(false) }
    var giftSheet by remember { mutableStateOf(false) }
    var videoSheet by remember { mutableStateOf(false) }
    var videoPaid by remember { mutableStateOf(false) }
    var mediaTray by remember { mutableStateOf<String?>(null) }
    var creatorNotice by remember { mutableStateOf<String?>(null) }
    var creatorNeedsVerification by remember { mutableStateOf(false) }
    var checkingCreator by remember { mutableStateOf(false) }
    var manualAccountId by remember { mutableStateOf("") }
    var accountLookupBusy by remember { mutableStateOf(false) }
    var accountLookupError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(onSendImage)
    }
    creatorNotice?.let { notice -> AlertDialog(
        onDismissRequest = { creatorNotice = null }, title = { Text("视频发送") }, text = { Text(notice) },
        confirmButton = { TextButton(onClick = {
            creatorNotice = null
            if (creatorNeedsVerification) onCreatorVerification()
        }) { Text(if (creatorNeedsVerification) "去认证为主播" else "知道了") } },
        dismissButton = if (creatorNeedsVerification) ({ TextButton(onClick = { creatorNotice = null }) { Text("稍后") } }) else null,
    ) }
    LaunchedEffect(composeOpen) { onChatVisibility(composeOpen) }
    LaunchedEffect(recipientId) { giftSheet = false; videoSheet = false; mediaTray = null }
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
                IconButton(onClick = onExit) { Icon(Icons.Rounded.Close, contentDescription = "返回") }
                Text("消息", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onRecipientId(""); manualAccountId = ""; accountLookupError = null; enteringAccount = true; composeOpen = true }, enabled = ready && !busy) { Text("新消息") }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = Fog)
            if (!ready) TextButton(onClick = onConnect, enabled = !busy) { Text(if (busy) "正在连接…" else "重新连接") }
            else TextButton(onClick = onRefresh, enabled = !busy) { Text("刷新会话") }
        }
        if (recipientId.isBlank() && !composeOpen) {
            item {
                Row(Modifier.fillMaxWidth().clickable(onClick = onOpenNotifications).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Notifications, contentDescription = null, tint = Coral)
                    Text("通知偏好设置", Modifier.weight(1f).padding(start = 10.dp), color = Color(0xFF333333), style = MaterialTheme.typography.bodyMedium)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "打开通知设置", tint = Fog, modifier = Modifier.size(18.dp))
                }
                HorizontalDivider(color = NightLine)
            }
            item {
                Row(Modifier.fillMaxWidth().clickable(onClick = onOpenAnnouncements).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Campaign, contentDescription = null, tint = Coral, modifier = Modifier.size(34.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("平台公告", fontWeight = FontWeight.SemiBold)
                        Text("2 条平台公告", style = MaterialTheme.typography.bodyMedium, color = Fog)
                    }
                }
                HorizontalDivider(color = NightLine)
            }
            item {
                Row(Modifier.fillMaxWidth().clickable(onClick = onOpenVisitors).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Visibility, contentDescription = null, tint = Mint, modifier = Modifier.size(34.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("最近访客", fontWeight = FontWeight.SemiBold)
                        Text("查看最近访问你主页的人", style = MaterialTheme.typography.bodyMedium, color = Fog)
                    }
                }
                HorizontalDivider(color = NightLine)
            }
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
                    Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, tint = Fog, modifier = Modifier.size(36.dp))
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
                ChatOnlineAvatar(conversation.name, conversation.avatar, conversation.userId in onlineUserIds)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(conversation.name, modifier = Modifier.weight(1f, fill = false), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (conversation.userId in onlineUserIds) ChatOnlineLabel()
                    }
                    Text(conversation.latestMessage, style = MaterialTheme.typography.bodyMedium, color = Fog, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "发消息", tint = Fog, modifier = Modifier.size(18.dp))
            }
            HorizontalDivider(color = NightLine)
        }
    }
    if (composeOpen && recipientId.isNotBlank()) DirectConversationScreen(
        token, ownUserId, recipientId, recipientName ?: recipient?.name ?: "用户", recipient?.avatar, recipientId in onlineUserIds,
        history, historyLoading, historyError, ready, busy, message, gifts, balance, playable,
        pendingGift != null, pendingVideoPhase,
        onBack = { composeOpen = false; onExit() }, onDraft = onMessage, onSend = onSend,
        onVoice = onVoiceCall, onVideo = onVideoCall, onPaidVideo = onPaidVideoCall, onPrices = onCallPriceSettings,
        onImage = onSendImage, onSendVideo = onSendVideo, onGift = onSendGift, onRetryGift = onRetryGift,
        onVerifyGift = onVerifyGift, onUnlock = onUnlock, onPlay = onPlay, onOlder = onOlderMessages,
        onRetry = onHistoryRetry, onConnect = onConnect, onVerifyCreator = onCreatorVerification,
        ownAvatar = ownAvatar,
        ownName = ownName, celebrationGift = celebrationGift, celebrationNonce = celebrationNonce,
        onCelebrationFinished = onCelebrationFinished,
    )
    if (composeOpen && recipientId.isBlank()) ModalBottomSheet(onDismissRequest = { composeOpen = false }) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (recipientId.isNotBlank()) ChatOnlineAvatar(recipientName ?: recipient?.name ?: "用户", recipient?.avatar, recipientId in onlineUserIds)
                Text((recipientName ?: recipient?.name)?.let { "发给 $it" } ?: "新消息", modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (recipientId in onlineUserIds) ChatOnlineLabel()
            }
            if (recipientId != ownUserId) DmCallActions(
                canStart = recipientId.isNotBlank() && recipientId.toLongOrNull() != null,
                showLaunchButtons = false,
                incoming = history?.let { latestIncomingDmCall(it.messages, ownUserId) },
                onVideo = onVideoCall,
                onVoice = onVoiceCall,
                onJoin = onJoinCall,
                onAccept = onAcceptCall,
                onPriceSettings = onCallPriceSettings,
            )
            if (enteringAccount) {
                OutlinedTextField(
                    value = manualAccountId, onValueChange = { manualAccountId = it.filter(Char::isDigit).take(6); accountLookupError = null },
                    label = { Text("对方六位现场号") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    scope.launch {
                        accountLookupBusy = true
                        try {
                            val target = LivePlatformApi().searchUsers(token, manualAccountId)
                                .firstOrNull { it.publicId.toString() == manualAccountId }
                                ?: error("没有找到这个现场号")
                            onOpenChat(target.id.toString(), target.username)
                            enteringAccount = false
                        } catch (error: CancellationException) { throw error
                        } catch (error: Exception) { accountLookupError = error.message ?: "查找失败，请重试"
                        } finally { accountLookupBusy = false }
                    }
                }, enabled = manualAccountId.matches(Regex("[1-9][0-9]{5}")) && !accountLookupBusy) {
                    Text(if (accountLookupBusy) "查找中…" else "查找用户")
                }
                accountLookupError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
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
            } else if (videoSheet) DmVideoSheet(busy, pendingVideoPhase == "upload", videoPaid, onDismiss = { videoSheet = false }) { title, price, bytes, duration ->
                videoSheet = false
                onSendVideo(title, price, bytes, duration)
            } else {
                val canMessage = ready && !busy && recipientId.isNotBlank()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onVoiceCall, enabled = canMessage) {
                        Icon(Icons.Rounded.PhoneInTalk, contentDescription = "语音通话", tint = Color(0xFF6548E9), modifier = Modifier.size(30.dp))
                    }
                    IconButton(onClick = onVideoCall, enabled = canMessage) {
                        Icon(Icons.Rounded.Videocam, contentDescription = "视频通话", tint = Color(0xFF3C6DE8), modifier = Modifier.size(31.dp))
                    }
                    IconButton(onClick = { giftSheet = true; mediaTray = null }, enabled = canMessage && pendingGift == null) {
                        Image(painterResource(R.drawable.qumi_chat_gift), contentDescription = "赠送礼物", modifier = Modifier.size(38.dp))
                    }
                    IconButton(onClick = { mediaTray = if (mediaTray == "emoji") null else "emoji" }) {
                        Icon(Icons.Rounded.Mood, contentDescription = "表情", tint = Color(0xFF6F628D), modifier = Modifier.size(29.dp))
                    }
                    IconButton(onClick = { mediaTray = if (mediaTray == "media") null else "media" }) {
                        Icon(Icons.Rounded.AddCircleOutline, contentDescription = "更多", tint = Color(0xFF6F628D), modifier = Modifier.size(30.dp))
                    }
                }
                when (mediaTray) {
                    "emoji" -> QumiEmojiStrip { onMessage((message + it).take(500)) }
                    "media" -> Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        QumiChatMediaTile(Icons.Rounded.Photo, "图片", Color(0xFF45C66B)) {
                            if (canMessage) { mediaTray = null; imagePicker.launch("image/*") }
                        }
                        QumiChatMediaTile(Icons.Rounded.Videocam, "视频", Color(0xFF5D75E8)) {
                            if (canMessage && !checkingCreator) scope.launch {
                                checkingCreator = true
                                try {
                                    val applications = LivePlatformApi().creatorApplications(token)
                                    if (applications.any { it.status == "APPROVED" }) mediaTray = "video"
                                    else {
                                        creatorNeedsVerification = true
                                        creatorNotice = "只有主播认证审核通过后，才能发送免费视频或付费视频。"
                                    }
                                } catch (error: CancellationException) { throw error
                                } catch (_: Exception) {
                                    creatorNeedsVerification = false
                                    creatorNotice = "暂时无法确认主播认证状态，请稍后重试。"
                                } finally { checkingCreator = false }
                            }
                        }
                    }
                    "video" -> Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        QumiChatMediaTile(Icons.Rounded.Videocam, "免费视频", Color(0xFF5D75E8)) {
                            videoPaid = false; videoSheet = true; mediaTray = null
                        }
                        QumiChatMediaTile(Icons.Rounded.Videocam, "付费视频", Color(0xFF6548E9)) {
                            videoPaid = true; videoSheet = true; mediaTray = null
                        }
                    }
                }
                if (pendingVideoPhase == "deliver") TextButton(onClick = { onSendVideo("", 0, ByteArray(0), 1) }, enabled = canMessage) { Text("重试发送视频") }
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
    onReferral: () -> Unit,
    customerServiceAvailable: Boolean,
    onCustomerService: () -> Unit,
    onGiftHistory: () -> Unit,
    onRefresh: () -> Unit,
    onTopUp: (Int) -> Unit,
) {
    var confirmRecharge by remember { mutableStateOf<Int?>(null) }
    var ageText by remember(profile) { mutableStateOf(profile?.age?.toString().orEmpty()) }
    var regionText by remember(profile) { mutableStateOf(profile?.region.orEmpty()) }
    confirmRecharge?.let { coins ->
        QumiConfirmSheet(
            title = "充值 $coins 金币",
            message = "实付 ¥${String.format(java.util.Locale.ROOT, "%.2f", coins / 100.0)}。确认后前往支付宝，到账结果请在充值记录查看。",
            confirmLabel = "前往支付",
            busy = rechargeBusy,
            onDismiss = { confirmRecharge = null },
            onConfirm = { confirmRecharge = null; onTopUp(coins) },
        )
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(session.username, 1, 56)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(session.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("账号 ID ${session.publicId}", style = MaterialTheme.typography.bodySmall, color = Fog, modifier = Modifier.padding(top = 4.dp))
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, contentDescription = "刷新账户信息") }
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
                            Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, tint = Mint, modifier = Modifier.size(20.dp))
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
        item { OutlinedButton(onClick = onReferral, modifier = Modifier.fillMaxWidth()) { Text("拉新送币") } }
        if (customerServiceAvailable) item { OutlinedButton(onClick = onCustomerService, modifier = Modifier.fillMaxWidth()) { Text("联系客服") } }
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
    return if (id > 0 && token.isNotBlank()) UserSession(id, prefs.getString("username", "用户$id").orEmpty(), token,
        prefs.getLong("public_id", id), prefs.getString("avatar", null)) else null
}

private fun saveSession(context: android.content.Context, session: UserSession?) {
    context.getSharedPreferences("live_session", android.content.Context.MODE_PRIVATE).edit().apply {
        if (session == null) {
            remove("id"); remove("public_id"); remove("username"); remove("token"); remove("avatar")
        } else {
            putLong("id", session.id)
            putLong("public_id", session.publicId)
            putString("avatar", session.avatar)
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
                avatar = row.optString("faceURL"),
            ))
        }
    }
}.getOrDefault(emptyList())

@Composable
private fun LiveRoomPreview(room: LiveRoom, onBack: () -> Unit, onVideo: () -> Unit, onEnter: () -> Unit) {
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Image(painterResource(LivePlaceholderCovers[Math.floorMod(room.stream.hashCode(), LivePlaceholderCovers.size)]), null,
            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        room.coverPath?.let { LiveCoverImage(it) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .4f), Color.Transparent, Color.Black.copy(alpha = .7f))))
            .clickable(onClickLabel = "进入直播间", onClick = onEnter))
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            Text("视频", Modifier.clickable(onClick = onVideo).padding(15.dp), color = Color.White.copy(alpha = .7f), fontSize = 16.sp)
            Text("直播", Modifier.padding(15.dp), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }
        LiveEnterButton(onEnter, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 158.dp))
        Column(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("直播中", Modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xFF8251E9)).padding(8.dp, 4.dp), color = Color.White, fontSize = 12.sp)
            Text("@" + room.host, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(room.title, color = Color.White, fontSize = 15.sp)
        }
    }
}
