package cn.liveroom.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val momentServerZone = ZoneId.of("Asia/Shanghai")

@Composable
internal fun rememberMomentNow(): State<Instant> = produceState(Instant.now()) {
    while (true) {
        delay(60_000L - System.currentTimeMillis() % 60_000L)
        value = Instant.now()
    }
}

internal fun momentCommentTime(value: String, now: Instant = Instant.now(),
    zone: ZoneId = momentServerZone): String {
    val normalized = value.trim().replace(' ', 'T')
    val posted = runCatching { Instant.parse(normalized) }
        .recoverCatching { LocalDateTime.parse(normalized).atZone(momentServerZone).toInstant() }
        .getOrNull() ?: return value.replace('T', ' ').take(10)
    val elapsed = Duration.between(posted, now).seconds.coerceAtLeast(0)
    return when {
        elapsed < 3600 -> "${(elapsed / 60).coerceAtLeast(1)}分钟前"
        elapsed <= 86400 -> "${elapsed / 3600}小时前"
        else -> DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)
            .withZone(zone).format(posted)
    }
}
