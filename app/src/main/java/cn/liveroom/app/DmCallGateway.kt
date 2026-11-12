package cn.liveroom.app

import android.content.Context
import io.livekit.android.renderer.SurfaceViewRenderer

/**
 * 1v1 private-call adapter over [LiveKitRoomGateway].
 * Live rooms and PK keep using [LiveKitRoomGateway] directly; hang up is [hangUp].
 */
class DmCallGateway(context: Context) {
    private val room = LiveKitRoomGateway(context)
    val connection = room.connection

    val remoteParticipantCount: Int
        get() = room.remoteParticipantCount

    suspend fun connect(url: String, token: String, video: Boolean) {
        room.join(url, token)
        room.publishCohost(video)
    }

    fun createVideoRenderer(context: Context, participantId: String?): SurfaceViewRenderer =
        room.createVideoRenderer(context, participantId)

    fun attachVideoRenderer(view: SurfaceViewRenderer) = room.attachVideoRenderer(view)

    fun releaseVideoRenderer(view: SurfaceViewRenderer) = room.releaseVideoRenderer(view)

    fun hangUp() = room.leave()
    suspend fun setMicrophone(enabled: Boolean) = room.setMicrophone(enabled)
    suspend fun setCamera(enabled: Boolean) = room.setCamera(enabled)
    fun flipCamera() = room.flipCamera()
    fun setSpeaker(enabled: Boolean) = room.setSpeaker(enabled)
}
