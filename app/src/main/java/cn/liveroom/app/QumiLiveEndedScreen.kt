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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun QumiLiveEndedScreen(username: String, coverPath: String?, roomType: String,
    startedAtMillis: Long, session: CreatorLiveSession?, durationSeconds: Long,
    loading: Boolean, onClose: () -> Unit, onMore: () -> Unit) {
    val startTime = runCatching { OffsetDateTime.parse(session?.startedAt).toInstant() }
        .getOrElse { Instant.ofEpochMilli(startedAtMillis) }
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA))
    val minutes = (session?.durationSeconds ?: durationSeconds).coerceAtLeast(0) / 60
    val income = session?.incomeFen?.let { String.format(Locale.CHINA, "%.2f", it / 100.0) }
    val typeLabel = when (roomType) {
        "MULTI" -> "多人间"
        "VOICE" -> "语音房"
        else -> "单人间"
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        Color(0xFF1A1436), Color(0xFF45346D), Color(0xFF50639B), Color(0xFF1A1539),
    )))) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.End).padding(12.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭直播统计", tint = Color.White)
            }
            Column(Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.live_placeholder_1), contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(2.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    coverPath?.let { LiveCoverImage(it) }
                }
                Text("直播已结束", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("$username · 本场观看数据暂未统计", color = Color.White.copy(alpha = .58f), fontSize = 13.sp)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp)
                .clip(RoundedCornerShape(15.dp)).background(Color(0xFF19132F).copy(alpha = .57f))
                .padding(horizontal = 18.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (loading) "统计中" else income ?: "待核算", color = Color.White,
                            fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("本场收入（元）", color = Color.White.copy(alpha = .84f), fontSize = 14.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(minutes.toString(), color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("开播时长（分钟）", color = Color.White.copy(alpha = .84f), fontSize = 14.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("开播时间：$startTime", color = Color.White.copy(alpha = .84f), fontSize = 12.sp)
                    Text("房间类型：$typeLabel", color = Color.White.copy(alpha = .84f), fontSize = 12.sp)
                }
            }
            Text("更多数据  ›", modifier = Modifier.align(Alignment.CenterHorizontally)
                .clickable(onClick = onMore).padding(horizontal = 18.dp, vertical = 8.dp),
                color = Color.White.copy(alpha = .88f), fontSize = 15.sp)
        }
    }
}
