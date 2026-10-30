package cn.liveroom.app

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.room.Room

/** Thin adapter over the upstream LiveKit Android SDK for a live room or PK room. */
class LiveKitRoomGateway(context: Context) {
    private val room: Room = LiveKit.create(context.applicationContext)

    val remoteParticipantCount: Int
        get() = room.remoteParticipants.size

    suspend fun join(url: String, token: String) {
        room.connect(url, token)
    }

    fun leave() {
        room.disconnect()
    }
}
