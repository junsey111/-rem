package cn.liveroom.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

private data class PendingClose(val requestId: String, val liveId: String, val reason: String)

private class CloseOutbox(context: Context, userId: Long) {
    private val prefs = context.getSharedPreferences("moderation_outbox", Context.MODE_PRIVATE)
    private val key = "${BuildConfig.BILLD_API_URL}:$userId"
    fun load(): PendingClose? = prefs.getString(key, null)?.let { raw -> JSONObject(raw).let {
        PendingClose(it.getString("request_id"), it.getString("live_id"), it.getString("reason"))
    } }
    fun save(action: PendingClose) {
        check(load() == null) { "请先核实上一次下播操作" }
        check(prefs.edit().putString(key, JSONObject().put("request_id", action.requestId).put("live_id", action.liveId)
            .put("reason", action.reason).toString()).commit()) { "无法保存操作，请检查手机存储空间" }
    }
    fun clear() { check(prefs.edit().remove(key).commit()) { "结果已确认，但本地记录未更新，请再次核实" } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationPanel(session: UserSession, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val api = remember { LivePlatformApi() }
    val outbox = remember(session.id) { CloseOutbox(context, session.id) }
    val restored = remember(outbox) { runCatching { outbox.load() } }
    var pending by remember(outbox) { mutableStateOf(restored.getOrNull()) }
    val storageError = restored.exceptionOrNull()
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var cursor by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var lives by remember { mutableStateOf<ModerationLives?>(null) }
    var actions by remember { mutableStateOf<ModerationActions?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<ModerationLive?>(null) }
    var reason by remember { mutableStateOf("") }
    var showConfirmation by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var operationMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(session.token, tab, cursor, refresh) {
        loading = true
        error = null
        if (cursor.isBlank()) { lives = null; actions = null }
        try {
            if (tab == 0) {
                val page = api.moderationLives(session.token, cursor)
                lives = if (cursor.isBlank()) page else page.copy(lives = (lives?.lives.orEmpty() + page.lives).distinctBy { it.id })
            } else {
                val page = api.moderationActions(session.token, cursor)
                actions = if (cursor.isBlank()) page else page.copy(actions = (actions?.actions.orEmpty() + page.actions).distinctBy { it.id })
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { error = "读取失败，请检查权限或连接后重试"
        } finally { loading = false }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        LazyColumn(Modifier.fillMaxWidth().imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("直播管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { cursor = ""; refresh++ }, enabled = !loading && !busy) { Text("刷新") }
                }
                TabRow(selectedTabIndex = tab) {
                    listOf("直播场次", "处置记录").forEachIndexed { index, title ->
                        Tab(selected = tab == index, enabled = !busy, onClick = { if (tab != index) { tab = index; cursor = "" } }, text = { Text(title) })
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { refresh++ }, enabled = !loading) { Text("重试") } }
                storageError?.let { Text("本地操作记录无法读取，请先联系维护人员核对，暂不能发起下播。", color = MaterialTheme.colorScheme.error) }
                operationMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                pending?.let {
                    Text("有一笔下播操作待核实，请继续使用原单号确认结果。")
                    TextButton(onClick = { showConfirmation = true }, enabled = !busy) { Text("核实原操作") }
                }
            }
            if (tab == 0) {
                item { Text("以下场次尚未结束。主播的媒体连接可能已中断，请核实后处置。", style = MaterialTheme.typography.bodySmall) }
                lives?.let { data ->
                    items(data.lives, key = { "live-${it.id}" }) { live ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("主播账号 ${live.creatorId}", fontWeight = FontWeight.SemiBold)
                            Text("直播场次 ${live.id}", style = MaterialTheme.typography.bodySmall)
                            Text("开播 ${moderationTime(live.startedAt)}", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { selected = live; reason = ""; operationMessage = null; showConfirmation = true },
                                enabled = !busy && !loading && pending == null && storageError == null) { Text("强制下播") }
                        }
                        HorizontalDivider(Modifier.padding(top = 12.dp))
                    }
                    if (data.lives.isEmpty()) item { Text("当前没有尚未结束的直播场次") }
                }
            } else actions?.let { data ->
                items(data.actions, key = { "action-${it.id}" }) { action ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("已确认结束 · 场次 ${action.liveId}", fontWeight = FontWeight.SemiBold)
                        Text("原因：${action.reason}")
                        Text("运营账号 ${action.operatorId} · ${moderationTime(action.createdAt)}", style = MaterialTheme.typography.bodySmall)
                        Text("操作单号 ${action.requestId}", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider(Modifier.padding(top = 12.dp))
                }
                if (data.actions.isEmpty()) item { Text("还没有已确认的下播操作") }
            }
            val next = if (tab == 0) lives?.nextCursor else actions?.nextCursor
            if (!next.isNullOrBlank()) item { TextButton(onClick = { loading = true; cursor = next }, enabled = !loading && !busy) { Text("加载更早记录") } }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showConfirmation) QumiModalSheet(onDismiss = { showConfirmation = false }, dismissEnabled = !busy) {
        QumiSheetTitle(if (pending == null) "确认强制下播" else "核实下播结果", "直播场次 ${pending?.liveId ?: selected?.id}")
        Text("结束该场直播和相关 PK，媒体连接会随后断开。此操作不能撤销。")
        OutlinedTextField(value = pending?.reason ?: reason, onValueChange = { if (it.codePointCount(0, it.length) <= 200) reason = it }, enabled = pending == null && !busy,
            label = { Text("处置原因（必填）") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        operationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { showConfirmation = false }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(if (pending == null) "取消" else "稍后核实") }
            Button(enabled = !busy && storageError == null && (pending != null || (selected != null && reason.isNotBlank())), onClick = {
                busy = true
                operationMessage = null
                scope.launch {
                    try {
                        val intent = pending ?: PendingClose(UUID.randomUUID().toString(), checkNotNull(selected).id, reason.trim()).also {
                            outbox.save(it); pending = it
                        }
                        val result = api.moderationReceipt(session.token, intent.requestId)
                            ?: api.forceCloseLive(session.token, intent.liveId, intent.requestId, intent.reason)
                        check(result.requestId == intent.requestId && result.liveId == intent.liveId && result.reason == intent.reason && result.operatorId == session.id.toString()) { "操作记录不符，请保留单号核对" }
                        outbox.clear()
                        pending = null
                        selected = null
                        showConfirmation = false
                        operationMessage = "已确认本场直播结束，处置记录已保存"
                        cursor = ""; refresh++
                    } catch (e: ModerationRejectedException) {
                        runCatching { outbox.clear() }.onSuccess { pending = null }
                        operationMessage = e.message ?: "操作未执行，请检查后重试"
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) { operationMessage = if (pending != null) "结果未确认，请核实原操作。重复核实不会重复处置。" else e.message ?: "操作未能提交"
                    } finally { busy = false }
                }
            }, modifier = Modifier.weight(1f)) { Text(if (busy) "正在确认…" else if (pending == null) "确认下播" else "核实并重试") }
        }
    }
}

private fun moderationTime(value: String): String = runCatching { java.time.Instant.parse(value).atZone(java.time.ZoneId.systemDefault())
    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }.getOrDefault(value)
