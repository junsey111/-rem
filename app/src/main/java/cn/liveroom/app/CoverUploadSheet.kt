package cn.liveroom.app

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverUploadSheet(session:UserSession,api:LivePlatformApi,onDismiss:()->Unit){
 val context=LocalContext.current;val scope=rememberCoroutineScope()
 var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf<String?>(null)}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)scope.launch{
  busy=true;message=null
  try{val bytes=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)!!.use{it.readBytesLimited(3*1024*1024+1)}};api.uploadCover(session.token,bytes);message="封面已保存，直播列表将在刷新后更新"}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"上传失败"}finally{busy=false}
 }}
 ModalBottomSheet(onDismissRequest={if(!busy)onDismiss()}){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("直播封面",style=MaterialTheme.typography.titleLarge);Text("选择静态 JPEG、PNG 或 WebP，大小不超过 3MB。图片会裁剪为竖版封面并移除原始元数据。");message?.let{Text(it)};if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());Button(enabled=!busy,onClick={picker.launch("image/*")}){Text("选择图片并上传")}}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarUploadSheet(session: UserSession, api: LivePlatformApi, onUploaded: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = null
            try {
                val bytes = withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytesLimited(5 * 1024 * 1024 + 1) }
                }
                require(bytes.size <= 5 * 1024 * 1024) { "头像不能超过 5MB" }
                onUploaded(api.uploadAvatar(session.token, bytes))
                message = "头像已更新"
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                message = error.message ?: "头像上传失败"
            } finally {
                busy = false
            }
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("更换头像", style = MaterialTheme.typography.titleLarge)
            Text("选择静态 JPEG、PNG 或 WebP，大小不超过 5MB。服务端会自动裁切并清除原图元数据。")
            message?.let { Text(it) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(enabled = !busy, onClick = { picker.launch("image/*") }) { Text("选择图片并上传") }
        }
    }
}

internal fun java.io.InputStream.readBytesLimited(limit:Int):ByteArray{
 val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
 while(output.size()<limit){val n=read(buffer,0,minOf(buffer.size,limit-output.size()));if(n<0)break;output.write(buffer,0,n)}
 return output.toByteArray()
}
@Composable
fun LiveCoverImage(path:String,onLoaded:((Boolean)->Unit)?=null){
 var bitmap by remember(path){mutableStateOf<android.graphics.Bitmap?>(null)}
 LaunchedEffect(path){val loaded=withContext(Dispatchers.IO){runCatching{
  require(path.matches(Regex("/live/covers/[1-9][0-9]{0,9}")))
  val connection=URL(BuildConfig.BILLD_API_URL.trimEnd('/')+path).openConnection() as HttpURLConnection
  try{connection.connectTimeout=5000;connection.readTimeout=5000;connection.instanceFollowRedirects=false;check(connection.responseCode==200);val bytes=connection.inputStream.use{it.readBytesLimited(2*1024*1024+1)};check(bytes.size<=2*1024*1024);BitmapFactory.decodeByteArray(bytes,0,bytes.size)}finally{connection.disconnect()}
 }.getOrNull()};bitmap=loaded;onLoaded?.invoke(loaded!=null)}
 bitmap?.let{Image(it.asImageBitmap(),contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())}
}
