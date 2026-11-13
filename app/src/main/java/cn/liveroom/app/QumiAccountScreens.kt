package cn.liveroom.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HeadsetMic
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalActivity
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

internal enum class QumiAccountPage { MINE, PROFILE, PROFILE_EDIT, PUBLIC_PROFILE, WALLET, BILLS, SETTINGS, ABOUT, REFERRAL, COSTUME, YOUTH, LEVEL, TASKS, GUARD, HOST, IDENTITY, CONTACTS, VISITORS, EVENTS, ANNOUNCEMENT_DETAIL, NOTIFICATIONS, GUILD, NOBLE, FRIENDS, BEAUTY, BLACKLIST, HELP, QUALIFICATION, SEARCH, SECRETARY_CHAT, SECRETARY_SETTINGS, SECRETARY_PROFILE }

private val QumiPage = Color(0xFFF4F5FA)
private val QumiText = Color(0xFF17171A)
private val QumiSubtle = Color(0xFF969AA6)
private val QumiLine = Color(0xFFF0F1F4)

@Composable
internal fun QumiMineScreen(
    session: UserSession,
    wallet: Wallet?,
    level: UserLevel?,
    onRefresh: () -> Unit,
    onProfile: () -> Unit,
    onWallet: () -> Unit,
    onBills: (Int) -> Unit,
    onSettings: () -> Unit,
    onBusiness: () -> Unit,
    onTasks: () -> Unit,
    onReferral: () -> Unit,
    onLevel: () -> Unit,
    onGuard: () -> Unit,
    onCostume: () -> Unit,
    onIdentity: () -> Unit,
    onContacts: (Int) -> Unit,
    onCustomerService: () -> Unit,
    onGuild: () -> Unit,
    onNoble: () -> Unit,
    onFriends: () -> Unit,
    onBeauty: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(QumiPage),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().clickable(onClick = onProfile).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                QumiUserAvatar(session.token, session.avatar, Modifier.size(82.dp).clip(CircleShape), "个人头像") {
                    Image(painterResource(R.drawable.qumi_default_avatar), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(session.username, color = QumiText, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(color = Color(0xFF7575FF), shape = RoundedCornerShape(20.dp)) {
                            Text("ID ${session.id}", Modifier.padding(horizontal = 9.dp, vertical = 3.dp), color = Color.White, fontSize = 12.sp)
                        }
                        Text(level?.let { "Lv.${it.viewerLevel}" } ?: "等级暂不可用", color = QumiSubtle, fontSize = 13.sp)
                    }
                    Text("趣蜜号：${session.id}", color = QumiSubtle, fontSize = 13.sp)
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, contentDescription = "刷新账户") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
                listOf("好友", "关注", "粉丝").forEachIndexed { index, label ->
                    Column(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { onContacts(index) }.padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text("—", color = QumiText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Text(label, color = QumiText, fontSize = 16.sp)
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF242426)).clickable(onClick = onNoble).padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = Color(0xFFF7D495))
                    Text("荣耀贵族", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("开通即享专属特权", color = Color(0xFFE4E0D9), fontSize = 13.sp)
                }
                Text("立即开通", Modifier.clip(CircleShape).background(Color(0xFFF6D9B8)).padding(horizontal = 12.dp, vertical = 7.dp), color = Color(0xFF614B36), fontSize = 13.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QumiBalanceCard("玫瑰余额", "—", Color(0xFFFFF1F3), Icons.Rounded.Favorite, Modifier.weight(1f)) { onBills(1) }
                QumiBalanceCard("金币余额", wallet?.balance?.toString() ?: "—", Color(0xFFFFF9EB), Icons.Rounded.AccountBalanceWallet, Modifier.weight(1f), onWallet)
            }
        }
        item {
            Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Row {
                        QumiShortcut("主播后台", Icons.Rounded.LocalActivity, Color(0xFF63C6E6), onBusiness, Modifier.weight(1f))
                        QumiShortcut("公会", Icons.Rounded.PersonOutline, Color(0xFFF1A96D), onGuild, Modifier.weight(1f))
                        QumiShortcut("贵族中心", Icons.Rounded.EmojiEvents, Color(0xFFF1B750), onNoble, Modifier.weight(1f))
                        QumiShortcut("任务中心", Icons.Rounded.CheckCircle, Color(0xFF64C5DE), onTasks, Modifier.weight(1f))
                    }
                    Row {
                        QumiShortcut("邀请好友", Icons.Rounded.PersonAdd, Color(0xFFEA6F9E), onReferral, Modifier.weight(1f))
                        QumiShortcut("我的等级", Icons.Rounded.EmojiEvents, Color(0xFFE8A04E), onLevel, Modifier.weight(1f))
                        QumiShortcut("我的守护", Icons.Rounded.Shield, Color(0xFFE56D9D), onGuard, Modifier.weight(1f))
                        QumiShortcut("我的蜜友", Icons.Rounded.Favorite, Color(0xFFEA6F9E), onFriends, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    QumiSettingsRow("装扮商城", Icons.Rounded.Store, onClick = onCostume)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        listOf(
                            R.drawable.qumi_shop_frame_angel to "粉红天使",
                            R.drawable.qumi_shop_bubble_purple to "紫气东来",
                            R.drawable.qumi_shop_entry_friend to "蜜友",
                            R.drawable.qumi_shop_car_flag to "红旗",
                        ).forEach { (image, name) ->
                            Column(Modifier.width(104.dp).clickable(onClick = onCostume), horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(painterResource(image), contentDescription = name, contentScale = ContentScale.Fit, modifier = Modifier.size(92.dp).clip(RoundedCornerShape(8.dp)))
                                Text(name, color = QumiText, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    QumiSettingsRow("认证中心", Icons.Rounded.VerifiedUser, onClick = onIdentity)
                    QumiDivider()
                    QumiSettingsRow("美颜效果", Icons.Rounded.Face, onClick = onBeauty)
                    QumiDivider()
                    QumiSettingsRow("常见问题/客服", Icons.Rounded.HeadsetMic, onClick = onCustomerService)
                    QumiDivider()
                    QumiSettingsRow("设置", Icons.Rounded.Settings, onClick = onSettings)
                }
            }
        }
    }
}

@Composable
internal fun QumiContactsScreen(token: String, initialTab: Int, onBack: () -> Unit, onSearch: (Int) -> Unit, onOpenUser: (String, String) -> Unit, onMessageUser: (String, String) -> Unit) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    var users by remember { mutableStateOf(emptyList<PublicUser>()) }
    var nextCursor by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var followBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val api = remember { LivePlatformApi() }
    fun refresh() {
        loading = true
        loadingMore = false
        error = null
        scope.launch {
            runCatching { api.relations(token, listOf("following", "followers", "friends")[selectedTab]) }
                .onSuccess { page -> users = page.users; nextCursor = page.nextCursor }
                .onFailure { error = it.message ?: "列表加载失败"; users = emptyList(); nextCursor = "" }
            loading = false
        }
    }
    fun loadMore() {
        val cursor = nextCursor
        if (cursor.isBlank() || loading || loadingMore) return
        loadingMore = true
        error = null
        scope.launch {
            runCatching { api.relations(token, listOf("following", "followers", "friends")[selectedTab], cursor) }
                .onSuccess { page ->
                    users = users + page.users.filterNot { user -> users.any { it.id == user.id } }
                    nextCursor = page.nextCursor
                }
                .onFailure { error = it.message ?: "加载更多失败" }
            loadingMore = false
        }
    }
    LaunchedEffect(token, selectedTab) { refresh() }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = QumiText) }
            listOf("关注", "粉丝", "好友").forEachIndexed { index, title ->
                Column(
                    Modifier.weight(1f).fillMaxSize().clickable { selectedTab = index },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(title, color = if (selectedTab == index) QumiText else QumiSubtle, fontSize = 17.sp, fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal)
                    if (selectedTab == index) Spacer(Modifier.padding(top = 5.dp).width(24.dp).height(3.dp).clip(CircleShape).background(Color(0xFF8E7AF5)))
                }
            }
            IconButton(onClick = { refresh() }, enabled = !loading) {
                Icon(Icons.Rounded.Refresh, contentDescription = "刷新联系人", tint = Color(0xFF7864E8))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)
                .clip(RoundedCornerShape(14.dp)).background(Color(0xFFF4F4F5)).clickable { onSearch(selectedTab) }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF898B92))
            Text("输入 ID 或昵称", color = Color(0xFFB7B8BE), fontSize = 16.sp)
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null && users.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TextButton(onClick = { refresh() }) { Text("${error} · 点击重试") } }
            users.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("暂无", color = Color(0xFF81869A), fontSize = 17.sp)
                    Text("${listOf("关注", "粉丝", "好友")[selectedTab]}列表为空", color = Color(0xFFB1B3BB), fontSize = 13.sp)
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(users, key = { it.id }) { user ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        QumiUserAvatar(token, user.avatar, Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFECE8FF)).clickable { onOpenUser(user.id.toString(), user.username) }, "${user.username}的头像") {
                            Text(user.username.take(1), color = Color(0xFF7864E8), fontSize = 18.sp)
                        }
                        Column(Modifier.weight(1f).clickable { onOpenUser(user.id.toString(), user.username) }.padding(start = 12.dp)) {
                            Text(user.username, color = QumiText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("ID ${user.id}", color = QumiSubtle, fontSize = 12.sp)
                        }
                        if (selectedTab != 1) TextButton(enabled = !followBusy, onClick = {
                            followBusy = true
                            scope.launch {
                                runCatching { api.setFollowing(token, user.id, false) }
                                    .onSuccess { refresh() }
                                    .onFailure { error = it.message ?: "操作失败" }
                                followBusy = false
                            }
                        }) { Text(if (selectedTab == 2) "取消好友" else "取消关注", color = Color(0xFF7864E8)) }
                        TextButton(onClick = { onMessageUser(user.id.toString(), user.username) }) { Text("私信") }
                    }
                    HorizontalDivider(color = Color(0xFFF1F1F4))
                }
                if (error != null) item { TextButton(onClick = { loadMore() }, modifier = Modifier.fillMaxWidth()) { Text("${error} · 点击重试") } }
                else if (nextCursor.isNotBlank()) item {
                    TextButton(enabled = !loadingMore, onClick = { loadMore() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (loadingMore) "正在加载…" else "加载更多")
                    }
                }
            }
        }
    }
}

@Composable
internal fun QumiVisitorsScreen(token: String, onBack: () -> Unit, onOpenUser: (String, String) -> Unit) {
    var visitors by remember { mutableStateOf(emptyList<ProfileVisitor>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val api = remember { LivePlatformApi() }
    LaunchedEffect(token, refresh) {
        loading = true
        error = null
        runCatching { api.profileVisitors(token) }
            .onSuccess { visitors = it }
            .onFailure { error = it.message ?: "访客加载失败"; visitors = emptyList() }
        loading = false
    }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        QumiPageTitle("最近访客", onBack)
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TextButton(onClick = { refresh++ }) { Text("${error} · 点击重试") } }
            visitors.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(visitors, key = { it.user.id }) { visitor ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenUser(visitor.user.id.toString(), visitor.user.username) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        QumiUserAvatar(token, visitor.user.avatar, Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFECE8FF)), "${visitor.user.username}的头像") {
                            Text(visitor.user.username.take(1), color = Color(0xFF7864E8), fontSize = 18.sp)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(visitor.user.username, color = QumiText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("ID ${visitor.user.id}", color = QumiSubtle, fontSize = 12.sp)
                        }
                        Text(visitor.lastSeenAt.replace('T', ' ').take(16), color = QumiSubtle, fontSize = 11.sp)
                    }
                    HorizontalDivider(color = QumiLine)
                }
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, modifier = Modifier.size(80.dp), tint = Color(0xFFB6A6EC))
                    Text("暂无访客", Modifier.padding(top = 16.dp), color = Color(0xFF81869A), fontSize = 18.sp)
                    Text("有人访问你的主页后，会显示在这里", Modifier.padding(top = 8.dp), color = Color(0xFFB1B3BB), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
internal fun QumiAnnouncementListScreen(token: String, onBack: () -> Unit, onOpenDetail: (PlatformAnnouncementItem) -> Unit) {
    var notices by remember { mutableStateOf<List<PlatformAnnouncementItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(token) {
        loading = true
        error = null
        runCatching { api.announcements(token) }.onSuccess { notices = it }.onFailure { error = it.message ?: "公告加载失败" }
        loading = false
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F5F6))) {
        QumiPageTitle("平台活动", onBack)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (loading) item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            error?.let { message -> item { Text(message, Modifier.padding(24.dp), color = Color(0xFF8B8FA0)) } }
            if (!loading && error == null && notices.isEmpty()) item {
                Text("暂无平台公告", Modifier.padding(24.dp), color = Color(0xFF8B8FA0))
            }
            items(notices, key = { it.id }) { notice ->
                Column {
                    Text(notice.createdAt.take(10), Modifier.fillMaxWidth().padding(vertical = 18.dp), color = Color(0xFF99999D), fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(notice.title, Modifier.padding(start = 6.dp, top = 14.dp, bottom = 6.dp), color = QumiText, fontSize = 18.sp)
                            Text(notice.summary.ifBlank { "平台公告" }, Modifier.padding(start = 6.dp, bottom = 12.dp), color = Color(0xFF888A93), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            HorizontalDivider(color = QumiLine)
                            Row(Modifier.fillMaxWidth().height(52.dp).clickable {
                                scope.launch { runCatching { api.markAnnouncementRead(token, notice.id) }; onOpenDetail(notice) }
                            }, verticalAlignment = Alignment.CenterVertically) {
                                Text("查看详情", Modifier.weight(1f), color = Color(0xFFB0B0B3), fontSize = 18.sp)
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = QumiText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun QumiAnnouncementDetailScreen(title: String, content: String? = null, onBack: () -> Unit) {
    val body = content?.trim()?.takeIf { it.isNotEmpty() }?.split("\n")?.filter { it.isNotBlank() } ?: listOf(
        "公告正文暂不可用，请返回公告列表后重试。",
    )
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = QumiText) }
            Text(title, Modifier.weight(1f), color = QumiText, fontSize = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(48.dp))
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(25.dp)) {
            item {
                Text(title, Modifier.fillMaxWidth().padding(bottom = 95.dp), color = Color.Black, fontSize = 25.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Medium)
            }
            items(body) { paragraph ->
                Text(paragraph, color = Color(0xFF222222), fontSize = 16.sp, lineHeight = 27.sp)
            }
        }
    }
}

@Composable
internal fun QumiMessagesHomeScreen(
    ready: Boolean,
    busy: Boolean,
    conversations: List<Triple<String, String, String>>,
    supportName: String?,
    supportPreview: String?,
    onNewMessage: () -> Unit,
    onContacts: () -> Unit,
    onConnect: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenVisitors: () -> Unit,
    onOpenSecretaryChat: () -> Unit,
    onOpenConversation: (String, String) -> Unit,
    onOpenSupport: () -> Unit,
    onOpenNotifications: () -> Unit,
    onRequestNotifications: () -> Unit = onOpenNotifications,
    onOpenMatch: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = QumiGrid.pagePadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("消息", color = Color.Black, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onContacts) {
                    Icon(Icons.Rounded.PersonOutline, contentDescription = "联系人", tint = Color(0xFF111111), modifier = Modifier.size(28.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp)
                    .clip(RoundedCornerShape(11.dp)).background(Color(0xFFFFF0F0))
                    .clickable(onClick = onRequestNotifications).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Notifications, contentDescription = null, tint = Color(0xFFF3A747), modifier = Modifier.size(24.dp))
                Text("打开通知，及时接收消息", Modifier.weight(1f), color = Color(0xFF333333), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Surface(color = Color(0xFF7771F7), shape = CircleShape) {
                    Text("去开启", Modifier.padding(horizontal = 9.dp, vertical = 4.dp), color = Color.White, fontSize = 10.sp)
                }
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).padding(top = QumiGrid.sectionGap),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(QumiGrid.itemGap),
            ) {
                item {
                    QumiMessageRow(
                        title = "系统公告", subtitle = "绿色直播文明公约", date = "2025-09-28",
                        icon = Icons.Rounded.Campaign, iconColor = Color(0xFF65C5DE),
                        onClick = onOpenAnnouncements,
                    )
                }
                item {
                    QumiMessageRow(
                        title = "最近访客", subtitle = "暂无访客", icon = Icons.Rounded.Star,
                        iconColor = Color(0xFFF0C94C), onClick = onOpenVisitors,
                    )
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().height(88.dp).clickable(onClick = onOpenSecretaryChat),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painterResource(R.drawable.qumi_secretary_avatar), contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(44.dp).clip(CircleShape),
                        )
                        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("趣蜜小秘书", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("严禁用户以承诺见面、交换微信、", color = Color(0xFF8B8FA0), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("安全提醒", color = Color(0xFFB6B6B8), fontSize = 13.sp)
                        }
                    }
                }
                supportName?.let { name ->
                    item {
                        QumiMessageRow(
                            title = name, subtitle = supportPreview?.ifBlank { "平台客服" } ?: "平台客服",
                            icon = Icons.Rounded.HeadsetMic, iconColor = Color(0xFF70BFD1), onClick = onOpenSupport,
                        )
                    }
                }
                items(conversations.size) { index ->
                    val (id, name, preview) = conversations[index]
                    Row(
                        Modifier.fillMaxWidth().height(76.dp).clickable { onOpenConversation(id, name) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(color = Color(0xFFF0EAFE), shape = CircleShape, modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(name.take(1), color = Color(0xFF6E59D8), fontSize = 18.sp) }
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(name, color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(preview, color = Color(0xFF8B8FA0), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (!ready) item {
                    TextButton(onClick = onConnect, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "正在连接消息服务…" else "消息服务暂不可用 · 点击重试", color = Color(0xFF8B8FA0), fontSize = 12.sp)
                    }
                }
                item { TextButton(onClick = onNewMessage, enabled = ready && !busy) { Text("新消息") } }
            }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 24.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF6544E9), Color(0xFFD72FE5))))
                .clickable(onClick = onOpenMatch).padding(start = 9.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = Color(0xFFFFD7F0), shape = CircleShape, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color(0xFFE44FA2), modifier = Modifier.size(19.dp)) }
            }
            Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("与你匹配的 ›", color = Color.White, fontSize = 18.sp, lineHeight = 21.sp)
                Text("查看直播", color = Color(0xFFE9DEFF), fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun QumiMessageRow(
    title: String,
    subtitle: String,
    date: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(88.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = iconColor.copy(alpha = .13f), shape = CircleShape, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(31.dp)) }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, color = Color(0xFF8B8FA0), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        date?.let { Text(it, color = Color(0xFFB6B6B8), fontSize = 14.sp) }
    }
}

@Composable
internal fun QumiSecretaryChatScreen(onBack: () -> Unit, onSettings: () -> Unit) {
    val message = "严禁用户以承诺见面、交换微信、做女朋友等理由索要礼物，一经发现严格处罚。同时也希望各位嘉宾理性消费，切勿借钱、转账或者发红包、现金给未见面的男女嘉宾或主播。谨防诈骗! 用户可在视频交友，私聊中通过语音播报，自由交换联系方式。平台没有语音屏蔽功能，没有需要消费才能解锁交换联系方式的限制。平台没有任何刷单充值返现活动，一切以刷单充值返现为理由要求充值的都是诈骗!"
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.width(52.dp)) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Color.Black) }
            Text("趣蜜小秘书", Modifier.weight(1f), color = Color.Black, fontSize = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = onSettings, modifier = Modifier.width(52.dp)) { Icon(Icons.Rounded.MoreHoriz, contentDescription = "设置", tint = Color.Black) }
        }
        Row(
            Modifier.fillMaxWidth().height(36.dp).background(Color(0xFF5943E9)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text("关注TA成为好友，随时畅聊", Modifier.weight(1f).padding(start = 8.dp), color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Text("关注", Modifier.padding(horizontal = 17.dp, vertical = 6.dp), color = Color(0xFF17171A), fontSize = 14.sp)
            }
        }
        Text(
            "安全提醒:请勿透漏验证码等个人信息，若有涉及加 qq/微信、帮注册账号、转账、视频、下载软件等，请提高警惕谨防诈骗",
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            color = Color(0xFF777777), fontSize = 14.sp, lineHeight = 27.sp,
        )
        // Keep the announcement bubble, avatar, and reply bubble on one 56dp content grid.
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 56.dp)) {
            Surface(color = Color(0xFFEEEEEE), shape = RoundedCornerShape(15.dp)) {
                Text(message, Modifier.padding(horizontal = 12.dp, vertical = 13.dp), color = Color.Black, fontSize = 12.sp, lineHeight = 22.sp)
            }
            Text("今天10:00", Modifier.fillMaxWidth().padding(vertical = 16.dp), color = Color(0xFF77777F), fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Row(verticalAlignment = Alignment.Top) {
                Image(painterResource(R.drawable.qumi_secretary_avatar), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.padding(top = 4.dp).size(40.dp).clip(CircleShape))
                Surface(Modifier.padding(start = 7.dp).weight(1f), color = Color(0xFFEEEEEE), shape = RoundedCornerShape(15.dp)) {
                    Text(message, Modifier.padding(horizontal = 12.dp, vertical = 13.dp), color = Color.Black, fontSize = 12.sp, lineHeight = 22.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(end = 15.dp, top = 5.dp, bottom = 9.dp), horizontalArrangement = Arrangement.End) {
            Surface(color = Color(0xFFF7F7F7), shape = RoundedCornerShape(28.dp)) {
            Row(Modifier.padding(horizontal = 15.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color(0xFFE5A2B6), modifier = Modifier.size(18.dp))
                Text("红玫瑰", color = Color(0xFFB1B1B1), fontSize = 15.sp)
            }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp).height(42.dp)
                .clip(RoundedCornerShape(10.dp)).background(Color(0xFFF6F6F6)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("常", color = Color(0xFFB5B5B5), fontSize = 24.sp)
            Text("发送消息…", Modifier.weight(1f).padding(start = 14.dp), color = Color(0xFF85858C), fontSize = 18.sp)
            Surface(color = Color(0xFF5C46EC), shape = RoundedCornerShape(8.dp)) {
                Text("发送", Modifier.padding(horizontal = 13.dp, vertical = 7.dp), color = Color.White, fontSize = 18.sp)
            }
        }
        Row(Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 32.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Mic, contentDescription = null, tint = Color(0xFF555555), modifier = Modifier.size(22.dp))
            Icon(Icons.Rounded.Videocam, contentDescription = null, tint = Color(0xFF555555), modifier = Modifier.size(22.dp))
            Icon(Icons.Rounded.CardGiftcard, contentDescription = null, tint = Color(0xFFE8A64B), modifier = Modifier.size(24.dp))
            Icon(Icons.Rounded.Mood, contentDescription = null, tint = Color(0xFF555555), modifier = Modifier.size(22.dp))
            Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, tint = Color(0xFF555555), modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
internal fun QumiSecretarySettingsScreen(
    token: String,
    api: LivePlatformApi,
    gateway: OpenImGateway,
    recipientId: String?,
    messageReady: Boolean,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onReport: () -> Unit,
    onHistoryCleared: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var blocked by remember(token, recipientId) { mutableStateOf(false) }
    var loadingBlock by remember(token, recipientId) { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(token, recipientId) {
        loadingBlock = true
        error = null
        val id = recipientId?.toLongOrNull()
        if (id == null) {
            error = "客服账号未配置"
            loadingBlock = false
            return@LaunchedEffect
        }
        try {
            blocked = api.blockedUsers(token).any { it.id == id }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "屏蔽状态暂时无法读取"
        } finally {
            loadingBlock = false
        }
    }
    fun setBlocked(value: Boolean) {
        val id = recipientId?.toLongOrNull() ?: return
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                api.setBlocked(token, id, value)
                blocked = value
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "屏蔽操作失败，请重试"
            } finally {
                busy = false
            }
        }
    }
    if (confirmBlock) QumiConfirmSheet(
        title = "拉黑趣蜜小秘书？",
        message = "拉黑后你将无法继续向该账号发送消息，可在黑名单设置中解除。",
        confirmLabel = if (busy) "处理中" else "拉黑",
        busy = busy,
        onDismiss = { if (!busy) confirmBlock = false },
        onConfirm = { confirmBlock = false; setBlocked(true) },
    )
    if (confirmClear) QumiConfirmSheet(
        title = "清除聊天记录",
        message = "消息服务会清除这个会话的聊天记录，此操作无法撤销。",
        confirmLabel = if (busy) "正在清除…" else "清除记录",
        busy = busy,
        onDismiss = { if (!busy) confirmClear = false },
        onConfirm = {
            val id = recipientId
            if (id.isNullOrBlank() || !messageReady) {
                error = "消息服务未连接，请连接后重试"
                confirmClear = false
            } else {
                busy = true
                error = null
                scope.launch {
                    try {
                        gateway.clearConversationHistory(id)
                        onHistoryCleared()
                        confirmClear = false
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure.message ?: "聊天记录清除失败，请重试"
                    } finally {
                        busy = false
                    }
                }
            }
        },
    )
    Column(Modifier.fillMaxSize().background(Color.White)) {
        QumiPageTitle("设置", onBack)
        Row(Modifier.fillMaxWidth().height(112.dp).clickable(onClick = onOpenProfile).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFFF7F7F7), shape = CircleShape, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Photo, contentDescription = null, tint = Color(0xFFE0E0E0), modifier = Modifier.size(23.dp)) }
            }
            Text("趣蜜小秘书", Modifier.weight(1f).padding(start = 14.dp), color = Color.Black, fontSize = 18.sp)
            Text("主页", color = Color.Black, fontSize = 18.sp)
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.Black)
        }
        Spacer(Modifier.fillMaxWidth().height(11.dp).background(Color(0xFFF5F5F5)))
        Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("置顶聊天", color = Color.Black, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Text("消息页固定置顶", color = Color(0xFF969AA6), fontSize = 14.sp)
        }
        QumiDivider()
        Row(Modifier.fillMaxWidth().height(66.dp).clickable(enabled = messageReady && !busy && recipientId != null) { confirmClear = true }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("清除聊天记录", color = Color.Black, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Text(if (!messageReady) "未连接" else if (busy) "处理中" else "", color = Color(0xFF969AA6), fontSize = 14.sp)
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.Black)
        }
        QumiDivider()
        Row(Modifier.fillMaxWidth().height(66.dp).clickable(onClick = onReport).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("举报", color = Color.Black, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.Black)
        }
        QumiDivider()
        Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("拉黑", color = Color.Black, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            if (loadingBlock) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Switch(checked = blocked, onCheckedChange = { value -> if (value) confirmBlock = true else setBlocked(false) }, enabled = !busy && recipientId != null)
        }
        error?.let { Text(it, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun QumiSecretaryProfileScreen(
    token: String,
    api: LivePlatformApi,
    support: ChatRecipient?,
    onBack: () -> Unit,
    onReport: () -> Unit,
) {
    var dynamic by remember { mutableStateOf(false) }
    var posts by remember { mutableStateOf(emptyList<MomentPost>()) }
    var nextCursor by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var busyPost by remember { mutableStateOf<String?>(null) }
    var commentsFor by remember { mutableStateOf<MomentPost?>(null) }
    val scope = rememberCoroutineScope()
    var showMore by remember { mutableStateOf(false) }
    if (showMore) QumiProfileMoreDialog(onDismiss = { showMore = false }, onReport = { showMore = false; onReport() })
    val scroll = rememberLazyListState()
    val collapsed by remember { derivedStateOf { scroll.firstVisibleItemIndex >= 2 } }
    LaunchedEffect(token, support?.userId, dynamic, refresh) {
        if (!dynamic) return@LaunchedEffect
        val userId = support?.userId?.toLongOrNull()
        if (userId == null) {
            posts = emptyList()
            nextCursor = ""
            error = "客服账号未配置"
            return@LaunchedEffect
        }
        loading = true
        error = null
        runCatching { api.moments(token, "user", userId = userId) }
            .onSuccess { (items, cursor) -> posts = items; nextCursor = cursor }
            .onFailure { posts = emptyList(); nextCursor = ""; error = it.message ?: "动态加载失败" }
        loading = false
    }
    fun loadMore() {
        val userId = support?.userId?.toLongOrNull() ?: return
        val cursor = nextCursor
        if (cursor.isBlank() || loading) return
        loading = true
        scope.launch {
            runCatching { api.moments(token, "user", cursor, userId) }
                .onSuccess { (items, newCursor) ->
                    posts = posts + items.filterNot { post -> posts.any { it.id == post.id } }
                    nextCursor = newCursor
                    error = null
                }
                .onFailure { error = it.message ?: "加载更多动态失败" }
            loading = false
        }
    }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Box(Modifier.weight(1f)) {
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize()) {
            item {
                Box(Modifier.fillMaxWidth().height(595.dp).clip(RoundedCornerShape(0.dp))) {
                    Image(painterResource(R.drawable.qumi_secretary_avatar), null, Modifier.fillMaxWidth().requiredHeight(619.dp).offset(y = (-12).dp), contentScale = ContentScale.FillBounds)
                    Image(painterResource(R.drawable.qumi_secretary_avatar), null, Modifier.align(Alignment.BottomStart).padding(start = 25.dp, bottom = 16.dp).size(88.dp).clip(CircleShape))
                }
            }
            item {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 35.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("趣蜜小秘书", color = QumiText, fontSize = 32.sp)
                    Text("系统账号", Modifier.height(58.dp).padding(top = 10.dp), color = QumiSubtle, fontSize = 17.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QumiSecretaryLevel("土豪等级", "平民级", Icons.Rounded.EmojiEvents, Color(0xFFFFF7DF), Modifier.weight(1f))
                        QumiSecretaryLevel("魅力等级", "新秀级", Icons.Rounded.Favorite, Color(0xFFFCEBFA), Modifier.weight(1f))
                        QumiSecretaryLevel("个人等级", "1级", Icons.Rounded.Star, Color(0xFFE9FAFB), Modifier.weight(1f))
                    }
                }
                HorizontalDivider(thickness = 18.dp, color = Color(0xFFF5F6FA))
            }
            stickyHeader {
                Column(Modifier.fillMaxWidth().background(Color.White)) {
                    // Reserve the toolbar when the tabs pin, so their touch targets stay below it.
                    Spacer(Modifier.height(112.dp))
                    Row(Modifier.fillMaxWidth().height(78.dp).padding(horizontal = 27.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(55.dp)) {
                    listOf("关于TA", "动态").forEachIndexed { index, label ->
                        val selected = dynamic == (index == 1)
                        Column(Modifier.clickable { dynamic = index == 1 }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, color = if (selected) Color.Black else Color(0xFF777777), fontSize = if (selected) 20.sp else 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                            Spacer(Modifier.height(12.dp))
                            Box(Modifier.size(25.dp, 6.dp).clip(CircleShape).background(if (selected) Color.Black else Color.Transparent))
                        }
                    }
                    }
                    HorizontalDivider(color = Color(0xFFF5F6FA))
                }
            }
            if (dynamic) {
                when {
                    loading && posts.isEmpty() -> item { Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    error != null && posts.isEmpty() -> item {
                        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { refresh++ }) { Text("${error} · 点击重试") }
                        }
                    }
                    posts.isEmpty() -> item { QumiEmptyState("暂无动态") }
                    else -> item { Text("动态 · ${posts.size}", Modifier.fillMaxWidth().padding(horizontal = 27.dp, vertical = 16.dp), color = QumiSubtle, fontSize = 13.sp) }
                }
                if (error != null && posts.isNotEmpty()) item { Text(error!!, Modifier.padding(horizontal = 27.dp), color = Color(0xFFB4233B), fontSize = 12.sp) }
                items(posts, key = { it.id }) { post ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 27.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (post.content.isNotBlank()) Text(post.content, color = QumiText, fontSize = 15.sp, lineHeight = 22.sp)
                        post.media.forEach { media ->
                            MomentMediaImage(token, media, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp)))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(post.createdAt.replace('T', ' ').take(16), Modifier.weight(1f), color = QumiSubtle, fontSize = 12.sp)
                            TextButton(enabled = busyPost == null, onClick = {
                                busyPost = post.id
                                scope.launch {
                                    runCatching { api.likeMoment(token, post.id, !post.isLiked) }
                                        .onSuccess {
                                            posts = posts.map { current ->
                                                if (current.id == post.id) current.copy(
                                                    isLiked = !post.isLiked,
                                                    likeCount = (post.likeCount + if (post.isLiked) -1 else 1).coerceAtLeast(0),
                                                ) else current
                                            }
                                        }
                                        .onFailure { error = it.message ?: "点赞失败，请重试" }
                                    busyPost = null
                                }
                            }) { Text("${if (post.isLiked) "已赞" else "点赞"} ${post.likeCount}") }
                            TextButton(onClick = { commentsFor = post }) { Text("评论 ${post.commentCount}") }
                        }
                        HorizontalDivider(color = QumiLine)
                    }
                }
                if (nextCursor.isNotBlank()) item {
                    TextButton(onClick = { loadMore() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                        Text(if (loading) "正在加载…" else "加载更多")
                    }
                }
            } else item {
                Column(Modifier.padding(horizontal = 27.dp).padding(top = 30.dp, bottom = 40.dp)) {
                    Text("认证信息", color = Color.Black, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(26.dp))
                    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFF5E7)).padding(horizontal = 14.dp, vertical = 19.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PhoneAndroid, null, Modifier.size(26.dp, 31.dp), tint = Color(0xFFFFB22D))
                        Spacer(Modifier.width(6.dp))
                        Text("手机认证", color = Color(0xFFFFB22D), fontSize = 23.sp)
                    }
                    Spacer(Modifier.height(40.dp))
                    Text("个人信息", color = Color.Black, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(32.dp))
                    Text("该账号用于发布平台安全提醒，没有公开个人资料。", color = Color(0xFF454A64), fontSize = 17.sp, lineHeight = 26.sp)
                }
            }
        }
            Row(Modifier.fillMaxWidth().height(92.dp).background(if (collapsed || scroll.firstVisibleItemIndex > 0) Color.White else Color.Transparent), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 19.dp).size(48.dp)) {
                    Box(Modifier.size(31.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFBDBDBD)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ArrowBack, "返回", tint = Color.White) }
                }
                Text("趣蜜小秘书的主页", Modifier.weight(1f), color = if (collapsed || scroll.firstVisibleItemIndex > 0) Color.Black else Color.White, fontSize = 30.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { showMore = true }, modifier = Modifier.padding(end = 19.dp).size(48.dp)) {
                    Box(Modifier.size(31.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFBDBDBD)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MoreHoriz, "更多", tint = Color.White) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(145.dp).padding(start = 56.dp, end = 56.dp, top = 22.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(55.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.PersonAdd, null, Modifier.size(23.dp), tint = Color(0xFF7864E8)); Spacer(Modifier.height(8.dp)); Text("关注", color = Color(0xFF777777), fontSize = 20.sp) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.ChatBubbleOutline, null, Modifier.size(23.dp), tint = Color(0xFF7864E8)); Spacer(Modifier.height(8.dp)); Text("私信", color = Color(0xFF777777), fontSize = 20.sp) }
            Surface(Modifier.weight(1f).height(70.dp), color = Color(0xFF5846EB), shape = CircleShape) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Mood, null, Modifier.size(39.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("打招呼", color = Color.White, fontSize = 32.sp)
                }
            }
        }
    }
    commentsFor?.let { post ->
        QumiMomentCommentsSheet(
            token = token,
            post = post,
            onDismiss = { commentsFor = null },
            onCountChanged = { delta -> posts = posts.map { current -> if (current.id == post.id) current.copy(commentCount = (current.commentCount + delta).coerceAtLeast(0)) else current } },
        )
    }
}

@Composable
private fun QumiSecretaryLevel(label: String, value: String, image: androidx.compose.ui.graphics.vector.ImageVector, background: Color, modifier: Modifier) {
    Row(modifier.height(110.dp).clip(RoundedCornerShape(9.dp)).background(background).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(image, null, Modifier.size(52.dp), tint = Color(0xFFB88746))
        Column(Modifier.padding(start = 7.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(value, color = Color(0xFF78786E), fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, color = Color(0xFF78786E), fontSize = 16.sp, maxLines = 1)
        }
    }
}

@Composable
internal fun QumiLevelScreen(level: UserLevel?, onBack: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF21170F))) {
        Row(
            Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Color(0xFFFFE8B8)) }
            Text("我的等级", color = Color(0xFFFFE8B8), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = Color(0xFFFFE8B8)) }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF51351A), Color(0xFF21170F)))),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("等级与积分", color = Color(0xFFFFE8B8), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    level?.let {
                        QumiLevelSummary("观众等级", it.viewerLevel, it.viewerPoints, it.viewerNextLevelPoints, "累计消费金币")
                        QumiLevelSummary("主播等级", it.creatorLevel, it.creatorPoints, it.creatorNextLevelPoints, "累计获得钻石")
                    } ?: Text("等级数据暂不可用 · 下拉刷新后重试", color = Color(0xFFD9C9AC), fontSize = 14.sp)
                }
            }
            item {
                Text("观众积分来自已确认的礼物消费；主播积分来自已确认的礼物钻石收益。积分和升级门槛由服务端计算。",
                    color = Color(0xFFD9C9AC), fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
    }
}

@Composable
private fun QumiLevelSummary(title: String, level: Int, points: Long, nextPoints: Long, pointsLabel: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xCC302318)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = Color(0xFFFFE8B8), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("LV.$level", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
        Text("$pointsLabel $points", color = Color.White, fontSize = 15.sp)
        Text(if (nextPoints > 0) "距离 LV.${level + 1} 还差 ${(nextPoints - points).coerceAtLeast(0)} 分" else "已达到最高等级",
            color = Color(0xFFD9C9AC), fontSize = 13.sp)
    }
}

@Composable
internal fun QumiTaskCenterScreen(session: UserSession, onBack: () -> Unit, onOpenMoments: () -> Unit, onOpenDiscovery: () -> Unit, onOpenLive: () -> Unit, onEditProfile: () -> Unit) {
    val token = session.token
    val context = LocalContext.current
    val claimPrefs = remember(session.id) { context.getSharedPreferences("qumi_task_claims", android.content.Context.MODE_PRIVATE) }
    val taskDate = remember { LocalDate.now(ZoneOffset.UTC).toString() }
    fun claimPreferenceKey(taskKey: String, claimPeriod: String) = "${session.id}:${if (claimPeriod == "once") "once" else taskDate}:$taskKey"
    fun claimRequestId(taskKey: String, claimPeriod: String): String {
        val key = claimPreferenceKey(taskKey, claimPeriod)
        return claimPrefs.getString(key, null) ?: UUID.randomUUID().toString().also {
            claimPrefs.edit().putString(key, it).apply()
        }
    }
    fun clearClaimRequestId(taskKey: String, claimPeriod: String) {
        claimPrefs.edit().remove(claimPreferenceKey(taskKey, claimPeriod)).apply()
    }
    var selectedCategory by remember { mutableIntStateOf(0) }
    val categories = listOf("日常任务", "新手福利", "直播任务")
    var progress by remember { mutableStateOf<MomentDailyProgress?>(null) }
    var checkin by remember { mutableStateOf<DailyCheckin?>(null) }
    var loading by remember { mutableStateOf(false) }
    var checkinBusy by remember { mutableStateOf(false) }
    var checkinError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(token, reload) {
        loading = true
        error = null
        runCatching { api.momentDailyProgress(token) }
            .onSuccess { progress = it }
            .onFailure { error = it.message ?: "今日进度加载失败" }
        runCatching { api.dailyCheckin(token) }
            .onSuccess { checkin = it; checkinError = null }
            .onFailure { checkinError = it.message ?: "签到状态加载失败" }
        loading = false
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFF2F4F9))) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).background(Color(0xFFB9D8F6)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回") }
            Text("任务中心", Modifier.weight(1f), color = QumiText, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(48.dp))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("每日签到", color = QumiText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(checkin?.let { if (it.checkedIn) "已连续签到 ${it.streakDays} 天" else "当前连续签到 ${it.streakDays} 天" }
                                ?: checkinError ?: "正在读取签到状态…", color = QumiSubtle, fontSize = 13.sp)
                            if (checkin?.rewardConfigured == false) Text("签到记录已保存，平台尚未配置奖励。", color = QumiSubtle, fontSize = 12.sp)
                        }
                        if (checkinError != null) TextButton(onClick = { reload++ }) { Text("重试") }
                        Button(enabled = checkin != null && !checkinBusy && checkin?.checkedIn == false, onClick = {
                            checkinBusy = true
                            checkinError = null
                            scope.launch {
                                runCatching { api.submitDailyCheckin(token) }
                                    .onSuccess { checkin = it; reload++ }
                                    .onFailure { checkinError = it.message ?: "签到失败，请重试" }
                                checkinBusy = false
                            }
                        }) { Text(if (checkinBusy) "提交中" else if (checkin?.checkedIn == true) "已签到" else "签到") }
                    }
                }
            }
            item {
                Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(14.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                            categories.forEachIndexed { index, title ->
                                Column(
                                    Modifier.weight(1f).clickable { selectedCategory = index },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(title, color = if (selectedCategory == index) QumiText else QumiSubtle, fontSize = 14.sp, fontWeight = if (selectedCategory == index) FontWeight.SemiBold else FontWeight.Normal)
                                    Spacer(Modifier.width(24.dp).height(3.dp).clip(CircleShape).background(if (selectedCategory == index) Color(0xFF17171A) else Color.Transparent))
                                }
                            }
                        }
                        HorizontalDivider(color = QumiLine)
                        if (selectedCategory == 0) {
                            Text("今日互动统计", Modifier.padding(start = 16.dp, top = 14.dp), color = QumiSubtle, fontSize = 13.sp)
                            when {
                                loading && progress == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                                error != null && progress == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                                    TextButton(onClick = { reload++ }) { Text("${error} · 点击重试") }
                                }
                                else -> {
                                    val stats = progress ?: MomentDailyProgress(0, 0, 0)
                                    if (error != null) Text("刷新失败，以下为上次数据。${error}", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Color(0xFFB4233B), fontSize = 12.sp)
                                    listOf("发布动态" to stats.published, "点赞动态" to stats.liked, "评论动态" to stats.commented).forEach { (task, count) ->
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF9B9EAA), modifier = Modifier.size(28.dp))
                                            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(task, color = QumiText, fontSize = 15.sp)
                                                Text("今日 $count 次", color = QumiSubtle, fontSize = 13.sp)
                                            }
                                            TextButton(onClick = onOpenMoments) { Text("去动态") }
                                        }
                                        HorizontalDivider(color = QumiLine)
                                    }
                                    val dailyTasks = stats.tasks.filter { it.category == "daily" || it.category == "social" }
                                    if (dailyTasks.isEmpty()) Text("平台尚未配置日常任务。", Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = QumiSubtle, fontSize = 12.sp)
                                    else dailyTasks.forEach { task ->
                                        val taskComplete = task.progress >= task.targetCount
                                        val completeAction = when (task.key) {
                                            "follow_user", "first_follow" -> onOpenDiscovery
                                            "set_city" -> onEditProfile
                                            "publish_moment", "like_moment", "comment_moment" -> onOpenMoments
                                            else -> null
                                        }
                                        val actionLabel = when (task.key) {
                                            "follow_user", "first_follow" -> "去关注"
                                            "set_city" -> "去完善"
                                            "publish_moment" -> "去发布"
                                            "like_moment", "comment_moment" -> "去动态"
                                            else -> "未完成"
                                        }
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Text(task.title, color = QumiText, fontSize = 15.sp)
                                                Text("${task.progress.coerceAtMost(task.targetCount)}/${task.targetCount} · 奖励 ${task.rewardCoins} 金币", color = QumiSubtle, fontSize = 12.sp)
                                            }
                                            TextButton(enabled = !task.claimed && (taskComplete || completeAction != null), onClick = {
                                                if (taskComplete) scope.launch {
                                                    runCatching { api.claimTask(token, task.key, claimRequestId(task.key, task.claimPeriod)) }
                                                        .onSuccess { clearClaimRequestId(task.key, task.claimPeriod); reload++ }
                                                        .onFailure { error = it.message ?: "领取失败，请重试" }
                                                } else {
                                                    completeAction?.invoke()
                                                }
                                            }) { Text(if (task.claimed) "已领取" else if (taskComplete) "领取" else actionLabel) }
                                        }
                                        HorizontalDivider(color = QumiLine)
                                    }
                                }
                            }
                        } else {
                            val categoryKey = if (selectedCategory == 1) "newcomer" else "live"
                            val tasks = progress?.tasks.orEmpty().filter { it.category == categoryKey }
                            if (tasks.isEmpty()) Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                                Text("平台尚未配置此类任务", color = QumiSubtle, fontSize = 14.sp)
                            } else Column {
                                tasks.forEach { task ->
                                    val taskComplete = task.progress >= task.targetCount
                                    val completeAction = when (task.key) {
                                        "first_follow" -> onOpenDiscovery
                                        "set_city" -> onEditProfile
                                        "send_gift", "start_live" -> onOpenLive
                                        else -> null
                                    }
                                    val actionLabel = when (task.key) {
                                        "first_follow" -> "去关注"
                                        "set_city" -> "去完善"
                                        "send_gift" -> "去直播"
                                        "start_live" -> "去开播"
                                        else -> "未完成"
                                    }
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(task.title, color = QumiText, fontSize = 15.sp)
                                            Text("${task.progress.coerceAtMost(task.targetCount)}/${task.targetCount} · 奖励 ${task.rewardCoins} 金币", color = QumiSubtle, fontSize = 12.sp)
                                        }
                                        TextButton(enabled = !task.claimed && (taskComplete || completeAction != null), onClick = {
                                            if (taskComplete) scope.launch {
                                                runCatching { api.claimTask(token, task.key, claimRequestId(task.key, task.claimPeriod)) }
                                                    .onSuccess { clearClaimRequestId(task.key, task.claimPeriod); reload++ }
                                                    .onFailure { error = it.message ?: "领取失败，请重试" }
                                            } else {
                                                completeAction?.invoke()
                                            }
                                        }) { Text(if (task.claimed) "已领取" else if (taskComplete) "领取" else actionLabel) }
                                    }
                                    HorizontalDivider(color = QumiLine)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun QumiGuardScreen(token: String, onBack: () -> Unit) {
    var showRules by remember { mutableStateOf(false) }
    var supporters by remember { mutableStateOf<List<CreatorSupporter>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val api = remember { LivePlatformApi() }
    LaunchedEffect(token, reload) {
        error = null
        runCatching { api.creatorSupporters(token) }
            .onSuccess { supporters = it }
            .onFailure { error = it.message ?: "支持记录加载失败" }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F5F8))) {
        Row(Modifier.fillMaxWidth().height(56.dp).background(Color(0xFFA8A0FA)), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回") }
            Text("我的守护", Modifier.weight(1f), color = QumiText, fontSize = 20.sp)
            IconButton(onClick = { reload++ }) { Icon(Icons.Rounded.Refresh, contentDescription = "刷新支持记录", tint = QumiText) }
            TextButton(onClick = { showRules = true }) { Text("说明", color = QumiText) }
        }
        Text("近7日收到的礼物支持", Modifier.padding(horizontal = 18.dp, vertical = 12.dp), color = QumiSubtle, fontSize = 13.sp)
        when {
            supporters == null && error == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null && supporters == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TextButton(onClick = { reload++ }) { Text("${error} · 点击重试") } }
            supporters.isNullOrEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color(0xFFB9B2D3), modifier = Modifier.size(46.dp))
                    Text("近7日还没有礼物支持记录", color = QumiSubtle, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(supporters.orEmpty(), key = { _, supporter -> supporter.userId }) { index, supporter ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 15.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", color = Color(0xFF7265E8), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(34.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("用户 ID ${supporter.userId}", color = QumiText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("${supporter.giftCount} 件礼物", color = QumiSubtle, fontSize = 12.sp)
                        }
                        Text("${supporter.coins} 金币", color = QumiText, fontSize = 14.sp)
                    }
                }
            }
        }
    }
    if (showRules) QumiInfoSheet(
        title = "数据说明",
        message = "榜单统计当前账号近7日收到的已结算直播礼物，按金币总额排序。列表是礼物记录，不代表平台授予守护身份、特权或奖励。",
        onDismiss = { showRules = false },
    )
}

@Composable
internal fun QumiHostScreen(
    session: UserSession,
    daily: Earnings?,
    monthly: Earnings?,
    onBack: () -> Unit,
) {
    var sessions by remember { mutableStateOf<List<CreatorLiveSession>?>(null) }
    var sessionsError by remember { mutableStateOf<String?>(null) }
    var reloading by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val api = remember { LivePlatformApi() }
    LaunchedEffect(session.token, reload) {
        reloading = true
        sessionsError = null
        runCatching { api.creatorSessions(session.token) }
            .onSuccess { sessions = it }
            .onFailure { sessionsError = it.message ?: "开播记录加载失败" }
        reloading = false
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F5F7))) {
        Column(
            Modifier.fillMaxWidth().background(Color(0xFF5F4CEB)).padding(bottom = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Color.White) }
                Text("主播后台", Modifier.weight(1f), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(48.dp))
            }
            Column(Modifier.padding(start = 24.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(session.username, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("ID：${session.id}", color = Color.White.copy(alpha = .9f), fontSize = 16.sp)
            }
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp).offset(y = (-16).dp)
                .clip(RoundedCornerShape(16.dp)).background(Color.White).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QumiHostStat("今日收益（钻石）", daily?.diamonds?.toString() ?: "暂不可用", Modifier.weight(1f))
                QumiHostStat("本月收益（钻石）", monthly?.diamonds?.toString() ?: "暂不可用", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3F3F4)).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QumiHostStat("今日礼物", daily?.gifts?.toString() ?: "暂不可用", Modifier.weight(1f))
                QumiHostStat("本月礼物", monthly?.gifts?.toString() ?: "暂不可用", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("最近开播", Modifier.weight(1f), color = QumiText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { reload++ }, enabled = !reloading) { Icon(Icons.Rounded.Refresh, "刷新开播记录", tint = QumiText) }
            }
            when {
                reloading && sessions == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                sessionsError != null && sessions == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { reload++ }) { Text("${sessionsError} · 点击重试") }
                }
                sessions.isNullOrEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(if (sessionsError != null) "${sessionsError} · 下拉刷新" else "还没有开播记录", color = QumiSubtle, fontSize = 15.sp)
                }
                else -> LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sessions.orEmpty(), key = { it.id }) { live ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF6F6F8)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("场次 ${live.id}", color = QumiText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text("${live.startedAt.replace('T', ' ').take(16)} · ${formatLiveDuration(live.durationSeconds)}", color = QumiSubtle, fontSize = 12.sp)
                            }
                            Text(if (live.status == "OPEN") "直播中" else "已结束", color = if (live.status == "OPEN") Color(0xFF27966C) else QumiSubtle, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatLiveDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}小时${minutes}分钟" else "${minutes}分钟"
}

@Composable
internal fun QumiIdentityScreen(
    verification: IdentityVerificationStatus?,
    loading: Boolean,
    starting: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onStartRealName: () -> Unit,
) {
    var confirmRealName by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    if (confirmRealName) QumiConfirmSheet(
        title = "开始实名认证",
        message = "将打开平台配置的认证服务。只有在服务商页面继续后才会提交认证资料；本应用不会收集证件信息。",
        confirmLabel = if (starting) "正在连接…" else "继续",
        busy = starting,
        onDismiss = { confirmRealName = false },
        onConfirm = { confirmRealName = false; onStartRealName() },
    )
    notice?.let { message -> QumiInfoSheet(title = "认证状态", message = message, onDismiss = { notice = null }) }
    val items = listOf(
        Triple("实名认证", "提高交友成功率", Icons.Rounded.Badge),
        Triple("微信认证", "可使用微信进行打款", Icons.Rounded.ChatBubbleOutline),
        Triple("真人认证", "提高交友成功率", Icons.Rounded.Face),
        Triple("语音签名", "提高交友成功率", Icons.Rounded.Mic),
        Triple("手机认证", "提高交友成功率", Icons.Rounded.PhoneAndroid),
    )
    val colors = listOf(
        Color(0xFFFF9C98), Color(0xFF18BD19), Color(0xFF85A8F3),
        Color(0xFFFFC994), Color(0xFFB6A0F5),
    )
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F5F7))) {
        QumiPageTitle("我的认证", onBack, onRefresh)
        if (error != null) Text(error, Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), color = Color(0xFFB84B52), fontSize = 13.sp)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(items.size) { index ->
                val (title, subtitle, icon) = items[index]
                val status = if (index == 0) when {
                    loading -> "正在读取"
                    verification?.status == "VERIFIED" -> "已认证"
                    verification?.status == "PENDING" -> "认证中"
                    verification?.status == "REJECTED" -> "未通过"
                    verification?.enabled == true -> "未认证"
                    verification != null -> "服务未启用"
                    else -> "点击重试"
                } else "暂未接入"
                Surface(Modifier.fillMaxWidth().clickable {
                    if (index != 0) notice = "${title}服务暂未接入，当前没有提交任何资料。"
                    else when {
                        verification?.status == "VERIFIED" -> notice = "该账号已完成实名认证。"
                        verification == null -> onRefresh()
                        !verification.enabled -> notice = "平台尚未配置实名认证服务，请稍后再试。"
                        verification?.enabled == true -> confirmRealName = true
                        else -> confirmRealName = true
                    }
                }, color = Color.White, shape = RoundedCornerShape(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth().height(108.dp).padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Box(
                            Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(colors[index]),
                            contentAlignment = Alignment.Center,
                        ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(title, color = QumiText, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                            Text(subtitle, color = Color(0xFF9A9A9E), fontSize = 15.sp)
                        }
                        Text(
                            status,
                            color = if (verification?.status == "VERIFIED" && index == 0) Color(0xFF42A56B) else Color(0xFFB9B9BD),
                            fontSize = 14.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QumiHostStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, color = QumiText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = QumiSubtle, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun QumiPersonalHomeScreen(
    session: UserSession,
    profile: UserProfile?,
    socialProfile: SocialProfile?,
    level: UserLevel?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    var dynamicTab by remember { mutableStateOf(false) }
    var dynamicPosts by remember { mutableStateOf(emptyList<MomentPost>()) }
    var dynamicLoading by remember { mutableStateOf(false) }
    var dynamicError by remember { mutableStateOf<String?>(null) }
    var dynamicNextCursor by remember { mutableStateOf("") }
    var dynamicRefresh by remember { mutableIntStateOf(0) }
    var deletePost by remember { mutableStateOf<MomentPost?>(null) }
    var deletingPost by remember { mutableStateOf(false) }
    var dynamicBusyPost by remember { mutableStateOf<String?>(null) }
    var dynamicCommentsFor by remember { mutableStateOf<MomentPost?>(null) }
    val scope = rememberCoroutineScope()
    val api = remember { LivePlatformApi() }
    LaunchedEffect(dynamicTab, session.token, dynamicRefresh) {
        if (!dynamicTab) return@LaunchedEffect
        dynamicLoading = true
        dynamicError = null
        runCatching { api.moments(session.token, "mine") }
            .onSuccess { (items, cursor) -> dynamicPosts = items; dynamicNextCursor = cursor }
            .onFailure { dynamicError = it.message ?: "动态加载失败"; dynamicPosts = emptyList(); dynamicNextCursor = "" }
        dynamicLoading = false
    }
    LazyColumn(Modifier.fillMaxSize().background(Color.White)) {
        item {
            Box(Modifier.fillMaxWidth().height(620.dp).background(Brush.verticalGradient(listOf(Color(0xFFEADBF5), Color(0xFFF4EFF8), Color.White)))) {
                Image(painterResource(R.drawable.qumi_profile_cover), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(575.dp).align(Alignment.BottomCenter))
                Box(Modifier.fillMaxWidth().height(575.dp).align(Alignment.BottomCenter).background(Color.White.copy(alpha = .12f)))
                Column(Modifier.align(Alignment.TopCenter).padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${session.username}的主页", color = QumiText, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                }
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 4.dp)) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Color.White) }
                TextButton(onClick = onEdit, modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp)) { Text("编辑", color = Color.White) }
                QumiUserAvatar(
                    session.token, session.avatar,
                    Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 8.dp).size(76.dp).clip(CircleShape).background(Color.White),
                    "个人头像",
                ) {
                    Image(painterResource(R.drawable.qumi_default_avatar), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(session.username, color = QumiText, fontSize = 27.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color(0xFF7774FF), shape = CircleShape) { Text("ID ${session.id}", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Color.White, fontSize = 12.sp) }
                    Text(if (profile?.source == "SELF_REPORTED") "资料已填写" else "资料未填写", Modifier.clip(CircleShape).background(Color(0xFFF1F1F4)).padding(horizontal = 10.dp, vertical = 4.dp), color = QumiSubtle, fontSize = 12.sp)
                }
                Text("等级信息", color = QumiSubtle, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QumiLevelTile("消费等级", level?.viewerLevel?.let { "Lv.$it" } ?: "—", Color(0xFFFFF7DF), Modifier.weight(1f))
                    QumiLevelTile("主播等级", level?.creatorLevel?.let { "Lv.$it" } ?: "—", Color(0xFFFCEBFA), Modifier.weight(1f))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().height(58.dp).background(Color.White), verticalAlignment = Alignment.CenterVertically) {
                listOf("关于TA", "动态").forEachIndexed { index, label ->
                    Column(Modifier.weight(1f).clickable { dynamicTab = index == 1 }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(label, color = if (dynamicTab == (index == 1)) QumiText else QumiSubtle, fontSize = 15.sp, fontWeight = if (dynamicTab == (index == 1)) FontWeight.SemiBold else FontWeight.Normal)
                        Box(Modifier.width(20.dp).height(3.dp).clip(CircleShape).background(if (dynamicTab == (index == 1)) QumiText else Color.Transparent))
                    }
                }
            }
            HorizontalDivider(color = QumiLine)
        }
        if (dynamicTab) {
            item {
                when {
                    dynamicLoading -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    dynamicError != null -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { TextButton(onClick = { dynamicRefresh++ }) { Text("${dynamicError} · 点击重试") } }
                    dynamicPosts.isEmpty() -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { Text("还没有发布动态", color = QumiSubtle) }
                    else -> Text("我的动态 · ${dynamicPosts.size}", Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), color = QumiSubtle, fontSize = 13.sp)
                }
            }
            if (dynamicError != null && dynamicPosts.isNotEmpty()) item { Text(dynamicError!!, Modifier.padding(horizontal = 18.dp), color = Color(0xFFB4233B), fontSize = 12.sp) }
            items(dynamicPosts, key = { it.id }) { post ->
                Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), color = Color(0xFFF7F7F9), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QumiUserAvatar(session.token, post.authorAvatar ?: session.avatar, Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFECE8FF)), "个人头像") {
                                Image(painterResource(R.drawable.qumi_default_avatar), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(session.username, color = QumiText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(post.createdAt.replace('T', ' ').take(16), color = QumiSubtle, fontSize = 11.sp)
                            }
                        }
                        Text(post.content, color = QumiText, fontSize = 15.sp, lineHeight = 22.sp)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.weight(1f))
                            TextButton(enabled = dynamicBusyPost == null, onClick = {
                                dynamicBusyPost = post.id
                                scope.launch {
                                    runCatching { api.likeMoment(session.token, post.id, !post.isLiked) }
                                        .onSuccess {
                                            dynamicPosts = dynamicPosts.map { current ->
                                                if (current.id == post.id) current.copy(
                                                    isLiked = !post.isLiked,
                                                    likeCount = (post.likeCount + if (post.isLiked) -1 else 1).coerceAtLeast(0),
                                                ) else current
                                            }
                                        }
                                        .onFailure { dynamicError = it.message ?: "点赞状态更新失败" }
                                    dynamicBusyPost = null
                                }
                            }) { Text("${if (post.isLiked) "已赞" else "点赞"} ${post.likeCount}") }
                            TextButton(onClick = { dynamicCommentsFor = post }) { Text("评论 ${post.commentCount}") }
                            TextButton(onClick = { deletePost = post }) { Text("删除", color = Color(0xFFB4233B), fontSize = 12.sp) }
                        }
                    }
                }
            }
            if (dynamicNextCursor.isNotBlank()) item {
                TextButton(
                    enabled = !dynamicLoading,
                    onClick = {
                        dynamicLoading = true
                        scope.launch {
                            runCatching { api.moments(session.token, "mine", dynamicNextCursor) }
                                .onSuccess { (items, cursor) ->
                                    dynamicPosts = dynamicPosts + items
                                    dynamicNextCursor = cursor
                                }
                                .onFailure { dynamicError = it.message ?: "加载更多失败" }
                            dynamicLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (dynamicLoading) "正在加载…" else "加载更多") }
            }
        } else {
            item {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("认证信息", color = QumiText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("认证信息请在实名认证页面查看", color = QumiSubtle, fontSize = 15.sp)
                    Text("个人信息", color = QumiText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        QumiProfileFact("地区", profile?.region)
                        QumiProfileFact("年龄", profile?.age?.let { "$it" })
                        QumiProfileFact("生日", profile?.birthDate)
                        QumiProfileFact("性别", when (profile?.gender) { "MALE" -> "男"; "FEMALE" -> "女"; else -> null })
                        socialProfileFacts(socialProfile).forEach { (label, value) -> QumiProfileFact(label, value) }
                        socialProfile?.profileTags?.takeIf { it.isNotEmpty() }?.let { QumiProfileFact("标签", it.joinToString("、")) }
                        QumiProfileFact("征友条件", socialProfile?.datingPreferences)
                    }
                    Text("${if (socialProfile?.isPublic == true) "扩展资料对外公开" else "扩展资料仅自己可见"}；所有资料由本人填写，未经身份核验。", color = QumiSubtle, fontSize = 13.sp)
                }
            }
        }
    }
    deletePost?.let { post ->
        QumiConfirmSheet(
            title = "删除这条动态？",
            message = "删除后将从动态列表中移除。",
            confirmLabel = "删除",
            busy = deletingPost,
            onDismiss = { if (!deletingPost) deletePost = null },
            onConfirm = {
                deletingPost = true
                scope.launch {
                    runCatching { api.deleteMoment(session.token, post.id) }
                        .onSuccess { dynamicPosts = dynamicPosts.filterNot { it.id == post.id }; deletePost = null }
                        .onFailure { dynamicError = it.message ?: "删除失败，请重试" }
                    deletingPost = false
                }
            },
        )
    }
    dynamicCommentsFor?.let { post ->
        QumiMomentCommentsSheet(token = session.token, post = post, onDismiss = { dynamicCommentsFor = null }, onCountChanged = { delta ->
            dynamicPosts = dynamicPosts.map { current ->
                if (current.id == post.id) current.copy(commentCount = (current.commentCount + delta).coerceAtLeast(0)) else current
            }
        })
    }
}

@Composable
private fun QumiLevelTile(label: String, level: String, background: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(background).padding(horizontal = 8.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(level, color = Color(0xFF666B79), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = Color(0xFF777B86), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QumiProfileFact(label: String, value: String?) {
    Text("$label：${value ?: "暂未填写"}", color = Color(0xFF4C5366), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private fun socialProfileFacts(profile: SocialProfile?): List<Pair<String, String>> {
    if (profile == null) return emptyList()
    val income = when (profile.annualIncome) {
        "UNDER_50K" -> "5 万以下"
        "50K_100K" -> "5-10 万"
        "100K_200K" -> "10-20 万"
        "200K_500K" -> "20-50 万"
        "OVER_500K" -> "50 万以上"
        else -> null
    }
    val marital = when (profile.maritalStatus) {
        "SINGLE" -> "单身"
        "IN_RELATIONSHIP" -> "恋爱中"
        "MARRIED" -> "已婚"
        "DIVORCED" -> "离异"
        "WIDOWED" -> "丧偶"
        else -> null
    }
    return listOfNotNull(
        profile.heightCm?.let { "身高" to "$it cm" },
        profile.weightKg?.let { "体重" to "$it kg" },
        profile.occupation?.let { "工作" to it },
        income?.let { "年收入" to it },
        profile.city?.let { "所在地" to it },
        marital?.let { "婚姻" to it },
    )
}

@Composable
internal fun QumiPublicProfileScreen(
    token: String,
    userId: Long,
    fallbackName: String,
    onBack: () -> Unit,
    onMessage: (String, String) -> Unit,
    onBlock: (Long) -> Unit,
    onReport: (Long) -> Unit,
) {
    var user by remember(userId) { mutableStateOf<PublicUser?>(null) }
    var loading by remember(userId) { mutableStateOf(true) }
    var error by remember(userId) { mutableStateOf<String?>(null) }
    var profileRefresh by remember(userId) { mutableIntStateOf(0) }
    var selectedProfileTab by remember(userId) { mutableIntStateOf(0) }
    var posts by remember(userId) { mutableStateOf(emptyList<MomentPost>()) }
    var nextCursor by remember(userId) { mutableStateOf("") }
    var postsLoading by remember(userId) { mutableStateOf(false) }
    var postsError by remember(userId) { mutableStateOf<String?>(null) }
    var postsPagingError by remember(userId) { mutableStateOf(false) }
    var actionError by remember(userId) { mutableStateOf<String?>(null) }
    var postsRefresh by remember(userId) { mutableIntStateOf(0) }
    var followBusy by remember(userId) { mutableStateOf(false) }
    var supporterBoard by remember(userId) { mutableStateOf<CreatorSupporterBoard?>(null) }
    var supporterError by remember(userId) { mutableStateOf<String?>(null) }
    var supporterRefresh by remember(userId) { mutableIntStateOf(0) }
    var socialProfile by remember(userId) { mutableStateOf<SocialProfile?>(null) }
    var likeBusyId by remember(userId) { mutableStateOf<String?>(null) }
    var commentsFor by remember(userId) { mutableStateOf<MomentPost?>(null) }
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(token, userId, profileRefresh) {
        loading = true
        error = null
        runCatching {
            api.searchUsers(token, userId.toString()).firstOrNull { it.id == userId }
                ?: throw IllegalStateException("该用户不存在或暂不可查看")
        }.onSuccess { found ->
            user = found
            runCatching { api.recordProfileVisit(token, userId) }
        }.onFailure {
            user = null
            error = it.message ?: "主页加载失败"
        }
        loading = false
    }

    LaunchedEffect(token, userId, profileRefresh) {
        socialProfile = runCatching { api.publicSocialProfile(token, userId) }.getOrNull()
    }

    LaunchedEffect(token, user?.id, selectedProfileTab, postsRefresh) {
        val targetId = user?.id ?: return@LaunchedEffect
        if (selectedProfileTab != 1) return@LaunchedEffect
        postsLoading = true
        postsError = null
        postsPagingError = false
        runCatching { api.moments(token, "user", userId = targetId) }
            .onSuccess { (items, cursor) -> posts = items; nextCursor = cursor }
            .onFailure { postsError = it.message ?: "动态加载失败"; posts = emptyList(); nextCursor = "" }
        postsLoading = false
    }

    LaunchedEffect(token, user?.id, selectedProfileTab, supporterRefresh) {
        val targetId = user?.id ?: return@LaunchedEffect
        if (selectedProfileTab != 2) return@LaunchedEffect
        supporterBoard = null
        supporterError = null
        runCatching { api.publicCreatorSupporters(token, targetId) }
            .onSuccess { supporterBoard = it }
            .onFailure { supporterError = it.message ?: "守护榜加载失败" }
    }

    fun loadMorePosts() {
        val targetId = user?.id ?: return
        val cursor = nextCursor
        if (cursor.isBlank() || postsLoading) return
        postsLoading = true
        postsError = null
        scope.launch {
            runCatching { api.moments(token, "user", cursor, targetId) }
                .onSuccess { (items, newCursor) ->
                    posts = posts + items.filterNot { item -> posts.any { it.id == item.id } }
                    nextCursor = newCursor
                    postsPagingError = false
                }
                .onFailure { postsError = it.message ?: "加载更多动态失败"; postsPagingError = true }
            postsLoading = false
        }
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = QumiText) }
            Text(user?.username ?: fallbackName, Modifier.weight(1f), color = QumiText, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Row {
                TextButton(enabled = user != null, onClick = { user?.let { onReport(it.id) } }) { Text("举报", color = QumiSubtle) }
                TextButton(enabled = user != null, onClick = { user?.let { onBlock(it.id) } }) { Text("屏蔽", color = QumiSubtle) }
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            user == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "主页暂不可用", color = QumiSubtle)
                    TextButton(onClick = { profileRefresh++ }) { Text("重试") }
                }
            }
            else -> {
                val profile = user!!
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Box(Modifier.fillMaxWidth().height(238.dp).background(Brush.verticalGradient(listOf(Color(0xFFEADBF5), Color(0xFFF4EFF8), Color.White)))) {
                            Image(painterResource(R.drawable.qumi_profile_cover), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .2f)))
                            QumiUserAvatar(
                                token, profile.avatar,
                                Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 16.dp).size(82.dp).clip(CircleShape).background(Color(0xFFECE8FF)),
                                "${profile.username}的头像",
                            ) {
                                Text(profile.username.take(1), color = QumiPurple, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text(profile.username, color = QumiText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                            Text("ID ${profile.id}", color = QumiSubtle, fontSize = 13.sp)
                            profile.description?.let { Text(it, color = Color(0xFF4C5366), fontSize = 14.sp, lineHeight = 20.sp) }
                            Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(enabled = !followBusy, onClick = {
                                    followBusy = true
                                    scope.launch {
                                        val follow = !profile.isFollowing
                                        runCatching { api.setFollowing(token, profile.id, follow) }
                                            .onSuccess { user = profile.copy(isFollowing = follow) }
                                            .onFailure { actionError = it.message ?: "关注状态更新失败" }
                                        followBusy = false
                                    }
                                }, modifier = Modifier.weight(1f)) { Text(if (followBusy) "处理中…" else if (profile.isFollowing) "已关注" else "关注") }
                                OutlinedButton(onClick = { onMessage(profile.id.toString(), profile.username) }, modifier = Modifier.weight(1f)) { Text("私信") }
                            }
                            actionError?.let { Text(it, color = Color(0xFFB4233B), fontSize = 12.sp) }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf("关于TA", "动态", "守护").forEachIndexed { index, label ->
                                val selected = selectedProfileTab == index
                                Column(Modifier.weight(1f).fillMaxSize().clickable { selectedProfileTab = index }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(label, color = if (selected) QumiText else QumiSubtle, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                    if (selected) Spacer(Modifier.padding(top = 5.dp).width(22.dp).height(3.dp).clip(CircleShape).background(QumiPurple))
                                }
                            }
                        }
                        HorizontalDivider(color = QumiLine)
                    }
                    if (selectedProfileTab == 0) item {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("个人介绍", color = QumiText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(profile.description?.takeIf { it.isNotBlank() } ?: "对方还没有填写公开介绍", color = QumiSubtle, fontSize = 14.sp, lineHeight = 21.sp)
                            Text("个人资料", color = QumiText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                            val details = socialProfile?.takeIf { it.isPublic }
                            if (details == null) {
                                Text("TA暂未公开更多资料", color = QumiSubtle, fontSize = 14.sp)
                            } else {
                                socialProfileFacts(details).forEach { (label, value) -> QumiProfileFact(label, value) }
                                details.profileTags.takeIf { it.isNotEmpty() }?.let { QumiProfileFact("标签", it.joinToString("、")) }
                                QumiProfileFact("征友条件", details.datingPreferences)
                            }
                            Text("公开资料由用户本人填写，未经身份核验。", color = Color(0xFF999BA4), fontSize = 12.sp)
                        }
                    } else if (selectedProfileTab == 1) {
                        postsError?.let { message -> item {
                            TextButton(onClick = { if (postsPagingError) loadMorePosts() else postsRefresh++ }, modifier = Modifier.fillMaxWidth()) { Text("$message · 点击重试") }
                        } }
                        if (postsLoading && posts.isEmpty()) item { Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                        if (!postsLoading && posts.isEmpty() && postsError == null) item { Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) { Text("还没有发布动态", color = QumiSubtle) } }
                        items(posts, key = { it.id }) { post ->
                            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    QumiUserAvatar(token, post.authorAvatar ?: profile.avatar, Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFECE8FF)), "${profile.username}的头像") {
                                        Text(profile.username.take(1), color = QumiPurple, fontWeight = FontWeight.SemiBold)
                                    }
                                    Column(Modifier.padding(start = 10.dp)) {
                                        Text(profile.username, color = QumiText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(post.createdAt.replace('T', ' ').take(16), color = QumiSubtle, fontSize = 11.sp)
                                    }
                                }
                                Text(post.content, color = QumiText, fontSize = 15.sp, lineHeight = 22.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(Modifier.weight(1f))
                                    TextButton(enabled = likeBusyId == null, onClick = {
                                        likeBusyId = post.id
                                        scope.launch {
                                            runCatching { api.likeMoment(token, post.id, !post.isLiked) }
                                                .onSuccess { posts = posts.map { if (it.id == post.id) it.copy(isLiked = !post.isLiked, likeCount = (post.likeCount + if (post.isLiked) -1 else 1).coerceAtLeast(0)) else it } }
                                                .onFailure { postsError = it.message ?: "点赞失败" }
                                            likeBusyId = null
                                        }
                                    }) { Text("${if (post.isLiked) "已赞" else "点赞"} ${post.likeCount}") }
                                    TextButton(onClick = { commentsFor = post }) { Text("评论 ${post.commentCount}") }
                                }
                            }
                            HorizontalDivider(color = QumiLine)
                        }
                        if (postsLoading && posts.isNotEmpty()) item { Text("正在加载…", Modifier.fillMaxWidth().padding(14.dp), color = QumiSubtle, textAlign = TextAlign.Center) }
                        if (nextCursor.isNotBlank()) item {
                            TextButton(enabled = !postsLoading, onClick = { loadMorePosts() }, modifier = Modifier.fillMaxWidth()) { Text(if (postsLoading) "正在加载…" else "加载更多") }
                        }
                    } else {
                        when {
                            supporterError != null -> item {
                                TextButton(onClick = { supporterRefresh++ }, modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp)) {
                                    Text("${supporterError} · 点击重试")
                                }
                            }
                            supporterBoard == null -> item { Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                            supporterBoard?.hidden == true -> item {
                                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                                    Text("对方暂未公开守护榜", color = QumiSubtle)
                                }
                            }
                            supporterBoard?.supporters.isNullOrEmpty() -> item {
                                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                                    Text("近7日还没有守护记录", color = QumiSubtle)
                                }
                            }
                            else -> itemsIndexed(supporterBoard?.supporters.orEmpty(), key = { _, supporter -> supporter.userId }) { index, supporter ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${index + 1}", color = QumiPurple, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(34.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("用户 ID ${supporter.userId}", color = QumiText, fontSize = 15.sp)
                                        Text("${supporter.giftCount} 件礼物", color = QumiSubtle, fontSize = 12.sp)
                                    }
                                    Text("${supporter.coins} 金币", color = QumiText, fontSize = 14.sp)
                                }
                                HorizontalDivider(color = QumiLine)
                            }
                        }
                    }
                }
            }
        }
    }
    commentsFor?.let { post ->
        QumiMomentCommentsSheet(token, post, onDismiss = { commentsFor = null }, onCountChanged = { delta ->
            posts = posts.map { if (it.id == post.id) it.copy(commentCount = (it.commentCount + delta).coerceAtLeast(0)) else it }
        })
    }
}

@Composable
private fun QumiBalanceCard(label: String, amount: String, background: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), color = Color.White, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().height(86.dp).background(background.copy(alpha = .28f)).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = QumiPurple, modifier = Modifier.size(22.dp)) }
            Column(Modifier.weight(1f).padding(start = 9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(label, color = QumiText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(amount, color = QumiText, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = QumiSubtle, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun QumiShortcut(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier.height(88.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)) }
        Text(label, Modifier.padding(top = 7.dp), color = QumiText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun QumiWalletScreen(
    wallet: Wallet?, daily: Earnings?, monthly: Earnings?, rechargeBusy: Boolean,
    rechargeOrders: List<RechargeOrder>, rechargeOrdersError: String?,
    onBack: () -> Unit, onRefresh: () -> Unit, onTopUp: (Int) -> Unit,
    onBills: (Int) -> Unit, onWithdraw: () -> Unit, onGiftHistory: () -> Unit,
) {
    var confirmRecharge by remember { mutableStateOf<Int?>(null) }
    confirmRecharge?.let { coins ->
        QumiConfirmSheet(
            title = "充值 $coins 金币",
            message = "实付 ¥${String.format(Locale.ROOT, "%.2f", coins / 100.0)}。确认后前往支付宝，到账结果可在充值记录查看。",
            confirmLabel = "前往支付",
            busy = rechargeBusy,
            onDismiss = { confirmRecharge = null },
            onConfirm = { confirmRecharge = null; onTopUp(coins) },
        )
    }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFB7DBFF), Color(0xFFF3E7EF), Color(0xFFEAF2F5))))) {
        QumiPageTitle("我的钱包", onBack, onRefresh)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("金币余额", color = QumiText, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                            Text(wallet?.balance?.let { "$it 金币" } ?: "暂不可用", color = Color(0xFFE83837), fontSize = 32.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F6F8)).padding(vertical = 17.dp)) {
                            QumiIncomeMetric("今日收入", daily?.diamonds?.let { "$it 钻石" } ?: "—", Modifier.weight(1f))
                            QumiIncomeMetric("本月收入", monthly?.diamonds?.let { "$it 钻石" } ?: "—", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onWithdraw, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape) { Text("提现", color = QumiPurple, fontSize = 18.sp) }
                            Button(onClick = { onBills(0) }, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape) { Text("账单明细", fontSize = 18.sp) }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().clickable { onBills(1) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color(0xFFE35778))
                    Text("玫瑰余额：暂不可用", Modifier.weight(1f).padding(start = 8.dp), color = QumiText, fontSize = 17.sp)
                    Text("兑换记录", color = QumiText, fontSize = 15.sp)
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = QumiSubtle)
                }
            }
            item {
                Text("充值金币", color = QumiText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(100, 500, 1000).forEach { coins ->
                        Surface(Modifier.weight(1f).clickable(enabled = !rechargeBusy) { confirmRecharge = coins }, color = Color.White, shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.LocalActivity, contentDescription = null, tint = Color(0xFFE77B71), modifier = Modifier.size(24.dp))
                                Text("$coins", color = QumiText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                                Text("¥${coins / 100}", color = Color(0xFFE83837), fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
            item {
                Surface(color = Color.White, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QumiSettingsRow("充值记录", Icons.Rounded.ReceiptLong, onClick = { onBills(2) })
                        QumiDivider()
                        QumiSettingsRow("送礼记录", Icons.Rounded.Favorite, onClick = onGiftHistory)
                        if (rechargeBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        rechargeOrdersError?.let { Text(it, color = QumiSubtle, fontSize = 13.sp) }
                        Text("最近 ${rechargeOrders.size} 笔充值订单", color = QumiSubtle, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QumiIncomeMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Color(0xFF85899A), fontSize = 17.sp)
        Text(value, color = QumiText, fontSize = 17.sp)
    }
}

@Composable
internal fun QumiBillsScreen(
    session: UserSession,
    api: LivePlatformApi,
    initialCategory: Int,
    rechargeOrders: List<RechargeOrder>,
    rechargeOrdersError: String?,
    giftHistory: GiftHistory?,
    giftHistoryLoading: Boolean,
    giftHistoryError: String?,
    roseBills: RoseBills?,
    roseBillsLoading: Boolean,
    roseBillsError: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadGiftHistory: () -> Unit,
    onLoadRoseBills: (Boolean) -> Unit,
) {
    var category by remember(initialCategory) { mutableIntStateOf(initialCategory.coerceIn(0, 3)) }
    var filter by remember { mutableIntStateOf(0) }
    var reload by remember { mutableIntStateOf(0) }
    var withdrawals by remember { mutableStateOf(emptyList<JSONObject>()) }
    var withdrawalError by remember { mutableStateOf<String?>(null) }
    var withdrawalsLoading by remember { mutableStateOf(false) }
    LaunchedEffect(category, reload) {
        if (category == 0) onLoadGiftHistory()
        if (category == 1) onLoadRoseBills(false)
        if (category == 3) {
            withdrawalsLoading = true
            withdrawalError = null
            try {
                val rows = api.businessRequest(session.token, "/revenue/withdrawals").optJSONArray("withdrawals")
                withdrawals = if (rows == null) emptyList() else (0 until rows.length()).map { rows.getJSONObject(it) }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { withdrawalError = "提现记录暂时无法加载，请刷新重试" }
            finally { withdrawalsLoading = false }
        }
    }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        QumiPageTitle("账单明细", onBack) { reload++; onRefresh() }
        Row(Modifier.fillMaxWidth().height(58.dp).background(Color.White), verticalAlignment = Alignment.CenterVertically) {
            listOf("余额明细", "玫瑰明细", "充值记录", "提现记录").forEachIndexed { index, title ->
                Column(Modifier.weight(1f).clickable { category = index; filter = 0 }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(title, color = if (category == index) QumiText else Color(0xFF4A4A4D), fontSize = 15.sp, fontWeight = if (category == index) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                    Box(Modifier.width(14.dp).height(3.dp).clip(CircleShape).background(if (category == index) QumiPurple else Color.Transparent))
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFFEDF2F5)).padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (category == 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "收入", "支出").forEachIndexed { index, title ->
                    FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(title) }, shape = CircleShape)
                }
            }
            Text(YearMonth.now().format(DateTimeFormatter.ofPattern("M月", Locale.CHINA)), color = QumiText, fontSize = 27.sp, fontWeight = FontWeight.Medium)
        }
        when (category) {
            0 -> when {
                giftHistoryLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { CircularProgressIndicator(Modifier.padding(top = 36.dp)) }
                giftHistoryError != null -> QumiEmptyState(giftHistoryError)
                filter == 1 -> QumiEmptyState("暂无收入记录")
                giftHistory?.orders.orEmpty().isEmpty() -> QumiEmptyState("数据为空")
                else -> LazyColumn(Modifier.fillMaxSize().background(QumiPage), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    items(giftHistory?.orders.orEmpty(), key = { it.id }) { row ->
                        QumiBillRow(row.giftName, "${row.quantity} 份 · ${row.createdAt}", "-${row.chargedCoins} 金币", row.status)
                    }
                }
            }
            1 -> when {
                roseBillsLoading && roseBills?.bills.orEmpty().isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { CircularProgressIndicator(Modifier.padding(top = 36.dp)) }
                roseBillsError != null && roseBills?.bills.orEmpty().isEmpty() -> QumiEmptyState(roseBillsError!!)
                roseBills?.bills.orEmpty().isEmpty() -> QumiEmptyState("数据为空")
                else -> LazyColumn(Modifier.fillMaxSize().background(QumiPage), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    items(roseBills?.bills.orEmpty(), key = { it.id }) { row ->
                        QumiBillRow("${row.giftName} ×${row.quantity}", "用户 ${row.viewerId} · ${row.createdAt}", "+${row.diamondReward} 钻石", row.status)
                    }
                    if (!roseBills?.nextCursor.isNullOrBlank()) {
                        item {
                            TextButton(onClick = { onLoadRoseBills(true) }, enabled = !roseBillsLoading, modifier = Modifier.fillMaxWidth()) {
                                Text(if (roseBillsLoading) "正在加载…" else "加载更多")
                            }
                        }
                    }
                }
            }
            2 -> when {
                rechargeOrdersError != null -> QumiEmptyState(rechargeOrdersError)
                rechargeOrders.isEmpty() -> QumiEmptyState("数据为空")
                else -> LazyColumn(Modifier.fillMaxSize().background(QumiPage), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    items(rechargeOrders, key = { it.id }) { order ->
                        QumiBillRow("充值 ${order.coins} 金币", "${order.id} · ¥${String.format(Locale.ROOT, "%.2f", order.amountFen / 100.0)}", order.status.let { if (it == "CREDITED") "已到账" else "待支付" }, order.status)
                    }
                }
            }
            else -> when {
                withdrawalsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { CircularProgressIndicator(Modifier.padding(top = 36.dp)) }
                withdrawalError != null -> QumiEmptyState(withdrawalError!!)
                withdrawals.isEmpty() -> QumiEmptyState("数据为空")
                else -> LazyColumn(Modifier.fillMaxSize().background(QumiPage), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    items(withdrawals, key = { it.optString("withdrawal_id") }) { row ->
                        QumiBillRow("提现 ${row.optLong("diamonds")} 钻石", "${row.optString("withdrawal_id")} · ${row.optString("created_at")}", "${row.optLong("payout_fen")} 分", row.optString("status"))
                    }
                }
            }
        }
    }
}

@Composable
private fun QumiBillRow(title: String, subtitle: String, amount: String, status: String) {
    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = QumiText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = QumiSubtle, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(amount, color = QumiText, fontSize = 15.sp)
            Text(billStatus(status), color = QumiSubtle, fontSize = 12.sp)
        }
    }
}

private fun billStatus(status: String): String = when (status) {
    "CREDITED" -> "已到账"
    "PENDING" -> "审核中"
    "APPROVED" -> "已通过"
    "REJECTED" -> "已驳回"
    "PAID" -> "已登记支付"
    "FAILED" -> "失败"
    else -> status.ifBlank { "处理中" }
}

@Composable
private fun QumiEmptyState(message: String) {
    Box(Modifier.fillMaxSize().background(Color(0xFFEDF2F5)), contentAlignment = Alignment.Center) {
        Text(message, Modifier.padding(24.dp), color = Color(0xFFB1B6C1), fontSize = 14.sp)
    }
}

@Composable
internal fun QumiSettingsScreen(
    session: UserSession,
    onBack: () -> Unit,
    onAbout: () -> Unit,
    onYouth: () -> Unit,
    onBlacklist: () -> Unit,
    onQualification: () -> Unit,
    onChangePassword: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("qumi_account_settings", android.content.Context.MODE_PRIVATE) }
    var recommendation by remember(session.id) { mutableStateOf(false) }
    var hideGuardian by remember(session.id) { mutableStateOf(false) }
    var privacyPreferencesLoaded by remember(session.id) { mutableStateOf(false) }
    var privacyError by remember(session.id) { mutableStateOf<String?>(null) }
    var privacySaving by remember(session.id) { mutableStateOf<String?>(null) }
    var privacyReload by remember(session.id) { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf<String?>(null) }
    var clearCacheConfirm by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableStateOf("…") }
    val scope = rememberCoroutineScope()
    val api = remember { LivePlatformApi() }
    LaunchedEffect(Unit) {
        cacheSize = withContext(Dispatchers.IO) { formatQumiBytes(context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }) }
    }
    LaunchedEffect(session.token, privacyReload) {
        privacyError = null
        runCatching { api.accountPrivacyPreferences(session.token) }
            .onSuccess { value ->
                recommendation = value.personalizedRecommendations
                hideGuardian = value.hideGuardian
                privacyPreferencesLoaded = true
                prefs.edit().putBoolean("recommendation", value.personalizedRecommendations).putBoolean("hide_guardian", value.hideGuardian).apply()
            }
            .onFailure { privacyError = it.message ?: "隐私设置加载失败" }
    }
    fun savePrivacyPreference(key: String, enabled: Boolean) {
        if (!privacyPreferencesLoaded || privacySaving != null) return
        val previousRecommendation = recommendation
        val previousHideGuardian = hideGuardian
        if (key == "personalized_recommendations") recommendation = enabled else hideGuardian = enabled
        privacySaving = key
        privacyError = null
        scope.launch {
            runCatching { api.saveAccountPrivacyPreference(session.token, key, enabled) }
                .onSuccess { value ->
                    recommendation = value.personalizedRecommendations
                    hideGuardian = value.hideGuardian
                    prefs.edit().putBoolean("recommendation", recommendation).putBoolean("hide_guardian", hideGuardian).apply()
                }
                .onFailure {
                    recommendation = previousRecommendation
                    hideGuardian = previousHideGuardian
                    privacyError = it.message ?: "保存失败，请重试"
                }
            privacySaving = null
        }
    }
    showInfo?.let { title ->
        QumiInfoSheet(title = title, message = if (title == "当前版本") "现场 Live ${BuildConfig.VERSION_NAME}" else "相关服务接口尚未接入。", onDismiss = { showInfo = null })
    }
    if (clearCacheConfirm) QumiConfirmSheet(
        title = "清除缓存",
        message = "清除本机缓存文件，不会删除账号或聊天记录。",
        confirmLabel = "清除",
        onDismiss = { clearCacheConfirm = false },
        onConfirm = {
            clearCacheConfirm = false
            scope.launch {
                withContext(Dispatchers.IO) { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                cacheSize = "0 B"
            }
        },
    )
    Column(Modifier.fillMaxSize().background(QumiPage)) {
        Row(Modifier.fillMaxWidth().height(92.dp).background(Color.White), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 18.dp).size(48.dp)) { Icon(Icons.Rounded.ArrowBack, "返回", tint = Color.Black) }
            Text("设置", Modifier.weight(1f), color = Color.Black, fontSize = 32.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.width(66.dp))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 25.dp, vertical = 25.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { QumiSettingsPageGroup { QumiSettingsPageRow("账户与安全", Icons.Rounded.Lock, onClick = onChangePassword) } }
            item {
                QumiSettingsPageGroup {
                    QumiSettingsPageRow("关于我们", Icons.Rounded.Info, onClick = onAbout)
                    QumiSettingsPageRow("当前版本", Icons.Rounded.Info, value = BuildConfig.VERSION_NAME, onClick = { showInfo = "当前版本" })
                    QumiSettingsPageRow("平台资质", Icons.Rounded.VerifiedUser, onClick = onQualification)
                    QumiSettingsPageToggle("个性化推荐", recommendation, enabled = privacyPreferencesLoaded && privacySaving == null, onChecked = {
                        savePrivacyPreference("personalized_recommendations", it)
                    })
                }
            }
            item {
                QumiSettingsPageGroup {
                    QumiSettingsPageToggle("隐藏我的守护榜", hideGuardian, enabled = privacyPreferencesLoaded && privacySaving == null, onChecked = {
                        savePrivacyPreference("hide_guardian", it)
                    }, subtitle = "开启后，其他人无法查看我的守护榜")
                }
            }
            if (privacySaving != null) item { Text("正在保存隐私设置…", Modifier.padding(horizontal = 6.dp), color = QumiSubtle, fontSize = 13.sp) }
            privacyError?.let { message -> item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), color = Color(0xFFB4233B), fontSize = 13.sp)
                    TextButton(onClick = { privacyReload++ }) { Text("重试") }
                }
            } }
            item {
                QumiSettingsPageGroup {
                    QumiSettingsPageRow("青少年模式", Icons.Rounded.ChildCare, onClick = onYouth)
                    QumiSettingsPageRow("清除缓存", Icons.Rounded.CleaningServices, value = cacheSize) { clearCacheConfirm = true }
                    QumiSettingsPageRow("拉黑列表", Icons.Rounded.Shield) { onBlacklist() }
                }
            }
            item {
                Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(12.dp)) {
                    QumiSettingsPageRow("退出登录", Icons.Rounded.Logout, danger = true, onClick = onLogout)
                }
            }
        }
    }
}

@Composable
private fun QumiSettingsPageGroup(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)) { content() }
}

@Composable
private fun QumiSettingsPageRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(90.dp).clickable(onClick = onClick).padding(horizontal = 25.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = if (danger) Color(0xFFCF343D) else Color(0xFF202020), fontSize = 26.sp)
        value?.let { Text(it, color = Color(0xFFB1B1B1), fontSize = 24.sp) }
        if (value == null || title == "清除缓存") Icon(Icons.Rounded.ChevronRight, null, tint = Color(0xFFD4D4D4), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun QumiSettingsPageToggle(title: String, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().height(if (subtitle == null) 90.dp else 125.dp).padding(horizontal = 25.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = Color(0xFF202020), fontSize = 26.sp)
            subtitle?.let { Text(it, color = Color(0xFFAAAAAA), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
        Box(Modifier.size(48.dp).toggleable(enabled = enabled, value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChecked), contentAlignment = Alignment.Center) {
            Switch(checked = checked, enabled = enabled, onCheckedChange = null, modifier = Modifier.scale(.6f),
                colors = androidx.compose.material3.SwitchDefaults.colors(uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF818181), uncheckedBorderColor = Color.Transparent, checkedThumbColor = Color.White, checkedTrackColor = QumiPurple))
        }
    }
}

@Composable
internal fun QumiProfileEditScreen(
    session: UserSession,
    profile: UserProfile?,
    socialProfile: SocialProfile?,
    profileLoaded: Boolean,
    profileLoadError: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onAvatarUpload: () -> Unit,
    onCoverUpload: () -> Unit,
    onRetry: () -> Unit,
    onSave: (Int, String, String, String?, SocialProfile) -> Unit,
) {
    var age by remember(profile) { mutableStateOf(profile?.age?.toString().orEmpty()) }
    var region by remember(profile) { mutableStateOf(profile?.region.orEmpty()) }
    var gender by remember(profile) { mutableStateOf(profile?.gender ?: "UNSPECIFIED") }
    var birthDate by remember(profile) { mutableStateOf(profile?.birthDate.orEmpty()) }
    var height by remember(socialProfile) { mutableStateOf(socialProfile?.heightCm?.toString().orEmpty()) }
    var weight by remember(socialProfile) { mutableStateOf(socialProfile?.weightKg?.toString().orEmpty()) }
    var occupation by remember(socialProfile) { mutableStateOf(socialProfile?.occupation.orEmpty()) }
    var annualIncome by remember(socialProfile) { mutableStateOf(socialProfile?.annualIncome.orEmpty()) }
    var city by remember(socialProfile) { mutableStateOf(socialProfile?.city.orEmpty()) }
    var maritalStatus by remember(socialProfile) { mutableStateOf(socialProfile?.maritalStatus.orEmpty()) }
    var tags by remember(socialProfile) { mutableStateOf(socialProfile?.profileTags?.joinToString("、").orEmpty()) }
    var datingPreferences by remember(socialProfile) { mutableStateOf(socialProfile?.datingPreferences.orEmpty()) }
    var isPublic by remember(socialProfile) { mutableStateOf(socialProfile?.isPublic ?: false) }
    val incomeOptions = listOf(
        "" to "不填写", "UNDER_50K" to "5万以下", "50K_100K" to "5-10万",
        "100K_200K" to "10-20万", "200K_500K" to "20-50万", "OVER_500K" to "50万以上",
    )
    val maritalOptions = listOf(
        "" to "不填写", "SINGLE" to "单身", "IN_RELATIONSHIP" to "恋爱中",
        "MARRIED" to "已婚", "DIVORCED" to "离异", "WIDOWED" to "丧偶",
    )
    val normalizedTags = tags.split(Regex("[,，、\\s]+")).map(String::trim).filter(String::isNotEmpty).distinct()
    val genderLocked = profile?.gender != null
    val birthDateValid = birthDate.isBlank() || runCatching { LocalDate.parse(birthDate) }.isSuccess
    val detailsValid = (height.isBlank() || height.toIntOrNull()?.let { it in 80..250 } == true)
        && (weight.isBlank() || weight.toIntOrNull()?.let { it in 20..300 } == true)
        && occupation.length <= 50 && city.length <= 60 && datingPreferences.length <= 300
        && normalizedTags.size <= 10 && normalizedTags.all { it.length <= 12 }
    Column(Modifier.fillMaxSize().background(QumiPage)) {
        QumiPageTitle("个人资料", onBack)
        if (!profileLoaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(profileLoadError ?: "正在读取已保存的资料…", color = QumiSubtle)
                    if (profileLoadError != null) TextButton(onClick = onRetry) { Text("重试") }
                }
            }
        } else Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QumiUserAvatar(session.token, session.avatar, Modifier.size(64.dp).clip(CircleShape).background(Color.White), "个人头像") {
                    Image(painterResource(R.drawable.qumi_default_avatar), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(session.username, color = QumiText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("账号 ID ${session.id}", color = QumiSubtle, fontSize = 14.sp)
                }
                TextButton(enabled = !busy, onClick = onAvatarUpload) { Text("更换头像") }
            }
            TextButton(onClick = onCoverUpload) { Text("设置直播封面") }
            Text("本人资料", color = QumiText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(if (profile?.source == "SELF_REPORTED") "本人填写，未经身份核验" else "年龄和地区需由本人填写。", color = QumiSubtle, fontSize = 13.sp)
            OutlinedTextField(age, { age = it.filter(Char::isDigit).take(3) }, label = { Text("当前年龄") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                birthDate,
                { birthDate = it.filter { char -> char.isDigit() || char == '-' }.take(10) },
                label = { Text("生日") },
                placeholder = { Text("YYYY-MM-DD") },
                supportingText = { Text("填写后，年龄以生日由服务端计算") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(region, { region = it.uppercase(Locale.ROOT).take(2) }, label = { Text("地区代码，例如 CN、US、KR") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(if (genderLocked) "性别（已填写，不可修改）" else "性别（本人填写后不可修改）", color = QumiText, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("UNSPECIFIED" to "不填写", "MALE" to "男", "FEMALE" to "女").forEach { (value, label) ->
                    FilterChip(selected = gender == value, enabled = !genderLocked, onClick = { gender = value }, label = { Text(label) })
                }
            }
            HorizontalDivider(color = QumiLine)
            Text("更多资料", color = QumiText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("填写内容由你决定是否展示在个人主页。", color = QumiSubtle, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(height, { height = it.filter(Char::isDigit).take(3) }, label = { Text("身高 cm") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(weight, { weight = it.filter(Char::isDigit).take(3) }, label = { Text("体重 kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(occupation, { occupation = it.take(50) }, label = { Text("工作") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(city, { city = it.take(60) }, label = { Text("所在地") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            QumiProfileChoiceGroup("年收入", annualIncome, incomeOptions) { annualIncome = it }
            QumiProfileChoiceGroup("婚姻状况", maritalStatus, maritalOptions) { maritalStatus = it }
            OutlinedTextField(tags, { tags = it.take(150) }, label = { Text("个人标签") }, supportingText = { Text("用顿号或逗号分隔，最多 10 个") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(datingPreferences, { datingPreferences = it.take(300) }, label = { Text("征友条件") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("公开扩展资料", color = QumiText, fontSize = 15.sp)
                    Text("关闭后，其他用户无法查看这些字段", color = QumiSubtle, fontSize = 12.sp)
                }
                Switch(checked = isPublic, enabled = !busy, onCheckedChange = { isPublic = it })
            }
            Button(onClick = {
                val ageValue = age.toIntOrNull() ?: return@Button
                onSave(ageValue, region, gender, birthDate.trim().takeIf(String::isNotEmpty), SocialProfile(
                    heightCm = height.toIntOrNull(),
                    weightKg = weight.toIntOrNull(),
                    occupation = occupation.trim().takeIf(String::isNotEmpty),
                    annualIncome = annualIncome.takeIf(String::isNotEmpty),
                    city = city.trim().takeIf(String::isNotEmpty),
                    maritalStatus = maritalStatus.takeIf(String::isNotEmpty),
                    profileTags = normalizedTags,
                    datingPreferences = datingPreferences.trim().takeIf(String::isNotEmpty),
                    isPublic = isPublic,
                ))
            }, enabled = !busy && age.toIntOrNull() in 1..120 && region.length == 2 && birthDateValid && detailsValid, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "正在保存…" else "保存资料")
            }
        }
    }
}

@Composable
private fun QumiProfileChoiceGroup(
    title: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = QumiText, fontSize = 14.sp)
        options.chunked(3).forEach { rowOptions ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowOptions.forEach { (value, label) ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = selected == value,
                        onClick = { onSelected(value) },
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
                repeat(3 - rowOptions.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun QumiPageTitle(title: String, onBack: () -> Unit, onRefresh: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(54.dp).background(Color.White), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = QumiText) }
        Text(title, Modifier.weight(1f), color = QumiText, fontSize = 21.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Medium)
        if (onRefresh == null) Spacer(Modifier.width(48.dp)) else IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = QumiText) }
    }
}

@Composable
private fun QumiSettingsGroup(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 14.dp)) { content() }
    }
}

@Composable
private fun QumiSettingsRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(68.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = if (danger) Color(0xFFE34C59) else Color(0xFF55565B), modifier = Modifier.size(23.dp))
        Text(title, Modifier.weight(1f).padding(start = 12.dp), color = if (danger) Color(0xFFE34C59) else QumiText, fontSize = 17.sp)
        value?.let { Text(it, color = Color(0xFFB0B2B8), fontSize = 15.sp) }
        if (value == null) Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color(0xFFD1D2D6), modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun QumiSettingsToggle(title: String, checked: Boolean, onChecked: (Boolean) -> Unit, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().height(if (subtitle == null) 68.dp else 82.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = QumiText, fontSize = 17.sp)
            subtitle?.let { Text(it, color = Color(0xFFB0B2B8), fontSize = 12.sp) }
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun QumiDivider() { HorizontalDivider(color = QumiLine, thickness = .6.dp) }

private fun formatQumiBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(Locale.ROOT, "%.0fMB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> String.format(Locale.ROOT, "%.0fKB", bytes / 1024.0)
    bytes > 0 -> "$bytes B"
    else -> "0 B"
}
