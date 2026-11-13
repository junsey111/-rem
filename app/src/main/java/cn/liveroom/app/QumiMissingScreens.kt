package cn.liveroom.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val MissingPaper = LiveStyle.canvas
private val MissingInk = LiveStyle.ink
private val MissingPurple = LiveStyle.accent

@Composable
private fun MissingTitle(title: String, onBack: () -> Unit, dark: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.ArrowBack, "返回", tint = if (dark) Color.White else MissingInk)
        }
        Text(title, Modifier.weight(1f), color = if (dark) Color.White else MissingInk, fontSize = 20.sp,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
internal fun QumiGuildScreen(onBack: () -> Unit) {
    var showRank by remember { mutableStateOf(false) }
    var showRecommended by remember { mutableStateOf(false) }
    var showCreateNotice by remember { mutableStateOf(false) }
    if (showCreateNotice) AlertDialog(onDismissRequest = { showCreateNotice = false }, title = { Text("暂不可创建公会") },
        text = { Text("创建公会需要实名认证。认证服务尚未接入，当前不会提交或修改认证信息。") },
        confirmButton = { TextButton(onClick = { showCreateNotice = false }) { Text("知道了") } })
    Box(Modifier.fillMaxSize().background(Color(0xFF17120D))) {
        Image(painterResource(R.drawable.qumi_guild_background), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .7f)
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            MissingTitle(if (showRank) "公会榜" else "公会广场", onBack, dark = true)
            if (showRank) {
                Row(Modifier.fillMaxWidth().padding(top = 28.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xCC21180D))
                    .clickable { showRank = false }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Leaderboard, null, tint = Color(0xFFFFD783), modifier = Modifier.size(36.dp))
                    Text("公会榜", Modifier.weight(1f).padding(start = 14.dp), color = Color(0xFFFFE5A8), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("返回广场", color = Color.White.copy(alpha = .8f), fontSize = 14.sp)
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Leaderboard, null, tint = Color(0xFFB69355), modifier = Modifier.size(56.dp))
                        Text("暂无公会榜单", color = Color(0xFFFFE5A8), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("公会排名服务暂未接入", color = Color(0xFFD2C5AE), fontSize = 14.sp)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 46.dp).clip(RoundedCornerShape(22.dp))
                    .background(Color(0xEE21180D)).clickable { showRank = true }.padding(horizontal = 18.dp, vertical = 23.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EmojiEvents, null, tint = Color(0xFFFFCE60), modifier = Modifier.size(44.dp))
                    Text("公会榜", Modifier.weight(1f).padding(start = 14.dp), color = Color(0xFFFFE89D), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("›", color = Color.White, fontSize = 28.sp)
                }
                Row(Modifier.padding(top = 24.dp).align(Alignment.CenterHorizontally).clip(RoundedCornerShape(10.dp))
                    .background(Color(0xCC21180D)).clickable { showRecommended = !showRecommended }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFD783), modifier = Modifier.size(20.dp))
                    Text("推荐公会", Modifier.padding(horizontal = 9.dp), color = Color(0xFFFFE5A8), fontSize = 17.sp)
                    Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFD783), modifier = Modifier.size(20.dp))
                }
                if (showRecommended) Text("暂无推荐公会", Modifier.align(Alignment.CenterHorizontally).padding(top = 18.dp),
                    color = Color(0xFFD2C5AE), fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Button(onClick = { showCreateNotice = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp).height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD994)), shape = RoundedCornerShape(30.dp)) {
                    Text("＋创建公会", color = Color(0xFF50371B), fontSize = 19.sp)
                }
            }
        }
    }
}

@Composable
internal fun QumiCloseFriendsScreen(session: UserSession, onBack: () -> Unit, onFindUser: () -> Unit) {
    var showRules by remember { mutableStateOf(false) }
    var relations by remember { mutableStateOf<CloseFriendRelations?>(null) }
    var targetId by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(session.token, refresh) {
        error = null
        runCatching { api.closeFriendRelations(session.token) }
            .onSuccess { relations = it }
            .onFailure { error = it.message ?: "蜜友关系加载失败" }
    }
    if (showRules) AlertDialog(onDismissRequest = { showRules = false }, title = { Text("蜜友关系") },
        text = { Text("蜜友关系由双方自愿建立。发送邀请后，对方接受才会建立关系；任一方可以随时解除。当前不收费，也不附带礼物或等级权益。") },
        confirmButton = { TextButton(onClick = { showRules = false }) { Text("知道了") } })
    Box(Modifier.fillMaxSize().background(Color(0xFFFFE6EF))) {
        Image(painterResource(R.drawable.qumi_friends_hero), null, modifier = Modifier.fillMaxWidth().height(330.dp), contentScale = ContentScale.Crop)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回", tint = Color.White) }
                Text("我的蜜友", Modifier.weight(1f), color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
                TextButton(onClick = { refresh++ }) { Text("刷新", color = Color.White, fontSize = 14.sp) }
                TextButton(onClick = { showRules = true }) { Text("规则", color = Color.White, fontSize = 16.sp) }
            }
            Spacer(Modifier.height(265.dp))
            Column(Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFFFFF8F6)).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text("亲密关系", color = Color(0xFF9F3C59), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("账号 ${session.publicId}", Modifier.padding(top = 3.dp), color = Color(0xFF936B72), fontSize = 13.sp)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = targetId, onValueChange = { value -> targetId = value.filter(Char::isDigit).take(20) },
                        modifier = Modifier.weight(1f), singleLine = true, label = { Text("输入对方用户 ID") })
                    Button(enabled = !busy && targetId.matches(Regex("[1-9][0-9]{5}")) && targetId.toLongOrNull() != session.publicId,
                        onClick = {
                            val code = targetId
                            busy = true
                            scope.launch {
                                runCatching {
                                    val id = api.searchUsers(session.token, code).firstOrNull { it.publicId.toString() == code }?.id
                                        ?: kotlin.error("没有找到这个现场号")
                                    api.inviteCloseFriend(session.token, id)
                                }
                                    .onSuccess { targetId = ""; refresh++ }
                                    .onFailure { error = it.message ?: "邀请发送失败" }
                                busy = false
                            }
                        }, modifier = Modifier.padding(start = 8.dp)) { Text(if (busy) "提交中" else "邀请") }
                }
                TextButton(onClick = onFindUser, modifier = Modifier.align(Alignment.End)) { Text("搜索用户") }
                if (error != null) TextButton(onClick = { refresh++ }) { Text("${error} · 点击重试", color = Color(0xFFB4233B)) }
                val state = relations
                if (state == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state != null) {
                    CloseFriendSection("待处理邀请 · ${state.incoming.size}", state.incoming) { person ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(enabled = !busy, onClick = {
                                busy = true
                                scope.launch {
                                    runCatching { api.acceptCloseFriend(session.token, person.id) }
                                        .onSuccess { refresh++ }.onFailure { error = it.message ?: "接受邀请失败" }
                                    busy = false
                                }
                            }) { Text("接受") }
                            TextButton(enabled = !busy, onClick = {
                                busy = true
                                scope.launch {
                                    runCatching { api.removeCloseFriend(session.token, person.id) }
                                        .onSuccess { refresh++ }.onFailure { error = it.message ?: "忽略邀请失败" }
                                    busy = false
                                }
                            }) { Text("忽略") }
                        }
                    }
                    CloseFriendSection("我的蜜友 · ${state.friends.size}", state.friends) { person ->
                        TextButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                runCatching { api.removeCloseFriend(session.token, person.id) }
                                    .onSuccess { refresh++ }.onFailure { error = it.message ?: "解除关系失败" }
                                busy = false
                            }
                        }) { Text("解除") }
                    }
                    CloseFriendSection("已发送邀请 · ${state.outgoing.size}", state.outgoing) { person ->
                        Text("待对方接受", Modifier.padding(horizontal = 8.dp), color = Color(0xFF9E7D85), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CloseFriendSection(title: String, users: List<CloseFriendUser>, action: @Composable (CloseFriendUser) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(title, Modifier.padding(vertical = 8.dp), color = Color(0xFF6D3748), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (users.isEmpty()) Text("暂无", Modifier.padding(vertical = 7.dp), color = Color(0xFF9E7D85), fontSize = 13.sp)
        users.forEach { person ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFF06D9C), modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f).padding(start = 9.dp)) {
                    Text(person.username, color = Color(0xFF6D3748), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("ID ${person.id}", color = Color(0xFF9E7D85), fontSize = 12.sp)
                }
                action(person)
            }
            HorizontalDivider(color = Color(0xFFF0DFE3))
        }
    }
}

@Composable
internal fun QumiBlacklistScreen(token: String, onBack: () -> Unit) {
    var users by remember { mutableStateOf(emptyList<PublicUser>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<Long?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(token, refresh) {
        loading = true
        error = null
        runCatching { api.blockedUsers(token) }
            .onSuccess { users = it }
            .onFailure { error = it.message ?: "黑名单加载失败"; users = emptyList() }
        loading = false
    }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        MissingTitle("拉黑列表", onBack)
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TextButton(onClick = { refresh++ }) { Text("${error} · 点击重试") } }
            users.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Shield, null, tint = Color(0xFF9298A8), modifier = Modifier.size(48.dp))
                    Text("黑名单为空", color = MissingInk, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text("你屏蔽的用户会显示在这里", color = Color(0xFF858997), fontSize = 14.sp)
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(users, key = { it.id }) { user ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFF0F1F4)), contentAlignment = Alignment.Center) {
                            Text(user.username.take(1), color = MissingPurple, fontSize = 17.sp)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(user.username, color = MissingInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("ID ${user.id}", color = Color(0xFF858997), fontSize = 12.sp)
                        }
                        TextButton(enabled = busyId == null, onClick = {
                            busyId = user.id
                            scope.launch {
                                runCatching { api.setBlocked(token, user.id, false) }
                                    .onSuccess { users = users.filterNot { it.id == user.id } }
                                    .onFailure { error = it.message ?: "解除屏蔽失败" }
                                busyId = null
                            }
                        }) { Text("解除屏蔽", color = MissingPurple) }
                    }
                    HorizontalDivider(color = Color(0xFFF0F1F4))
                }
            }
        }
    }
}

@Composable
internal fun QumiQualificationScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MissingPaper)) {
        MissingTitle("平台资质", onBack)
        Column(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(18.dp)) {
            Icon(Icons.Rounded.VerifiedUser, null, tint = MissingPurple, modifier = Modifier.size(36.dp))
            Text("资质信息尚未配置", Modifier.padding(top = 14.dp), color = MissingInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("平台主体与许可证资料需要运营方提供并核验后展示。", Modifier.padding(top = 8.dp), color = Color(0xFF666A76), fontSize = 14.sp)
        }
    }
}

@Composable
internal fun QumiHelpScreen(onBack: () -> Unit, onSupport: () -> Unit) {
    val questions = listOf("如何开始直播？" to "直播服务需要完成主播配置并授权相机、麦克风。当前账号和服务状态会在直播页显示。",
        "充值和消费记录在哪里查看？" to "钱包页面展示服务端返回的余额和订单。网络或服务不可用时，不会显示虚构记录。",
        "如何联系客服？" to "当平台配置了客服账号后，可通过站内消息联系。")
    var expanded by remember { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxSize().background(MissingPaper)) {
        MissingTitle("常见问题/客服", onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)) {
            questions.forEachIndexed { index, item ->
                Column(Modifier.fillMaxWidth().clickable { expanded = if (expanded == index) -1 else index }.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.HelpOutline, null, tint = MissingPurple, modifier = Modifier.size(22.dp))
                        Text(item.first, Modifier.weight(1f).padding(start = 10.dp), color = MissingInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(if (expanded == index) "−" else "+", color = Color(0xFF858997), fontSize = 20.sp)
                    }
                    if (expanded == index) Text(item.second, Modifier.padding(start = 32.dp, top = 10.dp), color = Color(0xFF666A76), fontSize = 14.sp, lineHeight = 20.sp)
                }
                if (index != questions.lastIndex) HorizontalDivider(color = Color(0xFFF0F1F4))
            }
        }
        Button(onClick = onSupport, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = MissingPurple)) {
            Icon(Icons.Rounded.SupportAgent, null)
            Spacer(Modifier.width(8.dp))
            Text("联系在线客服")
        }
    }
}
