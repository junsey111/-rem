package cn.liveroom.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

internal data class DemoChatMessage(val id: String, val own: Boolean, val text: String, val at: Long,
    val kind: String = "text", val media: String = "")

/** Separate local demonstration; never enters OpenIM, the gift ledger or another user's inbox. */
internal class DemoChatState(context: Context, userId: Long, val peer: DemoProfile? = null,
    private val onUpdated: ((String) -> Unit)? = null) {
    val name = peer?.name ?: "安然"
    val avatar = peer?.let { "/demo-media/${it.publicId.toString().takeLast(5)}.jpg" }
    val poster = peer?.poster ?: R.drawable.qumi_demo_live_51287
    private val prefs = context.getSharedPreferences("demo_chat_${userId}${peer?.let { "_${it.publicId}" } ?: ""}", Context.MODE_PRIVATE)
    val messages = mutableStateListOf<DemoChatMessage>()
    init {
        val stored = runCatching {
            val rows = JSONArray(prefs.getString("messages", "[]"))
            (0 until rows.length()).map { rows.getJSONObject(it).let { row ->
                DemoChatMessage(row.getString("id"), row.getBoolean("own"), row.getString("text"), row.getLong("at"), row.optString("kind", "text"), row.optString("media"))
            } }.takeLast(100)
        }.getOrDefault(emptyList())
        messages.addAll(stored.ifEmpty { listOf(
            DemoChatMessage("welcome-1", false, "嗨，我是${name}，很高兴认识你～", System.currentTimeMillis() - 120_000),
            DemoChatMessage("welcome-2", false, "可以试试图片、视频、送礼，或给我打个电话 😊", System.currentTimeMillis() - 60_000),
            DemoChatMessage("welcome-video", false, "分享一段小视频给你", System.currentTimeMillis() - 30_000, "video", "sample"),
        ) })
    }
    fun send(text: String) {
        val content = text.trim().take(500)
        if (content.isNotEmpty()) append(true, content)
    }
    fun reply() {
        val last = messages.lastOrNull()?.takeIf { it.own } ?: return
        val reply = when {
            last.kind == "call" -> return
            last.kind == "gift" -> "谢谢你的${last.text}，收到啦～💜"
            last.kind == "image" -> "照片收到啦，拍得真好看 😊"
            last.kind == "video" -> "视频收到啦，我来看看～"
            last.text.contains("你好") || last.text.contains("嗨") -> "你好呀～今天过得怎么样？😊"
            last.text.contains("直播") -> "等会儿一起看看直播吧，听听歌、聊聊天～"
            last.text.contains("晚安") -> "晚安，好好休息，明天见 🌙"
            last.text.contains("谢谢") -> "不客气呀，和你聊天很开心～"
            else -> listOf("收到啦～我在认真听呢 😊", "哈哈，继续聊呀，最近有什么开心的事？", "嗯嗯，有空来这里聊聊天吧～")[messages.count { !it.own } % 3]
        }
        append(false, reply)
    }
    fun append(own: Boolean, text: String, kind: String = "text", media: String = "") {
        messages += DemoChatMessage(UUID.randomUUID().toString(), own, text, System.currentTimeMillis(), kind, media)
        while (messages.size > 100) messages.removeAt(0)
        prefs.edit().putString("messages", JSONArray().apply {
            messages.forEach { put(JSONObject().put("id", it.id).put("own", it.own).put("text", it.text).put("at", it.at).put("kind", it.kind).put("media", it.media)) }
        }.toString()).apply()
        onUpdated?.invoke(when(kind) { "image" -> "[图片]"; "video" -> "[视频]"; "gift" -> "[礼物] $text"; else -> text })
    }
}

@Composable
internal fun QumiDemoChatScreen(state: DemoChatState, ownName: String, onBack: () -> Unit) {
    val purple = Color(0xFF7044D8)
    var draft by remember { mutableStateOf("") }
    var emojiOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val replying = state.messages.lastOrNull()?.own == true
    LaunchedEffect(state.messages.lastOrNull()?.id) {
        listState.animateScrollToItem((state.messages.size - 1).coerceAtLeast(0))
        if (state.messages.lastOrNull()?.own == true) { delay(1_100); state.reply() }
    }
    fun send() { state.send(draft); draft = ""; emojiOpen = false }
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F2FC)).imePadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFFECE3FF), Color(0xFFE8EEFF))))
            .padding(end = 16.dp).height(70.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Color(0xFF352C4B)) }
            ChatOnlineAvatar("安然", null, true, demo = true)
            Column(Modifier.padding(start = 10.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("安然", color = Color(0xFF30273E), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    ChatOnlineLabel()
                }
                Text(if (replying) "正在输入…" else "单人演示聊天", color = Color(0xFF9B86B6), fontSize = 11.sp)
            }
        }
        Text("演示会话 · 消息由本机模拟回复", Modifier.fillMaxWidth().background(Color(0xFFF0EAFB)).padding(horizontal = 18.dp, vertical = 8.dp),
            color = Color(0xFF9583AC), fontSize = 11.sp)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
            contentPadding = PaddingValues(16.dp, 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items(state.messages, key = { it.id }) { message ->
                Column(Modifier.fillMaxWidth()) {
                    Text(SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(message.at)),
                        Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp), color = Color(0xFFB4A9C2), fontSize = 10.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.own) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
                        if (!message.own) { ChatOnlineAvatar("安然", null, false, demo = true); Spacer(Modifier.width(9.dp)) }
                        Surface(color = if (message.own) purple else Color.White,
                            shape = RoundedCornerShape(topStart = if (message.own) 16.dp else 4.dp, topEnd = if (message.own) 4.dp else 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                            modifier = Modifier.widthIn(max = 240.dp)) {
                            Text(message.text, Modifier.padding(13.dp, 11.dp), color = if (message.own) Color.White else Color(0xFF42384F), fontSize = 15.sp, lineHeight = 23.sp)
                        }
                        if (message.own) { Spacer(Modifier.width(9.dp)); ChatOnlineAvatar(ownName, null, false) }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { emojiOpen = !emojiOpen }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.Mood, "表情", tint = purple, modifier = Modifier.size(27.dp))
                }
                BasicTextField(draft, { draft = it.take(500) }, Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFF4F0FA)).padding(horizontal = 14.dp, vertical = 11.dp),
                    textStyle = TextStyle(color = Color(0xFF42384F), fontSize = 15.sp), maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }),
                    decorationBox = { inner -> Box { if (draft.isEmpty()) Text("说点什么…", color = Color(0xFFB0A3C0), fontSize = 15.sp); inner() } })
                Button(onClick = { send() }, enabled = draft.isNotBlank(), shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp), modifier = Modifier.height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = purple)) { Text("发送", fontSize = 13.sp) }
            }
            if (emojiOpen) QumiEmojiStrip { draft = (draft + it).take(500) }
        }
    }
}
