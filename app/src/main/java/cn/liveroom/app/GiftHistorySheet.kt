package cn.liveroom.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftHistorySheet(history: GiftHistory?, loading: Boolean, error: String?, onDismiss: () -> Unit, onRefresh: () -> Unit, onOlder: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().imePadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("送礼记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onRefresh, enabled = !loading) { Text("刷新") }
                }
                Text("这里只显示服务端已确认的赠送或拒绝。未查到记录时，请继续核实原送礼。", style = MaterialTheme.typography.bodySmall)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            history?.let { data ->
                items(data.orders, key = { it.id }) { order ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${order.giftName} × ${order.quantity}", fontWeight = FontWeight.SemiBold)
                        Text("送给主播账号 ${order.creatorId}", style = MaterialTheme.typography.bodyMedium)
                        Text(when (order.status) {
                            "ACCEPTED" -> "已赠送 · 已扣 ${order.chargedCoins} 金币"
                            "REJECTED" -> "未赠送 · 未扣币"
                            else -> "结果待核实"
                        }, style = MaterialTheme.typography.bodyMedium)
                        val time = runCatching { java.time.Instant.parse(order.createdAt).atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }.getOrNull()
                        time?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text("送礼单号 ${order.id}", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider(Modifier.padding(top = 12.dp))
                }
                if (data.orders.isEmpty()) item { Text("还没有已确认的送礼记录") }
                if (data.nextCursor.isNotBlank()) item { TextButton(onClick = onOlder, enabled = !loading) { Text("加载更早记录") } }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
