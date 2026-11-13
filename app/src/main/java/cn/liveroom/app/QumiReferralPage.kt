package cn.liveroom.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException

@Composable
internal fun QumiReferralPage(
    token: String,
    api: LivePlatformApi,
    onBack: () -> Unit,
    onRedeem: () -> Unit,
) {
    val context = LocalContext.current
    var profile by remember(token) { mutableStateOf<ReferralProfile?>(null) }
    var loading by remember(token) { mutableStateOf(true) }
    var error by remember(token) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var showRules by remember { mutableStateOf(false) }

    LaunchedEffect(token, reload) {
        loading = true
        error = null
        try {
            profile = api.referralProfile(token)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: PlatformHttpException) {
            profile = null
            error = referralErrorMessage(failure.statusCode, failure.errorCode, failure.message)
        } catch (_: Exception) {
            profile = null
            error = "邀请信息暂时无法加载，请检查网络后重试"
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFFFF5F0))) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).background(Color.White).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回") }
            Text("邀请好友", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { showRules = true }) { Text("规则说明") }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Box(
                    Modifier.fillMaxWidth().height(186.dp).clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFFFB66F), Color(0xFFFF5F79), Color(0xFFE94279))))
                        .padding(horizontal = 22.dp, vertical = 24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("邀请好友", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                        Text("一起加入，奖励按平台规则发放", color = Color.White.copy(alpha = .94f), fontSize = 15.sp)
                    }
                }
            }

            item {
                Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("我的邀请码", color = Color(0xFF35343A), fontSize = 15.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                profile?.code ?: if (loading) "正在获取" else "暂无可用邀请码",
                                Modifier.weight(1f),
                                color = Color(0xFFE85468),
                                fontSize = 27.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            IconButton(
                                onClick = {
                                    val code = profile?.code ?: return@IconButton
                                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("邀请码", code))
                                    Toast.makeText(context, "邀请码已复制", Toast.LENGTH_SHORT).show()
                                },
                                enabled = profile?.code != null,
                            ) { Icon(Icons.Rounded.ContentCopy, contentDescription = "复制邀请码", tint = Color(0xFFE85468)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = profile?.code != null,
                                onClick = {
                                    val code = profile?.code ?: return@Button
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "邀你加入直播社区，注册时填写邀请码：$code")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "分享邀请"))
                                },
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("  分享邀请")
                            }
                            OutlinedButton(onClick = { reload++ }, enabled = !loading) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "刷新邀请信息")
                            }
                        }
                    }
                }
            }

            item {
                Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("邀请统计", color = Color(0xFF35343A), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ReferralStat("成功邀请", profile?.invitedCount?.toString() ?: "—", "人", Modifier.weight(1f))
                            ReferralStat("累计奖励", profile?.rewardedCoins?.toString() ?: "—", "金币", Modifier.weight(1f))
                        }
                        if (profile?.enabled == false) {
                            Text("邀请奖励暂未开放，开启后将显示邀请码和统计。", color = Color(0xFF8D8790), fontSize = 13.sp)
                        }
                        if (loading) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("正在同步邀请数据", color = Color(0xFF8D8790), fontSize = 13.sp)
                            }
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                    }
                }
            }

            item {
                Spacer(Modifier.height(2.dp))
                Button(onClick = onRedeem, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text("填写好友邀请码")
                }
            }
        }
    }

    if (showRules) {
        QumiInfoSheet(
            title = "邀请规则",
            message = "邀请奖励由平台运营后台配置。页面只展示服务端返回的邀请码、邀请人数和金币奖励；充值、提现比例等未由接口提供的数据不会在此推算。",
            onDismiss = { showRules = false },
        )
    }
}

@Composable
private fun ReferralStat(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF5F6)).padding(horizontal = 12.dp, vertical = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(value, color = Color(0xFFED4F61), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("$label ${if (value == "—") "" else unit}", color = Color(0xFF85828A), fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
