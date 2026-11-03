package cn.liveroom.app

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.events.collect
import io.livekit.android.events.RoomEvent
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MediaConnectionState { IDLE, CONNECTED, RECONNECTING, DISCONNECTED }

/** Thin adapter over the upstream LiveKit Android SDK for a live room, PK room, or 1v1 call. */
class LiveKitRoomGateway(context: Context) {
    private val room: Room = LiveKit.create(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val renderers = mutableMapOf<SurfaceViewRenderer, String?>()
    private val attachedTracks = mutableMapOf<SurfaceViewRenderer, VideoTrack>()
    private val connectionState = MutableStateFlow(MediaConnectionState.IDLE)
    private val videoIdentityState = MutableStateFlow<List<String>>(emptyList())
    val videoIdentities = videoIdentityState.asStateFlow()
    val connection = connectionState.asStateFlow()
    private var leaving = false

    val remoteParticipantCount: Int
        get() = room.remoteParticipants.size

    suspend fun join(url: String, token: String) {
        scope.launch {
            room.events.collect { event ->
                if (!leaving) when (event) {
                    is RoomEvent.Connected, is RoomEvent.Reconnected -> connectionState.value = MediaConnectionState.CONNECTED
                    is RoomEvent.Reconnecting -> connectionState.value = MediaConnectionState.RECONNECTING
                    is RoomEvent.Disconnected, is RoomEvent.FailedToConnect -> connectionState.value = MediaConnectionState.DISCONNECTED
                    else -> Unit
                }
                refreshRenderers()
            }
        }
        room.connect(url, token)
    }

    suspend fun publishCameraAndMicrophone() {
        room.localParticipant.setMicrophoneEnabled(true)
        room.localParticipant.setCameraEnabled(true)
        refreshRenderers()
    }
    suspend fun publishCohost(video: Boolean) {
        room.localParticipant.setMicrophoneEnabled(true)
        room.localParticipant.setCameraEnabled(video)
        refreshRenderers()
    }

    fun createVideoRenderer(context: Context, participantId: String? = null): SurfaceViewRenderer = SurfaceViewRenderer(context).also {
        room.initVideoRenderer(it)
        renderers[it] = participantId
        attachVideoRenderer(it)
    }

    fun attachVideoRenderer(view: SurfaceViewRenderer) {
        val identity = renderers[view]
        val participant = if (identity == null || room.localParticipant.identity?.value == identity) room.localParticipant
            else room.remoteParticipants.values.firstOrNull { it.identity?.value == identity }
        val track = participant?.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
        val fallback = if (identity == null) room.remoteParticipants.values.firstNotNullOfOrNull {
            it.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
        } else null
        val selected = track ?: fallback
        if (attachedTracks[view] === selected) return
        attachedTracks.remove(view)?.removeRenderer(view)
        if (selected != null) {
            selected.addRenderer(view)
            attachedTracks[view] = selected
        }
    }

    fun releaseVideoRenderer(view: SurfaceViewRenderer) {
        attachedTracks.remove(view)?.removeRenderer(view)
        renderers.remove(view)
    }

    fun leave() {
        leaving = true
        renderers.keys.toList().forEach(::releaseVideoRenderer)
        room.disconnect()
        scope.cancel()
    }

    private fun refreshRenderers() {
        videoIdentityState.value = (listOf(room.localParticipant) + room.remoteParticipants.values).filter {
            it.getTrackPublication(Track.Source.CAMERA)?.track is VideoTrack
        }.mapNotNull { it.identity?.value }.distinct()
        renderers.keys.toList().forEach(::attachVideoRenderer)
    }
}
