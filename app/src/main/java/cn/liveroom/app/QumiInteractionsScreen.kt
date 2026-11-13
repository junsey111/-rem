package cn.liveroom.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val interactionPurple = Color(0xFF7044D8)
private val interactionInk = Color(0xFF282633)
private val interactionMuted = Color(0xFF92919D)

@Composable
internal fun QumiInteractionsScreen(token: String, onBack: () -> Unit,
    onOpenUser: (String, String) -> Unit, onViewed: (List<String>) -> Unit) {
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()
    var interactions by remember(token) { mutableStateOf(emptyList<MomentInteraction>()) }
    var nextBefore by remember(token) { mutableStateOf("") }
    var loading by remember(token) { mutableStateOf(false) }
    var loaded by remember(token) { mutableStateOf(false) }
    var error by remember(token) { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("all") }
    var selected by remember { mutableStateOf<MomentInteraction?>(null) }

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            runCatching { api.momentInteractions(token, limit = 100) }
                .onSuccess { (items, cursor) ->
                    interactions = items
                    nextBefore = cursor
                    onViewed(items.map { it.id })
                }
                .onFailure { error = it.message ?: "互动消息加载失败" }
            loaded = true
            loading = false
        }
    }

    fun loadMore() {
        if (loading || nextBefore.isBlank()) return
        val cursor = nextBefore
        loading = true
        error = null
        scope.launch {
            runCatching { api.momentInteractions(token, cursor) }
                .onSuccess { (items, next) ->
                    val seen = interactions.map { it.id }.toSet()
                    interactions = interactions + items.filterNot { it.id in seen }
                    nextBefore = next
                    onViewed(items.map { it.id })
                }
                .onFailure { error = it.message ?: "加载更多互动失败" }
            loading = false
        }
    }

    LaunchedEffect(token) { refresh() }
    BackHandler(enabled = selected != null) { selected = null }

    selected?.let { item ->
        QumiInteractionDetail(item, token, onBack = { selected = null }, onOpenUser = onOpenUser)
        return
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().height(66.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xFFE5DFFF), Color(0xFFD9E7FF))))
            .padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = interactionInk) }
            Text("互动消息", Modifier.weight(1f), color = interactionInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { refresh() }, enabled = !loading) {
                Icon(Icons.Rounded.Refresh, "刷新互动消息", tint = interactionPurple)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "全部", "like" to "赞", "comment" to "评论", "reply" to "回复").forEach { (key, label) ->
                val active = filter == key
                Box(Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(if (active) interactionPurple else Color(0xFFF4F2F8))
                    .clickable { filter = key }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(label, color = if (active) Color.White else interactionMuted, fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        val visible = interactions.filter { filter == "all" || it.type == filter }
        when {
            loading && !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = interactionPurple)
            }
            error != null && interactions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { refresh() }) { Text("${error} · 点击重试", color = interactionPurple) }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (visible.isEmpty()) item {
                    Box(Modifier.fillMaxWidth().padding(top = 110.dp), contentAlignment = Alignment.Center) {
                        Text(if (filter == "all") "暂时没有互动消息" else "暂时没有${listOf("like" to "赞", "comment" to "评论", "reply" to "回复").first { it.first == filter }.second}消息",
                            color = interactionMuted, fontSize = 14.sp)
                    }
                }
                items(visible, key = { it.id }) { item ->
                    QumiInteractionRow(item, onClick = { selected = item },
                        onOpenUser = { onOpenUser(item.actorId.toString(), item.actorName) })
                    HorizontalDivider(color = Color(0xFFF2F0F5), modifier = Modifier.padding(start = 70.dp))
                }
                if (error != null && interactions.isNotEmpty()) item {
                    TextButton(onClick = { loadMore() }, modifier = Modifier.fillMaxWidth()) {
                        Text("${error} · 点击重试", color = interactionPurple)
                    }
                }
                if (nextBefore.isNotBlank()) item {
                    TextButton(onClick = { loadMore() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                        Text(if (loading) "正在加载…" else "加载更多", color = interactionPurple)
                    }
                }
            }
        }
    }
}

@Composable
private fun QumiInteractionRow(item: MomentInteraction, onClick: () -> Unit, onOpenUser: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.Top) {
        QumiUserAvatar(item.actorAvatar, item.actorName, Modifier.size(43.dp).clickable(onClick = onOpenUser))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.actorName, color = interactionInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("  ${interactionAction(item.type)}", color = interactionInk, fontSize = 14.sp)
            }
            if (!item.content.isNullOrBlank()) Text(item.content, color = interactionInk, fontSize = 13.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.post.content.ifBlank { if (item.post.mediaKind == "video") "视频" else if (item.post.mediaKind == "image") "图片" else "动态" },
                color = interactionMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.createdAt.replace('T', ' ').take(16), color = Color(0xFFB3B1BB), fontSize = 11.sp)
        }
        val tint = if (item.type == "like") Color(0xFFFF668B) else interactionPurple
        Box(Modifier.size(31.dp).clip(CircleShape).background(tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(when (item.type) {
                "like" -> Icons.Rounded.Favorite
                "reply" -> Icons.AutoMirrored.Rounded.Reply
                else -> Icons.Rounded.ChatBubbleOutline
            }, null, tint = tint, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun QumiInteractionDetail(item: MomentInteraction, token: String, onBack: () -> Unit,
    onOpenUser: (String, String) -> Unit) {
    var commentsVisible by remember(item.id) { mutableStateOf(false) }
    var commentCount by remember(item.id) { mutableStateOf(item.post.commentCount) }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().height(62.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xFFE5DFFF), Color(0xFFD9E7FF)))),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = interactionInk) }
            Text("互动详情", color = interactionInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QumiUserAvatar(item.actorAvatar, item.actorName, Modifier.size(42.dp).clickable {
                        onOpenUser(item.actorId.toString(), item.actorName)
                    })
                    Column {
                        Text("${item.actorName}${interactionAction(item.type)}", color = interactionInk,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(item.createdAt.replace('T', ' ').take(16), color = interactionMuted, fontSize = 12.sp)
                    }
                }
                if (!item.content.isNullOrBlank()) Text(item.content, Modifier.padding(start = 52.dp, top = 8.dp),
                    color = interactionInk, fontSize = 14.sp)
            }
            item { HorizontalDivider(color = Color(0xFFF0EEF3)) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    QumiUserAvatar(item.post.authorAvatar, item.post.username, Modifier.size(36.dp))
                    Text(item.post.username, color = interactionInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                if (item.post.content.isNotBlank()) Text(item.post.content, Modifier.padding(top = 12.dp),
                    color = interactionInk, fontSize = 15.sp)
                if (item.post.mediaPath != null) QumiMomentMedia(item.post, token,
                    Modifier.padding(top = 12.dp)
                        .width(if (item.post.mediaKind == "video") 120.dp else 200.dp))
                TextButton(onClick = { commentsVisible = true }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("查看评论  $commentCount", color = interactionPurple)
                }
            }
        }
    }
    if (commentsVisible) QumiMomentCommentsSheet(token, item.post.copy(commentCount = commentCount),
        onDismiss = { commentsVisible = false },
        onCountChanged = { commentCount = (commentCount + it).coerceAtLeast(0) })
}

private fun interactionAction(type: String) = when (type) {
    "like" -> "赞了你的发布"
    "reply" -> "回复了你的评论"
    else -> "评论了你的发布"
}
