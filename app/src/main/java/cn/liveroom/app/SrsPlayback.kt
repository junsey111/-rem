package cn.liveroom.app

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * SRS docker image `ossrs/srs:6` with `conf/docker.conf` serves HLS at
 * `http://<playback-host>:<playback-port>/<app>/<stream>.m3u8`.
 * Billd `/live/list` has no play URL; the stream name is the live session / room id.
 * App name `live` matches the SRS examples and the pinned Billd demo paths (`/live/<stream>.flv`).
 * @媒体连麦 must publish the ordinary broadcast to this same app and stream.
 */
internal const val SRS_HLS_APP = "live"

internal fun srsPlaybackUrl(playbackBase: String, roomId: String): String? {
    val base = playbackBase.trim().trimEnd('/')
    val scheme = base.substringBefore("://", missingDelimiterValue = "")
    if (scheme != "http" && scheme != "https") return null
    val authority = base.substringAfter("://", "")
    if (authority.isBlank() || authority.startsWith("/")) return null
    if (!Regex("[1-9][0-9]{0,15}").matches(roomId)) return null
    return "$base/$SRS_HLS_APP/$roomId.m3u8"
}

/** Ordinary viewers use SRS. Host publish, an active PK, and an accepted co-host seat stay on LiveKit. */
internal fun viewerUsesSrsPlayback(isHost: Boolean, matchId: Long?, cohostMode: String?): Boolean =
    !isHost && matchId == null && cohostMode == null

/**
 * Who owns the camera for this room.
 * [HOST_RTMP] connects LiveKit but does not publish the camera; RTMP does.
 * [HOST_LIVEKIT] is PK or a co-host seat: RTMP has stopped, then LiveKit publishes.
 */
internal enum class LiveMediaRoute {
    IDLE, PENDING, VIEWER_HLS, VIEWER_PK, VIEWER_COHOST, VIEWER_VOICE,
    HOST_RTMP, HOST_LIVEKIT, HOST_VOICE,
}

internal fun liveMediaRoute(
    hasRoom: Boolean,
    isHost: Boolean,
    matchId: Long?,
    viewerCohostKnown: Boolean,
    viewerCohostMode: String?,
    hostCohostSeat: Boolean,
    roomType: String = "SINGLE",
): LiveMediaRoute = when {
    !hasRoom -> LiveMediaRoute.IDLE
    isHost && roomType == "VOICE" -> LiveMediaRoute.HOST_VOICE
    isHost && hostPublishesOrdinarySrs(true, matchId, hostCohostSeat) -> LiveMediaRoute.HOST_RTMP
    isHost -> LiveMediaRoute.HOST_LIVEKIT
    matchId != null -> LiveMediaRoute.VIEWER_PK
    !viewerCohostKnown -> LiveMediaRoute.PENDING
    viewerCohostMode != null -> LiveMediaRoute.VIEWER_COHOST
    roomType == "VOICE" -> LiveMediaRoute.VIEWER_VOICE
    else -> LiveMediaRoute.VIEWER_HLS
}

internal fun liveKitPublishesHostCamera(route: LiveMediaRoute): Boolean = route == LiveMediaRoute.HOST_LIVEKIT

internal fun srsPlaybackStatus(errorCode: Int?, playbackState: Int): String = when {
    errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
        errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
        "直播间暂无画面，请确认主播已开播后重试"
    errorCode != null -> "直播画面暂时无法播放，请退出后重试"
    playbackState == Player.STATE_READY -> "已加入直播间"
    playbackState == Player.STATE_ENDED -> "直播已结束或暂无画面"
    playbackState == Player.STATE_BUFFERING -> "正在缓冲直播画面"
    else -> "正在连接直播"
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun SrsPlaybackView(url: String, token: String, onStatus: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val onStatusState by rememberUpdatedState(onStatus)
    val player = remember(url, token) {
        val http = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
            .setAllowCrossProtocolRedirects(false)
        val mediaSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(http)
        ExoPlayer.Builder(context.applicationContext).setMediaSourceFactory(mediaSource).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player.playerError != null) return
                onStatusState(srsPlaybackStatus(null, playbackState))
            }

            override fun onPlayerError(error: PlaybackException) {
                onStatusState(srsPlaybackStatus(error.errorCode, player.playbackState))
            }
        }
        player.addListener(listener)
        onStatusState(srsPlaybackStatus(player.playerError?.errorCode, player.playbackState))
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                this.player = player
                keepScreenOn = true
            }
        },
        update = { view -> if (view.player !== player) view.player = player },
        onRelease = { view -> view.player = null },
        modifier = modifier,
    )
}
