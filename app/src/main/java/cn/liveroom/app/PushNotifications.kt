package cn.liveroom.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val PUSH_PREFS = "live_push"
private const val REGISTERED_PUSH_TOKEN = "registered_token"
private const val PENDING_PUSH_TOKEN = "pending_token"
private const val CHANNEL_ID = "live-platform"
internal const val PUSH_KIND_EXTRA = "live_push_kind"
internal const val PUSH_ID_EXTRA = "live_push_id"
internal const val PUSH_NOTIFICATION_ID_EXTRA = "live_push_notification_id"
internal const val PUSH_ACTOR_ID_EXTRA = "live_push_actor_id"

private fun firebaseApp(context: Context): FirebaseApp? {
    FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }?.let { return it }
    if (BuildConfig.FIREBASE_API_KEY.isBlank() || BuildConfig.FIREBASE_APP_ID.isBlank()
        || BuildConfig.FIREBASE_PROJECT_ID.isBlank() || BuildConfig.FIREBASE_SENDER_ID.isBlank()) return null
    return runCatching {
        FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
            .build())
    }.getOrNull()
}

private suspend fun messagingToken(): String? = suspendCancellableCoroutine { continuation ->
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        continuation.resume(if (task.isSuccessful) task.result?.takeIf(String::isNotBlank) else null)
    }
}

object PushRegistration {
    suspend fun register(context: Context, authToken: String, api: LivePlatformApi) = withContext(Dispatchers.IO) {
        val app = firebaseApp(context) ?: return@withContext
        val pushToken = runCatching { messagingToken() }.getOrNull() ?: return@withContext
        registerToken(context, authToken, api, pushToken)
    }

    suspend fun registerToken(context: Context, authToken: String, api: LivePlatformApi, pushToken: String) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PUSH_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(PENDING_PUSH_TOKEN, pushToken).apply()
        runCatching {
            api.registerPushToken(authToken, pushToken)
            (prefs.getString(REGISTERED_PUSH_TOKEN, null) ?: prefs.getString("token", null))
                ?.takeIf { it != pushToken }?.let {
                api.removePushToken(authToken, it)
            }
        }.onSuccess {
            prefs.edit().putString(REGISTERED_PUSH_TOKEN, pushToken).remove(PENDING_PUSH_TOKEN).remove("token").apply()
        }
    }

    suspend fun unregister(context: Context, authToken: String, api: LivePlatformApi) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PUSH_PREFS, Context.MODE_PRIVATE)
        val tokens = listOfNotNull(
            prefs.getString(REGISTERED_PUSH_TOKEN, null),
            prefs.getString(PENDING_PUSH_TOKEN, null),
            prefs.getString("token", null),
        ).distinct()
        runCatching { tokens.forEach { api.removePushToken(authToken, it) } }
            .onSuccess { prefs.edit().remove(REGISTERED_PUSH_TOKEN).remove(PENDING_PUSH_TOKEN).remove("token").apply() }
    }

    fun remember(context: Context, token: String) {
        context.getSharedPreferences(PUSH_PREFS, Context.MODE_PRIVATE).edit().putString(PENDING_PUSH_TOKEN, token).apply()
    }
}

class LiveFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushRegistration.remember(this, token)
        val authToken = getSharedPreferences("live_session", Context.MODE_PRIVATE).getString("token", null)
        if (!authToken.isNullOrBlank()) runBlocking {
            PushRegistration.registerToken(this@LiveFirebaseMessagingService, authToken, LivePlatformApi(), token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "现场 Live"
        val body = message.notification?.body ?: message.data["body"] ?: return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "现场通知", NotificationManager.IMPORTANCE_DEFAULT))
        val notificationId = (System.currentTimeMillis() and 0x7fffffff).toInt()
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            message.data["kind"]?.let { putExtra(PUSH_KIND_EXTRA, it) }
            message.data["id"]?.let { putExtra(PUSH_ID_EXTRA, it) }
            message.data["notification_id"]?.let { putExtra(PUSH_NOTIFICATION_ID_EXTRA, it) }
            message.data["actor_id"]?.let { putExtra(PUSH_ACTOR_ID_EXTRA, it) }
        }
        val pendingIntent = intent?.let {
            PendingIntent.getActivity(this, notificationId, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = android.app.Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .apply { pendingIntent?.let(::setContentIntent) }
            .build()
        manager.notify(notificationId, notification)
    }
}
