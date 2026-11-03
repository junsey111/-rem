package cn.liveroom.app

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

/** Listens for server giftAccepted events. This socket does not register the user as the host. */
class RoomGiftChannel(private val baseUrl: String = BuildConfig.BILLD_API_URL) {
    private var socket: Socket? = null

    fun listen(token: String, roomId: String, onGift: (RoomGiftBroadcast) -> Unit) {
        close()
        val active = IO.socket(baseUrl, IO.Options.builder()
            .setAuth(mapOf("token" to token))
            .setReconnection(true)
            .build())
        socket = active
        active.on(Socket.EVENT_CONNECT) {
            active.emit("join", JSONObject()
                .put("roomId", roomId)
                .put("roomName", "")
                .put("data", JSONObject()))
        }
        active.on("giftAccepted") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            parseRoomGiftBroadcast(data, roomId)?.let(onGift)
        }
        active.connect()
    }

    fun close() {
        socket?.let {
            it.off()
            it.disconnect()
            it.close()
        }
        socket = null
    }
}
