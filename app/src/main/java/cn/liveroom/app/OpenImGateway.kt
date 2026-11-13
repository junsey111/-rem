package cn.liveroom.app

import android.content.Context
import open_im_sdk.Open_im_sdk
import open_im_sdk_callback.Base
import open_im_sdk_callback.OnAdvancedMsgListener
import open_im_sdk_callback.OnConnListener
import open_im_sdk_callback.SendMsgCallBack
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class MessageConnection { CONNECTING, CONNECTED, DISCONNECTED, SESSION_EXPIRED }

/** Official OpenIM SDK Core adapter. Tokens and endpoints still come from the Billd gateway. */
class OpenImGateway(private val context: Context) {
    private var initialized = false
    private var activeToken: String? = null
    private val loginMutex = Mutex()
    private val _connection = MutableStateFlow(MessageConnection.DISCONNECTED)
    val connection = _connection.asStateFlow()
    private val _messageVersion = MutableStateFlow(0L)
    val messageVersion = _messageVersion.asStateFlow()

    suspend fun login(credentials: OpenImCredentials): String = loginMutex.withLock { withContext(Dispatchers.IO) {
        val dataDir = java.io.File(context.filesDir, "openim")
        val logDir = java.io.File(dataDir, "logs")
        check(dataDir.mkdirs() || dataDir.isDirectory) { "无法创建 OpenIM 数据目录" }
        check(logDir.mkdirs() || logDir.isDirectory) { "无法创建 OpenIM 日志目录" }
        val operationId = "android-${UUID.randomUUID()}"
        val config = JSONObject()
            .put("platformID", 1)
            .put("apiAddr", credentials.apiUrl)
            .put("wsAddr", credentials.wsUrl)
            .put("dataDir", dataDir.absolutePath)
            .put("logLevel", 3)
            .put("isLogStandardOutput", false)
            .put("logFilePath", logDir.absolutePath)
            .put("logRemainCount", 3)
            .toString()
        if (!initialized) {
            check(Open_im_sdk.initSDK(ConnectionListener(), operationId, config)) { "OpenIM SDK 初始化失败" }
            initialized = true
            Open_im_sdk.setAdvancedMsgListener(MessageListener())
        }
        if (Open_im_sdk.getLoginStatus(operationId) == 3L &&
            (_connection.value == MessageConnection.SESSION_EXPIRED || activeToken != credentials.token || Open_im_sdk.getLoginUserID() != credentials.userId)) {
            awaitBase { callback -> Open_im_sdk.logout(callback, operationId) }
        }
        if (Open_im_sdk.getLoginStatus(operationId) != 3L || Open_im_sdk.getLoginUserID() != credentials.userId) {
            awaitBase { callback -> Open_im_sdk.login(callback, operationId, credentials.userId, credentials.token) }
        }
        activeToken = credentials.token
        "OpenIM 已登录 ${credentials.userId}"
    } }

    suspend fun conversations(): String = withContext(Dispatchers.IO) {
        awaitBase { callback -> Open_im_sdk.getAllConversationList(callback, "android-${UUID.randomUUID()}") }
    }

    suspend fun sendText(recipientId: String, text: String): String = withContext(Dispatchers.IO) {
        val operationId = "android-${UUID.randomUUID()}"
        val message = Open_im_sdk.createTextMessage(operationId, text)
        awaitSend { callback -> Open_im_sdk.sendMessage(callback, operationId, message, recipientId, "", "", false) }
    }

    /** operationID, data, extension, description — same order as the SDK binding used for text. */
    suspend fun sendCustom(recipientId: String, data: String, extension: String, description: String): String = withContext(Dispatchers.IO) {
        val operationId = "android-${UUID.randomUUID()}"
        val message = Open_im_sdk.createCustomMessage(operationId, data, extension, description)
        awaitSend { callback -> Open_im_sdk.sendMessage(callback, operationId, message, recipientId, "", "", false) }
    }

    /** C2C conversation for an existing OpenIM user. Does not log in or send a message. */
    suspend fun ensureDirectConversation(recipientId: String): String = withContext(Dispatchers.IO) {
        check(recipientId.isNotBlank()) { "对方账号无效" }
        check(_connection.value == MessageConnection.CONNECTED) { "消息暂未连接，请稍后重试" }
        val conversation = JSONObject(awaitBase { callback ->
            Open_im_sdk.getOneConversation(callback, "android-${UUID.randomUUID()}", 1, recipientId)
        })
        conversation.getString("conversationID")
    }

    suspend fun history(recipientId: String, before: String = ""): OpenImHistory = withContext(Dispatchers.IO) {
        val conversationId = ensureDirectConversation(recipientId)
        val options = JSONObject().put("conversationID", conversationId).put("startClientMsgID", before).put("count", 30).put("viewType", 0)
        val result = JSONObject(awaitBase { callback ->
            Open_im_sdk.getAdvancedHistoryMessageList(callback, "android-${UUID.randomUUID()}", options.toString())
        })
        check(result.optInt("errCode") == 0) { result.optString("errMsg", "历史消息读取失败") }
        val rows = result.optJSONArray("messageList") ?: JSONArray()
        val allMessages = buildList {
            for (index in 0 until rows.length()) rows.optJSONObject(index)?.let { add(parseOpenImMessage(it)) }
        }.distinctBy { it.id }.sortedWith(compareBy<OpenImMessage> { it.sentAt }.thenBy { it.id })
        OpenImHistory(conversationId, allMessages.filter { it.status < 4 }, result.optBoolean("isEnd"), rows.optJSONObject(0)?.optString("clientMsgID").orEmpty())
    }

    suspend fun clearConversationHistory(recipientId: String) = withContext(Dispatchers.IO) {
        val conversationId = ensureDirectConversation(recipientId)
        awaitBase { callback -> Open_im_sdk.clearConversationAndDeleteAllMsg(callback, "android-${UUID.randomUUID()}", conversationId) }
        _messageVersion.update { it + 1 }
    }

    suspend fun markRead(conversationId: String) = withContext(Dispatchers.IO) {
        awaitBase { callback -> Open_im_sdk.markConversationMessageAsRead(callback, "android-${UUID.randomUUID()}", conversationId) }
    }

    fun close() {
        if (initialized) Open_im_sdk.unInitSDK("android-${UUID.randomUUID()}")
        initialized = false
        activeToken = null
        _connection.value = MessageConnection.DISCONNECTED
    }

    private suspend fun awaitBase(call: (Base) -> Unit): String = suspendCancellableCoroutine { continuation ->
        call(object : Base {
            override fun onSuccess(data: String) {
                if (continuation.isActive) continuation.resume(data)
            }

            override fun onError(errCode: Int, errMsg: String) {
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("OpenIM $errCode: $errMsg"))
            }
        })
    }

    private suspend fun awaitSend(call: (SendMsgCallBack) -> Unit): String = suspendCancellableCoroutine { continuation ->
        call(object : SendMsgCallBack {
            override fun onSuccess(data: String) { if (continuation.isActive) continuation.resume(data) }
            override fun onError(errCode: Int, errMsg: String) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("OpenIM $errCode: $errMsg")) }
            override fun onProgress(progress: Long) = Unit
        })
    }

    private inner class ConnectionListener : OnConnListener {
        override fun onConnecting() { _connection.value = MessageConnection.CONNECTING }
        override fun onConnectSuccess() { _connection.value = MessageConnection.CONNECTED }
        override fun onConnectFailed(code: Int, message: String) { _connection.value = MessageConnection.DISCONNECTED }
        override fun onKickedOffline() { _connection.value = MessageConnection.SESSION_EXPIRED }
        override fun onUserTokenExpired() { _connection.value = MessageConnection.SESSION_EXPIRED }
        override fun onUserTokenInvalid(message: String) { _connection.value = MessageConnection.SESSION_EXPIRED }
    }

    private inner class MessageListener : OnAdvancedMsgListener {
        private fun changed() { _messageVersion.update { it + 1 } }
        override fun onRecvNewMessage(message: String) = changed()
        override fun onRecvC2CReadReceipt(message: String) = changed()
        override fun onNewRecvMessageRevoked(message: String) = changed()
        override fun onRecvOfflineNewMessage(message: String) = changed()
        override fun onMsgDeleted(message: String) = changed()
        override fun onMessageModified(message: String) = changed()
        override fun onRecvOnlineOnlyMessage(message: String) = changed()
    }
}
