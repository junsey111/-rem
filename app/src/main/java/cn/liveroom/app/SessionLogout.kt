package cn.liveroom.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val logoutMutex = Mutex()

/** Persist remote revocation before removing the local login, including when offline. */
suspend fun queueLogout(context: Context, token: String) = withContext(Dispatchers.IO) {
    logoutMutex.withLock {
        check(context.getSharedPreferences("live_session", Context.MODE_PRIVATE).edit()
            .clear().putString("pending_logout", token).commit()) { "退出未保存，请重试" }
    }
}

suspend fun flushLogout(context: Context, api: LivePlatformApi) = logoutMutex.withLock {
    withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("live_session", Context.MODE_PRIVATE)
        val token = prefs.getString("pending_logout", null) ?: return@withContext
        try {
            api.logout(token)
        } catch (error: PlatformHttpException) {
            if (error.statusCode != 401) throw error
            // A replaced or expired app session already has no authority to retain.
        }
        check(prefs.edit().remove("pending_logout").commit()) { "退出记录未保存，请重试" }
    }
}
