package cn.liveroom.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val savedPurple = Color(0xFF7044D8)

@Composable
internal fun QumiMySavedScreen(token: String, likes: Boolean, onBack: () -> Unit,
    onOpenUser: (String, String) -> Unit) {
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()
    var selectedTab by remember(likes) { mutableIntStateOf(0) }
    val queryScope = when {
        !likes -> "favorite_video"
        selectedTab == 0 -> "liked_post"
        else -> "liked_video"
    }
    var posts by remember(token, queryScope) { mutableStateOf(emptyList<MomentPost>()) }
    var nextCursor by remember(token, queryScope) { mutableStateOf("") }
    var loading by remember(token, queryScope) { mutableStateOf(true) }
    var loadingMore by remember(token, queryScope) { mutableStateOf(false) }
    var error by remember(token, queryScope) { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var commentsFor by remember { mutableStateOf<MomentPost?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var playingPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(token, queryScope, refresh) {
        loading = true
        error = null
        try {
            val (items, cursor) = api.moments(token, queryScope)
            posts = items
            nextCursor = cursor
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "内容加载失败"
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF7F6FB))) {
        Row(Modifier.fillMaxWidth().height(60.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xFFE8DFFF), Color(0xFFDDE8FF)))),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = Color(0xFF242130))
            }
            Text(if (likes) "我的点赞" else "我的收藏", Modifier.weight(1f),
                color = Color(0xFF242130), fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
            IconButton(onClick = { refresh++ }, enabled = !loading && !loadingMore) {
                Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = savedPurple)
            }
        }
        if (likes) Row(Modifier.fillMaxWidth().background(Color.White)) {
            val tabs = listOf("动态", "视频")
            tabs.forEachIndexed { index, label ->
                Column(Modifier.weight(1f).height(48.dp).clickable { selectedTab = index },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom) {
                    Text(label, color = if (selectedTab == index) savedPurple else Color(0xFF8A8791),
                        fontSize = 16.sp,
                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal)
                    Spacer(Modifier.height(9.dp))
                    Box(Modifier.width(31.dp).height(3.dp).clip(CircleShape)
                        .background(if (selectedTab == index) savedPurple else Color.Transparent))
                }
            }
        }
        if (!likes) LazyVerticalGrid(columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (posts.isEmpty() && loading) item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = savedPurple)
                }
            }
            if (posts.isEmpty() && !loading && error == null) item(span = { GridItemSpan(maxLineSpan) }) {
                Text("还没有收藏视频", Modifier.fillMaxWidth().padding(top = 85.dp),
                    color = Color(0xFF9D99A5), fontSize = 15.sp, textAlign = TextAlign.Center)
            }
            gridItems(posts, key = { it.id }) { post ->
                val path = post.mediaPath
                if (path != null) Box(Modifier.fillMaxWidth().aspectRatio(.78f)
                    .clickable { playingPath = path }) {
                    VideoPoster(path, token, posts.indexOf(post), Modifier.fillMaxSize())
                    Box(Modifier.fillMaxWidth().height(74.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .72f)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(9.dp)) {
                        Text(post.content.ifBlank { post.username }, color = Color.White,
                            fontSize = 13.sp, maxLines = 2, fontWeight = FontWeight.SemiBold)
                        Text("★ ${post.favoriteCount}", color = Color.White.copy(alpha = .9f), fontSize = 11.sp)
                    }
                }
            }
            error?.let { message -> item(span = { GridItemSpan(maxLineSpan) }) {
                TextButton(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth()) {
                    Text("$message · 重试", color = savedPurple)
                }
            } }
            if (nextCursor.isNotBlank() && !loading) item(span = { GridItemSpan(maxLineSpan) }) {
                TextButton(onClick = {
                    if (loadingMore) return@TextButton
                    val before = nextCursor
                    loadingMore = true
                    scope.launch {
                        try {
                            val (items, cursor) = api.moments(token, queryScope, before)
                            val knownIds = posts.map { it.id }.toSet()
                            posts = posts + items.filterNot { it.id in knownIds }
                            nextCursor = cursor
                        } catch (failure: Exception) {
                            error = failure.message ?: "加载更多失败"
                        } finally { loadingMore = false }
                    }
                }, enabled = !loadingMore, modifier = Modifier.fillMaxWidth()) {
                    Text(if (loadingMore) "正在加载…" else "加载更多", color = savedPurple)
                }
            }
        } else LazyColumn(Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (posts.isEmpty() && loading) item {
                Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = savedPurple)
                }
            }
            if (posts.isEmpty() && !loading && error == null) item {
                val emptyText = when {
                    !likes -> "还没有收藏视频"
                    selectedTab == 0 -> "还没有点赞动态"
                    else -> "还没有点赞视频"
                }
                Text(emptyText, Modifier.fillMaxWidth().padding(top = 85.dp),
                    color = Color(0xFF9D99A5), fontSize = 15.sp, textAlign = TextAlign.Center)
            }
            items(posts, key = { it.id }) { post ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Color.White).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QumiUserAvatar(post.authorAvatar, post.username,
                            Modifier.size(36.dp).clickable { onOpenUser(post.userId.toString(), post.username) })
                        Column(Modifier.weight(1f).padding(start = 9.dp)
                            .clickable { onOpenUser(post.userId.toString(), post.username) }) {
                            Text(post.username, color = Color(0xFF282530), fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold)
                            Text(post.createdAt.replace('T', ' ').take(16),
                                color = Color(0xFF9995A2), fontSize = 11.sp)
                        }
                    }
                    if (post.content.isNotBlank()) Text(post.content,
                        color = Color(0xFF282530), fontSize = 15.sp, lineHeight = 21.sp)
                    if (post.mediaPath != null) QumiMomentMedia(post, token,
                        Modifier.width(if (post.mediaKind == "video") 120.dp else 170.dp))
                    HorizontalDivider(color = Color(0xFFF0EFF3))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { commentsFor = post }, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
                            Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null,
                                modifier = Modifier.size(17.dp), tint = Color(0xFF8F8B98))
                            Text("评论 ${post.commentCount}", color = Color(0xFF8F8B98), fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            if (busyId != null) return@TextButton
                            busyId = post.id
                            scope.launch {
                                try {
                                    if (likes) api.likeMoment(token, post.id, false)
                                    else api.favoriteMoment(token, post.id, false)
                                    posts = posts.filterNot { it.id == post.id }
                                } catch (failure: Exception) {
                                    error = failure.message ?: "操作失败，请重试"
                                } finally {
                                    busyId = null
                                }
                            }
                        }, enabled = busyId == null, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
                            Icon(if (likes) Icons.Rounded.Favorite else Icons.Rounded.Star,
                                contentDescription = null, modifier = Modifier.size(18.dp),
                                tint = if (likes) Color(0xFFE94B70) else Color(0xFFE4AD18))
                            Text(if (likes) "取消点赞" else "取消收藏", color = savedPurple, fontSize = 13.sp)
                        }
                    }
                }
            }
            error?.let { message -> item {
                TextButton(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth()) {
                    Text("$message · 重试", color = savedPurple)
                }
            } }
            if (nextCursor.isNotBlank() && !loading) item {
                TextButton(onClick = {
                    if (loadingMore) return@TextButton
                    val before = nextCursor
                    loadingMore = true
                    scope.launch {
                        try {
                            val (items, cursor) = api.moments(token, queryScope, before)
                            val knownIds = posts.map { it.id }.toSet()
                            posts = posts + items.filterNot { it.id in knownIds }
                            nextCursor = cursor
                        } catch (failure: Exception) {
                            error = failure.message ?: "加载更多失败"
                        } finally {
                            loadingMore = false
                        }
                    }
                }, enabled = !loadingMore, modifier = Modifier.fillMaxWidth()) {
                    Text(if (loadingMore) "正在加载…" else "加载更多", color = savedPurple)
                }
            }
        }
    }
    commentsFor?.let { post ->
        QumiMomentCommentsSheet(token, post,
            onDismiss = { commentsFor = null },
            onCountChanged = { delta ->
                posts = posts.map { item -> if (item.id == post.id)
                    item.copy(commentCount = (item.commentCount + delta).coerceAtLeast(0)) else item }
            })
    }
    playingPath?.let { path -> QumiMomentVideoViewer(path, token) { playingPath = null } }
}
