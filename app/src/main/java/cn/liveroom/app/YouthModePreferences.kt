package cn.liveroom.app

import android.content.Context
import java.security.MessageDigest

data class YouthModeState(val enabled: Boolean, val hasPassword: Boolean)

object YouthModePreferences {
    private fun prefs(context: Context, userId: Long) =
        context.getSharedPreferences("youth_mode_$userId", Context.MODE_PRIVATE)

    fun load(context: Context, userId: Long) = prefs(context, userId).let {
        YouthModeState(it.getBoolean("enabled", false), it.getString("password_hash", null) != null)
    }

    fun setPasswordAndEnable(context: Context, userId: Long, password: String) {
        prefs(context, userId).edit()
            .putString("password_hash", hash(password))
            .putBoolean("enabled", true)
            .apply()
    }

    fun disable(context: Context, userId: Long, password: String): Boolean {
        val stored = prefs(context, userId).getString("password_hash", null) ?: return false
        if (stored != hash(password)) return false
        prefs(context, userId).edit().putBoolean("enabled", false).apply()
        return true
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
