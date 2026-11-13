package cn.liveroom.app

import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

internal object LiveStyle {
    val canvas = Color(0xFFF5F6F8)
    val surface = Color.White
    val raised = Color(0xFFEEF1F3)
    val line = Color(0xFFE4E7EB)
    val ink = Color(0xFF171A20)
    val muted = Color(0xFF69727D)
    val live = Color(0xFFE94755)
    val liveSoft = Color(0xFFFFE9EC)
    val accent = Color(0xFF7044D8)
    val accentSoft = Color(0xFFF0EAFF)
    val dialogSurface = Color(0xFFFDFCFF)
    val sheetSurface = Color.White
    val teal = Color(0xFF087F78)
    val tealSoft = Color(0xFFE5F4F1)
    val gold = Color(0xFFA96B13)
    val goldSoft = Color(0xFFFFF2D9)
    val stage = Color(0xFF14191D)
    val stageSurface = Color(0xFF22282C)

    val colors = lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = accentSoft,
        onPrimaryContainer = ink,
        secondary = teal,
        onSecondary = Color.White,
        secondaryContainer = tealSoft,
        onSecondaryContainer = ink,
        tertiary = gold,
        background = canvas,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceContainerLow = sheetSurface,
        surfaceContainerHigh = dialogSurface,
        surfaceVariant = raised,
        onSurfaceVariant = muted,
        outline = line,
    )

    val shapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(10.dp),
        extraLarge = RoundedCornerShape(14.dp),
    )
}
