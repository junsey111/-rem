package cn.liveroom.app

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

internal data class DemoConversation(val id: String, val publicId: Long, val name: String, val avatar: String, val preview: String)
internal class DemoConversations(context: Context, userId: Long) {
    private val prefs = context.getSharedPreferences("demo_conversations_$userId", Context.MODE_PRIVATE)
    val rows = mutableStateListOf<DemoConversation>()
    init {
        val saved = runCatching { JSONArray(prefs.getString("rows", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until saved.length()) runCatching { saved.getJSONObject(i).let {
            rows.add(DemoConversation(it.getString("id"), it.getLong("publicId"), it.getString("name"), it.getString("avatar"), it.optString("preview")))
        } }
        context.getSharedPreferences("demo_profiles", Context.MODE_PRIVATE).all.forEach { (id, publicId) ->
            val peer = demoProfiles.firstOrNull { it.publicId == (publicId as? Number)?.toLong() } ?: return@forEach
            if (rows.any { it.id == id }) return@forEach
            val messages = runCatching { JSONArray(context.getSharedPreferences("demo_chat_${userId}_${peer.publicId}", Context.MODE_PRIVATE).getString("messages", "[]")) }.getOrDefault(JSONArray())
            if (messages.length() > 0) rows.add(DemoConversation(id, peer.publicId, peer.name,
                "/demo-media/${peer.publicId.toString().takeLast(5)}.jpg", messages.optJSONObject(messages.length()-1)?.optString("text").orEmpty()))
        }
        if (rows.isNotEmpty()) save()
    }
    fun touch(id: String, peer: DemoProfile) {
        if (rows.none { it.id == id }) {
            rows.add(0, DemoConversation(id, peer.publicId, peer.name, "/demo-media/${peer.publicId.toString().takeLast(5)}.jpg", "已开始聊天")); save()
        }
    }
    fun update(id: String, preview: String) {
        val row = rows.firstOrNull { it.id == id } ?: return
        rows.remove(row); rows.add(0, row.copy(preview = preview)); save()
    }
    private fun save() { prefs.edit().putString("rows", JSONArray().apply { rows.forEach { put(JSONObject()
        .put("id", it.id).put("publicId", it.publicId).put("name", it.name).put("avatar", it.avatar).put("preview", it.preview)) } }.toString()).apply() }
}
