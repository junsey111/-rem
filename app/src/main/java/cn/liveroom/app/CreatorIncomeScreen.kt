package cn.liveroom.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.math.BigDecimal

@Composable
internal fun CreatorIncomeScreen(token: String, preview: Boolean, onBack: () -> Unit) {
    val sources = listOf("全部" to "", "直播礼物" to "LIVE_GIFT", "私信礼物" to "PRIVATE_GIFT", "付费视频" to "PAID_VIDEO", "视频通话" to "VIDEO_CALL", "付费观看" to "LIVE_WATCH")
    var tab by remember { mutableIntStateOf(0) }
    var records by remember { mutableStateOf(emptyList<JSONObject>()) }
    var cursor by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    suspend fun load(more: Boolean) {
        busy = true; failure = null
        try {
            val data = LivePlatformApi().creatorIncome(token, sources[tab].second, if (more) cursor else "")
            val rows = data.optJSONArray("records")
            val page = if (rows == null) emptyList() else (0 until rows.length()).map { rows.getJSONObject(it) }
            records = if (more) (records + page).distinctBy { it.getString("id") } else page
            cursor = data.optString("next_before")
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) { failure = error.message ?: "收益明细加载失败"
        } finally { busy = false }
    }
    LaunchedEffect(token, tab, revision) { records = emptyList(); cursor = ""; if (!preview) load(false) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFECE3FF), Color(0xFFFAF8FF))))) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
            Text("收益明细", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        ScrollableTabRow(tab, containerColor = Color.Transparent, edgePadding = 10.dp) {
            sources.forEachIndexed { index, source -> Tab(selected = tab == index, onClick = { if (!busy) tab = index }, text = { Text(source.first) }) }
        }
        Text("按已结算收入展示 · 单位：人民币", Modifier.padding(16.dp), color = Color(0xFF9B8CB0), fontSize = 12.sp)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            preview -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("预览模式不显示真实收入记录", color = Color(0xFF9B8CB0)) }
            records.isEmpty() && failure == null && !busy -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("暂无收益记录", color = Color(0xFF9B8CB0)) }
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(records, key = { it.getString("id") }) { row ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(row.optString("label"), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(row.optString("created_at").replace('T', ' ').take(19), color = Color(0xFF9B8CB0), fontSize = 11.sp)
                        }
                        Text(if (row.isNull("income_fen")) "待配置" else "+¥${BigDecimal.valueOf(row.getLong("income_fen"), 2).toPlainString()}",
                            color = Color(0xFF7044D8), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (failure != null) item { TextButton(onClick = { if (records.isEmpty()) revision++ else scope.launch { load(true) } }) { Text("$failure · 重试") } }
                else if (cursor.isNotBlank()) item { TextButton(onClick = { scope.launch { load(true) } }, enabled = !busy) { Text("加载更多") } }
            }
        }
    }
}
