package cn.liveroom.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val DiscoveryInk = LiveStyle.ink
private val DiscoveryMuted = LiveStyle.muted
private val DiscoveryPurple = LiveStyle.accent
private val DiscoveryAccent = Color(0xFF7044D8)
private val DiscoveryCanvas = LiveStyle.canvas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QumiFriendsScreen(
    token: String,
    section: Int,
    filter: Int,
    onSearch: () -> Unit,
    onOpenUser: (String, String) -> Unit,
    onMessageUser: (String, String) -> Unit,
    onBlockUser: (Long) -> Unit,
) {
    var showFilter by remember { mutableStateOf(false) }
    var gender by remember { mutableIntStateOf(0) }
    var age by remember { mutableIntStateOf(0) }
    var region by remember { mutableIntStateOf(0) }
    var appliedGender by remember { mutableIntStateOf(0) }
    var appliedAge by remember { mutableIntStateOf(0) }
    var appliedRegion by remember { mutableIntStateOf(0) }
    var users by remember { mutableStateOf(emptyList<PublicUser>()) }
    var followingUsers by remember { mutableStateOf(emptyList<PublicUser>()) }
    var followingCursor by remember { mutableStateOf("") }
    var followingLoadingMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var followBusyId by remember { mutableStateOf<Long?>(null) }
    var unfollowingId by remember { mutableStateOf<Long?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(token, section, filter, reload, appliedGender, appliedAge, appliedRegion) {
        loading = true
        error = null
        if (section == 2) {
            runCatching { api.relations(token, "following") }
                .onSuccess { page -> followingUsers = page.users; followingCursor = page.nextCursor }
                .onFailure { error = it.message ?: "关注列表加载失败"; followingUsers = emptyList(); followingCursor = "" }
        } else {
            val ages = when (appliedAge) { 1 -> 18 to 25; 2 -> 26 to 35; 3 -> 36 to 120; else -> null }
            runCatching { api.recommendedUsers(token,
                gender = when (appliedGender) { 1 -> "MALE"; 2 -> "FEMALE"; else -> null },
                ageMin = ages?.first, ageMax = ages?.second, sameRegion = appliedRegion == 1 || filter == 1) }
                .onSuccess { users = it }
                .onFailure { error = it.message ?: "用户列表加载失败"; users = emptyList() }
        }
        loading = false
    }
    fun loadFollowingMore() {
        val cursor = followingCursor
        if (cursor.isBlank() || loading || followingLoadingMore) return
        followingLoadingMore = true
        error = null
        scope.launch {
            runCatching { api.relations(token, "following", cursor) }
                .onSuccess { page ->
                    followingUsers = followingUsers + page.users.filterNot { user -> followingUsers.any { it.id == user.id } }
                    followingCursor = page.nextCursor
                }
                .onFailure { error = it.message ?: "加载更多关注失败" }
            followingLoadingMore = false
        }
    }
    Column(Modifier.fillMaxSize().background(DiscoveryCanvas)) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (section == 0) "推荐" else if (section == 1) "交友" else "专属", Modifier.weight(1f), color = DiscoveryInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (section == 0) TextButton(onClick = { showFilter = true }) {
                Icon(Icons.Rounded.Tune, null, tint = DiscoveryInk)
                Spacer(Modifier.width(5.dp)); Text("筛选", color = DiscoveryInk, fontSize = 16.sp)
            }
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "搜索用户", tint = DiscoveryInk) }
        }
        if (section != 2 && filter == 1) Text("同城推荐 · 只展示已填写地区的用户", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = DiscoveryMuted, fontSize = 12.sp)
        Image(painterResource(R.drawable.qumi_home_banner), "平台活动", contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(132.dp).clip(RoundedCornerShape(14.dp)))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DiscoveryPurple) }
            error != null && (section != 2 || followingUsers.isEmpty()) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TextButton(onClick = { reload++ }) { Text("${error} · 点击重试") } }
            section == 2 && followingUsers.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.FavoriteBorder, null, tint = LiveStyle.muted, modifier = Modifier.size(46.dp))
                    Text("还没有关注的人", Modifier.padding(top = 12.dp), color = DiscoveryInk, fontSize = 17.sp)
                    Text("关注用户后，他们会显示在这里", Modifier.padding(top = 6.dp), color = DiscoveryMuted, fontSize = 13.sp)
                }
            }
            section == 2 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(QumiGrid.itemGap)) {
                items(followingUsers, key = { it.id }) { user ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(LiveStyle.accentSoft), contentAlignment = Alignment.Center) {
                            Text(user.username.take(1), color = DiscoveryPurple, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp).clickable { onOpenUser(user.id.toString(), user.username) }) {
                            Text(user.username, color = DiscoveryInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("ID ${user.id}", color = DiscoveryMuted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { onMessageUser(user.id.toString(), user.username) }) { Text("私信") }
                        TextButton(enabled = unfollowingId == null, onClick = {
                            unfollowingId = user.id
                            scope.launch {
                                runCatching { api.setFollowing(token, user.id, false) }
                                    .onSuccess { followingUsers = followingUsers.filterNot { it.id == user.id } }
                                    .onFailure { error = it.message ?: "取消关注失败，请重试" }
                                unfollowingId = null
                            }
                        }) { Text(if (unfollowingId == user.id) "处理中" else "已关注", color = DiscoveryPurple) }
                    }
                    HorizontalDivider(color = Color(0xFFF1F1F4))
                }
                if (error != null) item {
                    TextButton(onClick = { loadFollowingMore() }, modifier = Modifier.fillMaxWidth()) { Text("${error} · 点击重试") }
                } else if (followingCursor.isNotBlank()) item {
                    TextButton(enabled = !followingLoadingMore, onClick = { loadFollowingMore() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (followingLoadingMore) "正在加载…" else "加载更多")
                    }
                }
            }
            users.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.FavoriteBorder, null, tint = LiveStyle.muted, modifier = Modifier.size(46.dp))
                    Text("暂时没有可推荐的用户", Modifier.padding(top = 12.dp), color = DiscoveryInk, fontSize = 17.sp)
                    OutlinedButton(onClick = onSearch, modifier = Modifier.padding(top = 10.dp)) { Text("按 ID 或昵称搜索") }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(QumiGrid.itemGap)) {
                items(users, key = { it.id }) { user ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenUser(user.id.toString(), user.username) }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(LiveStyle.accentSoft), contentAlignment = Alignment.Center) {
                            Text(user.username.take(1), color = DiscoveryPurple, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(user.username, color = DiscoveryInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("ID ${user.id}${user.description?.let { " · $it" } ?: ""}", color = DiscoveryMuted, fontSize = 12.sp, maxLines = 1)
                        }
                        TextButton(enabled = followBusyId == null, onClick = {
                            followBusyId = user.id
                            scope.launch {
                                runCatching { api.setFollowing(token, user.id, !user.isFollowing) }
                                    .onSuccess { users = users.map { if (it.id == user.id) it.copy(isFollowing = !user.isFollowing) else it } }
                                    .onFailure { error = it.message ?: "关注失败，请重试" }
                                followBusyId = null
                            }
                        }) { Text(if (followBusyId == user.id) "处理中" else if (user.isFollowing) "已关注" else "关注", color = DiscoveryPurple) }
                        TextButton(onClick = { onMessageUser(user.id.toString(), user.username) }) { Text("私信") }
                        TextButton(onClick = { onBlockUser(user.id) }) { Text("屏蔽", color = DiscoveryMuted) }
                    }
                    HorizontalDivider(color = Color(0xFFF1F1F4))
                }
            }
        }
    }
    if (showFilter) ModalBottomSheet(onDismissRequest = { showFilter = false }, containerColor = LiveStyle.sheetSurface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("筛选", Modifier.weight(1f), color = DiscoveryInk, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { gender = 0; age = 0; region = 0; appliedGender = 0; appliedAge = 0; appliedRegion = 0; reload++; showFilter = false }) { Text("重置") }
                TextButton(onClick = { appliedGender = gender; appliedAge = age; appliedRegion = region; reload++; showFilter = false }) { Text("完成") }
            }
            FilterOptions("想看到的用户", listOf("不限", "男生", "女生"), gender) { gender = it }
            FilterOptions("征友年龄", listOf("不限", "18-25", "26-35", "36+"), age) { age = it }
            FilterOptions("征友地区", listOf("不限", "同城"), region) { region = it }
            Text("筛选仅匹配本人填写资料的用户。", color = DiscoveryMuted, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun FilterOptions(title: String, values: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, color = DiscoveryInk, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEachIndexed { index, value ->
                FilterChip(selected = selected == index, onClick = { onSelected(index) },
                    label = { Text(value) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LiveStyle.accentSoft,
                        selectedLabelColor = LiveStyle.accent,
                    ))
            }
        }
    }
}

@Composable
internal fun QumiMomentsScreen(token: String, currentUserId: Long,
    onMessages: () -> Unit, onOpenAnnouncements: () -> Unit, onOpenUser: (String, String) -> Unit,
    onMessageUser: (String, String, Boolean) -> Unit, onComposerVisible: (Boolean) -> Unit) {
    val momentNow by rememberMomentNow()
    var following by remember { mutableStateOf(false) }
    var posts by remember { mutableStateOf(emptyList<MomentPost>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var nextCursor by remember { mutableStateOf("") }
    var pagingError by remember { mutableStateOf(false) }
    var composeOpen by remember { mutableStateOf(false) }
    var busyPost by remember { mutableStateOf<String?>(null) }
    var commentsFor by remember { mutableStateOf<MomentPost?>(null) }
    var detailPostId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val api = remember { LivePlatformApi() }
    fun refresh() {
        loading = true
        error = null
        pagingError = false
        scope.launch {
            runCatching { api.moments(token, if (following) "following" else "all") }
                .onSuccess { (items, cursor) -> posts = items; nextCursor = cursor }
                .onFailure { error = it.message ?: "动态加载失败"; if (posts.isEmpty()) nextCursor = "" }
            loading = false
        }
    }
    fun loadMore() {
        val cursor = nextCursor
        if (loading || cursor.isBlank()) return
        loading = true
        error = null
        scope.launch {
            runCatching { api.moments(token, if (following) "following" else "all", cursor) }
                .onSuccess { (items, newCursor) ->
                    posts = posts + items.filterNot { item -> posts.any { it.id == item.id } }
                    nextCursor = newCursor
                    pagingError = false
                }
                .onFailure { error = it.message ?: "加载更多失败"; pagingError = true }
            loading = false
        }
    }
    fun toggleLike(post: MomentPost) {
        if (busyPost != null) return
        busyPost = post.id
        scope.launch {
            runCatching { api.likeMoment(token, post.id, !post.isLiked) }
                .onSuccess { posts = posts.map { if (it.id == post.id) it.copy(isLiked = !post.isLiked, likeCount = (it.likeCount + if (post.isLiked) -1 else 1).coerceAtLeast(0)) else it } }
                .onFailure { error = it.message ?: "点赞状态更新失败" }
            busyPost = null
        }
    }
    LaunchedEffect(following, token) {
        posts = emptyList()
        nextCursor = ""
        refresh()
    }
    DisposableEffect(composeOpen) {
        onComposerVisible(composeOpen)
        onDispose { if (composeOpen) onComposerVisible(false) }
    }
    if (composeOpen) {
        QumiMomentComposer(token, onBack = { composeOpen = false }, onPublished = { post ->
            if (post.reviewStatus == "APPROVED") posts = listOf(post) + posts
            following = false
            composeOpen = false
        })
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
      Box(Modifier.fillMaxWidth().height(250.dp)
          .background(Brush.horizontalGradient(listOf(Color(0xFFE3DDFF), Color(0xFFD7E9FF))))
          .background(Brush.verticalGradient(listOf(Color.Transparent, Color.White))))
      val density = LocalDensity.current
      val widthPx = with(density) { maxWidth.toPx() }
      val heightPx = with(density) { maxHeight.toPx() }
      val buttonPx = with(density) { 42.dp.toPx() }
      val edgePx = with(density) { 18.dp.toPx() }
      var buttonX by remember(widthPx, heightPx) { mutableFloatStateOf((widthPx - buttonPx - edgePx).coerceAtLeast(edgePx)) }
      var buttonY by remember(widthPx, heightPx) { mutableFloatStateOf((heightPx - buttonPx - edgePx).coerceAtLeast(edgePx)) }
      Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("全部", "关注").forEachIndexed { index, label ->
                val selected = following == (index == 1)
                Column(Modifier.clickable { following = index == 1 }.padding(start = if (index == 0) 24.dp else 26.dp, end = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = if (selected) DiscoveryInk else DiscoveryMuted, fontSize = 17.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    Spacer(Modifier.padding(top = 4.dp).width(26.dp).height(3.dp).clip(CircleShape)
                        .background(if (selected) DiscoveryAccent else Color.Transparent))
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { refresh() }, enabled = !loading) { Icon(Icons.Rounded.Refresh, "刷新动态", tint = DiscoveryInk) }
            IconButton(onClick = onOpenAnnouncements) { Icon(Icons.Rounded.NotificationsNone, "消息通知", tint = DiscoveryInk) }
        }
        when {
            loading && posts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DiscoveryPurple) }
            error != null && posts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TextButton(onClick = { refresh() }) { Text("${error} · 点击重试") } }
            posts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.PhotoCamera, null, tint = LiveStyle.muted, modifier = Modifier.size(46.dp))
                    Text(if (following) "关注的人还没有发布动态" else "暂无动态", Modifier.padding(top = 14.dp), color = DiscoveryInk, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text("发布一条动态，和大家分享此刻", Modifier.padding(top = 7.dp), color = DiscoveryMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = DiscoveryPurple) }
                if (error != null) item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(error!!, Modifier.weight(1f), color = Color(0xFFB4233B), fontSize = 12.sp)
                        TextButton(onClick = { if (pagingError) loadMore() else refresh() }, enabled = !loading) { Text("重试") }
                    }
                }
                items(posts, key = { it.id }) { post ->
                    val compactText = post.mediaPath == null && post.content.length <= 80
                    Surface(Modifier.fillMaxWidth(), color = Color.Transparent) {
                        Column(Modifier.clickable { detailPostId = post.id }.padding(horizontal = 4.dp, vertical = 8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                QumiUserAvatar(post.authorAvatar, post.username,
                                    Modifier.size(40.dp).offset(y = 5.dp).clickable {
                                        onOpenUser(post.userId.toString(), post.username)
                                    })
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                  Row(Modifier.fillMaxWidth().heightIn(min = 30.dp),
                                      verticalAlignment = Alignment.CenterVertically) {
                                    Text(post.username, Modifier.weight(1f), color = DiscoveryInk, fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    val ownPost = post.userId == currentUserId
                                    Row(Modifier.height(27.dp).clip(CircleShape)
                                        .background(Brush.horizontalGradient(listOf(Color(0xFFDCCBFF), Color(0xFFF0E9FF))))
                                        .clickable {
                                            if (ownPost) onMessages()
                                            else onMessageUser(post.userId.toString(), post.username, false)
                                        }
                                        .padding(start = 9.dp, end = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text("私信", color = Color(0xFF6034B7), fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.width(5.dp))
                                        Box(Modifier.size(23.dp).clip(CircleShape)
                                            .background(Brush.verticalGradient(listOf(Color(0xFFEAD9FF), Color(0xFFC9A6FA)))),
                                            contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.Chat, contentDescription = null,
                                                tint = Color(0xFF7E42D7), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                  }
                            if (post.content.isNotBlank()) Text(post.content,
                                Modifier.padding(top = if (compactText) 3.dp else 6.dp),
                                color = DiscoveryInk, fontSize = 15.sp, lineHeight = 22.sp)
                            if (post.mediaPath != null) QumiMomentMedia(post, token,
                                Modifier.padding(top = 7.dp)
                                    .width(150.dp))
                            Row(Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(momentCommentTime(post.createdAt, momentNow), Modifier.weight(1f),
                                    color = Color(0xFFB8B8C1), fontSize = 11.sp, maxLines = 1)
                                TextButton(onClick = { toggleLike(post) }, enabled = busyPost == null,
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 7.dp)) {
                                    Icon(if (post.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (post.isLiked) Color(0xFFE94B70) else DiscoveryMuted, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(5.dp)); Text("${post.likeCount}", color = DiscoveryMuted)
                                }
                                TextButton(onClick = { commentsFor = post },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 7.dp)) {
                                    Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, tint = DiscoveryMuted, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(5.dp)); Text("${post.commentCount}", color = DiscoveryMuted)
                                }
                            }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F3))
                }
                if (nextCursor.isNotBlank()) item {
                    TextButton(onClick = { loadMore() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                        Text(if (loading) "正在加载…" else "加载更多")
                    }
                }
            }
        }
      }
      FloatingActionButton(
          onClick = { composeOpen = true },
          modifier = Modifier.offset { IntOffset(buttonX.roundToInt(), buttonY.roundToInt()) }
              .size(42.dp)
              .pointerInput(widthPx, heightPx) {
                  detectDragGestures { change, dragAmount ->
                      change.consume()
                      buttonX = (buttonX + dragAmount.x).coerceIn(edgePx, (widthPx - buttonPx - edgePx).coerceAtLeast(edgePx))
                      buttonY = (buttonY + dragAmount.y).coerceIn(edgePx, (heightPx - buttonPx - edgePx).coerceAtLeast(edgePx))
                  }
              },
          shape = RoundedCornerShape(9.dp),
          containerColor = DiscoveryAccent,
          contentColor = Color.White,
      ) {
          Icon(Icons.Rounded.Add, "发布动态", modifier = Modifier.size(25.dp))
      }
    }
    commentsFor?.let { post ->
        QumiMomentCommentsSheet(
            token = token,
            post = post,
            onDismiss = { commentsFor = null },
            onCountChanged = { delta -> posts = posts.map { if (it.id == post.id) it.copy(commentCount = (it.commentCount + delta).coerceAtLeast(0)) else it } },
        )
    }
    detailPostId?.let { id ->
        posts.firstOrNull { it.id == id }?.let { post ->
            QumiMomentDetailScreen(
                token = token,
                post = post,
                currentUserId = currentUserId,
                onDismiss = { detailPostId = null },
                onPostChanged = { changed -> posts = posts.map { if (it.id == changed.id) changed else it } },
                onOpenUser = { detailPostId = null; onOpenUser(post.userId.toString(), post.username) },
                onMessageUser = { detailPostId = null; onMessageUser(post.userId.toString(), post.username, false) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QumiMomentCommentsSheet(token: String, post: MomentPost, onDismiss: () -> Unit,
    onCountChanged: (Long) -> Unit, heightFraction: Float = .68f) {
    var comments by remember(post.id) { mutableStateOf(emptyList<MomentComment>()) }
    var replies by remember(post.id) { mutableStateOf<Map<String, List<MomentComment>>>(emptyMap()) }
    var replyCursors by remember(post.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loadedRoots by remember(post.id) { mutableStateOf<Set<String>>(emptySet()) }
    var expandedRoots by remember(post.id) { mutableStateOf<Set<String>>(emptySet()) }
    var loadingReplies by remember(post.id) { mutableStateOf<Set<String>>(emptySet()) }
    var replyTarget by remember(post.id) { mutableStateOf<MomentComment?>(null) }
    var visibleCount by remember(post.id) { mutableLongStateOf(post.commentCount) }
    var nextCursor by remember(post.id) { mutableStateOf("") }
    var loading by remember(post.id) { mutableStateOf(true) }
    var busy by remember(post.id) { mutableStateOf(false) }
    var loadError by remember(post.id) { mutableStateOf<String?>(null) }
    var actionError by remember(post.id) { mutableStateOf<String?>(null) }
    var content by remember(post.id) { mutableStateOf("") }
    var deleteTarget by remember(post.id) { mutableStateOf<MomentComment?>(null) }
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val momentNow by rememberMomentNow()

    fun loadReplies(rootId: String, cursor: String = "") {
        if (rootId in loadingReplies) return
        loadingReplies = loadingReplies + rootId
        scope.launch {
            runCatching { api.momentReplies(token, post.id, rootId, cursor) }
                .onSuccess { (items, next) ->
                    val old = if (cursor.isBlank()) emptyList() else replies[rootId].orEmpty()
                    replies = replies + (rootId to (old + items.filterNot { row -> old.any { it.id == row.id } }))
                    replyCursors = replyCursors + (rootId to next)
                    loadedRoots = loadedRoots + rootId
                    actionError = null
                }
                .onFailure { actionError = it.message ?: "回复加载失败" }
            loadingReplies = loadingReplies - rootId
        }
    }

    LaunchedEffect(token, post.id) {
        loading = true
        loadError = null
        runCatching { api.momentComments(token, post.id) }
            .onSuccess { (items, cursor) -> comments = items; nextCursor = cursor }
            .onFailure { loadError = it.message ?: "评论加载失败" }
        loading = false
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheetState, containerColor = LiveStyle.sheetSurface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(heightFraction).imePadding()) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("评论 $visibleCount", Modifier.weight(1f), color = DiscoveryInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDismiss, enabled = !busy) { Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = DiscoveryMuted) }
            }
            HorizontalDivider(color = Color(0xFFF0F0F2))
            when {
                loading && comments.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DiscoveryPurple) }
                comments.isEmpty() && loadError == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("还没有评论", color = DiscoveryMuted) }
                else -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
                    loadError?.let { message -> item { TextButton(onClick = {
                        loading = true
                        scope.launch {
                            runCatching { api.momentComments(token, post.id) }
                                .onSuccess { (items, cursor) -> comments = items; nextCursor = cursor; loadError = null }
                                .onFailure { loadError = it.message ?: "评论加载失败" }
                            loading = false
                        }
                    }) { Text("$message · 重试") } } }
                    items(comments, key = { it.id }) { comment ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            MomentCommentRow(comment, momentNow, onReply = { replyTarget = comment; focusRequester.requestFocus(); keyboard?.show() },
                                onDelete = { deleteTarget = comment }, enabled = !busy)
                            if (comment.replyCount > 0 || comment.id in expandedRoots) {
                                TextButton(onClick = {
                                    if (comment.id in expandedRoots) expandedRoots = expandedRoots - comment.id
                                    else {
                                        expandedRoots = expandedRoots + comment.id
                                        if (comment.id !in loadedRoots) loadReplies(comment.id)
                                    }
                                }, modifier = Modifier.padding(start = 40.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                                    Text(if (comment.id in expandedRoots) "收起回复" else "展开 ${comment.replyCount} 条回复", color = DiscoveryAccent, fontSize = 12.sp)
                                }
                            }
                            if (comment.id in expandedRoots) {
                                if (comment.id in loadingReplies) CircularProgressIndicator(Modifier.padding(start = 62.dp).size(18.dp),
                                    color = DiscoveryAccent, strokeWidth = 2.dp)
                                replies[comment.id].orEmpty().forEach { reply ->
                                    MomentCommentRow(reply, momentNow, modifier = Modifier.padding(start = 40.dp),
                                        onReply = { replyTarget = reply; focusRequester.requestFocus(); keyboard?.show() },
                                        onDelete = { deleteTarget = reply }, enabled = !busy)
                                }
                                if (replyCursors[comment.id].orEmpty().isNotBlank()) TextButton(
                                    onClick = { loadReplies(comment.id, replyCursors[comment.id].orEmpty()) },
                                    modifier = Modifier.padding(start = 48.dp), enabled = comment.id !in loadingReplies,
                                ) { Text("加载更多回复", color = DiscoveryAccent, fontSize = 12.sp) }
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF1F1F4))
                    }
                    if (nextCursor.isNotBlank()) item {
                        TextButton(onClick = {
                            if (!loading) {
                                loading = true
                                scope.launch {
                                    runCatching { api.momentComments(token, post.id, nextCursor) }
                                        .onSuccess { (items, cursor) -> comments = comments + items; nextCursor = cursor }
                                        .onFailure { loadError = it.message ?: "加载更多评论失败" }
                                    loading = false
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth(), enabled = !loading) { Text(if (loading) "正在加载…" else "加载更多") }
                    }
                }
            }
            actionError?.let { Text(it, Modifier.padding(horizontal = 18.dp, vertical = 4.dp), color = Color(0xFFB4233B), fontSize = 12.sp) }
            HorizontalDivider(color = Color(0xFFF0F0F2))
            BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 2.dp)) {
                val inputWidth = minOf(maxWidth * .68f, 250.dp)
                Column {
                    replyTarget?.let { target -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("回复 @${target.username}", Modifier.weight(1f), color = DiscoveryAccent, fontSize = 12.sp)
                        TextButton(onClick = { replyTarget = null }) { Text("取消回复", color = DiscoveryMuted, fontSize = 12.sp) }
                    } }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        var focused by remember { mutableStateOf(false) }
                        BasicTextField(
                            value = content, onValueChange = { content = it.take(500) }, singleLine = true,
                            modifier = Modifier.width(inputWidth).height(44.dp).focusRequester(focusRequester)
                                .onFocusChanged { focused = it.isFocused }
                                .clip(CircleShape).background(Color(0xFFF8F6FC))
                                .border(1.dp, if (focused) DiscoveryAccent else Color(0xFFE6DDF6), CircleShape)
                                .padding(horizontal = 16.dp),
                            textStyle = TextStyle(color = DiscoveryInk, fontSize = 14.sp),
                            cursorBrush = SolidColor(DiscoveryAccent),
                            decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                if (content.isEmpty()) Text("说点友善的话…", color = DiscoveryMuted, fontSize = 14.sp)
                                inner()
                            } },
                        )
                        Spacer(Modifier.weight(1f))
                        Button(enabled = content.isNotBlank() && !busy, onClick = {
                            val value = content.trim()
                            val target = replyTarget
                            busy = true
                            keyboard?.hide()
                            scope.launch {
                                runCatching { api.publishMomentComment(token, post.id, value, target?.id) }
                                    .onSuccess { created ->
                                        val rootId = created.parentId
                                        if (rootId == null) comments = listOf(created) + comments
                                        else {
                                            replies = replies + (rootId to (replies[rootId].orEmpty() + created))
                                            comments = comments.map { if (it.id == rootId) it.copy(replyCount = it.replyCount + 1) else it }
                                            expandedRoots = expandedRoots + rootId
                                        }
                                        visibleCount++
                                        content = ""
                                        replyTarget = null
                                        actionError = null
                                        onCountChanged(1)
                                    }
                                    .onFailure { actionError = it.message ?: "评论发送失败，请重试" }
                                busy = false
                            }
                        }, modifier = Modifier.width(70.dp).height(44.dp), shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DiscoveryAccent)) {
                            Text(if (busy) "发送中" else "发送", fontSize = 13.sp)
                        }
                    }
                    Text("${content.length}/500", Modifier.padding(start = 15.dp, top = 3.dp), color = DiscoveryMuted, fontSize = 10.sp)
                }
            }
        }
    }

    deleteTarget?.let { comment -> AlertDialog(
        onDismissRequest = { if (!busy) deleteTarget = null },
        title = { Text("删除这条评论？") },
        text = { Text("删除后将从评论列表中移除。") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                runCatching { api.deleteMomentComment(token, post.id, comment.id) }
                    .onSuccess { removed ->
                        if (comment.parentId == null) {
                            comments = comments.filterNot { it.id == comment.id }
                            replies = replies - comment.id
                        } else {
                            replies = replies + (comment.parentId to replies[comment.parentId].orEmpty().filterNot { it.id == comment.id })
                            comments = comments.map { if (it.id == comment.parentId)
                                it.copy(replyCount = (it.replyCount - 1).coerceAtLeast(0)) else it }
                        }
                        visibleCount = (visibleCount - removed).coerceAtLeast(0)
                        deleteTarget = null
                        onCountChanged(-removed)
                    }
                    .onFailure { actionError = it.message ?: "删除失败，请重试" }
                busy = false
            }
        }) { Text(if (busy) "处理中" else "删除") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { deleteTarget = null }) { Text("取消") } },
    ) }
}

@Composable
private fun MomentCommentRow(comment: MomentComment, now: java.time.Instant, onReply: () -> Unit, onDelete: () -> Unit,
    enabled: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QumiUserAvatar(comment.avatar, comment.username, Modifier.size(34.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(comment.username, color = DiscoveryInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            comment.replyToUsername?.let { Text("回复 @$it", color = DiscoveryAccent, fontSize = 12.sp) }
            Text(comment.content, color = DiscoveryInk, fontSize = 14.sp, lineHeight = 20.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(momentCommentTime(comment.createdAt, now), color = DiscoveryMuted, fontSize = 11.sp)
                TextButton(onClick = onReply, enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) { Text("回复", color = DiscoveryAccent, fontSize = 11.sp) }
                if (comment.isMine) TextButton(onClick = onDelete, enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("删除", color = DiscoveryMuted, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
internal fun QumiUserSearchScreen(token: String, onBack: () -> Unit, onOpenUser: (String, String) -> Unit, onMessageUser: (String, String) -> Unit, onBlockUser: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var followBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<PublicUser>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val api = remember { LivePlatformApi() }
    fun search() {
        val term = query.trim()
        if (term.isEmpty() || loading) return
        searched = true
        loading = true
        error = null
        scope.launch {
            runCatching { api.searchUsers(token, term) }
                .onSuccess { results = it }
                .onFailure { error = it.message ?: "搜索失败，请重试"; results = emptyList() }
            loading = false
        }
    }
    fun toggleFollow(user: PublicUser) {
        if (followBusy) return
        followBusy = true
        scope.launch {
            runCatching { api.setFollowing(token, user.id, !user.isFollowing) }
                .onSuccess { results = results.map { if (it.id == user.id) it.copy(isFollowing = !user.isFollowing) else it } }
                .onFailure { error = it.message ?: "关注操作失败" }
            followBusy = false
        }
    }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.ArrowBack, "返回", tint = DiscoveryInk) }
            Text("搜索", Modifier.weight(1f), color = DiscoveryInk, fontSize = 20.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.width(52.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)
            .clip(RoundedCornerShape(28.dp)).background(Color(0xFFF4F4F6)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = DiscoveryPurple)
            BasicTextField(value = query, onValueChange = { query = it; searched = false }, modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = DiscoveryInk, fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) Text("输入 ID 或昵称", color = Color(0xFFB7B8BE), fontSize = 16.sp)
                        innerTextField()
                    }
                },
                keyboardActions = KeyboardActions(onSearch = { search() }))
            Button(onClick = { search() }, enabled = query.isNotBlank() && !loading,
                modifier = Modifier.height(40.dp), contentPadding = PaddingValues(horizontal = 16.dp), shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryPurple)) { Text(if (loading) "搜索中" else "搜索") }
        }
        if (!searched) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("搜索用户 ID 或昵称", color = DiscoveryMuted, fontSize = 14.sp)
        } else if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DiscoveryPurple)
        } else if (error != null || results.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(error ?: "没有找到相关用户", color = DiscoveryMuted, fontSize = 14.sp)
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                results.forEach { user ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenUser(user.id.toString(), user.username) }
                        .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(LiveStyle.accentSoft), contentAlignment = Alignment.Center) {
                            Text(user.username.take(1), color = DiscoveryPurple, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(user.username, color = DiscoveryInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("ID ${user.id}${user.description?.let { " · $it" } ?: ""}", color = DiscoveryMuted, fontSize = 12.sp, maxLines = 1)
                        }
                        TextButton(onClick = { toggleFollow(user) }, enabled = !followBusy) {
                            Text(if (user.isFollowing) "已关注" else "关注", color = DiscoveryPurple, fontSize = 13.sp)
                        }
                        TextButton(onClick = { onMessageUser(user.id.toString(), user.username) }) { Text("私信", color = DiscoveryPurple, fontSize = 13.sp) }
                        TextButton(onClick = { onBlockUser(user.id) }) { Text("屏蔽", color = DiscoveryMuted, fontSize = 13.sp) }
                    }
                    HorizontalDivider(color = Color(0xFFF1F1F4))
                }
            }
        }
    }
}
