package cn.liveroom.app

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.events.collect
import io.livekit.android.events.RoomEvent
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.audio.AudioSwitchHandler
import com.twilio.audioswitch.AudioDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MediaConnectionState { IDLE, CONNECTED, RECONNECTING, DISCONNECTED }

/** Thin adapter over the upstream LiveKit Android SDK for a live room, PK room, or 1v1 call. */
class LiveKitRoomGateway(context: Context, beauty: CameraBeautySettings? = null) {
    private val sharedEgl = if (beauty != null) livekit.org.webrtc.EglBase.create() else null
    private val room: Room = LiveKit.create(context.applicationContext,
        overrides = LiveKitOverrides(eglBase = sharedEgl))
    private val beautyProcessor = beauty?.let { BeautyVideoProcessor(context.applicationContext, sharedEgl!!.eglBaseContext, it) }
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
        enableCamera(true)
        refreshRenderers()
    }
    suspend fun publishCohost(video: Boolean) {
        room.localParticipant.setMicrophoneEnabled(true)
        enableCamera(video)
        refreshRenderers()
    }

    suspend fun setMicrophone(enabled: Boolean) { room.localParticipant.setMicrophoneEnabled(enabled) }
    suspend fun setCamera(enabled: Boolean) {
        enableCamera(enabled)
        refreshRenderers()
    }
    private suspend fun enableCamera(enabled: Boolean) {
        val participant = room.localParticipant
        if (enabled && beautyProcessor != null && participant.getTrackPublication(Track.Source.CAMERA) == null) {
            val track = participant.createVideoTrack(videoProcessor = beautyProcessor)
            track.startCapture()
            participant.publishVideoTrack(track)
        } else participant.setCameraEnabled(enabled)
    }
    fun updateBeauty(settings: CameraBeautySettings) { beautyProcessor?.update(settings) }
    fun flipCamera() {
        val track = room.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
            ?: error("请先开启摄像头")
        track.switchCamera()
    }
    fun setSpeaker(enabled: Boolean) {
        val audio = room.audioHandler as? AudioSwitchHandler ?: error("当前设备暂不支持切换扬声器")
        val target = audio.availableAudioDevices.firstOrNull {
            if (enabled) it is AudioDevice.Speakerphone else it is AudioDevice.Earpiece
        } ?: error("当前设备没有可切换的音频输出")
        audio.selectDevice(target)
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
        if (leaving) return
        leaving = true
        renderers.keys.toList().forEach(::releaseVideoRenderer)
        room.disconnect()
        room.release()
        if (beautyProcessor != null) beautyProcessor.close { sharedEgl?.release() } else sharedEgl?.release()
        scope.cancel()
    }

    private fun refreshRenderers() {
        videoIdentityState.value = (listOf(room.localParticipant) + room.remoteParticipants.values).filter {
            it.getTrackPublication(Track.Source.CAMERA)?.track is VideoTrack
        }.mapNotNull { it.identity?.value }.distinct()
        renderers.keys.toList().forEach(::attachVideoRenderer)
    }
}
