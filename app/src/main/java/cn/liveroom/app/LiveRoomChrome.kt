package cn.liveroom.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val LIVE_ENTRY_NOTICE = "欢迎来到现场直播。请文明互动，禁止传播违法违规内容；切勿私下交易转账，谨防网络诈骗。"
internal data class LiveHeaderPerson(val name: String, val avatar: String? = null, val poster: Int? = null)

@Composable
internal fun LiveHeaderAvatar(person: LiveHeaderPerson, modifier: Modifier = Modifier) {
    if (person.avatar != null || person.poster == null) QumiUserAvatar(person.avatar, person.name, modifier)
    else Image(painterResource(person.poster), person.name, modifier.clip(CircleShape), contentScale = ContentScale.Crop)
}

@Composable
internal fun LiveRoomHeader(host: LiveHeaderPerson, subtitle: String, followed: Boolean, followBusy: Boolean,
    showFollow: Boolean, viewers: String, leaders: List<LiveHeaderPerson>, onProfile: () -> Unit,
    onFollow: () -> Unit, onRank: () -> Unit, onViewers: () -> Unit, onClose: () -> Unit,
    modifier: Modifier = Modifier, onAnnouncement: (() -> Unit)? = null) {
    val audienceHeight = 25.dp
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Row(Modifier.widthIn(max = 172.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = .30f)).padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    LiveHeaderAvatar(host, Modifier.size(36.dp).clip(CircleShape).clickable(onClickLabel = "查看${host.name}的主页", onClick = onProfile))
                    Column(Modifier.weight(1f, fill = false).padding(horizontal = 4.dp)) {
                        Text(host.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, color = Color.White.copy(alpha = .8f), fontSize = 9.sp,
                            lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (showFollow) Text(if (followed) "已关注" else "关注", Modifier.clip(CircleShape)
                        .background(if (followed) Color.White.copy(alpha = .14f) else Color(0xFF8454EA))
                        .clickable(enabled = !followBusy, onClick = onFollow).padding(8.dp, 5.dp),
                        color = Color.White, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(Modifier.clickable(onClickLabel = "查看榜单前三名", onClick = onRank), horizontalArrangement = Arrangement.spacedBy((-7).dp)) {
                leaders.take(3).forEachIndexed { index, person ->
                    LiveHeaderAvatar(person, Modifier.size(audienceHeight).clip(CircleShape).border(1.dp,
                        listOf(Color(0xFFF4CA72), Color(0xFFD5DCEC), Color(0xFFDBAB8F))[index], CircleShape))
                }
            }
            Box(Modifier.height(audienceHeight).clip(CircleShape).background(Color.Black.copy(alpha = .25f))
                .clickable(onClickLabel = "查看房间观众", onClick = onViewers).padding(horizontal = 9.dp),
                contentAlignment = Alignment.Center) {
                Text(viewers, color = Color.White, fontSize = 11.sp, lineHeight = 14.sp)
            }
            Box(Modifier.size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = .25f))
                .clickable(onClickLabel = "关闭直播间", onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "关闭直播间", Modifier.size(20.dp), tint = Color.White)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("榜单", Modifier.clip(CircleShape).background(Color.Black.copy(alpha = .28f))
                .clickable(onClick = onRank).padding(11.dp, 6.dp), color = Color.White, fontSize = 11.sp)
            if (onAnnouncement != null) Text("公告", Modifier.clip(CircleShape).background(Color.Black.copy(alpha = .28f))
                .clickable(onClick = onAnnouncement).padding(11.dp, 6.dp), color = Color.White, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun LiveEntryNotice(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = .38f))
        .padding(horizontal = 11.dp, vertical = 8.dp), color = Color(0xFFF4DFA0), fontSize = 12.sp, lineHeight = 20.sp,
        maxLines = 4, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun LiveEnterButton(onEnter: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(CircleShape).background(Color.White.copy(alpha = .22f))
        .border(1.dp, Color.White.copy(alpha = .45f), CircleShape).clickable(onClick = onEnter)
        .padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.GraphicEq, null, Modifier.size(22.dp), tint = Color.White)
        Spacer(Modifier.width(7.dp))
        Text("点击进入直播间", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}
