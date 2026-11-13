package cn.liveroom.app

import org.json.JSONObject

/** Billd stores its canonical Socket.IO join payload as JSON text in each directory row. */
internal fun parseLiveDirectory(data: JSONObject): List<BilldRoom> {
    val rows = data.getJSONArray("rows")
    val rooms = (0 until rows.length()).map { index ->
        val row = rows.getJSONObject(index)
        val details = when (val value = row.get("data")) {
            is JSONObject -> value
            is String -> JSONObject(value)
            else -> error("直播目录格式无效")
        }
        val roomId = row.getString("roomId")
        check(Regex("[1-9][0-9]{0,15}").matches(roomId) && details.getString("roomId") == roomId) { "直播目录身份不一致" }
        val owner = details.getJSONObject("data")
        validateLiveRoom(roomId, details.optString("roomName"), details.getString("roomId"),
            owner.get("userId").toString(), owner.optString("username")).copy(
                coverPath = row.optString("cover_path").takeIf { it.matches(Regex("/live/covers/[1-9][0-9]{0,9}")) },
                roomType = details.optString("roomType").takeIf { it in setOf("SINGLE", "MULTI", "VOICE") } ?: "SINGLE",
                welcomeText = details.optString("welcomeText").take(120),
                backgroundId = details.optInt("backgroundId", 0).coerceIn(0, 3),
                announcementText = details.optString("announcementText").take(300),
            )
    }
    check(rooms.map { it.roomId }.distinct().size == rooms.size) { "直播目录存在重复房间" }
    return rooms
}

internal fun validateLiveRoom(roomId: String, title: String, nestedRoomId: String, ownerId: String, ownerName: String): BilldRoom {
    check(Regex("[1-9][0-9]{0,15}").matches(roomId) && nestedRoomId == roomId) { "直播目录身份不一致" }
    val creatorId = ownerId.toLongOrNull()
    check(creatorId != null && creatorId > 0) { "直播目录缺少有效主播身份" }
    return BilldRoom(roomId, title.ifBlank { "主播正在直播" }, ownerName.ifBlank { "未设置昵称" }, creatorId)
}
