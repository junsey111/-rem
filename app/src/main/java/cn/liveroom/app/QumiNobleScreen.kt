package cn.liveroom.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

private val nobleBackground = Color(0xFF1C1036)
private val nobleCard = Color(0xFF14082C)
private val nobleMuted = Color(0xFFAAA0BC)
private val nobleGold = Color(0xFFF9E39D)

private data class NobleTier(
    val name: String,
    val badge: Int,
    val privilegeCount: Int,
    val cardStart: Color,
    val cardEnd: Color,
    val cardInk: Color,
    val condition: String,
    val detail: String,
    val privilegeIds: Set<Int>? = null,
)

private val nobleTiers = listOf(
    NobleTier("骑士", R.drawable.qumi_noble_knight, 5, Color(0xFFE5F4FF), Color(0xFFACD8E8), Color(0xFF3B7381), "98元 / 有效期30天", "开通骑士贵族立享专属特权"),
    NobleTier("子爵", R.drawable.qumi_noble_viscount, 7, Color(0xFFF1F8FF), Color(0xFFB6DFFF), Color(0xFF3C7282), "398元 / 有效期30天", "开通子爵贵族立享专属特权"),
    NobleTier("侯爵", R.drawable.qumi_noble_marquis, 10, Color(0xFFFFE9B6), Color(0xFFFFD481), Color(0xFF966A35), "998元 / 有效期30天", "开通侯爵贵族立享专属特权"),
    NobleTier("伯爵", R.drawable.qumi_noble_earl, 13, Color(0xFFFFD7C5), Color(0xFFFBB27F), Color(0xFF9A562C), "2888元 / 有效期30天", "开通伯爵贵族立享专属特权"),
    NobleTier("公爵", R.drawable.qumi_noble_duke, 14, Color(0xFFFFD5D8), Color(0xFFF7ABB3), Color(0xFFC45368), "11.7–12.7 充值2万元获得公爵30天", "达成条件后联系客服核对开通"),
    NobleTier("国王", R.drawable.qumi_noble_king, 15, Color(0xFFFFEAB8), Color(0xFFFFD17A), Color(0xFF9B7031), "11.7–12.7 充值5万元获得国王30天", "达成条件后联系客服核对开通"),
)

private data class NoblePrivilege(val title: String, val description: String, val icon: Int)

private val noblePrivileges = listOf(
    NoblePrivilege("贵族勋章", "尊贵身份象征", R.drawable.qumi_noble_perk_01),
    NoblePrivilege("开通/续费广播", "进房间通知", R.drawable.qumi_noble_perk_02),
    NoblePrivilege("贵族名片", "专属贵族礼物", R.drawable.qumi_noble_perk_03),
    NoblePrivilege("入场特效", "直播间、聊天室特效", R.drawable.qumi_noble_perk_04),
    NoblePrivilege("贵宾席", "优先展示", R.drawable.qumi_noble_perk_05),
    NoblePrivilege("专属头饰", "彰显与众不同", R.drawable.qumi_noble_perk_06),
    NoblePrivilege("开通特效", "贵族等级特效", R.drawable.qumi_noble_perk_07),
    NoblePrivilege("贵族礼物", "可赠送贵族礼物", R.drawable.qumi_noble_perk_08),
    NoblePrivilege("升级加速", "送礼经验值+12%", R.drawable.qumi_noble_perk_09),
    NoblePrivilege("贵族弹幕", "专属弹幕样式", R.drawable.qumi_noble_perk_10),
    NoblePrivilege("发言气泡", "专属发言气泡", R.drawable.qumi_noble_perk_11),
    NoblePrivilege("豪华座驾", "专属豪华座驾", R.drawable.qumi_noble_perk_12),
    NoblePrivilege("榜单隐身", "可设置榜单隐身", R.drawable.qumi_noble_perk_13),
    NoblePrivilege("进场隐身", "以神秘身份观看", R.drawable.qumi_noble_perk_14),
    NoblePrivilege("防踢功能", "直播间防踢", R.drawable.qumi_noble_perk_15),
)

@Composable
internal fun QumiNobleScreen(session: UserSession, onBack: () -> Unit) {
    var selectedTier by remember { mutableIntStateOf(0) }
    var showHelp by remember { mutableStateOf(false) }
    var showOpenInfo by remember { mutableStateOf(false) }
    var showSupportQr by remember { mutableStateOf(false) }
    var configured by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(session.token) {
        configured = runCatching { LivePlatformApi().platformContent("noble").optJSONObject("value") }.getOrNull()
    }
    val tiers = nobleTiers.mapIndexed { index, item ->
        configured?.optJSONArray("tiers")?.optJSONObject(index)?.let { row ->
            val ids = row.optJSONArray("privilege_ids")?.let { selection ->
                (0 until selection.length()).map { selection.optInt(it) }.filter { it in 1..15 }.toSet()
            }
            item.copy(condition = row.optString("condition", item.condition), detail = row.optString("detail", item.detail),
                privilegeCount = row.optInt("privilege_count", item.privilegeCount), privilegeIds = ids)
        } ?: item
    }
    val privileges = noblePrivileges.mapIndexed { index, item ->
        configured?.optJSONArray("privileges")?.optJSONObject(index)?.let { row ->
            item.copy(title = row.optString("title", item.title), description = row.optString("description", item.description))
        } ?: item
    }
    val tier = tiers[selectedTier]

    if (showSupportQr) DisplayQrDialog(DisplayQrKind.SUPPORT, onDismiss = { showSupportQr = false })
    if (showHelp) AlertDialog(
        onDismissRequest = { showHelp = false },
        title = { Text("贵族说明", color = Color(0xFF261D3C)) },
        text = { Text("页面展示各等级的达成条件与权益。实际开通和权益生效由平台核对确认，当前不会自动扣除余额。", color = Color(0xFF716783)) },
        confirmButton = { TextButton(onClick = { showHelp = false }) { Text("知道了", color = Color(0xFF7044D8)) } },
        containerColor = LiveStyle.dialogSurface,
    )
    if (showOpenInfo) AlertDialog(
        onDismissRequest = { showOpenInfo = false },
        title = { Text("开通${tier.name}贵族", color = Color(0xFF261D3C)) },
        text = { Text("达成条件：${tier.condition}。请联系客服核对开通资格，平台确认后再开通相应权益。", color = Color(0xFF716783)) },
        confirmButton = { TextButton(onClick = { showOpenInfo = false; showSupportQr = true }) { Text("联系客服", color = Color(0xFF7044D8)) } },
        dismissButton = { TextButton(onClick = { showOpenInfo = false }) { Text("取消", color = Color(0xFF716783)) } },
        containerColor = LiveStyle.dialogSurface,
    )

    Column(Modifier.fillMaxSize().background(nobleBackground)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ArrowBack, "返回", tint = Color.White) }
            Text("贵族中心", Modifier.weight(1f), color = Color.White, fontSize = 20.sp,
                fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            IconButton(onClick = { showHelp = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.HelpOutline, "贵族说明", tint = Color.White)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp)
                .clip(RoundedCornerShape(18.dp)).background(Brush.horizontalGradient(listOf(tier.cardStart, tier.cardEnd)))
                .padding(horizontal = 16.dp, vertical = 15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.qumi_mine_avatar), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(46.dp).clip(CircleShape))
                    Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(session.username, color = tier.cardInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("暂未开通", color = tier.cardInk, fontSize = 13.sp)
                    }
                    Text("立即开通", Modifier.clip(CircleShape).background(Brush.horizontalGradient(listOf(Color(0xFFFFF4C6), nobleGold)))
                        .clickable { showOpenInfo = true }.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color(0xFF302314), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Text(tier.detail, Modifier.padding(top = 12.dp), color = tier.cardInk, fontSize = 13.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 5.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                tiers.forEachIndexed { index, item ->
                    Column(Modifier.weight(1f).clickable { selectedTier = index }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(item.name, color = if (index == selectedTier) Color.White else nobleMuted,
                            fontSize = 16.sp, fontWeight = if (index == selectedTier) FontWeight.SemiBold else FontWeight.Normal)
                        Spacer(Modifier.height(7.dp))
                        Box(Modifier.width(18.dp).height(3.dp).clip(CircleShape)
                            .background(if (index == selectedTier) Color.White else Color.Transparent))
                    }
                }
            }
            Image(painterResource(tier.badge), contentDescription = "${tier.name}徽章", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(158.dp).padding(horizontal = 62.dp))
            Text("${tier.name}专享特权", Modifier.fillMaxWidth().padding(top = 5.dp), color = Color.White,
                fontSize = 21.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Text("— 拥有特权 ${tier.privilegeIds?.size ?: tier.privilegeCount}/15 项 —", Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 12.dp),
                color = nobleMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
            privileges.chunked(3).forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    row.forEachIndexed { columnIndex, privilege ->
                        NoblePrivilegeCard(privilege, tier.privilegeIds?.contains(rowIndex * 3 + columnIndex + 1)
                            ?: (rowIndex * 3 + columnIndex < tier.privilegeCount),
                            Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(Modifier.fillMaxWidth().background(Color(0xFF484D78)).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("达成条件", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("  ·  ${tier.condition}", color = Color.White, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF35205B), Color(0xFF26143F))))
            .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (selectedTier < 4) "${tier.name}等级" else "${tier.name}等级达成条件", color = Color.White,
                    fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(tier.condition, Modifier.padding(top = 4.dp), color = nobleMuted, fontSize = 11.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = { showOpenInfo = true }, modifier = Modifier.padding(start = 10.dp).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = nobleGold), shape = CircleShape) {
                Text("立即开通", color = Color(0xFF241936), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun NoblePrivilegeCard(privilege: NoblePrivilege, available: Boolean, modifier: Modifier) {
    Column(modifier.height(140.dp).clip(RoundedCornerShape(15.dp)).background(nobleCard)
        .padding(horizontal = 3.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(privilege.icon), contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.size(74.dp), alpha = if (available) 1f else .27f)
        Text(privilege.title, Modifier.padding(top = 3.dp),
            color = if (available) Color.White else nobleMuted.copy(alpha = .64f), fontSize = 12.sp,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(privilege.description, Modifier.padding(top = 2.dp),
            color = nobleMuted.copy(alpha = if (available) 1f else .55f), fontSize = 9.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
