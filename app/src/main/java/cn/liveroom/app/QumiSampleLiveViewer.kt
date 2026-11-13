package cn.liveroom.app

import android.app.Activity
import androidx.activity.compose.LocalActivity
import android.content.Context
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

private data class DemoRoom(val title: String, val host: String, val video: Int, val poster: Int,
    val viewers: Int, val welcome: String, val multi: Boolean = false)

private val demoRooms = listOf(
    DemoRoom("晚间聊天", "小橙", R.raw.qumi_demo_live_51287, R.drawable.qumi_demo_live_51287,
        28, "晚上好，来聊聊天吧"),
    DemoRoom("心动点歌", "阿宁", R.raw.qumi_demo_live_48036, R.drawable.qumi_demo_live_48036,
        46, "想听什么歌？告诉我"),
    DemoRoom("一起听歌", "小夏", R.raw.qumi_demo_live_47971, R.drawable.qumi_demo_live_47971,
        19, "欢迎来到我的直播间"),
    DemoRoom("舞台时刻", "小伊", R.raw.qumi_demo_live_41181, R.drawable.qumi_demo_live_41181,
        35, "今天一起分享好心情"),
)

private val demoMosaicRoom = DemoRoom("多人连麦演示", "小橙", R.raw.qumi_demo_live_51287, R.drawable.qumi_demo_live_51287, 128, "欢迎来到多人直播间，点击各主播画面查看主页", multi = true)

private val demoPurple = Color(0xFF794BE7)
private data class DemoLikeBurst(val id: Int, val position: Offset)

@Composable
internal fun QumiSampleLiveViewer(initialIndex: Int, userId: Long, token: String, initialPreview: Boolean = false, onBack: () -> Unit, onOpenVideo: () -> Unit,
    onMessage: (String, String) -> Unit, senderName: String, senderAvatar: String?) {
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousLightStatusBar = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previousLightStatusBar != null) controller?.isAppearanceLightStatusBars = previousLightStatusBar }
    }
    val liveRooms = remember { demoRooms + demoMosaicRoom }
    var entered by remember { mutableStateOf(!initialPreview) }
    val pager = rememberPagerState(initialPage = initialIndex.coerceIn(liveRooms.indices),
        pageCount = { liveRooms.size })
    var panel by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var participantUser by remember { mutableStateOf<PublicUser?>(null) }
    var demoUser by remember { mutableStateOf<PublicUser?>(null) }
    var userLoading by remember { mutableStateOf(false) }
    var userError by remember { mutableStateOf<String?>(null) }
    var profileOpen by remember { mutableStateOf(false) }
    var followBusy by remember { mutableStateOf(false) }
    var followNotice by remember { mutableStateOf<String?>(null) }
    var profileRefresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(pager.currentPage, profileRefresh) {
        demoUser = null; userLoading = true; userError = null
        try {
            val spec = demoProfiles[pager.currentPage % demoProfiles.size]
            demoUser = LivePlatformApi().searchUsers(token, spec.publicId.toString()).firstOrNull { it.publicId == spec.publicId }
                ?: error("演示资料暂不可用，请稍后重试")
            cacheDemoProfile(context, demoUser!!)
            val legacy = context.getSharedPreferences("demo_live_follow_$userId", Context.MODE_PRIVATE)
            val title = liveRooms[pager.currentPage].title
            if (legacy.getBoolean(title, false)) {
                if (demoUser?.isFollowing == false) LivePlatformApi().setFollowing(token, demoUser!!.id, true)
                demoUser = demoUser?.copy(isFollowing = true)
                legacy.edit().remove(title).apply()
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (failure: Exception) { userError = failure.message ?: "资料加载失败"
        } finally { userLoading = false }
    }
    var demoGifts by remember { mutableStateOf(demoGiftCatalog) }
    var demoGiftId by remember { mutableStateOf<String?>(null) }
    var giftSendCount by remember { mutableIntStateOf(0) }
    var giftSentName by remember { mutableStateOf("") }
    var effectGift by remember { mutableStateOf<Gift?>(null) }
    var effectNonce by remember { mutableIntStateOf(0) }
    var cohostQueued by remember { mutableStateOf(false) }
    var queueProgress by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        runCatching { LivePlatformApi().giftCatalog() }.onSuccess { if (it.isNotEmpty()) demoGifts = it }
    }
    LaunchedEffect(pager.currentPage) {
        panel = null
        effectGift = null
        cohostQueued = false
        queueProgress = 0
    }
    BackHandler { if (panel != null) panel = null else onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = panel == null) { index ->
            DemoLiveRoom(liveRooms[index], userId = userId, active = pager.currentPage == index,
                preview = !entered, onEnter = { entered = true }, onClose = onBack, onOpenVideo = onOpenVideo,
                onParticipant = { participant -> scope.launch {
                    try { val spec = demoProfiles[participant]
                        participantUser = LivePlatformApi().searchUsers(token, spec.publicId.toString()).firstOrNull { it.publicId == spec.publicId }
                            ?: error("资料暂不可用")
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { userError = "资料暂不可用，请重试" }
                } },
                followed = demoUser?.isFollowing == true, followBusy = followBusy || userLoading,
                onFollow = {
                    val target = demoUser
                    if (target == null) { userError = "资料尚未加载，请重试" }
                    else if (!followBusy) scope.launch {
                        followBusy = true
                        try { LivePlatformApi().setFollowing(token, target.id, !target.isFollowing)
                            demoUser = target.copy(isFollowing = !target.isFollowing)
                            followNotice = if (target.isFollowing) "已取消关注" else "已关注"
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
                        } catch (failure: Exception) { userError = failure.message ?: "关注失败，请重试"
                        } finally { followBusy = false }
                    }
                },
                giftSendCount = giftSendCount, giftSentName = giftSentName,
                onPanel = {
                    when (it) {
                        "profile" -> profileOpen = true
                        "message" -> demoUser?.let { user -> onMessage(user.id.toString(), user.username) }
                            ?: run { userError = "资料尚未加载，请重试" }
                        else -> panel = it
                    }
                })
        }
        panel?.let { selected ->
            DemoLivePanel(selected, liveRooms[pager.currentPage],
                gifts = demoGifts,
                selectedGiftId = demoGiftId,
                cohostQueued = cohostQueued,
                queueProgress = queueProgress,
                onApplyCohost = { cohostQueued = true },
                onWithdrawCohost = { cohostQueued = false },
                onAdvanceQueue = { queueProgress = (queueProgress + 1).coerceAtMost(3) },
                onGiftSelected = { demoGiftId = it },
                onGiftSent = { gift ->
                    giftSentName = gift.name
                    giftSendCount++
                    effectGift = gift
                    effectNonce++
                    panel = null
                },
                onDismiss = { panel = null })
        }
        effectGift?.let { gift ->
            val nonce = effectNonce
            QumiGiftCelebration(gift, nonce, demo = true, senderName = senderName, senderAvatar = senderAvatar) {
                if (effectNonce == nonce) effectGift = null
            }
        }
        participantUser?.let { target -> DirectChatProfile(token, target.id, userId, target.username,
            onBack = { participantUser = null }, onMessage = onMessage) }
        userError?.let { message -> AlertDialog(onDismissRequest = { userError = null }, title = { Text("温馨提示") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = { userError = null; profileRefresh++ }) { Text("重试") } },
            dismissButton = { TextButton(onClick = { userError = null }) { Text("关闭") } }) }
        if (profileOpen) Dialog(onDismissRequest = { profileOpen = false; profileRefresh++ },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val target = demoUser
            if (target != null) QumiPublicProfileScreen(token, target.id, userId, target.username,
                onBack = { profileOpen = false; profileRefresh++ },
                onMessage = onMessage, onBlock = { userError = "演示账号用于流程走查" })
            else Box(Modifier.fillMaxSize().background(Color(0xFFF5F0FD)), contentAlignment = Alignment.Center) {
                TextButton(onClick = { profileOpen = false }, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) { Text("返回") }
                if (userLoading) CircularProgressIndicator() else TextButton(onClick = { profileRefresh++ }) { Text("资料加载失败 · 重试") }
            }
        }
        QumiActionNotice(followNotice) { followNotice = null }
    }
}

@Composable
internal fun QumiPremiumGiftEffect(gift: Gift, nonce: Int) {
    val premium = gift.coinPrice >= 500
    val scale = remember { Animatable(.45f) }
    val opacity = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(nonce) {
        scale.snapTo(.45f)
        opacity.snapTo(0f)
        burst.snapTo(0f)
        launch { opacity.animateTo(1f, tween(220)) }
        if (premium) launch { burst.animateTo(1f, tween(1_250)) }
        scale.animateTo(1.12f, tween(380))
        scale.animateTo(1f, tween(160))
        delay(if (premium) 1_550 else 1_150)
        opacity.animateTo(0f, tween(400))
    }
    Box(Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center) {
        if (premium) Canvas(Modifier.fillMaxSize()) {
            val radius = (100 + 130 * burst.value).dp.toPx()
            val fade = (1f - burst.value).coerceIn(0f, 1f) * opacity.value
            drawCircle(Color(0xFFFFE3A2), radius, center, alpha = fade * .58f,
                style = Stroke(width = 3.dp.toPx()))
            repeat(12) { index ->
                val angle = index * PI / 6
                val start = Offset(center.x + (radius - 16.dp.toPx()) * cos(angle).toFloat(),
                    center.y + (radius - 16.dp.toPx()) * sin(angle).toFloat())
                val end = Offset(center.x + (radius + 16.dp.toPx()) * cos(angle).toFloat(),
                    center.y + (radius + 16.dp.toPx()) * sin(angle).toFloat())
                drawLine(Color(0xFFFFE3A2), start, end, strokeWidth = 3.dp.toPx(), alpha = fade)
            }
        }
        Column(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; alpha = opacity.value },
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(245.dp), contentAlignment = Alignment.Center) {
                GiftArtwork(gift, Modifier.size(if (premium) 208.dp else 185.dp), gift.name)
                listOf(
                    Triple((-108).dp, (-76).dp, 23.dp), Triple(100.dp, (-85).dp, 28.dp),
                    Triple((-117).dp, 58.dp, 18.dp), Triple(110.dp, 69.dp, 21.dp),
                    Triple((-18).dp, (-113).dp, 18.dp), Triple(39.dp, 112.dp, 16.dp),
                ).forEach { (x, y, size) ->
                    Icon(Icons.Rounded.Star, null, Modifier.offset(x, y).size(size),
                        tint = Color(0xFFFFD66E))
                }
            }
        }
    }
}

@Composable
private fun DemoLiveRoom(room: DemoRoom, userId: Long, active: Boolean, giftSendCount: Int,
    giftSentName: String, followed: Boolean, followBusy: Boolean, onFollow: () -> Unit, onPanel: (String) -> Unit,
    preview: Boolean, onEnter: () -> Unit, onClose: () -> Unit, onOpenVideo: () -> Unit, onParticipant: (Int) -> Unit) {
    val controlsBottom = 51.dp
    val context = LocalContext.current
    var likes by remember(room.title) { mutableIntStateOf(126) }
    var liked by remember(room.title) { mutableStateOf(false) }
    var likeBurstId by remember(room.title) { mutableIntStateOf(0) }
    var viewportSize by remember(room.title) { mutableStateOf(IntSize.Zero) }
    val likeBursts = remember(room.title) { mutableStateListOf<DemoLikeBurst>() }
    val density = LocalDensity.current
    fun likeAt(position: Offset) {
        if (!active) return
        likes++
        liked = true
        likeBursts.add(DemoLikeBurst(++likeBurstId, position))
        if (likeBursts.size > 8) likeBursts.removeAt(0)
    }
    var entryNotice by remember(room.title, preview) { mutableStateOf(!preview) }
    LaunchedEffect(room.title, preview, active) { if (!preview && active) { entryNotice = true; delay(12_000); entryNotice = false } }
    var draft by remember(room.title) { mutableStateOf("") }
    var showCommentComposer by remember(room.title) { mutableStateOf(false) }
    var messages by remember(room.title) { mutableStateOf(listOf(
        "我" to "进入直播间",
        room.host to room.welcome,
        "小柚" to "主播晚上好～",
    )) }
    val incoming = listOf("这个画面好美", "今天聊什么呀？", "刚进来，先听一会儿")
    LaunchedEffect(room.title, active) {
        if (!active || preview) return@LaunchedEffect
        var next = 0
        while (true) {
            delay(6_000)
            messages = (messages + ("观众演示" to incoming[next % incoming.size])).takeLast(5)
            next++
        }
    }
    LaunchedEffect(giftSendCount) {
        if (giftSendCount > 0 && active) {
            messages = (messages + ("我" to "赠送了 $giftSentName（演示）")).takeLast(5)
        }
    }
    fun send() {
        val text = draft.trim()
        if (text.isNotEmpty()) {
            messages = (messages + ("我" to text)).takeLast(5)
            draft = ""
            showCommentComposer = false
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { viewportSize = it }.background(Color.Black)) {
        if (!room.multi) {
            Image(painterResource(room.poster), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (active) DemoLiveVideo(room.video)
        } else LiveMosaic(demoRooms.indices.toList(), identity = { it }, onProfile = { if (preview) onEnter() else onParticipant(it) },
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = if (preview) 90.dp else 145.dp).fillMaxWidth().height(330.dp)) { participant ->
            Image(painterResource(demoRooms[participant].poster), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (active) DemoLiveVideo(demoRooms[participant].video)
            Text(demoRooms[participant].host, Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = .3f)).padding(7.dp, 4.dp), color = Color.White, fontSize = 12.sp)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color.Black.copy(alpha = .5f), Color.Transparent, Color.Transparent,
            Color.Black.copy(alpha = .82f))))
            .then(if (preview) Modifier.clickable(onClickLabel = "进入直播间", onClick = onEnter) else Modifier))
        if (!room.multi && !preview) Box(Modifier.fillMaxSize().pointerInput(active) {
            detectTapGestures(onDoubleTap = { likeAt(it) })
        })

        if (preview) {
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                Text("视频", Modifier.clickable(onClick = onOpenVideo).padding(14.dp), color = Color.White.copy(alpha = .7f), fontSize = 16.sp)
                Text("直播", Modifier.padding(14.dp), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f)); Spacer(Modifier.width(48.dp))
            }
            LiveEnterButton(onEnter, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 165.dp))
            Column(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("直播中", Modifier.clip(RoundedCornerShape(5.dp)).background(demoPurple).padding(8.dp, 4.dp), color = Color.White, fontSize = 12.sp)
                Text("@" + room.host, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(room.title, color = Color.White, fontSize = 15.sp)
            }
            return@Box
        }
        LiveRoomHeader(LiveHeaderPerson(room.host, poster = room.poster), likes.toString() + " 本场点赞",
            followed, followBusy, true, room.viewers.toString(),
            demoRooms.take(3).map { LiveHeaderPerson(it.host, poster = it.poster) },
            onProfile = { onPanel("profile") }, onFollow = onFollow, onRank = { onPanel("rank") },
            onViewers = { onPanel("viewers") }, onClose = onClose, onAnnouncement = { onPanel("announcement") },
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(.78f)
            .navigationBarsPadding()
            .padding(start = 16.dp, bottom = controlsBottom + 71.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (entryNotice) LiveEntryNotice(LIVE_ENTRY_NOTICE + "\n房间公告：" + room.welcome)
            messages.forEach { (name, content) ->
                Row(Modifier.clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = .42f))
                    .padding(horizontal = 9.dp, vertical = 5.dp)) {
                    Text("$name  ", color = if (name == "我") Color(0xFFB9A2FF)
                        else Color(0xFFFFD586), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(content, color = Color.White, fontSize = 12.sp, maxLines = 2)
                }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 13.dp, bottom = controlsBottom),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.weight(1f).height(44.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = .56f))
                .clickable { showCommentComposer = true }
                .padding(start = 15.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ChatBubbleOutline, null, Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(7.dp))
                Text("说点什么...", color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
            }
            DemoAction(Icons.Rounded.Email, "私信主播") { onPanel("message") }
            DemoAction(Icons.Rounded.Mic, "申请连麦") { onPanel("cohost") }
            DemoAction(Icons.Rounded.CardGiftcard, "送礼") { onPanel("gift") }
            Column(Modifier.size(43.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = .48f))
                .clickable {
                    likeAt(Offset(
                        viewportSize.width - with(density) { 38.dp.toPx() },
                        viewportSize.height - with(density) { 100.dp.toPx() },
                    ))
                }, horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.Favorite, contentDescription = "点赞", tint = if (liked) Color(0xFFFF4266) else Color.White,
                    modifier = Modifier.size(19.dp))
                Text("$likes", color = Color.White, fontSize = 9.sp, lineHeight = 10.sp)
            }
        }
        likeBursts.forEach { burst ->
            androidx.compose.runtime.key(burst.id) {
                DemoLikeHeartBurst(burst) { likeBursts.remove(burst) }
            }
        }
    }
    if (showCommentComposer) DemoCommentComposer(draft, { draft = it.take(100) },
        onSend = { send() }, onDismiss = { showCommentComposer = false })
}

@Composable
private fun DemoLikeHeartBurst(burst: DemoLikeBurst, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(burst.id) {
        progress.animateTo(1f, tween(850))
        onFinished()
    }
    val p = progress.value
    val fade = if (p < .65f) 1f else ((1f - p) / .35f).coerceIn(0f, 1f)
    val rise = with(density) { 80.dp.toPx() } * p
    val sizePx = with(density) { 72.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        Icon(Icons.Rounded.Favorite, contentDescription = null,
            modifier = Modifier.offset {
                androidx.compose.ui.unit.IntOffset(
                    (burst.position.x - sizePx / 2).roundToInt(),
                    (burst.position.y - sizePx / 2 - rise).roundToInt(),
                )
            }.size(72.dp).graphicsLayer {
                alpha = fade
                scaleX = .55f + p.coerceAtMost(.35f) * 1.7f
                scaleY = scaleX
                rotationZ = -14f + p * 18f
            }, tint = Color(0xFFFF385B))
        listOf(-1f, 1f).forEach { side ->
            Icon(Icons.Rounded.Favorite, contentDescription = null,
                modifier = Modifier.offset {
                    androidx.compose.ui.unit.IntOffset(
                        (burst.position.x + side * sizePx * (.35f + p * .55f) - sizePx * .15f).roundToInt(),
                        (burst.position.y - sizePx * .45f - rise * (1.1f + side * .15f)).roundToInt(),
                    )
                }.size(22.dp).graphicsLayer {
                    alpha = fade * p.coerceAtMost(.5f) * 2f
                    rotationZ = side * 25f
                }, tint = Color(0xFFFF6480))
        }
    }
}

@Composable
private fun DemoCommentComposer(draft: String, onDraft: (String) -> Unit,
    onSend: () -> Unit, onDismiss: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.apply {
                setGravity(Gravity.BOTTOM)
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        Row(Modifier.fillMaxWidth().background(LiveStyle.sheetSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(42.dp).clip(CircleShape)
                .background(Color(0xFFF2F1F6)).padding(horizontal = 15.dp),
                contentAlignment = Alignment.CenterStart) {
                if (draft.isEmpty()) Text("说点什么...", color = Color(0xFF9995A5), fontSize = 14.sp)
                BasicTextField(draft, onValueChange = onDraft,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF292335), fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }))
            }
            Text("发送", Modifier.clip(CircleShape)
                .background(if (draft.isNotBlank()) demoPurple else Color(0xFFD8D4DF))
                .clickable(enabled = draft.isNotBlank(), onClick = onSend)
                .padding(horizontal = 16.dp, vertical = 10.dp),
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DemoAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(43.dp).clip(CircleShape)
        .background(Color.Black.copy(alpha = .48f))) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun DemoLiveVideo(video: Int) {
    val context = LocalContext.current
    val player = remember(video) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItem(MediaItem.fromUri("android.resource://${context.packageName}/$video"))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(factory = { viewContext -> PlayerView(viewContext).apply {
        this.player = player
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    } }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun DemoLivePanel(panel: String, room: DemoRoom, gifts: List<Gift>, selectedGiftId: String?,
    cohostQueued: Boolean, queueProgress: Int,
    onApplyCohost: () -> Unit, onWithdrawCohost: () -> Unit, onAdvanceQueue: () -> Unit,
    onGiftSelected: (String) -> Unit, onGiftSent: (Gift) -> Unit, onDismiss: () -> Unit) {
    if (panel == "announcement") {
        LiveRoomAnnouncementDialog(room.host, room.welcome, onDismiss)
        return
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .54f)).clickable(onClick = onDismiss)) {
        if (panel == "gift") {
            Box(Modifier.align(Alignment.BottomCenter).clickable {}) {
                QumiGiftPicker(gifts, selectedGiftId, null, busy = false, demo = true,
                    onSelect = onGiftSelected,
                    onSend = { gifts.firstOrNull { it.id == selectedGiftId }?.let(onGiftSent) },
                    onDismiss = onDismiss)
            }
        } else if (panel == "rank") {
            Box(Modifier.align(Alignment.BottomCenter).clickable {}) {
                DemoRankPanel(room, onDismiss)
            }
        } else if (panel == "cohost") {
            Box(Modifier.align(Alignment.BottomCenter).clickable {}) {
                DemoCohostPanel(cohostQueued, queueProgress, onApplyCohost,
                    onWithdrawCohost, onAdvanceQueue, onDismiss)
            }
        } else Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(LiveStyle.sheetSurface).clickable {}
            .navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text(when (panel) {
                "message" -> "私信主播"
                "profile" -> room.host
                else -> "房间观众"
            }, color = Color(0xFF292335), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            when (panel) {
                "message" -> Text("真实直播间可以给主播发私信；演示房间没有真实主播账号。",
                    color = Color(0xFF777180), fontSize = 13.sp)
                "profile" -> Text("${room.host} · ${room.title}\n直播内容与主播资料为界面演示。",
                    color = Color(0xFF777180), fontSize = 13.sp)
                else -> {
                    Text("${room.viewers} 人正在观看", color = demoPurple,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    val viewers = listOf(
                        Triple("七号旺哥", 4, R.drawable.qumi_demo_live_51287),
                        Triple("天豪", 6, R.drawable.qumi_demo_live_48036),
                        Triple("草原狼", 5, R.drawable.qumi_demo_live_47971),
                        Triple("老帅", 3, R.drawable.qumi_demo_live_41181),
                    )
                    viewers.forEachIndexed { index, (name, level, avatar) ->
                        val rankColor = listOf(Color(0xFFE44951), Color(0xFFF18736),
                            Color(0xFFD4A622), Color(0xFFADB0B9))[index]
                        Row(Modifier.fillMaxWidth().height(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (index == 1) LiveStyle.accentSoft else Color.Transparent)
                            .padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", Modifier.width(24.dp), color = rankColor,
                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Image(painterResource(avatar), contentDescription = null,
                                modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            Text(name, Modifier.weight(1f).padding(start = 10.dp),
                                color = Color(0xFF302B36), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Image(painterResource(qumiLevelBadge(level)),
                                contentDescription = "$level 级勋章", modifier = Modifier.size(26.dp))
                            Text("LV.$level", Modifier.padding(start = 5.dp),
                                color = demoPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("演示观众榜", color = Color(0xFF92909B), fontSize = 11.sp)
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("关闭", color = demoPurple)
            }
        }
    }
}

@Composable
private fun DemoCohostPanel(queued: Boolean, progress: Int, onApply: () -> Unit,
    onWithdraw: () -> Unit, onAdvance: () -> Unit, onDismiss: () -> Unit) {
    val waiting = listOf("小柚", "晚风", "阿琪").drop(progress)
    val count = waiting.size + if (queued) 1 else 0
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        .background(LiveStyle.sheetSurface).navigationBarsPadding().padding(horizontal = 19.dp, vertical = 15.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$count 人正在等待", Modifier.weight(1f), color = Color(0xFF292335),
                fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("演示", color = demoPurple, fontSize = 11.sp)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, "关闭连麦排队", tint = Color(0xFF756E82))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("普通连线 $count", Modifier.weight(1f), color = demoPurple,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onAdvance, enabled = waiting.isNotEmpty()) {
                Text("模拟下一位", color = demoPurple, fontSize = 12.sp)
            }
        }
        if (count == 0) Text("暂无等待用户", Modifier.fillMaxWidth().padding(vertical = 27.dp),
            color = Color(0xFF9B95A7), fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        waiting.forEachIndexed { index, name ->
            DemoQueueRow(index + 1, name, demoRooms[index % demoRooms.size].poster,
                if (index == 0) "10分钟前" else "${index + 2}分钟前")
        }
        if (queued) DemoQueueRow(waiting.size + 1, "我", demoRooms[3].poster,
            if (waiting.isEmpty()) "即将连麦" else "刚刚", highlight = true)
        Row(Modifier.fillMaxWidth().padding(top = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (queued) "你排在第 ${waiting.size + 1} 位" else "等待主播接通",
                Modifier.weight(1f), color = Color(0xFF6C6479), fontSize = 13.sp)
            Text(if (queued) "撤回申请" else "申请连麦",
                Modifier.clip(CircleShape)
                    .background(if (queued) Color(0xFFECE8F3) else demoPurple)
                    .clickable(onClick = if (queued) onWithdraw else onApply)
                    .padding(horizontal = 17.dp, vertical = 10.dp),
                color = if (queued) Color(0xFF6C6479) else Color.White,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DemoQueueRow(position: Int, name: String, avatar: Int, time: String,
    highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(60.dp)
        .background(if (highlight) Color(0xFFF0EBFF) else Color.Transparent)
        .padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$position", Modifier.width(25.dp), color = if (highlight) demoPurple else Color(0xFF9B95A7),
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Image(painterResource(avatar), null, Modifier.size(38.dp).clip(CircleShape),
            contentScale = ContentScale.Crop)
        Text(name, Modifier.weight(1f).padding(start = 10.dp), color = Color(0xFF292335),
            fontSize = 14.sp, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium)
        Text(time, color = Color(0xFF9B95A7), fontSize = 12.sp)
    }
}

@Composable
private fun DemoRankPanel(room: DemoRoom, onDismiss: () -> Unit) {
    var selected by remember(room.title) { mutableIntStateOf(0) }
    val tabs = listOf("日榜", "周榜", "月榜")
    val names = listOf("小柚", "晚风", "阿琪", "星星", "小鹿")
    val scores = listOf(
        listOf(520, 280, 131, 88, 52),
        listOf(3120, 1850, 960, 520, 300),
        listOf(12800, 9760, 5200, 3200, 1880),
    )
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        .background(LiveStyle.sheetSurface).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 15.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${room.host}的礼物榜", Modifier.weight(1f), color = Color(0xFF292335),
                fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("演示", color = demoPurple, fontSize = 11.sp)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, "关闭榜单", tint = Color(0xFF756E82))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 13.dp)) {
            tabs.forEachIndexed { index, label ->
                Column(Modifier.weight(1f).clickable { selected = index },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = if (selected == index) demoPurple else Color(0xFF9B95A7),
                        fontSize = 15.sp,
                        fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Medium)
                    Spacer(Modifier.height(7.dp))
                    Box(Modifier.width(26.dp).height(3.dp).clip(CircleShape)
                        .background(if (selected == index) demoPurple else Color.Transparent))
                }
            }
        }
        names.forEachIndexed { index, name ->
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", Modifier.width(26.dp),
                    color = if (index < 3) Color(0xFFE8AC31) else Color(0xFF9E99A7),
                    fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Image(painterResource(demoRooms[index % demoRooms.size].poster), null,
                    Modifier.size(35.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                Text(name, Modifier.weight(1f).padding(start = 10.dp),
                    color = Color(0xFF292335), fontSize = 14.sp)
                Icon(Icons.Rounded.Star, null, Modifier.size(15.dp), tint = Color(0xFFF1B94D))
                Text(" ${scores[selected][index]}", color = Color(0xFF84652A), fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold)
            }
        }
        Text("示例榜单 · 按赠礼金币排序", Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            color = Color(0xFF9B95A7), fontSize = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
