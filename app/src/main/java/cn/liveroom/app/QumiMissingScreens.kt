package cn.liveroom.app

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.UUID

private val MissingPaper = Color(0xFFF4F5FA)
private val MissingInk = Color(0xFF19191E)
private val MissingPurple = Color(0xFF6650E8)

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
internal fun QumiGuildScreen(session: UserSession, onBack: () -> Unit) {
    var directory by remember { mutableStateOf<GuildDirectory?>(null) }
    var mine by remember { mutableStateOf<GuildState?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedGuild by remember { mutableStateOf<GuildSummary?>(null) }
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()
    fun refresh() {
        scope.launch {
            loading = true
            error = null
            runCatching { directory = api.guildDirectory(session.token); mine = api.myGuild(session.token) }
                .onFailure { error = it.message ?: "公会数据加载失败" }
            loading = false
        }
    }
    LaunchedEffect(session.token) { refresh() }
    if (showCreate) QumiModalSheet(onDismiss = { showCreate = false }, dismissEnabled = !busy) {
        QumiSheetTitle("创建公会", "创建后你将成为会长，后续可管理成员申请。")
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("公会名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it.take(255) }, label = { Text("公会简介") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(enabled = !busy, onClick = { showCreate = false }, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(enabled = !busy && name.trim().isNotEmpty(), onClick = {
            busy = true
            scope.launch {
                runCatching { api.createGuild(session.token, name.trim(), description.trim()) }
                    .onSuccess { name = ""; description = ""; showCreate = false; refresh() }
                    .onFailure { error = it.message ?: "创建失败" }
                busy = false
            }
            }, modifier = Modifier.weight(1f)) { Text(if (busy) "提交中" else "创建") }
        }
    }
    selectedGuild?.let { guild -> QumiModalSheet(onDismiss = { selectedGuild = null }, dismissEnabled = !busy) {
        QumiSheetTitle(guild.name, "成员 ${guild.memberCount} 人")
        Text(guild.description.ifBlank { "这个公会还没有简介" })
        if (mine?.guild?.id == guild.id) Text("你已在该公会中", color = Color(0xFF2F855A), fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(enabled = !busy, onClick = { selectedGuild = null }, modifier = Modifier.weight(1f)) { Text("关闭") }
            Button(enabled = !busy && mine?.guild == null, onClick = {
            busy = true
            scope.launch {
                runCatching { api.applyGuild(session.token, guild.id) }
                    .onSuccess { selectedGuild = null; error = "申请已提交，等待公会管理员审核" }
                    .onFailure { error = it.message ?: "申请加入失败" }
                busy = false
            }
            }, modifier = Modifier.weight(1f)) { Text("申请加入") }
        }
    } }
    Box(Modifier.fillMaxSize().background(Color(0xFF17120D))) {
        Image(painterResource(R.drawable.qumi_guild_background), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .7f)
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回", tint = Color.White) }
                Text("公会广场", Modifier.weight(1f), color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center)
                TextButton(onClick = { refresh() }) { Text("刷新", color = Color.White) }
            }
            mine?.guild?.let { guild ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xEE21180D)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EmojiEvents, null, tint = Color(0xFFFFCE60), modifier = Modifier.size(36.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("我的公会 · ${guild.name}", color = Color(0xFFFFE89D), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("${mine?.role ?: "MEMBER"} · ${guild.memberCount} 名成员", color = Color(0xFFD2C5AE), fontSize = 13.sp)
                    }
                }
            }
            if (error != null) TextButton(onClick = { refresh() }) { Text(error!!, color = Color(0xFFFFB4AB)) }
            when {
                loading && directory == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFFFFD783)) }
                directory?.guilds.isNullOrEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("暂无可加入的公会", color = Color(0xFFD2C5AE), fontSize = 16.sp)
                }
                else -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(directory!!.guilds, key = { it.id }) { guild ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xCC21180D)).clickable { selectedGuild = guild }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFD783), modifier = Modifier.size(30.dp))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(guild.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(guild.description.ifBlank { "欢迎加入公会" }, color = Color(0xFFD2C5AE), fontSize = 13.sp, maxLines = 2)
                            }
                            Text("${guild.memberCount}人", color = Color(0xFFFFE5A8), fontSize = 12.sp)
                        }
                    }
                }
            }
            Button(enabled = mine?.guild == null, onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp).height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD994)), shape = RoundedCornerShape(28.dp)) {
                Text(if (mine?.guild == null) "＋创建公会" else "已加入公会", color = Color(0xFF50371B), fontSize = 18.sp)
            }
        }
    }
}

@Composable
internal fun QumiNobleScreen(session: UserSession, onBack: () -> Unit) {
    var levels by remember { mutableStateOf<List<NobleLevelInfo>>(emptyList()) }
    var membership by remember { mutableStateOf<NobleMembershipState?>(null) }
    var tier by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var purchaseLevel by remember { mutableStateOf<NobleLevelInfo?>(null) }
    var purchaseBusy by remember { mutableStateOf(false) }
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val purchasePrefs = remember(session.id) { context.getSharedPreferences("qumi_catalog_purchases", Context.MODE_PRIVATE) }
    LaunchedEffect(session.token) {
        runCatching {
            levels = api.nobleLevels(session.token)
            membership = api.nobleMembership(session.token)
        }.onFailure { error = it.message ?: "贵族信息加载失败" }
    }
    val selectedLevel = levels.getOrNull(tier.coerceIn(0, (levels.size - 1).coerceAtLeast(0)))
    dialog?.let {
        QumiInfoSheet(
            title = it,
            message = "贵族等级、价格和有效期均由平台后台配置。开通时会由服务端校验余额并记录权益。",
            onDismiss = { dialog = null },
        )
    }
    notice?.let { message -> Text(message, Modifier.fillMaxWidth().padding(horizontal = 18.dp), color = Color(0xFFFFC2C2), fontSize = 13.sp) }
    Column(Modifier.fillMaxSize().background(Color(0xFF1D1036))) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ArrowBack, "返回", tint = Color.White) }
            Text("贵族中心", Modifier.weight(1f), color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center)
            TextButton(onClick = { dialog = "贵族说明" }) { Text("说明", color = Color(0xFFD9C8F3)) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFBBDCE7)).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("账号 ${session.id}", color = Color(0xFF356D7A), fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        Text(membership?.level?.name?.let { "当前身份：$it" } ?: "当前未开通贵族", Modifier.padding(top = 8.dp), color = Color(0xFF356D7A), fontSize = 14.sp)
                    }
                    Button(onClick = { dialog = "贵族开通" }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFE49A))) {
                        Text("查看规则", color = Color(0xFF50371B))
                    }
                }
                Text("专属身份、进场展示等权益", Modifier.padding(top = 15.dp), color = Color(0xFF356D7A), fontSize = 18.sp)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                levels.forEachIndexed { index, level ->
                    Column(Modifier.clickable { tier = index }.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(level.name, color = if (tier == index) Color.White else Color(0xFFA69CB6), fontSize = 18.sp,
                            fontWeight = if (tier == index) FontWeight.Bold else FontWeight.Normal)
                        if (tier == index) Spacer(Modifier.padding(top = 8.dp).width(24.dp).height(3.dp).clip(CircleShape).background(Color.White))
                    }
                }
            }
            if (selectedLevel == null) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    if (error == null) CircularProgressIndicator(color = Color(0xFFE6C267)) else Text(error!!, color = Color(0xFFC2B6D4))
                }
            } else if (tier < 2) {
                Image(painterResource(if (tier == 0) R.drawable.qumi_noble_knight else R.drawable.qumi_noble_viscount),
                    "${selectedLevel.name}等级展示", modifier = Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 65.dp), contentScale = ContentScale.Fit)
            } else {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Star, "${selectedLevel.name}等级占位图标", tint = Color(0xFFE6C267), modifier = Modifier.size(100.dp))
                }
            }
            Text("${selectedLevel?.name ?: "贵族"}专属权益", Modifier.fillMaxWidth().padding(top = 10.dp), color = Color.White,
                fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(selectedLevel?.description?.ifBlank { "权益内容由平台配置" } ?: "权益内容由平台配置", Modifier.fillMaxWidth().padding(top = 8.dp), color = Color(0xFFC2B6D4),
                fontSize = 14.sp, textAlign = TextAlign.Center)
            selectedLevel?.let { level ->
                Button(
                    enabled = !purchaseBusy,
                    onClick = { purchaseLevel = level },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD783)),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(if (level.priceCoins > 0) "${if (membership?.level?.level == level.level) "续费" else "开通"} ${level.name} · ${level.priceCoins} 金币" else "免费开通 ${level.name}", color = Color(0xFF4B3218))
                }
            }
            listOf("贵族身份展示" to "个人资料中的专属标识", "进场效果" to "进入直播间时展示", "专属名片" to "身份与礼物权益").forEach { (title, detail) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF170B2B)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Star, null, tint = Color(0xFFF2CC79), modifier = Modifier.size(28.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(detail, color = Color(0xFFA69CB6), fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    purchaseLevel?.let { level ->
        QumiConfirmSheet(
            title = "确认开通${level.name}",
            message = if (level.priceCoins > 0) "将从金币余额扣除 ${level.priceCoins} 金币，有效期 ${level.durationDays} 天。" else "本次开通不扣除金币，有效期 ${level.durationDays} 天。",
            confirmLabel = "确认开通",
            busy = purchaseBusy,
            onDismiss = { if (!purchaseBusy) purchaseLevel = null },
            onConfirm = {
                val key = "noble_${level.level}"
                val requestId = purchasePrefs.getString(key, null) ?: UUID.randomUUID().toString().also {
                    purchasePrefs.edit().putString(key, it).commit()
                }
                purchaseBusy = true
                scope.launch {
                    runCatching {
                        api.purchaseNoble(session.token, level.level, requestId)
                        api.nobleMembership(session.token)
                    }.onSuccess { state ->
                        membership = state
                        purchasePrefs.edit().remove(key).apply()
                        purchaseLevel = null
                        notice = "${level.name}已开通"
                    }.onFailure { failure ->
                        if (failure is PlatformHttpException && failure.statusCode < 500) purchasePrefs.edit().remove(key).apply()
                        notice = failure.message ?: "开通失败，请稍后重试"
                    }
                    purchaseBusy = false
                }
            },
        )
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
    if (showRules) QumiInfoSheet(
        title = "蜜友关系",
        message = "蜜友关系由双方自愿建立。发送邀请后，对方接受才会建立关系；任一方可以随时解除。当前不收费，也不附带礼物或等级权益。",
        onDismiss = { showRules = false },
    )
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
                Text("账号 ${session.id}", Modifier.padding(top = 3.dp), color = Color(0xFF936B72), fontSize = 13.sp)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = targetId, onValueChange = { value -> targetId = value.filter(Char::isDigit).take(20) },
                        modifier = Modifier.weight(1f), singleLine = true, label = { Text("输入对方用户 ID") })
                    Button(enabled = !busy && targetId.toLongOrNull()?.let { it > 0 && it != session.id } == true,
                        onClick = {
                            val id = targetId.toLongOrNull() ?: return@Button
                            busy = true
                            scope.launch {
                                runCatching { api.inviteCloseFriend(session.token, id) }
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
                    CloseFriendSection(session.token, "待处理邀请 · ${state.incoming.size}", state.incoming) { person ->
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
                    CloseFriendSection(session.token, "我的蜜友 · ${state.friends.size}", state.friends) { person ->
                        TextButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                runCatching { api.removeCloseFriend(session.token, person.id) }
                                    .onSuccess { refresh++ }.onFailure { error = it.message ?: "解除关系失败" }
                                busy = false
                            }
                        }) { Text("解除") }
                    }
                    CloseFriendSection(session.token, "已发送邀请 · ${state.outgoing.size}", state.outgoing) { person ->
                        Text("待对方接受", Modifier.padding(horizontal = 8.dp), color = Color(0xFF9E7D85), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CloseFriendSection(token: String, title: String, users: List<CloseFriendUser>, action: @Composable (CloseFriendUser) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(title, Modifier.padding(vertical = 8.dp), color = Color(0xFF6D3748), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (users.isEmpty()) Text("暂无", Modifier.padding(vertical = 7.dp), color = Color(0xFF9E7D85), fontSize = 13.sp)
        users.forEach { person ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                QumiUserAvatar(token, person.avatar, Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFFFE1EC)), "${person.username}的头像") {
                    Text(person.username.take(1), color = Color(0xFFF06D9C), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
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
internal fun QumiBeautyScreen(
    settings: CameraBeautySettings,
    onSettingsChange: (CameraBeautySettings) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MissingPaper).verticalScroll(rememberScrollState())) {
        MissingTitle("美颜效果", onBack)
        Column(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Face, null, tint = MissingPurple, modifier = Modifier.size(30.dp))
                Text("直播画面", Modifier.padding(start = 10.dp), color = MissingInk, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自然磨皮", color = MissingInk, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("柔化皮肤细节", color = Color(0xFF858997), fontSize = 13.sp)
                }
                Switch(checked = settings.smoothing, onCheckedChange = { onSettingsChange(settings.copy(smoothing = it)) })
            }
            HorizontalDivider(color = Color(0xFFF0F1F4), modifier = Modifier.padding(vertical = 12.dp))
            BeautySliderRow(
                title = "亮度", valueLabel = "${(settings.brightness * 100).toInt()}%",
                value = settings.brightness, valueRange = -0.3f..0.3f,
                onValueChange = { onSettingsChange(settings.copy(brightness = it)) },
            )
            BeautySliderRow(
                title = "饱和度", valueLabel = "${(settings.saturation * 100).toInt()}%",
                value = settings.saturation, valueRange = 0.5f..1.5f,
                onValueChange = { onSettingsChange(settings.copy(saturation = it)) },
            )
            TextButton(
                onClick = { onSettingsChange(CameraBeautySettings(smoothing = true, brightness = 0.04f, saturation = 1.04f)) },
                modifier = Modifier.align(Alignment.End),
            ) { Text("自然预设", color = MissingPurple) }
        }
        Text(
            "设置会按账号保存在本机，并应用于普通直播推流。连麦和 PK 的视频画面暂不应用这些效果。",
            Modifier.padding(horizontal = 20.dp, vertical = 2.dp), color = Color(0xFF777B89), fontSize = 13.sp, lineHeight = 19.sp,
        )
    }
}

@Composable
private fun BeautySliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = MissingInk, fontSize = 15.sp)
            Text(valueLabel, color = Color(0xFF858997), fontSize = 13.sp)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
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
                        QumiUserAvatar(token, user.avatar, Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFF0F1F4)), "${user.username}的头像") {
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
internal fun QumiQualificationScreen(session: UserSession, onBack: () -> Unit) {
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<PlatformQualification?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching { api.platformQualification(session.token) }
                .onSuccess { data = it }
                .onFailure { error = it.message ?: "平台资质加载失败" }
            loading = false
        }
    }
    LaunchedEffect(session.token) { load() }
    Column(Modifier.fillMaxSize().background(MissingPaper)) {
        MissingTitle("平台资质", onBack)
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MissingPurple) }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TextButton(onClick = ::load) { Text("${error} · 点击重试") }
            }
            data?.configured != true -> Column(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(18.dp)) {
                Icon(Icons.Rounded.VerifiedUser, null, tint = MissingPurple, modifier = Modifier.size(36.dp))
                Text("资质信息暂未发布", Modifier.padding(top = 14.dp), color = MissingInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("平台主体与许可证资料发布后会显示在这里。", Modifier.padding(top = 8.dp), color = Color(0xFF666A76), fontSize = 14.sp)
            }
            else -> {
                val value = data!!
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(18.dp)) {
                            Icon(Icons.Rounded.VerifiedUser, null, tint = MissingPurple, modifier = Modifier.size(36.dp))
                            Text(value.platformName ?: "平台资质", Modifier.padding(top = 12.dp), color = MissingInk, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                            Text("以下信息由平台运营方发布。", Modifier.padding(top = 6.dp), color = Color(0xFF666A76), fontSize = 13.sp)
                        }
                    }
                    item { QualificationRow("主体名称", value.subjectName) }
                    item { QualificationRow("登记注册号", value.registrationNumber) }
                    item { QualificationRow("办公地址", value.officeAddress) }
                    item { QualificationRow("联系邮箱", value.contactEmail) }
                    item { QualificationRow("ICP备案", value.icpRecord) }
                    item { QualificationRow("网络文化许可证", value.networkCultureLicense) }
                    item { QualificationRow("直播服务许可证", value.liveServiceLicense) }
                }
            }
        }
    }
}

@Composable
private fun QualificationRow(label: String, value: String?) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(label, color = Color(0xFF858997), fontSize = 12.sp)
        Text(value ?: "—", Modifier.padding(top = 4.dp), color = MissingInk, fontSize = 15.sp)
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
