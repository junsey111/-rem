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
import androidx.compose.material3.AlertDialog
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
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

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
                Image(painterResource(R.drawable.qumi_default_avatar), contentDescription = "个人头像", contentScale = ContentScale.Crop, modifier = Modifier.size(82.dp).clip(CircleShape))
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
                        Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFECE8FF)).clickable { onOpenUser(user.id.toString(), user.username) }, contentAlignment = Alignment.Center) {
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
                        Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFECE8FF)), contentAlignment = Alignment.Center) {
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
internal fun QumiAnnouncementListScreen(onBack: () -> Unit, onOpenDetail: (Int) -> Unit) {
    val notices = listOf(
        Triple("绿色直播文明公约", "2025-09-28", R.drawable.qumi_announcement_covenant),
        Triple("严厉打击违规交易", "2025-09-28", R.drawable.qumi_announcement_trading),
    )
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F5F6))) {
        QumiPageTitle("平台活动", onBack)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items(notices.size) { index ->
                val (title, date, image) = notices[index]
                Column {
                    Text(date, Modifier.fillMaxWidth().padding(vertical = 18.dp), color = Color(0xFF99999D), fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Image(painterResource(image), contentDescription = title, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(9.dp)))
                            Text(title, Modifier.padding(start = 6.dp, top = 14.dp, bottom = 12.dp), color = QumiText, fontSize = 18.sp)
                            HorizontalDivider(color = QumiLine)
                            Row(Modifier.fillMaxWidth().height(52.dp).clickable { onOpenDetail(index) }, verticalAlignment = Alignment.CenterVertically) {
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
internal fun QumiAnnouncementDetailScreen(title: String, index: Int, onBack: () -> Unit) {
    val body = if (index == 0) listOf(
        "为保障社区用户的良好体验，维护平台的正常秩序，根据《计算机信息网络国际联网安全保护管理办法》、《互联网信息服务管理办法》、《互联网电子公告服务管理规定》、《全国人民代表大会常务委员会关于维护互联网安全的决定》、《互联网新闻信息服务管理规定》、《文化部关于加强网络表演管理工作的通知》《关于加强网络视听节目直播服务管理有关问题的通知》等相关法律规定，以及《服务条款》和《隐私政策》等相关协议和政策，特制定本公约。",
        "官方提供视频直播服务以及围绕视频直播展开的社区化服务，旨在为用户创建公平、快乐、自由、健康的网络社区。",
        "以下条款适用于平台所有主播及用户，适用于所有直播形式。",
        "用户说明：",
        "一、凡接受《服务条款》、《用户隐私政策》和本公约并成功注册的，都是官方用户。",
        "二、用户的尊严、自由和权利受社区平等保护。",
        "三、用户的经验值和其他虚拟财产受社区保护。",
        "四、用户有依社区规则在社区内活动和发表言论的权利。",
        "五、用户的个人隐私受社区保护，社区须采取严格措施，确保用户隐私信息不被泄露。",
        "六、用户须妥善保管账号和密码；不应将账号、密码转让、出租、出借、赠与或以其他形式转移给他人使用。",
        "七、用户依社区管理规则规范产生的行为，受社区保护。用户从事违反法律法规及社区管理规则规范的行为活动将受到相应处罚。",
        "八、用户的任何权利，以不影响其他用户的相同权利为限。社区规则可以对这些限制做出具体的规定。",
        "九、禁止军人直播或利用人体缺陷或者以展示人体变异等方式招徕用户。",
        "十、未经批准，任何机构和个人不得在平台使用“电视台”“广播电台”“电台”“TV”等广播电视专有名称开展直播。",
        "十一、任何机构和个人不得通过平台对重大政治、军事、经济、社会、文化、等活动或事件进行音视频直播。",
        "十二、平台禁止讨论涉及中央领导人和亲属的违规营销信息",
        "言论规则：",
        "一、用户在社区内的言论(包括但不限于文字、图片、音频、视频)不得违反国家的法律法规。根据《互联网信息服务管理办法》，用户的言论不得含有下列内容：",
        "1）违反宪法确定的基本原则的；",
        "2）危害国家安全，泄露国家秘密，颠覆国家政权，破坏国家统一的；",
        "3）损害国家荣誉和利益的；",
        "4）煽动民族仇恨、民族歧视、破坏民族团结的；",
        "5）破坏国家宗教政策，宣扬邪教和封建迷信的；",
        "6）散布谣言，扰乱社会秩序，破坏社会稳定的；",
        "7）散布淫秽、色情、赌博、暴力、凶杀、恐怖或者教唆犯罪的，含有任何性或性暗示的；",
        "8）侮辱或者诽谤他人，侵害他人合法权益的，含有辱骂、恐吓、威胁内容的；",
        "9）煽动非法集会、结社、游行、示威、聚众扰乱社会秩序的；",
        "10）其他可能引起或已经引起不良影响的政治话题；",
        "11）非法民间组织的名义活动；",
        "12）发送骚扰、垃圾广告、恶意诋毁、诱骗信息的；",
        "13）干扰本服务正常运营和侵犯其他用户或第三方合法权益内容的信息；",
        "14）严禁通过任何方式传播毒品、展示枪支、管制刀具等；",
        "15）含有法律、行政法规禁止的其他内容的信息；",
        "二、用户的言论应符合社区规则规定，发言前应了解相关规定，不发表与社区规则相违背的言论。",
        "三、用户应遵守社区秩序和网络道德，杜绝污言秽语，杜绝刷屏、恶意连麦等影响他人正常使用的行为。",
        "四、用户应尊重他人隐私，除非危及公众利益或经当事人同意，不得公开他人姓名、住址、电话等个人资料及其他隐私信息。",
        "五、未经社区许可，禁止发布广告或其他以获取商业利益为目的的内容。",
        "六、用户在社区内发表违规言论的，社区管理人员有权删除其全部或部分言论，并视情节和危害结果，对其给予适当处罚。",
        "七、用户对自己的言论承担全部责任。",
        "社区管理：",
        "存在以上严禁行为的，平台有权视情节轻重，作出严格的处罚，包括但不限于禁言、删除信息、警告、处以7天、30天或永久封号的处理。",
        "情节内容特别严重的平台将上报公安机关进行处罚。平台用户可使用“举报”功能对违规的主播、用户进行举报。直播平台核实后会立即对违规行为进行处理。平台将采取技术审核过滤、人工主动巡查、用户投诉等方式进行集中处置。",
        "在平台开通直播功能的用户，有义务配合社区营造一个文明向上的社区生态环境，直播用户应自觉维护好直播间的气氛，引导直播间的用户合法合规参与直播间互动。",
        "本公约的效力仅限于虚拟社区内部，不得解释为可以对用户或他人的现实权利和义务产生影响。本公约的各项条款，以符合国家法律法规为基本原则，若有不一致之处，以国家的法律法规为解释准则。用户因使用社区服务产生的现实权利和义务受国家法律法规、《服务条款》、《用户隐私政策》的保护和制约。",
        "本公约解释权归官方所有。官方有义务将之不断完善并及时公示。",
        "社区其他任何规定、条例与本公约冲突时，以本公约为准。",
        "官方运营团队",
        "2025年4月",
    ) else listOf(
        "趣蜜一直以来致力于给用户提供一个绿色、健康的直播交友环境，打造一个真实直播交友平台。",
        "坚决反对任何违规交易虚拟礼物，包括但不限于如下等行为：",
        "1、以任何形式自行或诱导、撮合、帮助、教唆他人进行违规交易或赌博的行为;",
        "2、以任何形式发布、传播与违规交易或赌博相关的违法信息；",
        "3、以任何形式任何理由在平台内进行刷礼返现行为;",
        "4、其他违反法律法规及平台规则涉嫌违规交易、赌博的行为。",
        "请广大用户自觉维护平台秩序，共同创造良好社交环境！任何人不得以任何形式、手段违规交易本平台的虚拟礼物及其他权益、资产等，或利用虚拟礼物及其他利益、资产实施违法违规行为；",
        "趣蜜将在全站范围内进一步开展打击违规交易行为的专项合规巡查行动，如发现任何用户存在相关违规行为，一经查实一律从严处罚(包括但不限于封停账号、限制相关功能、扣除收益等)或移交司法机关追究相关法律责任!",
        "官方运营团队",
        "2025年4月",
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
internal fun QumiSecretarySettingsScreen(onBack: () -> Unit, onOpenProfile: () -> Unit) {
    var showReport by remember { mutableStateOf(false) }
    if (showReport) QumiSystemReportDialog { showReport = false }
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
        QumiReadOnlyToggleRow("置顶聊天")
        QumiDivider()
        Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("清除聊天记录", color = Color.Black, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.Black)
        }
        QumiDivider()
        Row(Modifier.fillMaxWidth().height(66.dp).clickable { showReport = true }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("举报", color = Color.Black, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.Black)
        }
        QumiDivider()
        QumiReadOnlyToggleRow("拉黑")
    }
}

@Composable
private fun QumiReadOnlyToggleRow(title: String) {
    Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.Black, fontSize = 18.sp)
        Spacer(Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = false, onCheckedChange = {}, enabled = false)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun QumiSecretaryProfileScreen(onBack: () -> Unit) {
    var dynamic by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    if (showMore) QumiProfileMoreDialog(onDismiss = { showMore = false }, onReport = { showMore = false; showReport = true })
    if (showReport) QumiSystemReportDialog { showReport = false }
    val scroll = rememberLazyListState()
    val collapsed by remember { derivedStateOf { scroll.firstVisibleItemIndex >= 2 } }
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
            if (dynamic) item { QumiEmptyState("动态服务暂未接入") } else item {
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
                    listOf("现居：暂无" to "星座：暂无", "身高：暂无" to "体重：暂无", "职业：2131821821" to "年收入：暂无", "婚姻：保密" to "").forEach { (left, right) ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(left, Modifier.weight(1f), color = Color(0xFF454A64), fontSize = 24.sp)
                            Text(right, Modifier.weight(1f), color = Color(0xFF454A64), fontSize = 24.sp)
                        }
                        Spacer(Modifier.height(32.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("我的的标签", color = Color.Black, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(68.dp))
                    Text("交友心声", color = Color.Black, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(32.dp))
                    Text("想找个有缘且有趣的人，快来联系我吧～", color = Color(0xFF454A64), fontSize = 25.sp)
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
internal fun QumiTaskCenterScreen(token: String, onBack: () -> Unit, onOpenMoments: () -> Unit) {
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
                                    .onSuccess { checkin = it }
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
                                    Text("仅展示服务端记录的互动次数；奖励规则尚未配置。", Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = QumiSubtle, fontSize = 12.sp)
                                }
                            }
                        } else {
                            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                                Text("平台尚未配置此类任务", color = QumiSubtle, fontSize = 14.sp)
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
    if (showRules) AlertDialog(
        onDismissRequest = { showRules = false },
        title = { Text("数据说明") },
        text = { Text("榜单统计当前账号近7日收到的已结算直播礼物，按金币总额排序。列表是礼物记录，不代表平台授予守护身份、特权或奖励。") },
        confirmButton = { TextButton(onClick = { showRules = false }) { Text("知道了") } },
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
    if (confirmRealName) AlertDialog(
        onDismissRequest = { confirmRealName = false },
        title = { Text("开始实名认证") },
        text = { Text("将打开平台配置的认证服务。只有在服务商页面继续后才会提交认证资料；本应用不会收集证件信息。") },
        confirmButton = { TextButton(enabled = !starting, onClick = { confirmRealName = false; onStartRealName() }) { Text(if (starting) "正在连接…" else "继续") } },
        dismissButton = { TextButton(onClick = { confirmRealName = false }) { Text("取消") } },
    )
    notice?.let { message -> AlertDialog(onDismissRequest = { notice = null }, title = { Text("认证状态") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { notice = null }) { Text("知道了") } }) }
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
                        Text(post.content, color = QumiText, fontSize = 15.sp, lineHeight = 22.sp)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(post.createdAt.replace('T', ' ').take(16), Modifier.weight(1f), color = QumiSubtle, fontSize = 12.sp)
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
                        QumiProfileFact("性别", when (profile?.gender) { "MALE" -> "男"; "FEMALE" -> "女"; else -> null })
                    }
                    Text("资料由本人填写，未经身份核验。", color = QumiSubtle, fontSize = 13.sp)
                }
            }
        }
    }
    deletePost?.let { post ->
        AlertDialog(
            onDismissRequest = { if (!deletingPost) deletePost = null },
            title = { Text("删除这条动态？") },
            text = { Text("删除后将从动态列表中移除。") },
            confirmButton = { TextButton(enabled = !deletingPost, onClick = {
                deletingPost = true
                scope.launch {
                    runCatching { api.deleteMoment(session.token, post.id) }
                        .onSuccess { dynamicPosts = dynamicPosts.filterNot { it.id == post.id }; deletePost = null }
                        .onFailure { dynamicError = it.message ?: "删除失败，请重试" }
                    deletingPost = false
                }
            }) { Text(if (deletingPost) "删除中" else "删除") } },
            dismissButton = { TextButton(enabled = !deletingPost, onClick = { deletePost = null }) { Text("取消") } },
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

@Composable
internal fun QumiPublicProfileScreen(
    token: String,
    userId: Long,
    fallbackName: String,
    onBack: () -> Unit,
    onMessage: (String, String) -> Unit,
    onBlock: (Long) -> Unit,
) {
    var user by remember(userId) { mutableStateOf<PublicUser?>(null) }
    var loading by remember(userId) { mutableStateOf(true) }
    var error by remember(userId) { mutableStateOf<String?>(null) }
    var profileRefresh by remember(userId) { mutableIntStateOf(0) }
    var dynamicTab by remember(userId) { mutableStateOf(false) }
    var posts by remember(userId) { mutableStateOf(emptyList<MomentPost>()) }
    var nextCursor by remember(userId) { mutableStateOf("") }
    var postsLoading by remember(userId) { mutableStateOf(false) }
    var postsError by remember(userId) { mutableStateOf<String?>(null) }
    var postsPagingError by remember(userId) { mutableStateOf(false) }
    var actionError by remember(userId) { mutableStateOf<String?>(null) }
    var postsRefresh by remember(userId) { mutableIntStateOf(0) }
    var followBusy by remember(userId) { mutableStateOf(false) }
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

    LaunchedEffect(token, user?.id, dynamicTab, postsRefresh) {
        val targetId = user?.id ?: return@LaunchedEffect
        if (!dynamicTab) return@LaunchedEffect
        postsLoading = true
        postsError = null
        postsPagingError = false
        runCatching { api.moments(token, "user", userId = targetId) }
            .onSuccess { (items, cursor) -> posts = items; nextCursor = cursor }
            .onFailure { postsError = it.message ?: "动态加载失败"; posts = emptyList(); nextCursor = "" }
        postsLoading = false
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
            TextButton(enabled = user != null, onClick = { user?.let { onBlock(it.id) } }) { Text("屏蔽", color = QumiSubtle) }
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
                            Box(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 16.dp).size(82.dp).clip(CircleShape).background(Color(0xFFECE8FF)), contentAlignment = Alignment.Center) {
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
                            listOf("关于TA", "动态").forEachIndexed { index, label ->
                                val selected = dynamicTab == (index == 1)
                                Column(Modifier.weight(1f).fillMaxSize().clickable { dynamicTab = index == 1 }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(label, color = if (selected) QumiText else QumiSubtle, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                    if (selected) Spacer(Modifier.padding(top = 5.dp).width(22.dp).height(3.dp).clip(CircleShape).background(QumiPurple))
                                }
                            }
                        }
                        HorizontalDivider(color = QumiLine)
                    }
                    if (!dynamicTab) item {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("个人介绍", color = QumiText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(profile.description?.takeIf { it.isNotBlank() } ?: "对方还没有填写公开介绍", color = QumiSubtle, fontSize = 14.sp, lineHeight = 21.sp)
                            Text("公开资料由用户本人填写，未经身份核验。", color = Color(0xFF999BA4), fontSize = 12.sp)
                        }
                    } else {
                        postsError?.let { message -> item {
                            TextButton(onClick = { if (postsPagingError) loadMorePosts() else postsRefresh++ }, modifier = Modifier.fillMaxWidth()) { Text("$message · 点击重试") }
                        } }
                        if (postsLoading && posts.isEmpty()) item { Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                        if (!postsLoading && posts.isEmpty() && postsError == null) item { Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) { Text("还没有发布动态", color = QumiSubtle) } }
                        items(posts, key = { it.id }) { post ->
                            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(post.content, color = QumiText, fontSize = 15.sp, lineHeight = 22.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(post.createdAt.replace('T', ' ').take(16), Modifier.weight(1f), color = QumiSubtle, fontSize = 12.sp)
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
        AlertDialog(
            onDismissRequest = { confirmRecharge = null },
            title = { Text("充值 $coins 金币") },
            text = { Text("实付 ¥${String.format(Locale.ROOT, "%.2f", coins / 100.0)}。确认后前往支付宝，到账结果可在充值记录查看。") },
            confirmButton = { TextButton(onClick = { confirmRecharge = null; onTopUp(coins) }, enabled = !rechargeBusy) { Text("前往支付") } },
            dismissButton = { TextButton(onClick = { confirmRecharge = null }) { Text("取消") } },
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
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadGiftHistory: () -> Unit,
) {
    var category by remember(initialCategory) { mutableIntStateOf(initialCategory.coerceIn(0, 3)) }
    var filter by remember { mutableIntStateOf(0) }
    var reload by remember { mutableIntStateOf(0) }
    var withdrawals by remember { mutableStateOf(emptyList<JSONObject>()) }
    var withdrawalError by remember { mutableStateOf<String?>(null) }
    var withdrawalsLoading by remember { mutableStateOf(false) }
    LaunchedEffect(category, reload) {
        if (category == 0) onLoadGiftHistory()
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
            1 -> QumiEmptyState("当前账户暂未接入玫瑰账单")
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
    var recommendation by remember { mutableStateOf(prefs.getBoolean("recommendation", false)) }
    var hideGuardian by remember { mutableStateOf(prefs.getBoolean("hide_guardian", false)) }
    var showInfo by remember { mutableStateOf<String?>(null) }
    var clearCacheConfirm by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableStateOf("…") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        cacheSize = withContext(Dispatchers.IO) { formatQumiBytes(context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }) }
    }
    showInfo?.let { title ->
        AlertDialog(onDismissRequest = { showInfo = null }, title = { Text(title) }, text = { Text(if (title == "当前版本") "现场 Live ${BuildConfig.VERSION_NAME}" else "相关服务接口尚未接入。") }, confirmButton = { TextButton(onClick = { showInfo = null }) { Text("知道了") } })
    }
    if (clearCacheConfirm) AlertDialog(
        onDismissRequest = { clearCacheConfirm = false }, title = { Text("清除缓存") }, text = { Text("清除本机缓存文件，不会删除账号或聊天记录。") },
        confirmButton = { TextButton(onClick = {
            clearCacheConfirm = false
            scope.launch {
                withContext(Dispatchers.IO) { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                cacheSize = "0 B"
            }
        }) { Text("清除") } },
        dismissButton = { TextButton(onClick = { clearCacheConfirm = false }) { Text("取消") } },
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
                    QumiSettingsPageToggle("个性化推荐", recommendation, onChecked = {
                        recommendation = it
                        prefs.edit().putBoolean("recommendation", it).apply()
                    })
                }
            }
            item {
                QumiSettingsPageGroup {
                    QumiSettingsPageToggle("守护榜开关", hideGuardian, { hideGuardian = it; prefs.edit().putBoolean("hide_guardian", it).apply() }, "开启后不对外展示我的守护")
                }
            }
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
private fun QumiSettingsPageToggle(title: String, checked: Boolean, onChecked: (Boolean) -> Unit, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().height(if (subtitle == null) 90.dp else 125.dp).padding(horizontal = 25.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = Color(0xFF202020), fontSize = 26.sp)
            subtitle?.let { Text(it, color = Color(0xFFAAAAAA), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
        Box(Modifier.size(48.dp).toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChecked), contentAlignment = Alignment.Center) {
            Switch(checked = checked, onCheckedChange = null, modifier = Modifier.scale(.6f),
                colors = androidx.compose.material3.SwitchDefaults.colors(uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF818181), uncheckedBorderColor = Color.Transparent, checkedThumbColor = Color.White, checkedTrackColor = QumiPurple))
        }
    }
}

@Composable
internal fun QumiProfileEditScreen(
    session: UserSession,
    profile: UserProfile?,
    busy: Boolean,
    onBack: () -> Unit,
    onCoverUpload: () -> Unit,
    onSave: (Int, String, String) -> Unit,
) {
    var age by remember(profile) { mutableStateOf(profile?.age?.toString().orEmpty()) }
    var region by remember(profile) { mutableStateOf(profile?.region.orEmpty()) }
    var gender by remember(profile) { mutableStateOf(profile?.gender ?: "UNSPECIFIED") }
    Column(Modifier.fillMaxSize().background(QumiPage)) {
        QumiPageTitle("个人资料", onBack)
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(session.username, color = QumiText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("账号 ID ${session.id}", color = QumiSubtle, fontSize = 14.sp)
            TextButton(onClick = onCoverUpload) { Text("设置直播封面") }
            Text("本人资料", color = QumiText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(if (profile?.source == "SELF_REPORTED") "本人填写，未经身份核验" else "年龄和地区需由本人填写。", color = QumiSubtle, fontSize = 13.sp)
            OutlinedTextField(age, { age = it.filter(Char::isDigit).take(3) }, label = { Text("当前年龄") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(region, { region = it.uppercase(Locale.ROOT).take(2) }, label = { Text("地区代码，例如 CN、US、KR") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("性别（本人填写）", color = QumiText, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("UNSPECIFIED" to "不填写", "MALE" to "男", "FEMALE" to "女").forEach { (value, label) ->
                    FilterChip(selected = gender == value, onClick = { gender = value }, label = { Text(label) })
                }
            }
            Button(onClick = { age.toIntOrNull()?.let { onSave(it, region, gender) } }, enabled = !busy && age.toIntOrNull() in 1..120 && region.length == 2, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "正在保存…" else "保存资料")
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
