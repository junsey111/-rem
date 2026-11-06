package cn.liveroom.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun QumiAboutScreen(onBack: () -> Unit) {
    var document by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(document != null) { document = null }
    Column(Modifier.fillMaxSize().background(Color(0xFFF8F8F8))) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (document != null) document = null else onBack() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.ArrowBack, "返回", tint = Color.Black)
            }
            Text(document ?: "关于我们", Modifier.weight(1f), fontSize = 28.sp, color = Color.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.width(48.dp))
        }
        if (document != null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(document!!, color = Color.Black, fontSize = 24.sp)
                Text("正式内容尚未发布", color = Color(0xFF777777), fontSize = 18.sp)
                Text("平台运营方尚未提供此文档的正式版本，当前页面不构成协议正文。", color = Color(0xFF777777), fontSize = 16.sp, lineHeight = 26.sp)
            }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(61.dp))
            Image(painterResource(R.drawable.qumi_about_logo), "应用图标", Modifier.align(Alignment.CenterHorizontally).size(92.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
            Spacer(Modifier.height(70.dp))
            Column(Modifier.padding(horizontal = 24.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)) {
                listOf("用户协议", "隐私协议", "平台行为规范", "直播开通服务协议", "直播管理规则").forEachIndexed { index, title ->
                    Row(Modifier.fillMaxWidth().height(if (index == 0) 76.dp else if (index == 4) 99.dp else 88.dp)
                        .clickable { document = title }.padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), color = Color(0xFF333333), fontSize = 16.sp)
                        Icon(Icons.Rounded.ChevronRight, null, tint = Color(0xFF969AA6), modifier = Modifier.size(20.dp))
                    }
                    if (index < 4) HorizontalDivider(thickness = .5.dp, color = Color(0xFFE7E7E7))
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
