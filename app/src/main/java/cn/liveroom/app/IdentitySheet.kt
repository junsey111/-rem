package cn.liveroom.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentitySheet(session:UserSession,api:LivePlatformApi,onDismiss:()->Unit){
 val context=LocalContext.current;val scope=rememberCoroutineScope()
 val prefs=remember{context.getSharedPreferences("identity_request",android.content.Context.MODE_PRIVATE)}
 val key="${BuildConfig.BILLD_API_URL}:${session.id}"
 var enabled by remember{mutableStateOf(false)};var status by remember{mutableStateOf("读取中")};var error by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)}
 suspend fun load(){val result=api.businessRequest(session.token,"/verification/me");enabled=result.optBoolean("enabled");val state=result.optJSONObject("verification")?.optString("status");if(state=="REJECTED")check(prefs.edit().remove(key).commit());status=when(state){"VERIFIED"->"已通过服务商认证";"REJECTED"->"认证未通过";"PENDING"->"等待认证结果";else->"尚未认证"}}
 LaunchedEffect(session.id){runCatching{load()}.onFailure{error="认证状态读取失败"}}
 ModalBottomSheet(onDismissRequest={if(!busy)onDismiss()}){Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("实名认证",style=MaterialTheme.typography.titleLarge);Text(status);if(!enabled)Text("平台尚未启用实名认证服务，不会上传个人身份资料。");error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  Button(enabled=enabled&&!busy,onClick={scope.launch{busy=true;try{
   val id=prefs.getString(key,null)?:UUID.randomUUID().toString().also{check(prefs.edit().putString(key,it).commit())}
   val result=api.businessRequest(session.token,"/verification/start","POST",JSONObject().put("request_id",id));val url=result.optString("verification_url")
   if(url.startsWith("https://"))context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))else load()
  }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{busy=false}}}){Text("前往服务商认证")}
  TextButton(enabled=!busy,onClick={scope.launch{runCatching{load()}.onFailure{error="认证状态读取失败"}}}){Text("刷新认证结果")}
 }}
}
