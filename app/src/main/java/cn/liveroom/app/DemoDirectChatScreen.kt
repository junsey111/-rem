package cn.liveroom.app

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
internal fun DemoDirectChatScreen(state: DemoChatState, ownName: String, ownAvatar: String?,
    onBack: () -> Unit, token: String, peerId: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember(state) { mutableStateOf("") }
    var panel by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<DemoChatMessage?>(null) }
    var selectedGift by remember { mutableStateOf(demoGiftCatalog.firstOrNull()?.id) }
    var celebration by remember { mutableStateOf<Gift?>(null) }
    var effectNonce by remember { mutableIntStateOf(0) }
    var call by remember { mutableStateOf<String?>(null) }
    var incoming by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    fun finishCall(rejected: Boolean = false) {
        val mode = call ?: return
        state.append(!incoming, "${if (mode == "video") "视频" else "语音"}通话 · ${if (connected) "通话时长 ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" else if (rejected) "已拒绝 · 未接通" else "已取消 · 未接通"}", "call")
        call = null
    }
    fun startCall(mode: String, fromPeer: Boolean = false) {
        panel = null; seconds = 0; incoming = fromPeer; connected = false; call = mode
    }
    LaunchedEffect(call, connected, incoming) {
        if (call == null) return@LaunchedEffect
        if (!connected) { if (!incoming) { delay(2500); connected = true }; return@LaunchedEffect }
        while (true) { delay(1000); seconds++ }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            try {
                val video = context.contentResolver.getType(uri)?.startsWith("video/") == true
                val file = withContext(Dispatchers.IO) {
                    val folder = File(context.filesDir, "demo-chat-media").apply { mkdirs() }
                    require((folder.listFiles()?.sumOf { it.length() } ?: 0) < 150L * 1024 * 1024) { "演示媒体空间已满，请清理后重试" }
                    val target = File(folder, "${UUID.randomUUID()}.${if (video) "mp4" else "img"}")
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { output ->
                            val buffer = ByteArray(8192); var total = 0
                            while (true) { val count = input.read(buffer); if (count < 0) break; total += count
                                require(total <= 20 * 1024 * 1024) { "请选择 20MB 以内的文件" }; output.write(buffer, 0, count) }
                        } } ?: error("无法读取文件")
                        target
                    } catch (failure: Exception) { target.delete(); throw failure }
                }
                state.append(true, if (video) "视频" else "图片", if (video) "video" else "image", file.absolutePath); panel = null
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (failure: Exception) { notice = failure.message ?: "无法读取文件"
            } finally { importing = false }
        }
    }
    val list = rememberLazyListState()
    LaunchedEffect(state, state.messages.lastOrNull()?.id) {
        list.animateScrollToItem((state.messages.size - 1).coerceAtLeast(0))
        if (state.messages.lastOrNull()?.let { it.own && it.kind != "call" } == true) { delay(1100); state.reply() }
    }
    Box(Modifier.fillMaxSize()) {
        DirectChatLayout(state.name, state.avatar, true, demo = true,
            subtitle = if (importing) "正在读取文件…" else "单人演示聊天", draft = draft, sending = importing,
            onDraft = { draft = it }, onBack = onBack, onSend = { state.send(draft); draft = ""; panel = null },
            onVoice = { startCall("voice") }, onVideo = { startCall("video") },
            onGift = { panel = if (panel == "gift") null else "gift" }, onEmoji = { panel = if (panel == "emoji") null else "emoji" },
            onMore = { panel = if (panel == "media") null else "media" }, panel = {
                when (panel) {
                    "emoji" -> QumiEmojiStrip { draft = (draft + it).take(500) }
                    "media" -> Column(Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { picker.launch("image/*") }) { Text("图片") }
                            OutlinedButton(onClick = { picker.launch("video/*") }) { Text("视频") }
                            OutlinedButton(onClick = { startCall("video", true) }) { Text("演示来电") }
                        }
                        Row {
                            TextButton(onClick = { state.append(true, "图片", "image", "sample"); panel = null }) { Text("示例图片") }
                            TextButton(onClick = { state.append(true, "视频", "video", "sample"); panel = null }) { Text("示例视频") }
                        }
                    }
                    "gift" -> QumiGiftPicker(demoGiftCatalog, selectedGift, null, false, demo = true,
                        onSelect = { selectedGift = it }, onSend = {
                            demoGiftCatalog.firstOrNull { it.id == selectedGift }?.let { gift ->
                                state.append(true, gift.name, "gift", gift.id); celebration = gift; effectNonce++; panel = null
                            }
                        }, onDismiss = { panel = null })
                }
            }) {
            if (peerId != null) DirectFollowReminder(token, peerId)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(state.messages, key = { it.id }) { message ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(message.at)), Modifier.align(Alignment.CenterHorizontally).padding(bottom = 9.dp), color = Color(0xFFB4A9C2), fontSize = 10.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.own) Arrangement.End else Arrangement.Start) {
                            if (!message.own) { ChatOnlineAvatar(state.name, state.avatar, false, demo = state.avatar == null); Spacer(Modifier.width(8.dp)) }
                            Surface(color = if (message.own) Color(0xFF7044D8) else Color(0xFFF5F3F8), contentColor = if (message.own) Color.White else Color(0xFF42384F),
                                shape = RoundedCornerShape(14.dp), modifier = Modifier.widthIn(max = 235.dp)) {
                                when (message.kind) {
                                    "image" -> DemoChatImage(message, state.poster, Modifier.size(190.dp, 170.dp).clickable { preview = message })
                                    "video" -> Column(Modifier.clickable { preview = message }) {
                                        Box(Modifier.size(190.dp, 170.dp), contentAlignment = Alignment.Center) {
                                            Image(painterResource(state.poster), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            Icon(Icons.Rounded.PlayArrow, "播放视频", Modifier.size(36.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = .35f)), tint = Color.White)
                                        }
                                        Text("免费视频 · 点击播放", Modifier.padding(9.dp), fontSize = 11.sp)
                                    }
                                    "gift" -> Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        demoGiftCatalog.firstOrNull { it.id == message.media }?.let { GiftArtwork(it, Modifier.size(86.dp), it.name) }
                                        Text("赠送 ${message.text} ×1", fontSize = 14.sp); Text("演示礼物 · 不扣金币", fontSize = 10.sp)
                                    }
                                    else -> Text(message.text, Modifier.padding(13.dp, 11.dp), fontSize = 15.sp, lineHeight = 23.sp)
                                }
                            }
                            if (message.own) { Spacer(Modifier.width(8.dp)); ChatOnlineAvatar(ownName, ownAvatar, false) }
                        }
                    }
                }
            }
        }
        celebration?.let { gift -> QumiGiftCelebration(gift, effectNonce, true) { celebration = null } }
    }
    notice?.let { AlertDialog(onDismissRequest = { notice = null }, title = { Text("温馨提示") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { notice = null }) { Text("知道了") } }) }
    preview?.let { message -> Dialog(onDismissRequest = { preview = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (message.kind == "image") DemoChatImage(message, state.poster, Modifier.fillMaxSize())
            else DemoChatVideo(if (message.media == "sample") Uri.parse("android.resource://${context.packageName}/${R.raw.qumi_demo_live_51287}") else Uri.fromFile(File(message.media)))
            TextButton(onClick = { preview = null }, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) { Text("返回", color = Color.White) }
        }
    } }
    call?.let { mode ->
        if (incoming && !connected) DmIncomingCallPrompt(state.name, state.avatar, mode, false, onAccept = { connected = true }, onReject = { finishCall(true) })
        else Dialog(onDismissRequest = { finishCall() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF60408B), Color(0xFF171020))))) {
                if (connected && mode == "video") DemoChatVideo(Uri.parse("android.resource://${context.packageName}/${R.raw.qumi_demo_live_51287}"))
                Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("演示${if (mode == "video") "视频" else "语音"}通话", color = Color.White)
                    Spacer(Modifier.weight(1f))
                    if (!connected || mode == "voice") { ChatOnlineAvatar(state.name, state.avatar, false, Modifier.size(108.dp), demo = state.avatar == null); Text(state.name, color = Color.White, fontSize = 25.sp, modifier = Modifier.padding(20.dp)) }
                    Text(if (connected) "已接通 ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" else "正在呼叫，等待对方接听…", color = Color.White)
                    Spacer(Modifier.weight(1f))
                    CallRoundAction(if (connected) "挂断" else "取消呼叫", Color(0xFFE34D6C), true, { finishCall() })
                    Text("本机模拟 · 不使用摄像头麦克风 · 不扣费", color = Color(0xFFD9CDE8), fontSize = 11.sp, modifier = Modifier.padding(top = 20.dp))
                }
            }
        }
    }
}

@Composable
private fun DemoChatImage(message: DemoChatMessage, poster: Int, modifier: Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, message.media) {
        if (message.media != "sample") value = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(message.media, bounds)
            var sample = 1; while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2
            BitmapFactory.decodeFile(message.media, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
    if (message.media == "sample") Image(painterResource(poster), "聊天图片", modifier, contentScale = ContentScale.Fit)
    else if (bitmap != null) Image(bitmap!!.asImageBitmap(), "聊天图片", modifier, contentScale = ContentScale.Fit)
    else Box(modifier, contentAlignment = Alignment.Center) { Text("图片暂不可用") }
}

@Composable
private fun DemoChatVideo(uri: Uri) {
    val context = LocalContext.current
    var failed by remember(uri) { mutableStateOf(false) }
    val player = remember(uri) { ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(uri)); repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE; volume = 0f
        addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { failed = true }
        }); prepare(); playWhenReady = true
    } }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
    if (failed) Text("视频暂无法播放，请选择其他文件", color = Color.White)
}
