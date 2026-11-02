package cn.liveroom.app

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

data class CohostRequest(val id: Long, val requestId: String, val liveId: Long, val userId: Long, val mode: String, val status: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CohostSheet(session: UserSession, liveId: Long, isHost: Boolean, api: LivePlatformApi, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val outbox = remember(session.id) { BusinessOutbox(context,session.id) }
    var pending by remember { mutableStateOf(runCatching { outbox.load() }) }
    var requests by remember { mutableStateOf(emptyList<CohostRequest>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var permissionMode by remember { mutableStateOf("AUDIO") }
    suspend fun refresh() { requests = api.cohosts(session.token,liveId) }
    fun send() {
        scope.launch {
            busy=true; error=null
            try {
                val command=pending.getOrThrow() ?: return@launch
                api.businessRequest(session.token,command.getString("path"),"POST",command.getJSONObject("body"))
                outbox.clear();pending=Result.success(null);refresh();onChanged()
            } catch(e:CancellationException) { throw e
            } catch(e:PlatformHttpException) {
                error=e.message
                if(e.statusCode in 400..499 && e.statusCode!=429) { outbox.clear();pending=Result.success(null) }
            } catch(e:Exception) { error="操作结果未确认，请用原单号核实"
            } finally { busy=false }
        }
    }
    fun submit(id:Long?=null, action:String?=null, mode:String?=null) {
        try {
            val body=JSONObject().put("request_id",UUID.randomUUID().toString())
            if(action!=null)body.put("action",action)
            if(mode!=null)body.put("mode",mode)
            outbox.save("/revenue/cohosts/$liveId${id?.let{"/$it"}.orEmpty()}",body)
            pending=Result.success(outbox.load());send()
        } catch(e:Exception){error=e.message}
    }
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){grants->
        if(grants.values.all{it})submit(mode=permissionMode)else error="连麦需要麦克风权限，视频还需要摄像头权限"
    }
    LaunchedEffect(liveId) {
        while(true) {
            try { refresh() } catch(e:CancellationException){throw e} catch(_:Exception){error="读取连麦队列失败，请稍后刷新"}
            delay(3000)
        }
    }
    ModalBottomSheet(onDismissRequest = { if(!busy)onDismiss() }) {
        LazyColumn(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(if(isHost)"连麦管理" else "申请连麦",style=MaterialTheme.typography.titleLarge)
                error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                if(pending.isFailure)Text("本地待确认记录无法读取，暂不能发起操作")
                if(pending.getOrNull()!=null)Button(enabled=!busy,onClick={send()}){Text("用原单号核实待确认操作")}
            }
            if(!isHost)item {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    for(mode in listOf("AUDIO","VIDEO"))OutlinedButton(enabled=!busy&&pending.isSuccess&&pending.getOrNull()==null,onClick={
                        permissionMode=mode
                        permissions.launch(if(mode=="VIDEO")arrayOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.CAMERA)else arrayOf(Manifest.permission.RECORD_AUDIO))
                    }){Text(if(mode=="VIDEO")"视频连麦" else "语音连麦")}
                }
            }
            items(requests,key={it.id}){request->
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("用户 ${request.userId} · ${if(request.mode=="VIDEO")"视频" else "语音"} · ${businessStatus(request.status)}")
                    val actions=if(isHost)when(request.status){"PENDING"->listOf("ACCEPT","REJECT");"ACCEPTED"->listOf("MUTE","REMOVE");"MUTED"->listOf("UNMUTE","REMOVE");else->emptyList()}
                        else if(request.status in listOf("PENDING","ACCEPTED","MUTED"))listOf("LEAVE")else emptyList()
                    Row {
                        actions.forEach{action->TextButton(enabled=!busy&&pending.isSuccess&&pending.getOrNull()==null,onClick={submit(request.id,action)}){
                            Text(mapOf("ACCEPT" to "接受","REJECT" to "拒绝","MUTE" to "暂停连麦","UNMUTE" to "恢复","REMOVE" to "移除","LEAVE" to "退出")[action].orEmpty())
                        }}
                    }
                    HorizontalDivider()
                }
            }
            if(requests.isEmpty())item{Text("暂无连麦申请")}
        }
    }
}
