package cn.liveroom.app

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal

private val inviteRed = Color(0xFFFF3156)
private val inviteInk = Color(0xFF452119)
private val inviteSoft = Color(0xFFFFF5F2)
private val inviteGradient = Brush.linearGradient(
    listOf(Color(0xFFFFC17F), Color(0xFFFF796E), Color(0xFFFF2862)),
)

@Composable
internal fun QumiReferralPage(
    token: String,
    api: LivePlatformApi,
    onBack: () -> Unit,
    onRedeem: () -> Unit,
) {
    var profile by remember(token) { mutableStateOf<ReferralProfile?>(null) }
    var loading by remember(token) { mutableStateOf(true) }
    var error by remember(token) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var showRules by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    BackHandler(showDetails || showQr || showRules) {
        when {
            showQr -> showQr = false
            showRules -> showRules = false
            else -> showDetails = false
        }
    }
    LaunchedEffect(token, reload) {
        loading = true
        error = null
        try {
            profile = api.referralProfile(token)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "邀请信息暂时无法加载，请重试"
        } finally {
            loading = false
        }
    }

    if (showDetails) {
        ReferralRewardDetails(token, api, onBack = { showDetails = false })
        return
    }

    val current = profile
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFFF2E3)),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().background(inviteGradient)) {
                Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = inviteInk)
                    }
                    Text("邀请好友", Modifier.weight(1f), color = inviteInk, fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    TextButton(onClick = { showRules = true }) {
                        Text("规则说明", color = inviteInk, fontSize = 14.sp)
                    }
                }
                Text("邀请好友得奖励", Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                    color = Color.White, fontSize = 37.sp, fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, maxLines = 1)
                Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFAD62), Color(0xFFFF6E53))))
                    .padding(start = 7.dp, end = 7.dp, top = 7.dp)) {
                    Row(Modifier.fillMaxWidth().height(115.dp)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(Color(0xFFFFFBF8)),
                        verticalAlignment = Alignment.CenterVertically) {
                        ReferralRate(current?.rechargeBps ?: 0, "下级现金充值", Modifier.weight(1f))
                        Box(Modifier.width(1.dp).height(75.dp).background(Color(0xFFFFD5AF)))
                        ReferralRate(current?.withdrawalBps ?: 0, "下级提现分成", Modifier.weight(1f))
                    }
                    Box(Modifier.fillMaxWidth().height(101.dp)
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFF6A58), inviteRed, Color(0xFFED1556))))) {
                        Box(Modifier.align(Alignment.TopCenter).size(57.dp)
                            .clip(CircleShape).background(Brush.verticalGradient(
                                listOf(Color(0xFFFFF5A5), Color(0xFFFFAC33))))
                            .padding(3.dp), contentAlignment = Alignment.Center) {
                            Text("¥", color = Color(0xFFC96913), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 15.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("我的邀请码：", color = Color.White, fontSize = 15.sp)
                            Text(current?.code ?: if (loading) "获取中" else "暂未开放",
                                color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp),
                    contentAlignment = Alignment.Center) {
                    Button(onClick = { showQr = true }, enabled = current?.code != null,
                        modifier = Modifier.width(230.dp).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = inviteRed),
                        shape = CircleShape) {
                        Icon(Icons.Rounded.QrCode2, null, modifier = Modifier.size(19.dp))
                        Text(" 邀请二维码", fontSize = 14.sp, maxLines = 1)
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(22.dp)).background(Color.White).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("邀请下级统计", Modifier.weight(1f), color = inviteInk,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("查看详情", Modifier.clip(CircleShape).background(inviteRed)
                        .clickable { showDetails = true }.padding(horizontal = 12.dp, vertical = 5.dp),
                        color = Color.White, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReferralStat("今日充值收益", money(current?.todayRechargeRewardCoins ?: 0), Modifier.weight(1f))
                    ReferralStat("今日下级提现", money(current?.todayWithdrawalRewardCoins ?: 0), Modifier.weight(1f))
                    ReferralStat("今日新增下级", (current?.todayInvitedCount ?: 0).toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReferralStat("累计充值收益", money(current?.totalRechargeRewardCoins ?: 0), Modifier.weight(1f))
                    ReferralStat("累计下级提现", money(current?.totalWithdrawalRewardCoins ?: 0), Modifier.weight(1f))
                    ReferralStat("累计新增下级", (current?.invitedCount ?: 0).toString(), Modifier.weight(1f))
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(22.dp)).background(Color.White)
                .padding(horizontal = 18.dp, vertical = 18.dp)) {
                Text("◇  今日邀请  ◇", Modifier.fillMaxWidth(), color = Color(0xFFAC4C1A),
                    fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (current?.todayInvitees.isNullOrEmpty()) {
                    Text("今天还没有好友加入", Modifier.fillMaxWidth().padding(vertical = 37.dp),
                        color = Color(0xFFB6A9A5), fontSize = 14.sp, textAlign = TextAlign.Center)
                } else {
                    current!!.todayInvitees.forEach { invite ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("好友 " + invite.userId, Modifier.weight(1f), color = inviteInk)
                            Text(invite.createdAt.replace('T', ' ').take(16),
                                color = Color(0xFF9E9594), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item {
            Text("我的上级：" + (current?.inviterId?.toString() ?: "--"),
                Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 5.dp),
                color = Color(0xFFBD7B4A), fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        item {
            Button(onClick = onRedeem, modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp).height(46.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = inviteRed)) {
                Text("填写好友邀请码", fontSize = 15.sp)
            }
        }
        if (loading) item {
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), color = inviteRed, strokeWidth = 2.dp)
            }
        }
        error?.let { message -> item {
            TextButton(onClick = { reload++ }, modifier = Modifier.fillMaxWidth()) {
                Text(message + " · 点击重试", color = inviteRed)
            }
        } }
    }

    if (showRules) AlertDialog(
        onDismissRequest = { showRules = false },
        title = { Text("邀请规则") },
        text = { Text("好友填写邀请码后成为你的下级。平台按后台当前设置，对下级实际到账充值和已完成提现发放奖励。充值奖励为 " +
            percent(current?.rechargeBps ?: 0) + "，提现奖励为 " +
            percent(current?.withdrawalBps ?: 0) +
            "。奖励进入你的平台消费余额，只能用于平台内消费，不能提现。") },
        confirmButton = { TextButton(onClick = { showRules = false }) { Text("知道了") } },
    )
    if (showQr) current?.code?.let { code ->
        val bitmap = remember(code) { referralQr(code) }
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text("邀请二维码") },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(bitmap.asImageBitmap(), contentDescription = "邀请码二维码",
                    modifier = Modifier.size(220.dp))
                Text(code, Modifier.padding(top = 12.dp), color = inviteInk, fontSize = 21.sp,
                    fontWeight = FontWeight.Bold)
                Text("扫码获取邀请码，注册时填写", Modifier.padding(top = 5.dp),
                    color = Color(0xFF8F8584), fontSize = 12.sp)
            } },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text("完成") } },
        )
    }
}

@Composable
private fun ReferralRate(bps: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(percent(bps), color = Color(0xFFE32915), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(label, color = inviteInk, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun ReferralStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.height(83.dp).clip(RoundedCornerShape(5.dp))
        .background(inviteSoft).padding(horizontal = 2.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween) {
        Text(value, color = Color(0xFFE83628), fontSize = 17.sp,
            fontWeight = FontWeight.Medium, maxLines = 1)
        Text(label, color = Color(0xFF8D8790), fontSize = 10.sp,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ReferralRewardDetails(token: String, api: LivePlatformApi, onBack: () -> Unit) {
    var rewards by remember(token) { mutableStateOf(emptyList<ReferralReward>()) }
    var cursor by remember(token) { mutableStateOf("") }
    var loading by remember(token) { mutableStateOf(false) }
    var loaded by remember(token) { mutableStateOf(false) }
    var error by remember(token) { mutableStateOf<String?>(null) }
    var page by remember(token) { mutableIntStateOf(0) }
    LaunchedEffect(token, page) {
        loading = true
        error = null
        try {
            val (next, nextCursor) = api.referralRewards(token, if (page == 0) "" else cursor)
            rewards = if (page == 0) next else rewards + next
            cursor = nextCursor
            loaded = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "奖励明细加载失败"
        } finally {
            loading = false
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFFFF7F1))) {
        Row(Modifier.fillMaxWidth().height(56.dp).background(Color.White),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
            Text("邀请奖励明细", Modifier.weight(1f), textAlign = TextAlign.Center,
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(48.dp))
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (loaded && rewards.isEmpty()) item {
                Text("暂无邀请奖励记录", Modifier.fillMaxWidth().padding(top = 75.dp),
                    textAlign = TextAlign.Center, color = Color(0xFF9A9192))
            }
            items(rewards, key = { it.id }) { reward ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White)
                    .padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (reward.source == "RECHARGE") "下级充值奖励" else "下级提现奖励",
                            color = inviteInk, fontWeight = FontWeight.SemiBold)
                        Text("好友 " + reward.inviteeId + " · " +
                            reward.createdAt.replace('T', ' ').take(16) +
                            " · 比例 " + percent(reward.rateBps),
                            color = Color(0xFF9A9192), fontSize = 12.sp)
                    }
                    Text("+" + money(reward.rewardCoins), color = inviteRed,
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (loading) item {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = inviteRed)
                }
            }
            error?.let { message -> item {
                TextButton(onClick = { page++ }, modifier = Modifier.fillMaxWidth()) {
                    Text(message + " · 重试")
                }
            } }
            if (!loading && cursor.isNotEmpty()) item {
                TextButton(onClick = { page++ }, modifier = Modifier.fillMaxWidth()) {
                    Text("加载更多", color = inviteRed)
                }
            }
        }
    }
}

private fun referralQr(code: String): Bitmap {
    val matrix = MultiFormatWriter().encode(code, BarcodeFormat.QR_CODE, 440, 440,
        mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8"))
    val pixels = IntArray(matrix.width * matrix.height) { index ->
        if (matrix[index % matrix.width, index / matrix.width]) android.graphics.Color.BLACK
        else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
    }
}

private fun percent(bps: Int): String =
    BigDecimal.valueOf(bps.toLong(), 2).stripTrailingZeros().toPlainString() + "%"

private fun money(coins: Long): String =
    "¥" + BigDecimal.valueOf(coins, 2).stripTrailingZeros().toPlainString()
