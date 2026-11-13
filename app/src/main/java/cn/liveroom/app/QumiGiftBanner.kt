package cn.liveroom.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun QumiGiftCelebration(gift: Gift, nonce: Int, demo: Boolean,
    senderName: String, senderAvatar: String?, quantity: Int = 1, bottomPadding: Dp = 310.dp,
    onFinished: () -> Unit) {
    val opacity = remember { Animatable(0f) }
    val entrance = remember { Animatable(-56f) }
    val countScale = remember { Animatable(.65f) }
    LaunchedEffect(nonce) {
        opacity.snapTo(0f); entrance.snapTo(-56f); countScale.snapTo(.65f)
        launch { opacity.animateTo(1f, tween(180)) }
        launch { entrance.animateTo(0f, tween(280)) }
        countScale.animateTo(1.18f, tween(240)); countScale.animateTo(1f, tween(140))
        delay(2400)
        opacity.animateTo(0f, tween(300)); onFinished()
    }
    // No backdrop or touch handlers: the room and conversation remain usable.
    Box(Modifier.fillMaxSize()) {
        gift.stageStyle()?.let { GiftStageEffect(gift, nonce, it) }
        Row(Modifier.align(Alignment.BottomStart).navigationBarsPadding()
            .padding(start = 14.dp, bottom = bottomPadding).widthIn(max = 316.dp).fillMaxWidth(.88f)
            .graphicsLayer { alpha = opacity.value; translationX = entrance.value }
            .clip(CircleShape).background(Brush.horizontalGradient(listOf(
                Color(0xFF19111F).copy(alpha = .68f), Color(0xFF291A33).copy(alpha = .46f), Color(0xFF49324F).copy(alpha = .12f))))
            .padding(start = 5.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            QumiUserAvatar(senderAvatar, senderName, Modifier.size(42.dp))
            Column(Modifier.weight(1f).padding(start = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(senderName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("送${gift.name}", color = Color.White.copy(alpha = .78f), fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            GiftArtwork(gift, Modifier.size(49.dp), gift.name)
            Text("×${quantity.coerceAtLeast(1)}", color = Color(0xFFFFE8AA), fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(start = 3.dp).graphicsLayer { scaleX = countScale.value; scaleY = countScale.value })
        }
    }
}
