package cn.liveroom.app

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCoverUploadSheet(session:UserSession,api:LivePlatformApi,onDismiss:()->Unit){
 val context=LocalContext.current;val scope=rememberCoroutineScope()
 var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf<String?>(null)}
 var revision by remember { mutableIntStateOf(0) }
 var uploaded by remember { mutableStateOf(false) }
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)scope.launch{
  busy=true;message=null;uploaded=false
  try{
   val bytes=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{it.readBytesLimited(3*1024*1024+1)} ?: error("无法读取所选图片")}
   require(bytes.size <= 3*1024*1024) { "图片不能超过 3MB" }
   api.uploadProfileCover(session.token,bytes);message="个人主页背景已保存";uploaded=true;revision++
  }catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"上传失败，请重试"}finally{busy=false}
 }}
 ModalBottomSheet(onDismissRequest={if(!busy)onDismiss()},containerColor=LiveStyle.sheetSurface,
  shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp),dragHandle=null){
  Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
   Box(Modifier.align(Alignment.CenterHorizontally).width(34.dp).height(4.dp).clip(CircleShape).background(Color(0xFFC8BEDD)))
   Row(verticalAlignment=Alignment.CenterVertically){
    Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Color(0xFFB08AF7),LiveStyle.accent))),contentAlignment=Alignment.Center){Icon(Icons.Rounded.Photo,null,tint=Color.White,modifier=Modifier.size(25.dp))}
    Column(Modifier.weight(1f).padding(start=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
     Text("个人主页背景",color=LiveStyle.ink,fontSize=20.sp,fontWeight=FontWeight.SemiBold)
     Text("用一张图片，装点你的专属主页",color=LiveStyle.muted,fontSize=12.sp)
    }
    IconButton(onClick=onDismiss,enabled=!busy){Icon(Icons.Rounded.Close,"关闭",tint=Color(0xFF9A8FAC),modifier=Modifier.size(21.dp))}
   }
   Box(Modifier.fillMaxWidth().height(158.dp).clip(RoundedCornerShape(18.dp))
    .background(Brush.horizontalGradient(listOf(Color(0xFFE3DDFF),Color(0xFFD7E9FF))))){
    ProfileCoverImage(session.id,revision)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.35f)))))
    Text(if(uploaded)"新的主页背景" else "当前主页背景",Modifier.align(Alignment.BottomStart).padding(14.dp),color=Color.White,fontSize=12.sp)
   }
   Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(12.dp),verticalAlignment=Alignment.CenterVertically){
    Text("JPG / PNG / WebP",Modifier.weight(1f),color=Color(0xFF7B6B93),fontSize=12.sp)
    Text("静态图片 · 不超过 3MB",color=Color(0xFF9B90AA),fontSize=11.sp)
   }
   if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp),color=LiveStyle.accent)
   message?.let{
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
     if(uploaded)Icon(Icons.Rounded.CheckCircle,null,tint=LiveStyle.accent,modifier=Modifier.size(17.dp))
     Text(it,color=if(uploaded)LiveStyle.accent else Color(0xFFB44D65),fontSize=13.sp)
    }
   }
   Button(enabled=!busy,onClick={picker.launch("image/*")},modifier=Modifier.fillMaxWidth().height(49.dp),shape=CircleShape,
    colors=ButtonDefaults.buttonColors(containerColor=LiveStyle.accent)){
    Icon(Icons.Rounded.Photo,null,modifier=Modifier.size(19.dp));Spacer(Modifier.width(8.dp));Text(if(busy)"正在上传…" else if(uploaded)"重新选择图片" else "选择图片并上传",fontSize=15.sp)
   }
  }
 }
}

@Composable
fun ProfileCoverImage(userId:Long,revision:Int=0){
 var bitmap by remember(userId,revision){mutableStateOf<android.graphics.Bitmap?>(null)}
 LaunchedEffect(userId,revision){
  bitmap=withContext(Dispatchers.IO){runCatching{
   require(userId>0)
   val connection=URL("${BuildConfig.BILLD_API_URL.trimEnd('/')}/user/profile-covers/$userId").openConnection() as HttpURLConnection
   try{connection.connectTimeout=5000;connection.readTimeout=5000;connection.useCaches=false;check(connection.responseCode==200)
    val bytes=connection.inputStream.use{it.readBytesLimited(3*1024*1024+1)};check(bytes.size<=3*1024*1024)
    BitmapFactory.decodeByteArray(bytes,0,bytes.size)}finally{connection.disconnect()}
  }.getOrNull()}
 }
 bitmap?.let{Image(it.asImageBitmap(),contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())}
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
