package cn.liveroom.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomChatSheet(session:UserSession,api:LivePlatformApi,roomId:String,onDismiss:()->Unit){
 val scope=rememberCoroutineScope();val context=LocalContext.current
 val outbox=remember(session.id){BusinessOutbox(context,session.id)}
 var pending by remember{mutableStateOf(runCatching{outbox.load()})}
 var messages by remember{mutableStateOf(emptyList<JSONObject>())}
 var text by remember{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)}
 var before by remember{mutableStateOf("")}
 suspend fun load(){val result=api.businessRequest(session.token,"/rooms/$roomId/messages");val array=result.getJSONArray("messages");messages=(0 until array.length()).map{array.getJSONObject(it)}.reversed();before=result.optString("next_before").takeUnless{it=="null"}.orEmpty()}
 fun send(){scope.launch{
  busy=true;error=null
  try{val command=pending.getOrThrow()?:return@launch;api.businessRequest(session.token,command.getString("path"),"POST",command.getJSONObject("body"));outbox.clear();pending=Result.success(null);text="";load()}
  catch(e:CancellationException){throw e}catch(e:PlatformHttpException){error=e.message;if(e.statusCode in 400..499&&e.statusCode!=429){outbox.clear();pending=Result.success(null)}}catch(_:Exception){error="消息结果未确认，请用原单号核实"}finally{busy=false}
 }}
 LaunchedEffect(roomId){while(true){try{load()}catch(e:CancellationException){throw e}catch(_:Exception){error="消息暂时无法加载"};delay(3000)}}
 ModalBottomSheet(onDismissRequest={if(!busy)onDismiss()}){
  LazyColumn(Modifier.fillMaxWidth().imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   item{Text("直播公屏",style=MaterialTheme.typography.titleLarge);error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}
   messages.forEach{message->item(key=message.getString("id")){Text("${message.optString("username")}：${message.optString("text")}")}}
   if(messages.isEmpty())item{Text("暂无消息，说点什么吧")}
   item{OutlinedTextField(text,{if(it.length<=500)text=it},modifier=Modifier.fillMaxWidth(),label={Text("发送公屏消息")});Button(enabled=!busy&&pending.isSuccess&&(pending.getOrNull()!=null||text.isNotBlank()),onClick={
    if(pending.getOrNull()!=null)send()else try{outbox.save("/rooms/$roomId/messages",JSONObject().put("request_id",UUID.randomUUID().toString()).put("text",text));pending=Result.success(outbox.load());send()}catch(e:Exception){error=e.message}
   }){Text(if(pending.getOrNull()!=null)"核实原消息" else "发送")}}
  }
 }
}
