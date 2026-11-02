package cn.liveroom.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralSheet(
    token: String,
    api: LivePlatformApi,
    pendingRequestId: String?,
    onPendingRequestId: (String?) -> Unit,
    onInviteeBalance: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<ReferralProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var codeText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var redeemError by remember { mutableStateOf<String?>(null) }
    var receiptText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(token, refresh) {
        loading = true
        loadError = null
        try {
            profile = api.referralProfile(token)
        } catch (error: CancellationException) {
            throw error
        } catch (error: PlatformHttpException) {
            profile = null
            loadError = referralErrorMessage(error.statusCode, error.errorCode, error.message)
        } catch (_: Exception) {
            profile = null
            loadError = "邀请信息暂时无法加载，请重试"
        } finally {
            loading = false
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("拉新送币", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            loadError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { refresh++ }, enabled = !loading) { Text("重新加载") }
            }
            profile?.let { current ->
                if (!current.enabled) {
                    Text("邀请奖励暂未开通。")
                    Text("服务端没有返回邀请码，这里不会编造邀请码或金币。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    if (current.code == null) {
                        Text("邀请码暂未返回", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("我的邀请码", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        SelectionContainer { Text(current.code, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                    }
                    current.invitedCount?.let { Text(referralInviteSummary(it)) }
                    current.rewardedCoins?.let { Text("作为邀请人累计获得 $it 金币") }
                    if (current.invitedCount == null) Text("邀请人数暂未返回", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = codeText,
                        onValueChange = { if (it.length <= 32) codeText = it.uppercase() },
                        label = { Text("兑换邀请码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    receiptText?.let { Text(it) }
                    redeemError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (pendingRequestId != null && redeemError != null) {
                        Text("将用原单号重试，不会另开一笔兑换。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            val code = codeText.trim()
                            if (code.isEmpty()) {
                                redeemError = "请填写邀请码"
                                return@Button
                            }
                            val requestId = pendingRequestId ?: UUID.randomUUID().toString()
                            onPendingRequestId(requestId)
                            scope.launch {
                                busy = true
                                redeemError = null
                                try {
                                    val receipt = api.redeemReferral(token, requestId, code)
                                    val text = referralReceiptText(receipt)
                                    if (text == null) {
                                        redeemError = "兑换结果尚未确认，请用原单号重试"
                                    } else {
                                        receiptText = text
                                        onInviteeBalance(receipt.inviteeBalance)
                                        onPendingRequestId(null)
                                        runCatching { profile = api.referralProfile(token) }
                                    }
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: PlatformHttpException) {
                                    redeemError = referralErrorMessage(error.statusCode, error.errorCode, error.message)
                                } catch (error: IllegalStateException) {
                                    redeemError = error.message ?: "兑换结果尚未确认，请用原单号重试"
                                } catch (_: Exception) {
                                    redeemError = "兑换结果尚未确认，请用原单号重试"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy && codeText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (busy) "正在兑换…" else "兑换") }
                }
            }
        }
    }
}
