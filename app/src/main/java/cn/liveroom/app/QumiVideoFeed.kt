package cn.liveroom.app

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val videoSamples = listOf(
    R.drawable.video_placeholder_1,
    R.drawable.video_placeholder_2,
    R.drawable.video_placeholder_3,
    R.drawable.video_placeholder_4,
)
private val videoPurple = Color(0xFF7044D8)

@Composable
internal fun QumiVideoFeed(token: String, currentUserId: Long, liveCreatorIds: Set<Long>,
    onOpenUser: (String, String) -> Unit, onGoMoments: () -> Unit,
    onOpenLive: () -> Unit, onOpenCreatorLive: (Long) -> Unit,
    openFirstSignal: Int = 0, consumedOpenSignal: Int = 0,
    onOpenFirstConsumed: (Int) -> Unit = {}, closeViewerSignal: Int = 0,
    showGrid: Boolean = true,
    onViewerOpened: () -> Unit = {}, onViewerUnavailable: () -> Unit = {}) {
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()
    var posts by remember(token) { mutableStateOf(emptyList<MomentPost>()) }
    var nextCursor by remember(token) { mutableStateOf("") }
    var loading by remember(token) { mutableStateOf(false) }
    var loadedOnce by remember(token) { mutableStateOf(false) }
    var error by remember(token) { mutableStateOf<String?>(null) }
    var openId by remember(token) { mutableStateOf<String?>(null) }
    var busyLike by remember { mutableStateOf<String?>(null) }
    var busyFavorite by remember { mutableStateOf<String?>(null) }
    var busyFollow by remember { mutableStateOf<Long?>(null) }
    var followNotice by remember { mutableStateOf<String?>(null) }
    var commentsFor by remember { mutableStateOf<MomentPost?>(null) }

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            runCatching { api.moments(token, "video") }
                .onSuccess { (items, cursor) ->
                    posts = items.filter { it.mediaKind == "video" && it.mediaPath != null }
                    nextCursor = cursor
                }
                .onFailure { error = it.message ?: "视频加载失败" }
            loadedOnce = true
            loading = false
        }
    }
    fun loadMore() {
        val cursor = nextCursor
        if (loading || cursor.isBlank()) return
        loading = true
        scope.launch {
            runCatching { api.moments(token, "video", cursor) }
                .onSuccess { (items, next) ->
                    val knownIds = posts.map { it.id }.toSet()
                    posts = posts + items.filter { it.mediaKind == "video" && it.mediaPath != null && it.id !in knownIds }
                    nextCursor = next
                    error = null
                }
                .onFailure { error = it.message ?: "更多视频加载失败" }
            loading = false
        }
    }
    fun toggleLike(post: MomentPost) {
        if (busyLike == post.id) return
        busyLike = post.id
        scope.launch {
            runCatching { api.likeMoment(token, post.id, !post.isLiked) }
                .onSuccess {
                    posts = posts.map { item -> if (item.id == post.id) item.copy(
                        isLiked = !post.isLiked,
                        likeCount = (item.likeCount + if (post.isLiked) -1 else 1).coerceAtLeast(0),
                    ) else item }
                }
                .onFailure { error = it.message ?: "点赞失败" }
            busyLike = null
        }
    }
    fun toggleFavorite(post: MomentPost) {
        if (busyFavorite == post.id) return
        busyFavorite = post.id
        scope.launch {
            runCatching { api.favoriteMoment(token, post.id, !post.isFavorited) }
                .onSuccess {
                    posts = posts.map { item -> if (item.id == post.id) item.copy(
                        isFavorited = !post.isFavorited,
                        favoriteCount = (item.favoriteCount + if (post.isFavorited) -1 else 1).coerceAtLeast(0),
                    ) else item }
                }
                .onFailure { error = it.message ?: "收藏失败" }
            busyFavorite = null
        }
    }
    fun toggleFollow(post: MomentPost) {
        if (post.userId == currentUserId || busyFollow == post.userId) return
        busyFollow = post.userId
        scope.launch {
            runCatching { api.setFollowing(token, post.userId, !post.isFollowing) }
                .onSuccess { followNotice = if (post.isFollowing) "已取消关注" else "已关注"
                    posts = posts.map { item -> if (item.userId == post.userId)
                    item.copy(isFollowing = !post.isFollowing) else item } }
                .onFailure { error = it.message ?: "关注失败" }
            busyFollow = null
        }
    }
    LaunchedEffect(token) { refresh() }
    LaunchedEffect(openFirstSignal, posts, loadedOnce) {
        if (openFirstSignal > consumedOpenSignal && loadedOnce) {
            onOpenFirstConsumed(openFirstSignal)
            if (posts.isEmpty()) onViewerUnavailable()
            else {
                openId = posts.first().id
                withFrameNanos { }
                onViewerOpened()
            }
        }
    }
    LaunchedEffect(closeViewerSignal) {
        if (closeViewerSignal > 0) {
            commentsFor = null
            openId = null
        }
    }

    if (showGrid) LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("精选视频", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = LiveStyle.ink)
                IconButton(onClick = { refresh() }, enabled = !loading) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新视频", tint = videoPurple)
                }
            }
        }
        if (loading && posts.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            CircularProgressIndicator(Modifier.padding(28.dp), color = videoPurple)
        }
        if (!loading && posts.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(error ?: "暂无可播放视频", color = LiveStyle.ink, fontSize = 16.sp)
                Text("主播发布视频后会显示在这里", color = LiveStyle.muted, fontSize = 13.sp)
                TextButton(onClick = onGoMoments) { Text("去动态页看看", color = videoPurple) }
            }
        }
        items(posts, key = { it.id }) { post ->
            Box(Modifier.fillMaxWidth().aspectRatio(.78f).clip(RoundedCornerShape(6.dp))
                .clickable { openId = post.id }) {
                VideoPoster(post.mediaPath!!, token, posts.indexOf(post), Modifier.fillMaxSize())
                Box(Modifier.fillMaxWidth().height(90.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .8f)))))
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp)
                    .clip(CircleShape).background(Color.Black.copy(alpha = .42f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "播放视频", tint = Color.White,
                        modifier = Modifier.size(17.dp))
                }
                Column(Modifier.align(Alignment.BottomStart).padding(11.dp)) {
                    Text(post.username, color = Color.White, fontSize = 12.sp)
                    Text(post.content.ifBlank { "视频动态" }, color = Color.White, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (loading && posts.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = videoPurple)
        }
        if (error != null && posts.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            TextButton(onClick = { if (nextCursor.isBlank()) refresh() else loadMore() }, modifier = Modifier.fillMaxWidth()) {
                Text("${error} · 重试")
            }
        }
        if (nextCursor.isNotBlank() && !loading) item(span = { GridItemSpan(maxLineSpan) }) {
            TextButton(onClick = { loadMore() }, modifier = Modifier.fillMaxWidth()) { Text("加载更多") }
        }
    }

    openId?.let { selectedId ->
        if (posts.isNotEmpty()) {
            Dialog(onDismissRequest = { commentsFor = null; openId = null },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                val startPage = posts.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
                val pager = rememberPagerState(initialPage = startPage,
                    pageCount = { posts.size })
                var showSwipeHint by remember(selectedId) { mutableStateOf(true) }
                LaunchedEffect(pager.currentPage) {
                    if (pager.currentPage != startPage) showSwipeHint = false
                }
                LaunchedEffect(pager.currentPage, posts.size, nextCursor) {
                    if (pager.currentPage >= posts.lastIndex - 2 && nextCursor.isNotBlank()) loadMore()
                }
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    val commentsVisible = commentsFor != null
                    VerticalPager(state = pager, modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !commentsVisible) { page ->
                        val post = posts[page]
                        Box(Modifier.fillMaxSize()) {
                            if (pager.currentPage == page) {
                                FeedVideoPlayer(post.mediaPath!!, token, !commentsVisible,
                                    if (commentsVisible) Modifier.fillMaxWidth().fillMaxHeight(.23f)
                                        .align(Alignment.TopCenter).padding(top = 24.dp)
                                    else Modifier.fillMaxSize())
                            } else {
                                VideoPoster(post.mediaPath!!, token, page, Modifier.fillMaxSize())
                            }
                            if (!commentsVisible) {
                                Box(Modifier.fillMaxWidth().height(200.dp).align(Alignment.BottomCenter)
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f)))))
                                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(.75f).padding(start = 18.dp, bottom = 42.dp)) {
                                    Text("@${post.username}", Modifier.clickable {
                                        commentsFor = null
                                        openId = null
                                        onOpenUser(post.userId.toString(), post.username)
                                    },
                                        color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    if (post.content.isNotBlank()) Text(post.content, Modifier.padding(top = 8.dp),
                                        color = Color.White, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                Column(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 96.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    val authorLive = post.userId in liveCreatorIds
                                    Box(Modifier.size(64.dp).then(if (authorLive) Modifier.clickable {
                                        onOpenCreatorLive(post.userId)
                                    } else Modifier), contentAlignment = Alignment.Center) {
                                        QumiUserAvatar(post.authorAvatar, post.username,
                                            Modifier.size(50.dp).border(2.dp,
                                                if (authorLive) Color(0xFFFF3D78) else Color.White, CircleShape)
                                                .then(if (authorLive) Modifier else Modifier.clickable {
                                                commentsFor = null
                                                openId = null
                                                onOpenUser(post.userId.toString(), post.username)
                                            }))
                                        if (authorLive) Text("直播", Modifier.align(Alignment.TopCenter)
                                            .clip(RoundedCornerShape(5.dp)).background(Color(0xFFFF3D78))
                                            .padding(horizontal = 5.dp, vertical = 1.dp),
                                            color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        else if (post.userId != currentUserId) Box(
                                            Modifier.align(Alignment.BottomCenter).size(24.dp).clip(CircleShape)
                                                .background(videoPurple)
                                                .clickable(enabled = busyFollow != post.userId) { toggleFollow(post) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(if (post.isFollowing) Icons.Rounded.Done else Icons.Rounded.Add,
                                                contentDescription = if (post.isFollowing) "取消关注" else "关注",
                                                tint = Color.White, modifier = Modifier.size(19.dp))
                                        }
                                    }
                                    VideoAction(if (post.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        "点赞", post.likeCount.toString(), if (post.isLiked) Color(0xFFFF4A7F) else Color.White,
                                        busyLike != post.id) { toggleLike(post) }
                                    VideoAction(Icons.Rounded.Star,
                                        "收藏", post.favoriteCount.toString(), if (post.isFavorited) Color(0xFFFFD54F) else Color.White,
                                        busyFavorite != post.id) { toggleFavorite(post) }
                                    VideoAction(Icons.Rounded.ChatBubbleOutline, "评论", post.commentCount.toString(), Color.White, true) {
                                        commentsFor = post
                                    }
                                }
                            }
                        }
                    }
                    if (!commentsVisible) {
                        IconButton(onClick = { commentsFor = null; openId = null },
                            modifier = Modifier.align(Alignment.TopStart).padding(top = 8.dp, start = 10.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回视频列表", tint = Color.White)
                        }
                        Row(Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("视频", Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("直播", Modifier.clickable {
                                onOpenLive()
                            }.padding(horizontal = 12.dp, vertical = 5.dp),
                                color = Color.White.copy(alpha = .72f), fontSize = 15.sp)
                        }
                        if (showSwipeHint && posts.size > 1) Text("上滑看下一个视频",
                            Modifier.align(Alignment.TopCenter).padding(top = 62.dp),
                            color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                        error?.let { Text(it, Modifier.align(Alignment.TopCenter).padding(top = 80.dp),
                            color = Color(0xFFFFB9C8), fontSize = 12.sp) }
                    }
                    commentsFor?.let { post -> QumiMomentCommentsSheet(token, post,
                        onDismiss = { commentsFor = null },
                        heightFraction = .72f,
                        onCountChanged = { delta -> posts = posts.map { item ->
                            if (item.id == post.id) item.copy(commentCount = (item.commentCount + delta).coerceAtLeast(0)) else item
                        } }) }
                    QumiActionNotice(followNotice) { followNotice = null }
                }
            }
        }
    }
}

@Composable
private fun VideoAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, count: String,
    tint: Color, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = .36f))) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(28.dp))
        }
        Text(count, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
@Suppress("ProduceStateDoesNotAssignValue") // The poster producer explicitly assigns value.
internal fun VideoPoster(path: String, token: String, index: Int, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, path, token) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource("${BuildConfig.BILLD_API_URL.trimEnd('/')}$path",
                        mapOf("Authorization" to "Bearer $token"))
                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally { retriever.release() }
            }.getOrNull()
        }
    }
    Box(modifier.background(Color(0xFF24202F))) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Image(painterResource(videoSamples[index % videoSamples.size]), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun FeedVideoPlayer(path: String, token: String, canPlay: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var paused by remember(path) { mutableStateOf(false) }
    var playbackError by remember(path) { mutableStateOf(false) }
    val player = remember(path, token) {
        val http = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
            .setAllowCrossProtocolRedirects(false)
        ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http)).build().apply {
                setMediaItem(MediaItem.fromUri("${BuildConfig.BILLD_API_URL.trimEnd('/')}$path"))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
            }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { playbackError = true }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(player, paused, canPlay) { player.playWhenReady = canPlay && !paused }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(factory = { viewContext -> PlayerView(viewContext).apply {
            this.player = player
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        } },
            update = { it.player = player }, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().clickable { paused = !paused })
        if (playbackError) TextButton(onClick = { playbackError = false; paused = false; player.prepare() }) {
            Text("视频加载失败，点击重试", color = Color.White)
        } else if (paused && canPlay) Icon(Icons.Rounded.PlayArrow, contentDescription = "继续播放",
            tint = Color.White, modifier = Modifier.size(66.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = .38f)).clickable { paused = false })
    }
}
