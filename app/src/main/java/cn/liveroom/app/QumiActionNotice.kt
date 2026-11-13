package cn.liveroom.app

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

@Composable
internal fun QumiActionNotice(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    LaunchedEffect(message) { delay(1500); onDismiss() }
    Popup(alignment = Alignment.Center, properties = PopupProperties(focusable = false)) {
        Surface(color = Color(0xDB222222), contentColor = Color.White, shape = RoundedCornerShape(6.dp)) {
            Text(message, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 12.sp)
        }
    }
}
