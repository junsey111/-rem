package cn.liveroom.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
internal fun QumiMomentDetailScreen(
    token: String,
    post: MomentPost,
    currentUserId: Long,
    onDismiss: () -> Unit,
    onPostChanged: (MomentPost) -> Unit,
    onOpenUser: () -> Unit,
    onMessageUser: () -> Unit,
) {
    var comments by remember(post.id) { mutableStateOf(emptyList<MomentComment>()) }
    var replies by remember(post.id) { mutableStateOf<Map<String, List<MomentComment>>>(emptyMap()) }
    var expanded by remember(post.id) { mutableStateOf(setOf<String>()) }
    var nextCursor by remember(post.id) { mutableStateOf("") }
    var loading by remember(post.id) { mutableStateOf(true) }
    var busy by remember(post.id) { mutableStateOf(false) }
    var likeBusy by remember(post.id) { mutableStateOf(false) }
    var error by remember(post.id) { mutableStateOf<String?>(null) }
    var draft by remember(post.id) { mutableStateOf("") }
    var replyTarget by remember(post.id) { mutableStateOf<MomentComment?>(null) }
    var deleteTarget by remember(post.id) { mutableStateOf<MomentComment?>(null) }
    var reload by remember(post.id) { mutableIntStateOf(0) }
    val now by rememberMomentNow()
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(token, post.id, reload) {
        loading = true
        runCatching { api.momentComments(token, post.id) }
            .onSuccess { (items, cursor) -> comments = items; nextCursor = cursor; error = null }
            .onFailure { error = it.message ?: "评论加载失败" }
        loading = false
    }

    fun sendComment() {
        val content = draft.trim()
        if (content.isEmpty() || busy) return
        busy = true
        val target = replyTarget
        scope.launch {
            runCatching { api.publishMomentComment(token, post.id, content, target?.id) }
                .onSuccess { created ->
                    if (created.parentId == null) comments = listOf(created) + comments
                    else {
                        val root = created.parentId
                        replies = replies + (root to (replies[root].orEmpty() + created))
                        comments = comments.map { if (it.id == root) it.copy(replyCount = it.replyCount + 1) else it }
                        expanded = expanded + root
                    }
                    onPostChanged(post.copy(commentCount = post.commentCount + 1))
                    draft = ""
                    replyTarget = null
                    error = null
                }
                .onFailure { error = it.message ?: "评论发送失败" }
            busy = false
        }
    }

    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
            Box(Modifier.fillMaxWidth().height(250.dp)
                .background(Brush.horizontalGradient(listOf(Color(0xFFE3DDFF), Color(0xFFD7E9FF))))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.White))))
        Column(Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回动态")
                }
                Text("动态详情", Modifier.weight(1f), textAlign = TextAlign.Center,
                    color = LiveStyle.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(48.dp))
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Surface(color = Color.White, shape = RoundedCornerShape(22.dp), shadowElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QumiUserAvatar(post.authorAvatar, post.username,
                            Modifier.size(44.dp).clickable(onClick = onOpenUser))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(post.username, color = LiveStyle.ink, fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold)
                            Text(momentCommentTime(post.createdAt, now), color = LiveStyle.muted, fontSize = 12.sp)
                        }
                        if (post.userId != currentUserId) TextButton(onClick = onMessageUser,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF7951ED), containerColor = Color(0xFFF2ECFF)),
                            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 4.dp), shape = CircleShape) { Text("私信", fontSize = 13.sp) }
                    }
                if (post.content.isNotBlank()) {
                    Text(post.content, color = LiveStyle.ink, fontSize = 16.sp, lineHeight = 24.sp)
                }
                if (post.mediaPath != null) {
                    QumiMomentMedia(post, token, Modifier.width(180.dp))
                }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = !likeBusy, onClick = {
                            likeBusy = true
                            scope.launch {
                                runCatching { api.likeMoment(token, post.id, !post.isLiked) }
                                    .onSuccess { onPostChanged(post.copy(isLiked = !post.isLiked,
                                        likeCount = (post.likeCount + if (post.isLiked) -1 else 1).coerceAtLeast(0))) }
                                    .onFailure { error = it.message ?: "点赞失败" }
                                likeBusy = false
                            }
                        }) {
                            Icon(if (post.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "点赞", tint = if (post.isLiked) LiveStyle.live else LiveStyle.muted,
                                modifier = Modifier.size(20.dp))
                            Text(" ${post.likeCount}", color = LiveStyle.muted)
                        }
                        Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = "评论",
                            tint = LiveStyle.muted, modifier = Modifier.size(20.dp))
                        Text(" ${post.commentCount}", color = LiveStyle.muted)
                    }
                    }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("全部评论", Modifier.weight(1f), color = LiveStyle.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("${post.commentCount} 条", color = Color(0xFF9A85B3), fontSize = 12.sp)
                    }
                }
                if (loading && comments.isEmpty()) item {
                    Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LiveStyle.accent)
                    }
                }
                if (!loading && comments.isEmpty() && error == null) item {
                    Text("暂无评论", Modifier.fillMaxWidth().padding(vertical = 55.dp),
                        color = LiveStyle.muted, textAlign = TextAlign.Center)
                }
                if (error != null) item {
                    TextButton(onClick = { reload++ }) { Text("${error} · 点击重试") }
                }
                items(comments, key = { it.id }) { comment ->
                    MomentDetailComment(comment, now, onReply = { replyTarget = comment },
                        onDelete = { deleteTarget = comment })
                    if (comment.replyCount > 0 || comment.id in expanded) TextButton(onClick = {
                        if (comment.id in expanded) expanded = expanded - comment.id
                        else {
                            expanded = expanded + comment.id
                            scope.launch {
                                runCatching { api.momentReplies(token, post.id, comment.id) }
                                    .onSuccess { (items, _) -> replies = replies + (comment.id to items) }
                                    .onFailure { error = it.message ?: "回复加载失败" }
                            }
                        }
                    }) { Text(if (comment.id in expanded) "收起回复" else "查看 ${comment.replyCount} 条回复") }
                    if (comment.id in expanded) replies[comment.id].orEmpty().forEach { reply ->
                        MomentDetailComment(reply, now, Modifier.padding(start = 38.dp),
                            onReply = { replyTarget = reply }, onDelete = { deleteTarget = reply })
                    }
                }
                if (nextCursor.isNotBlank()) item {
                    TextButton(enabled = !loading, onClick = {
                        loading = true
                        scope.launch {
                            runCatching { api.momentComments(token, post.id, nextCursor) }
                                .onSuccess { (items, cursor) -> comments = comments + items; nextCursor = cursor }
                                .onFailure { error = it.message ?: "加载更多评论失败" }
                            loading = false
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("加载更多评论") }
                }
            }
            replyTarget?.let { target ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("回复 @${target.username}", Modifier.weight(1f), color = LiveStyle.accent, fontSize = 12.sp)
                    TextButton(onClick = { replyTarget = null }) { Text("取消") }
                }
            }
            Surface(color = Color.White, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp), shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 42.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = draft, onValueChange = { draft = it.take(500) },
                    placeholder = { Text("说点什么…") }, singleLine = true,
                    modifier = Modifier.weight(1f), shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color(0xFFF7F3FD), focusedContainerColor = Color(0xFFF7F3FD),
                        focusedBorderColor = Color(0xFF7951ED), unfocusedBorderColor = Color(0xFFECE4F6), cursorColor = Color(0xFF7951ED)))
                Spacer(Modifier.width(10.dp))
                Button(onClick = { sendComment() }, enabled = draft.isNotBlank() && !busy,
                    shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7951ED))) { Text("发送") }
            }
            }
        }
        }
    }
    deleteTarget?.let { comment -> AlertDialog(onDismissRequest = { deleteTarget = null },
        title = { Text("删除这条评论？") }, text = { Text("删除后无法恢复。") },
        confirmButton = { TextButton(onClick = {
            busy = true
            scope.launch {
                runCatching { api.deleteMomentComment(token, post.id, comment.id) }
                    .onSuccess { removed ->
                        if (comment.parentId == null) comments = comments.filterNot { it.id == comment.id }
                        else replies = replies + (comment.parentId to replies[comment.parentId].orEmpty().filterNot { it.id == comment.id })
                        onPostChanged(post.copy(commentCount = (post.commentCount - removed).coerceAtLeast(0)))
                        deleteTarget = null
                    }
                    .onFailure { error = it.message ?: "删除失败" }
                busy = false
            }
        }, enabled = !busy) { Text("删除", color = LiveStyle.live) } },
        dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
    ) }
}

@Composable
private fun MomentDetailComment(comment: MomentComment, now: java.time.Instant,
    modifier: Modifier = Modifier, onReply: () -> Unit, onDelete: () -> Unit) {
    Surface(modifier.fillMaxWidth(), color = Color.White.copy(alpha = .9f), shape = RoundedCornerShape(16.dp)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        QumiUserAvatar(comment.avatar, comment.username, Modifier.size(34.dp).offset(y = (-7).dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(comment.username, color = LiveStyle.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            comment.replyToUsername?.takeIf { it.isNotBlank() && it != "null" }?.let {
                Text("回复 @$it", color = LiveStyle.accent, fontSize = 12.sp)
            }
            Text(comment.content, color = LiveStyle.ink, fontSize = 14.sp)
            Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(momentCommentTime(comment.createdAt, now), color = LiveStyle.muted,
                    fontSize = 11.sp, lineHeight = 16.sp)
                Text("回复", Modifier.clickable(onClick = onReply).padding(start = 5.dp, end = 2.dp),
                    color = LiveStyle.accent, fontSize = 11.sp, lineHeight = 16.sp)
                if (comment.isMine) Text("删除", Modifier.clickable(onClick = onDelete).padding(start = 2.dp, end = 5.dp),
                    color = LiveStyle.muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
    }
}
