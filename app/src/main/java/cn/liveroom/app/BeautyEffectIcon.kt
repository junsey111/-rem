package cn.liveroom.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

/** Original vector artwork; each treatment highlights the part that it changes. */
@Composable
internal fun BeautyEffectIcon(key: String, selected: Boolean) {
    Canvas(Modifier.size(32.dp)) {
        val ink = if (selected) Color(0xFFF3EAFF) else Color(0xFFDED7EC)
        val accent = if (selected) Color(0xFFD4B5FF) else Color(0xFFAC8ADC)
        scale(size.width / 32f, size.height / 32f, pivot = Offset.Zero) {
            effectIcon(key, ink, accent)
        }
    }
}

private fun DrawScope.effectIcon(key: String, ink: Color, accent: Color) {
    val stroke = Stroke(width = 1.45f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x: Float, y: Float, endX: Float, endY: Float, color: Color = ink) =
        drawLine(color, Offset(x, y), Offset(endX, endY), strokeWidth = stroke.width, cap = StrokeCap.Round)
    fun path(color: Color = ink, block: Path.() -> Unit) = drawPath(Path().apply(block), color, style = stroke)
    fun ring(x: Float, y: Float, radius: Float, color: Color = ink) = drawCircle(color, radius, Offset(x, y), style = stroke)
    fun spark(x: Float, y: Float, radius: Float, color: Color = accent) {
        path(color) { moveTo(x, y - radius); quadraticBezierTo(x + .6f, y - .6f, x + radius, y)
            quadraticBezierTo(x + .6f, y + .6f, x, y + radius)
            quadraticBezierTo(x - .6f, y + .6f, x - radius, y)
            quadraticBezierTo(x - .6f, y - .6f, x, y - radius); close() }
    }
    fun arrow(x: Float, y: Float, endX: Float, endY: Float) {
        line(x, y, endX, endY, accent)
        val dx = endX - x; val dy = endY - y
        val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(.1f)
        val ux = dx / length; val uy = dy / length
        line(endX, endY, endX - ux * 2.6f - uy * 2f, endY - uy * 2.6f + ux * 2f, accent)
        line(endX, endY, endX - ux * 2.6f + uy * 2f, endY - uy * 2.6f - ux * 2f, accent)
    }
    fun eye(x: Float = 4f, y: Float = 10f, width: Float = 24f, height: Float = 12f) {
        path { moveTo(x, y + height / 2); quadraticBezierTo(x + width / 2, y - height / 2, x + width, y + height / 2)
            quadraticBezierTo(x + width / 2, y + height * 1.5f, x, y + height / 2); close() }
        ring(x + width / 2, y + height / 2, height * .28f, accent)
    }
    fun face(narrow: Boolean = false, short: Boolean = false, pointed: Boolean = false) {
        val left = if (narrow) 9f else 7f; val right = 32f - left
        val bottom = if (short) 25f else 29f
        path { moveTo(left, 13f); cubicTo(left - 1f, 1f, right + 1f, 1f, right, 13f)
            cubicTo(right, 21f, if (pointed) 19f else 22f, bottom, 16f, bottom)
            cubicTo(if (pointed) 13f else 10f, bottom, left, 21f, left, 13f) }
        line(11f, 13f, 13f, 13f); line(19f, 13f, 21f, 13f)
        path(ink.copy(alpha = .65f)) { moveTo(13f, 22f); quadraticBezierTo(16f, 24f, 19f, 22f) }
    }
    fun lips(full: Boolean) {
        path { moveTo(4f, 17f); cubicTo(10f, if (full) 7f else 11f, 13f, 10f, 16f, 13f)
            cubicTo(19f, 10f, 22f, if (full) 7f else 11f, 28f, 17f)
            cubicTo(22f, if (full) 29f else 25f, 10f, if (full) 29f else 25f, 4f, 17f); close() }
        path(accent) { moveTo(4f, 17f); quadraticBezierTo(16f, 21f, 28f, 17f) }
    }
    when (key) {
        "bodyWaist", "bodyShoulders", "bodyHips", "bodyLegs" -> {
            ring(16f, 4f, 2.5f)
            path { moveTo(10f, 10f); quadraticBezierTo(5f, 12f, 4f, 19f) }
            path { moveTo(22f, 10f); quadraticBezierTo(27f, 12f, 28f, 19f) }
            path { moveTo(10f, 10f); quadraticBezierTo(13f, 15f, 10f, 21f); lineTo(11f, 30f) }
            path { moveTo(22f, 10f); quadraticBezierTo(19f, 15f, 22f, 21f); lineTo(21f, 30f) }
            line(16f, 22f, 16f, 30f)
            val y = when (key) { "bodyShoulders" -> 10f; "bodyWaist" -> 16f; "bodyHips" -> 21f; else -> 27f }
            arrow(1f, y, 7f, y); arrow(31f, y, 25f, y)
        }
        "smooth" -> {
            path { moveTo(14f, 4f); cubicTo(11f, 9f, 5f, 14f, 5f, 20f)
                cubicTo(5f, 30f, 23f, 30f, 23f, 20f); cubicTo(23f, 15f, 18f, 9f, 14f, 4f); close() }
            path(accent) { moveTo(9f, 20f); quadraticBezierTo(9f, 24f, 13f, 24f) }; spark(25f, 9f, 4f)
        }
        "blemish" -> {
            ring(14f, 18f, 10f); ring(10f, 15f, 1.1f, accent); ring(16f, 21f, 1.1f, accent)
            line(20f, 6f, 27f, 13f); line(20f, 13f, 27f, 6f); line(7f, 25f, 22f, 10f, accent)
        }
        "whiten" -> {
            ring(16f, 16f, 6.5f); for (i in 0..7) {
                val angle = i * kotlin.math.PI / 4; val x = kotlin.math.cos(angle).toFloat(); val y = kotlin.math.sin(angle).toFloat()
                line(16f + x * 10, 16f + y * 10, 16f + x * 13, 16f + y * 13, accent)
            }
        }
        "rosy" -> {
            path { moveTo(16f, 27f); cubicTo(11f, 23f, 3f, 18f, 5f, 11f)
                cubicTo(7f, 5f, 13f, 6f, 16f, 11f); cubicTo(19f, 6f, 25f, 5f, 27f, 11f)
                cubicTo(29f, 18f, 21f, 23f, 16f, 27f); close() }; spark(24f, 5f, 3f)
        }
        "clarity" -> {
            for (x in listOf(4f, 28f)) for (y in listOf(4f, 28f)) {
                line(x, y, x + if (x == 4f) 6f else -6f, y)
                line(x, y, x, y + if (y == 4f) 6f else -6f)
            }; spark(16f, 16f, 7f)
        }
        "sharpen" -> {
            path { moveTo(16f, 4f); lineTo(29f, 27f); lineTo(3f, 27f); close() }
            path(accent) { moveTo(16f, 5f); lineTo(16f, 26f); lineTo(28f, 26f) }
        }
        "brightEyes" -> { eye(); spark(25f, 7f, 3.5f) }
        "whiteTeeth" -> {
            path { moveTo(6f, 9f); cubicTo(7f, 1f, 13f, 7f, 16f, 7f)
                cubicTo(20f, 7f, 25f, 1f, 26f, 9f); cubicTo(27f, 15f, 24f, 28f, 21f, 28f)
                cubicTo(18f, 28f, 20f, 19f, 16f, 19f); cubicTo(12f, 19f, 14f, 28f, 11f, 28f)
                cubicTo(8f, 28f, 5f, 15f, 6f, 9f); close() }; spark(26f, 5f, 3f)
        }
        "darkCircles" -> {
            eye(4f, 7f, 24f, 10f)
            path(accent) { moveTo(7f, 24f); quadraticBezierTo(16f, 29f, 25f, 24f) }
            line(11f, 23f, 11f, 20f, accent); line(21f, 23f, 21f, 20f, accent)
        }
        "smileLines" -> {
            path { moveTo(12f, 5f); quadraticBezierTo(6f, 9f, 8f, 16f); quadraticBezierTo(9f, 21f, 6f, 27f) }
            path { moveTo(20f, 5f); quadraticBezierTo(26f, 9f, 24f, 16f); quadraticBezierTo(23f, 21f, 26f, 27f) }
            path(accent) { moveTo(12f, 22f); quadraticBezierTo(16f, 26f, 20f, 22f) }; spark(16f, 12f, 3f)
        }
        "bigEyes" -> { eye(4f, 10f, 24f, 12f); arrow(16f, 6f, 16f, 2f); arrow(16f, 26f, 16f, 30f) }
        "roundEyes" -> { eye(4f, 8f, 24f, 16f) }
        "eyeDistance" -> {
            eye(2f, 10f, 11f, 8f); eye(19f, 10f, 11f, 8f)
            arrow(14f, 25f, 7f, 25f); arrow(18f, 25f, 25f, 25f)
        }
        "eyePosition" -> { eye(); arrow(4f, 7f, 4f, 2f); arrow(28f, 25f, 28f, 30f) }
        "brows" -> {
            path { moveTo(3f, 15f); quadraticBezierTo(8f, 11f, 13f, 14f) }
            path { moveTo(19f, 14f); quadraticBezierTo(24f, 11f, 29f, 15f) }
            arrow(8f, 8f, 8f, 3f); arrow(24f, 22f, 24f, 27f)
        }
        "nose", "noseLength" -> {
            path { moveTo(14f, 5f); cubicTo(14f, 15f, 7f, 19f, 11f, 22f)
                quadraticBezierTo(16f, 26f, 21f, 22f); quadraticBezierTo(24f, 20f, 21f, 17f) }
            if (key == "nose") { arrow(2f, 19f, 7f, 19f); arrow(30f, 19f, 25f, 19f) }
            else { arrow(27f, 14f, 27f, 6f); arrow(27f, 19f, 27f, 27f) }
        }
        "mouth" -> { lips(false); arrow(3f, 6f, 8f, 6f); arrow(29f, 6f, 24f, 6f) }
        "lips" -> { lips(true); spark(26f, 6f, 3f) }
        "smile" -> {
            path { moveTo(5f, 14f); cubicTo(10f, 26f, 22f, 26f, 27f, 14f) }
            arrow(3f, 13f, 6f, 7f); arrow(29f, 13f, 26f, 7f)
        }
        "philtrum" -> {
            path { moveTo(11f, 6f); quadraticBezierTo(16f, 11f, 21f, 6f) }
            line(14f, 12f, 14f, 17f, accent); line(18f, 12f, 18f, 17f, accent)
            path { moveTo(6f, 23f); quadraticBezierTo(12f, 18f, 16f, 21f); quadraticBezierTo(20f, 18f, 26f, 23f)
                quadraticBezierTo(16f, 28f, 6f, 23f) }
        }
        "chin" -> {
            path { moveTo(6f, 5f); cubicTo(5f, 18f, 11f, 25f, 16f, 25f); cubicTo(21f, 25f, 27f, 18f, 26f, 5f) }
            arrow(16f, 18f, 16f, 30f)
        }
        "forehead" -> {
            path { moveTo(6f, 22f); lineTo(6f, 14f); cubicTo(6f, 1f, 26f, 1f, 26f, 14f); lineTo(26f, 22f) }
            path(ink.copy(alpha = .55f)) { moveTo(7f, 16f); quadraticBezierTo(16f, 10f, 25f, 16f) }
            arrow(16f, 13f, 16f, 6f)
        }
        else -> {
            face(narrow = key == "narrow", short = key == "short", pointed = key == "vFace")
            when (key) {
                "slim" -> { arrow(1f, 20f, 8f, 20f); arrow(31f, 20f, 24f, 20f) }
                "vFace" -> { line(9f, 21f, 16f, 29f, accent); line(23f, 21f, 16f, 29f, accent) }
                "narrow" -> { arrow(1f, 12f, 6f, 12f); arrow(31f, 12f, 26f, 12f) }
                "short" -> { arrow(16f, 2f, 16f, 8f); arrow(16f, 31f, 16f, 26f) }
                "small" -> { arrow(1f, 3f, 7f, 8f); arrow(31f, 29f, 25f, 24f) }
                "cheek" -> { arrow(1f, 16f, 9f, 16f); arrow(31f, 16f, 23f, 16f); ring(10f, 17f, 1.5f, accent); ring(22f, 17f, 1.5f, accent) }
                "jaw" -> { arrow(2f, 26f, 10f, 23f); arrow(30f, 26f, 22f, 23f) }
            }
        }
    }
}
