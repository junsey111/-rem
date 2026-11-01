package cn.liveroom.app

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Thin adapter over the upstream LiveKit Android SDK for a live room or PK room. */
class LiveKitRoomGateway(context: Context) {
    private val room: Room = LiveKit.create(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var renderer: SurfaceViewRenderer? = null
    private var attachedTrack: VideoTrack? = null

    val remoteParticipantCount: Int
        get() = room.remoteParticipants.size

    suspend fun join(url: String, token: String) {
        scope.launch {
            room.events.collect { event ->
                if (event is RoomEvent.TrackSubscribed) attachTrack(event.track as? VideoTrack)
            }
        }
        room.connect(url, token)
    }

    suspend fun publishCameraAndMicrophone() {
        room.localParticipant.setMicrophoneEnabled(true)
        room.localParticipant.setCameraEnabled(true)
        val track = room.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
        attachTrack(track)
    }

    fun createVideoRenderer(context: Context): SurfaceViewRenderer = SurfaceViewRenderer(context).also {
        room.initVideoRenderer(it)
        renderer = it
        attachAvailableTrack(it)
    }

    fun attachVideoRenderer(view: SurfaceViewRenderer) {
        renderer = view
        attachAvailableTrack(view)
    }

    fun leave() {
        room.disconnect()
        scope.cancel()
        renderer?.let { attachedTrack?.removeRenderer(it) }
        attachedTrack = null
        renderer = null
    }

    private fun attachAvailableTrack(view: SurfaceViewRenderer) {
        val local = room.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
        val remote = room.remoteParticipants.values.firstNotNullOfOrNull { participant ->
            participant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
        }
        attachTrack(local ?: remote, view)
    }

    private fun attachTrack(track: VideoTrack?) {
        val view = renderer ?: return
        attachTrack(track, view)
    }

    private fun attachTrack(track: VideoTrack?, view: SurfaceViewRenderer) {
        if (track == null || attachedTrack === track) return
        attachedTrack?.removeRenderer(view)
        track.addRenderer(view)
        attachedTrack = track
    }
}
