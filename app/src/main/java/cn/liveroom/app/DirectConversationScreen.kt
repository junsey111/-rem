package cn.liveroom.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun DirectConversationScreen(
    token: String, ownId: String, peerId: String, name: String, avatar: String?, online: Boolean,
    history: OpenImHistory?, loading: Boolean, error: String?, ready: Boolean, busy: Boolean,
    draft: String, gifts: List<Gift>, balance: Long?, playable: Map<String, Boolean>,
    pendingGift: Boolean, pendingVideo: String?,
    onBack: () -> Unit, onDraft: (String) -> Unit, onSend: () -> Unit,
    onVoice: () -> Unit, onVideo: () -> Unit, onPrices: () -> Unit,
    onImage: (Uri) -> Unit, onSendVideo: (String, Int, ByteArray, Int) -> Unit,
    onGift: (Gift) -> Unit, onRetryGift: () -> Unit, onVerifyGift: (ChatBody.Gift) -> Unit,
    onUnlock: (String) -> Unit, onPlay: (String) -> Unit,
    onOlder: () -> Unit, onRetry: () -> Unit, onConnect: () -> Unit, onVerifyCreator: () -> Unit,
    ownAvatar: String? = null,
) {
    var panel by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var verifyNeeded by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(onImage) }
    val list = rememberLazyListState()
    LaunchedEffect(history?.messages?.lastOrNull()?.id) { if (!history?.messages.isNullOrEmpty()) list.animateScrollToItem(0) }
    notice?.let { text -> AlertDialog(onDismissRequest = { notice = null }, title = { Text("温馨提示") }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { notice = null; if (verifyNeeded) onVerifyCreator() }) { Text(if (verifyNeeded) "去认证" else "知道了") } },
        dismissButton = { TextButton(onClick = { notice = null }) { Text("取消") } }) }
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DirectChatLayout(name, avatar, online, draft = draft, sending = busy, enabled = ready,
            onDraft = onDraft, onBack = onBack, onSend = onSend, onVoice = onVoice, onVideo = onVideo,
            onGift = { panel = if (panel == "gift") null else "gift" }, onEmoji = { panel = if (panel == "emoji") null else "emoji" },
            onMore = { panel = if (panel == "media") null else "media" },
            panel = {
                Column(Modifier.fillMaxWidth().heightIn(max = 310.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    when (panel) {
                        "emoji" -> QumiEmojiStrip { onDraft((draft + it).take(500)) }
                        "gift" -> DmGiftSheet(gifts, balance, busy, onDismiss = { panel = null }) { panel = null; onGift(it) }
                        "media" -> Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { panel = null; imagePicker.launch("image/*") }) { Text("图片") }
                            OutlinedButton(onClick = { panel = "video" }) { Text("视频") }
                            OutlinedButton(onClick = { panel = null; onPrices() }) { Text("通话价格") }
                        }
                        "video" -> Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { panel = "freeVideo" }) { Text("免费视频") }
                            Button(onClick = {
                                if (!checking) scope.launch {
                                    checking = true
                                    try {
                                        if (LivePlatformApi().creatorApplications(token).any { it.status == "APPROVED" }) panel = "paidVideo"
                                        else { verifyNeeded = true; notice = "只有认证通过的主播才能发送付费视频。" }
                                    } catch (cancelled: CancellationException) { throw cancelled
                                    } catch (_: Exception) { verifyNeeded = false; notice = "暂时无法确认认证状态，请稍后重试。"
                                    } finally { checking = false }
                                }
                            }, enabled = !checking) { Text(if (checking) "确认中…" else "付费视频") }
                        }
                        "freeVideo", "paidVideo" -> DmVideoSheet(busy, pendingVideo == "upload", panel == "paidVideo", onDismiss = { panel = null }) { title, price, bytes, duration ->
                            panel = null; onSendVideo(title, price, bytes, duration)
                        }
                    }
                }
            }) {
            if (peerId != ownId) DirectFollowReminder(token, peerId)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { TextButton(onClick = onRetry) { Text("$it · 重试") } }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, reverseLayout = true,
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(history?.messages.orEmpty().asReversed(), key = { it.id }) { chat ->
                    val own = chat.senderId == ownId
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (own) Arrangement.End else Arrangement.Start) {
                        if (!own) { ChatOnlineAvatar(name, avatar, false); Spacer(Modifier.width(8.dp)) }
                        Column(horizontalAlignment = if (own) Alignment.End else Alignment.Start) {
                            Surface(color = if (own) Color(0xFF7044D8) else Color(0xFFF5F3F8), contentColor = if (own) Color.White else Color(0xFF42384F),
                                modifier = Modifier.widthIn(max = 245.dp), shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    val invite = parseDmCallInvite(chat.content)
                                    if (invite != null) DmCallReceipt(token, invite)
                                    else {
                                        val id = (chatBody(chat) as? ChatBody.Video)?.videoId
                                        DmMessageBubble(chat, id?.let { playable[it] }, busy, onUnlock, onPlay, onVerifyGift)
                                    }
                                }
                            }
                            if (own) Text(when (chat.status) { 1 -> "发送中"; 2 -> "已发送"; 3 -> "发送失败"; else -> "" },
                                color = Color(0xFFB5A9C1), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (own) { Spacer(Modifier.width(8.dp)); ChatOnlineAvatar("我", ownAvatar, false) }
                    }
                }
                if (history?.isEnd == false) item { TextButton(onClick = onOlder, enabled = !loading) { Text("加载更早消息") } }
                if (history?.isEnd == true && history.messages.isEmpty()) item { Text("还没有消息，打个招呼吧。", color = Color(0xFFA396B3)) }
            }
            if (!ready) TextButton(onClick = onConnect) { Text("消息暂未连接 · 点击重连") }
            if (pendingGift) TextButton(onClick = onRetryGift, enabled = !busy) { Text("上一笔礼物待确认 · 重试原订单") }
            if (pendingVideo != null) TextButton(onClick = { onSendVideo("", 0, ByteArray(0), 1) }, enabled = !busy) { Text("视频待发送 · 重试") }
        }
    }
}
