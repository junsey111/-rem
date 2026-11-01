package cn.liveroom.app

import android.content.Context
import open_im_sdk.Open_im_sdk
import open_im_sdk_callback.Base
import open_im_sdk_callback.OnAdvancedMsgListener
import open_im_sdk_callback.OnConnListener
import open_im_sdk_callback.SendMsgCallBack
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OpenImMessage(val senderId: String, val receiverId: String, val content: String, val raw: String)

/** Official OpenIM SDK Core adapter. Tokens and endpoints still come from the Billd gateway. */
class OpenImGateway(private val context: Context) {
    private var initialized = false

    suspend fun login(credentials: OpenImCredentials): String = withContext(Dispatchers.IO) {
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
        awaitBase { callback -> Open_im_sdk.login(callback, operationId, credentials.userId, credentials.token) }
        "OpenIM 已登录 ${credentials.userId}"
    }

    suspend fun conversations(): String = withContext(Dispatchers.IO) {
        awaitBase { callback -> Open_im_sdk.getAllConversationList(callback, "android-${UUID.randomUUID()}") }
    }

    suspend fun sendText(recipientId: String, text: String): String = withContext(Dispatchers.IO) {
        val operationId = "android-${UUID.randomUUID()}"
        val message = Open_im_sdk.createTextMessage(operationId, text)
        awaitSend { callback -> Open_im_sdk.sendMessage(callback, operationId, message, recipientId, "", "", false) }
    }

    fun close() {
        if (initialized) Open_im_sdk.unInitSDK("android-${UUID.randomUUID()}")
        initialized = false
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

    private class ConnectionListener : OnConnListener {
        override fun onConnecting() = Unit
        override fun onConnectSuccess() = Unit
        override fun onConnectFailed(code: Int, message: String) = Unit
        override fun onKickedOffline() = Unit
        override fun onUserTokenExpired() = Unit
        override fun onUserTokenInvalid(message: String) = Unit
    }

    private class MessageListener : OnAdvancedMsgListener {
        override fun onRecvNewMessage(message: String) = Unit
        override fun onRecvC2CReadReceipt(message: String) = Unit
        override fun onNewRecvMessageRevoked(message: String) = Unit
        override fun onRecvOfflineNewMessage(message: String) = Unit
        override fun onMsgDeleted(message: String) = Unit
        override fun onMessageModified(message: String) = Unit
        override fun onRecvOnlineOnlyMessage(message: String) = Unit
    }
}
