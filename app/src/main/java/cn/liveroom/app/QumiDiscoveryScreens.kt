package cn.liveroom.app

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val DiscoveryInk = Color(0xFF19191F)
private val DiscoveryMuted = Color(0xFF858A98)
private val DiscoveryPurple = Color(0xFF7864E8)
private val DiscoveryCanvas = Color(0xFFF5F6FA)

@Composable
internal fun MomentMediaImage(token: String, media: MomentMedia, modifier: Modifier = Modifier) {
    var bitmap by remember(media.id, token) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(media.id, token) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                require(media.path.matches(Regex("/moments/media/[1-9][0-9]{0,19}")))
                val connection = URL(BuildConfig.BILLD_API_URL.trimEnd('/') + media.path).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 15_000
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    check(connection.responseCode in 200..299)
                    check(connection.contentType.orEmpty().startsWith("image/"))
                    val bytes = connection.inputStream.use { it.readBytesLimited(8 * 1024 * 1024 + 1) }
                    check(bytes.size <= 8 * 1024 * 1024)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } finally { connection.disconnect() }
            }.getOrNull()
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = "动态图片", contentScale = ContentScale.Crop, modifier = modifier) }
}

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
                    Icon(Icons.Rounded.FavoriteBorder, null, tint = Color(0xFFB9B2D3), modifier = Modifier.size(46.dp))
                    Text("还没有关注的人", Modifier.padding(top = 12.dp), color = DiscoveryInk, fontSize = 17.sp)
                    Text("关注用户后，他们会显示在这里", Modifier.padding(top = 6.dp), color = DiscoveryMuted, fontSize = 13.sp)
                }
            }
            section == 2 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(QumiGrid.itemGap)) {
                items(followingUsers, key = { it.id }) { user ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        QumiUserAvatar(token, user.avatar, Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFECE8FF)), "${user.username}的头像") {
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
                    Icon(Icons.Rounded.FavoriteBorder, null, tint = Color(0xFFB9B2D3), modifier = Modifier.size(46.dp))
                    Text("暂时没有可推荐的用户", Modifier.padding(top = 12.dp), color = DiscoveryInk, fontSize = 17.sp)
                    OutlinedButton(onClick = onSearch, modifier = Modifier.padding(top = 10.dp)) { Text("按 ID 或昵称搜索") }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(QumiGrid.itemGap)) {
                items(users, key = { it.id }) { user ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenUser(user.id.toString(), user.username) }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        QumiUserAvatar(token, user.avatar, Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFECE8FF)), "${user.username}的头像") {
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
    if (showFilter) ModalBottomSheet(onDismissRequest = { showFilter = false }, containerColor = Color.White) {
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
                FilterChip(selected = selected == index, onClick = { onSelected(index) }, label = { Text(value) })
            }
        }
    }
}

@Composable
internal fun QumiMomentsScreen(token: String, onMessages: () -> Unit, onOpenUser: (String, String) -> Unit) {
    var following by remember { mutableStateOf(false) }
    var posts by remember { mutableStateOf(emptyList<MomentPost>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var nextCursor by remember { mutableStateOf("") }
    var pagingError by remember { mutableStateOf(false) }
    var composeOpen by remember { mutableStateOf(false) }
    var content by remember { mutableStateOf("") }
    var publishing by remember { mutableStateOf(false) }
    var mediaUploading by remember { mutableStateOf(false) }
    var selectedMedia by remember { mutableStateOf<MomentMedia?>(null) }
    var busyPost by remember { mutableStateOf<String?>(null) }
    var commentsFor by remember { mutableStateOf<MomentPost?>(null) }
    val scope = rememberCoroutineScope()
    val api = remember { LivePlatformApi() }
    val context = LocalContext.current
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !publishing && !mediaUploading) scope.launch {
            mediaUploading = true
            error = null
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(8 * 1024 * 1024 + 1) }
                        ?: error("无法读取图片")
                }
                require(bytes.size <= 8 * 1024 * 1024) { "图片不能超过 8MB" }
                api.uploadMomentMedia(token, bytes)
            }.onSuccess { selectedMedia = it }
                .onFailure { error = it.message ?: "图片上传失败，请重试" }
            mediaUploading = false
        }
    }
    fun discardComposerMedia() {
        val media = selectedMedia
        selectedMedia = null
        if (media != null) scope.launch { runCatching { api.deleteMomentMedia(token, media.id) } }
    }
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
    Box(Modifier.fillMaxSize().background(Color.White)) {
      Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(58.dp).background(Color(0xFFE6E4FF)), verticalAlignment = Alignment.CenterVertically) {
            listOf("全部", "关注").forEachIndexed { index, label ->
                val selected = following == (index == 1)
                Column(Modifier.clickable { following = index == 1 }.padding(start = if (index == 0) 24.dp else 26.dp, end = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = DiscoveryInk, fontSize = 17.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    if (selected) Spacer(Modifier.padding(top = 4.dp).width(42.dp).height(3.dp).clip(CircleShape).background(Color(0xFF9785F4)))
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { refresh() }, enabled = !loading) { Icon(Icons.Rounded.Refresh, "刷新动态", tint = DiscoveryInk) }
            IconButton(onClick = onMessages) { Icon(Icons.Rounded.NotificationsNone, "消息通知", tint = DiscoveryInk) }
        }
        when {
            loading && posts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DiscoveryPurple) }
            error != null && posts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TextButton(onClick = { refresh() }) { Text("${error} · 点击重试") } }
            posts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.PhotoCamera, null, tint = Color(0xFFB9B2D3), modifier = Modifier.size(46.dp))
                    Text(if (following) "关注的人还没有发布动态" else "暂无动态", Modifier.padding(top = 14.dp), color = DiscoveryInk, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text("发布一条动态，和大家分享此刻", Modifier.padding(top = 7.dp), color = DiscoveryMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = DiscoveryPurple) }
                if (error != null) item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(error!!, Modifier.weight(1f), color = Color(0xFFB4233B), fontSize = 12.sp)
                        TextButton(onClick = { if (pagingError) loadMore() else refresh() }, enabled = !loading) { Text("重试") }
                    }
                }
                items(posts, key = { it.id }) { post ->
                    Surface(Modifier.fillMaxWidth(), color = Color(0xFFF8F8FA), shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                QumiUserAvatar(token, post.authorAvatar, Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFECE8FF)), "${post.username}的头像") {
                                    Text(post.username.take(1), color = DiscoveryPurple, fontWeight = FontWeight.SemiBold)
                                }
                                Column(Modifier.weight(1f).padding(start = 10.dp).clickable { onOpenUser(post.userId.toString(), post.username) }) {
                                    Text(post.username, color = DiscoveryInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(post.createdAt.replace('T', ' ').take(16), color = DiscoveryMuted, fontSize = 11.sp)
                                }
                            }
                            if (post.reviewStatus != "APPROVED") {
                                Text(if (post.reviewStatus == "PENDING") "审核中，审核通过后其他人可见" else "审核未通过",
                                    Modifier.padding(top = 10.dp), color = Color(0xFF9A6A00), fontSize = 12.sp)
                            }
                            if (post.content.isNotBlank()) Text(post.content, Modifier.padding(top = 12.dp), color = DiscoveryInk, fontSize = 15.sp, lineHeight = 22.sp)
                            post.media.forEach { media ->
                                MomentMediaImage(token, media, Modifier.fillMaxWidth().height(220.dp).padding(top = 10.dp).clip(RoundedCornerShape(8.dp)))
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { commentsFor = post }) {
                                    Text("评论 ${post.commentCount}", color = DiscoveryMuted)
                                }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { toggleLike(post) }, enabled = busyPost == null) {
                                    Icon(if (post.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (post.isLiked) Color(0xFFE94B70) else DiscoveryMuted, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(5.dp)); Text("${post.likeCount}", color = DiscoveryMuted)
                                }
                            }
                        }
                    }
                }
                if (nextCursor.isNotBlank()) item {
                    TextButton(onClick = { loadMore() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                        Text(if (loading) "正在加载…" else "加载更多")
                    }
                }
            }
        }
      }
      FloatingActionButton(onClick = { composeOpen = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp), containerColor = DiscoveryPurple, contentColor = Color.White) {
          Icon(Icons.Rounded.PhotoCamera, "发布动态")
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
    if (composeOpen) QumiModalSheet(
        onDismiss = { if (!publishing && !mediaUploading) { discardComposerMedia(); composeOpen = false } },
        dismissEnabled = !publishing && !mediaUploading,
    ) {
        QumiSheetTitle("发布动态")
        OutlinedTextField(value = content, onValueChange = { content = it.take(500) }, modifier = Modifier.fillMaxWidth(), minLines = 4,
            placeholder = { Text("分享此刻…") }, supportingText = { Text("${content.length}/500") })
        selectedMedia?.let { media ->
            MomentMediaImage(token, media, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)))
            TextButton(enabled = !publishing && !mediaUploading, onClick = { discardComposerMedia() }) { Text("移除图片") }
        }
        TextButton(enabled = !publishing && !mediaUploading, onClick = { mediaPicker.launch("image/*") }) {
            Text(if (mediaUploading) "图片上传中…" else "添加图片")
        }
        Text("发布后会进入审核，通过后会展示给其他用户", Modifier.fillMaxWidth(), color = DiscoveryMuted, fontSize = 12.sp)
        error?.let { Text(it, color = Color(0xFFB4233B), fontSize = 12.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(enabled = !publishing && !mediaUploading, onClick = { discardComposerMedia(); composeOpen = false }, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(enabled = (content.isNotBlank() || selectedMedia != null) && !publishing && !mediaUploading, onClick = {
                publishing = true
                scope.launch {
                    runCatching { api.publishMoment(token, content, selectedMedia?.let { listOf(it.id) } ?: emptyList()) }
                        .onSuccess { post -> posts = listOf(post) + posts; content = ""; selectedMedia = null; composeOpen = false; following = false }
                        .onFailure { error = it.message ?: "发布失败，请重试" }
                    publishing = false
                }
            }, modifier = Modifier.weight(1f)) { Text(if (publishing) "发布中" else "发布") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QumiMomentCommentsSheet(token: String, post: MomentPost, onDismiss: () -> Unit, onCountChanged: (Long) -> Unit) {
    var comments by remember(post.id) { mutableStateOf(emptyList<MomentComment>()) }
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

    LaunchedEffect(token, post.id) {
        loading = true
        loadError = null
        runCatching { api.momentComments(token, post.id) }
            .onSuccess { (items, cursor) -> comments = items; nextCursor = cursor }
            .onFailure { loadError = it.message ?: "评论加载失败" }
        loading = false
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.82f).imePadding()) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("评论 ${post.commentCount}", Modifier.weight(1f), color = DiscoveryInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                            QumiUserAvatar(token, comment.avatar, Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFECE8FF)), "${comment.username}的头像") {
                                Text(comment.username.take(1), color = DiscoveryPurple, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(comment.username, color = DiscoveryInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(comment.content, color = DiscoveryInk, fontSize = 14.sp, lineHeight = 20.sp)
                                if (comment.isMine && comment.reviewStatus != "APPROVED") Text(
                                    if (comment.reviewStatus == "PENDING") "审核中，仅自己可见" else "未通过审核${comment.moderationReason.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()}",
                                    color = DiscoveryMuted, fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(comment.createdAt.replace('T', ' ').take(16), Modifier.weight(1f), color = DiscoveryMuted, fontSize = 11.sp)
                                if (comment.isMine) TextButton(onClick = { deleteTarget = comment }, enabled = !busy) { Text("删除", color = Color(0xFFB4233B)) }
                            }
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
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = content, onValueChange = { content = it.take(500) }, modifier = Modifier.weight(1f),
                    placeholder = { Text("说点友善的话…") }, singleLine = true, supportingText = { Text("${content.length}/500") })
                Button(enabled = content.isNotBlank() && !busy, onClick = {
                    val value = content.trim()
                    busy = true
                    keyboard?.hide()
                    scope.launch {
                        runCatching { api.publishMomentComment(token, post.id, value) }
                            .onSuccess { comment -> comments = listOf(comment) + comments; content = ""; actionError = null; if (comment.reviewStatus == "APPROVED") onCountChanged(1) }
                            .onFailure { actionError = it.message ?: "评论发送失败，请重试" }
                        busy = false
                    }
                }) { Text(if (busy) "发送中" else "发送") }
            }
        }
    }

    deleteTarget?.let { comment -> QumiConfirmSheet(
        title = "删除这条评论？",
        message = "删除后将从评论列表中移除。",
        confirmLabel = if (busy) "处理中" else "删除",
        busy = busy,
        onDismiss = { deleteTarget = null },
        onConfirm = {
            busy = true
            scope.launch {
                runCatching { api.deleteMomentComment(token, post.id, comment.id) }
                    .onSuccess { comments = comments.filterNot { it.id == comment.id }; deleteTarget = null; if (comment.reviewStatus == "APPROVED") onCountChanged(-1) }
                    .onFailure { actionError = it.message ?: "删除失败，请重试" }
                busy = false
            }
        },
    ) }
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
                        QumiUserAvatar(token, user.avatar, Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFECE8FF)), "${user.username}的头像") {
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
