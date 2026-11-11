package cn.liveroom.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

private val creatorPageBackground = Color(0xFFF5F4FA)
private val creatorInk = Color(0xFF282631)
private val creatorPurple = Color(0xFF7044D8)

@Composable
internal fun QumiCreatorGatewayScreen(
    session: UserSession,
    onBack: () -> Unit,
    onApply: () -> Unit,
) {
    val api = remember { LivePlatformApi() }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var application by remember { mutableStateOf<CreatorApplication?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var previewOpen by remember(session.id) { mutableStateOf(false) }
    val canPreview = BuildConfig.DEBUG && session.id == 2L
    BackHandler(previewOpen) { previewOpen = false }
    LaunchedEffect(session.token, refresh) {
        loading = true
        error = null
        try {
            val applications = api.creatorApplications(session.token)
            application = applications.firstOrNull { it.status == "APPROVED" } ?: applications.firstOrNull()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            application = null
            error = failure.message ?: "认证状态暂时无法读取"
        } finally {
            loading = false
        }
    }
    if (!loading && error == null && application?.status == "APPROVED") {
        QumiHostScreen(session, onBack)
        return
    }
    if (previewOpen && canPreview) {
        QumiHostScreen(session, onBack = { previewOpen = false }, preview = true)
        return
    }
    Column(Modifier.fillMaxSize().background(creatorPageBackground)) {
        CreatorPageTitle("主播后台", onBack)
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            when {
                loading -> CircularProgressIndicator(color = creatorPurple)
                error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "认证状态暂时无法读取", color = creatorInk)
                    TextButton(onClick = { refresh++ }, colors = ButtonDefaults.textButtonColors(contentColor = creatorPurple)) { Text("重试") }
                }
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        if (application?.status == "PENDING") "主播认证正在审核" else "认证主播后可查看收益",
                        color = creatorInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    )
                    if (application?.status == "REJECTED")
                        Text(application?.decisionReason?.takeIf { it.isNotBlank() } ?: "申请未通过，可重新提交", color = Color(0xFFB34E62))
                    Button(onClick = onApply, colors = ButtonDefaults.buttonColors(containerColor = creatorPurple)) {
                        Text(if (application?.status == "PENDING") "查看认证进度" else "去认证为主播")
                    }
                    if (canPreview) TextButton(onClick = { previewOpen = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = creatorPurple)) {
                        Text("预览主播后台")
                    }
                }
            }
        }
    }
}

@Composable
internal fun QumiCreatorWalletGatewayScreen(
    session: UserSession,
    daily: Earnings?,
    monthly: Earnings?,
    onBack: () -> Unit,
    onApply: () -> Unit,
    onBills: (Int) -> Unit,
    onWithdraw: () -> Unit,
    onRefresh: () -> Unit,
) {
    val api = remember { LivePlatformApi() }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var application by remember { mutableStateOf<CreatorApplication?>(null) }
    var withdrawable by remember { mutableStateOf<CreatorWithdrawable?>(null) }
    var moneyRules by remember { mutableStateOf<CreatorMoneyRules?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session.token, refresh) {
        loading = true
        error = null
        try {
            val applications = api.creatorApplications(session.token)
            application = applications.firstOrNull { it.status == "APPROVED" } ?: applications.firstOrNull()
            withdrawable = if (application?.status == "APPROVED") api.creatorWithdrawable(session.token) else null
            moneyRules = if (application?.status == "APPROVED") api.creatorMoneyRules(session.token) else null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            withdrawable = null
            error = failure.message ?: "主播钱包暂时无法读取"
        } finally {
            loading = false
        }
    }
    if (!loading && error == null && application?.status == "APPROVED" && withdrawable != null) {
        QumiWalletScreen(
            withdrawable = withdrawable!!, daily = daily, monthly = monthly, moneyRules = moneyRules,
            onBack = onBack, onRefresh = { refresh++; onRefresh() },
            onBills = onBills, onWithdraw = onWithdraw,
        )
        return
    }
    Column(Modifier.fillMaxSize().background(creatorPageBackground)) {
        CreatorPageTitle("我的钱包", onBack)
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            when {
                loading -> CircularProgressIndicator(color = creatorPurple)
                error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "主播钱包暂时无法读取", color = creatorInk)
                    TextButton(onClick = { refresh++ }, colors = ButtonDefaults.textButtonColors(contentColor = creatorPurple)) { Text("重试") }
                }
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(if (application?.status == "PENDING") "主播认证正在审核" else "认证主播后可查看钱包并申请提现",
                        color = creatorInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    if (application?.status == "REJECTED")
                        Text(application?.decisionReason?.takeIf { it.isNotBlank() } ?: "认证未通过，可重新提交", color = Color(0xFFB34E62))
                    Button(onClick = onApply, colors = ButtonDefaults.buttonColors(containerColor = creatorPurple)) {
                        Text(if (application?.status == "PENDING") "查看认证进度" else "去认证为主播")
                    }
                }
            }
        }
    }
}

@Composable
internal fun QumiCreatorVerificationScreen(session: UserSession, onBack: () -> Unit,
    onHostBackend: () -> Unit = onBack) {
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var application by remember { mutableStateOf<CreatorApplication?>(null) }
    var statusError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var requestId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    LaunchedEffect(session.token, refresh) {
        loading = true
        statusError = null
        try {
            val applications = api.creatorApplications(session.token)
            application = applications.firstOrNull { it.status == "APPROVED" } ?: applications.firstOrNull()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            statusError = failure.message ?: "认证状态暂时无法读取"
        } finally {
            loading = false
        }
    }
    Column(Modifier.fillMaxSize().background(creatorPageBackground)) {
        CreatorPageTitle("我的认证", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (loading) CircularProgressIndicator()
            if (statusError != null) {
                Text(statusError ?: "读取失败", color = Color(0xFFB34E62))
                TextButton(onClick = { refresh++ }) { Text("刷新状态") }
            }
            if (error != null) Text(error ?: "提交失败", color = Color(0xFFB34E62))
            if (!loading && statusError == null) when (application?.status) {
                "APPROVED" -> CreatorApprovedCard(session.username, onHostBackend)
                "PENDING" -> {
                    Text("已提交，等待审核", color = creatorInk, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { refresh++ }) { Text("刷新审核状态") }
                }
                else -> {
                    if (application?.status == "REJECTED")
                        Text(application?.decisionReason?.takeIf { it.isNotBlank() } ?: "上次申请未通过，请核对信息后重新提交", color = Color(0xFFB34E62))
                    Text("认证为主播", color = creatorInk, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(name, { name = it.take(30) }, label = { Text("姓名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phone, { phone = it.filter { char -> char.isDigit() || char == '+' }.take(20) },
                        label = { Text("电话") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            val digits = phone.count(Char::isDigit)
                            if (name.trim().length < 2 || digits !in 6..20) {
                                error = "请填写姓名和有效电话"
                            } else {
                                submitting = true
                                error = null
                                scope.launch {
                                    try {
                                        application = api.applyCreator(session.token, requestId, name, phone)
                                        requestId = UUID.randomUUID().toString()
                                        refresh++
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: PlatformHttpException) {
                                        if (failure.statusCode == 409) refresh++
                                        else error = failure.message ?: "提交失败，请重试"
                                    } catch (failure: Exception) {
                                        error = "提交结果未确认，请用原信息重试"
                                    } finally {
                                        submitting = false
                                    }
                                }
                            }
                        },
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = creatorPurple),
                    ) { Text(if (submitting) "提交中" else "提交") }
                }
            }
        }
    }
}

@Composable
private fun CreatorApprovedCard(username: String, onHostBackend: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFE8DCFF), Color(0xFFF3EDFF), Color.White)))) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(110.dp).clip(CircleShape).background(Color.White.copy(alpha = .6f)), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFAF7EF8), Color(0xFF7044D8)))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Verified, contentDescription = "认证通过", tint = Color.White, modifier = Modifier.size(52.dp))
                    }
                }
                Text("主播认证已通过", color = creatorInk, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("恭喜你，成为现场认证主播", color = Color(0xFF8D7AAB), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F3FF))
                    .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("认证账号", color = Color(0xFFA091B3), fontSize = 12.sp)
                        Text(username, color = creatorInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("已认证", Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFE9DFFF))
                        .padding(horizontal = 12.dp, vertical = 6.dp), color = creatorPurple, fontSize = 12.sp)
                }
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("主播功能已开启", color = creatorInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            listOf(Triple(Icons.Rounded.Videocam, "开启直播", "创建专属房间，与观众实时互动"),
                Triple(Icons.Rounded.Movie, "发布视频", "分享精彩内容，让更多人发现你"),
                Triple(Icons.Rounded.AccountBalanceWallet, "主播收益", "查看礼物收益与钱包明细")).forEach { (icon, title, detail) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFFF1EAFF)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(23.dp), tint = creatorPurple)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, color = creatorInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(detail, color = Color(0xFF9B8FA8), fontSize = 12.sp)
                    }
                }
            }
        }
        Button(onClick = onHostBackend, Modifier.fillMaxWidth().height(49.dp), shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = creatorPurple)) { Text("进入主播后台", fontSize = 16.sp) }
        Text("感谢你的加入，期待你的每一次精彩开播", color = Color(0xFFA294B2), fontSize = 12.sp)
    }
}

@Composable
private fun CreatorPageTitle(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = creatorInk) }
        Text(title, modifier = Modifier.weight(1f), color = creatorInk, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(48.dp))
    }
}
