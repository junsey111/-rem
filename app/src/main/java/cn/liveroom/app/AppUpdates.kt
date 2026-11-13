package cn.liveroom.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class AppUpdate(val versionCode: Int, val versionName: String,
    val apkUrl: String, val sha256: String)

internal suspend fun availableAppUpdate(context: Context): AppUpdate? = withContext(Dispatchers.IO) {
    val source = URL(BuildConfig.APP_UPDATE_URL)
    val connection = source.openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 4000
        connection.readTimeout = 4000
        if (connection.responseCode != 200) return@withContext null
        val document = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val code = document.optInt("versionCode")
        val name = document.optString("versionName")
        val relativeApk = document.optString("apkUrl")
        val digest = document.optString("sha256").lowercase()
        val installed = PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
        if (code <= installed || name.isBlank() || !digest.matches(Regex("[0-9a-f]{64}")))
            return@withContext null
        val apk = URL(source, relativeApk)
        if (apk.protocol != source.protocol || apk.host != source.host || apk.port != source.port)
            return@withContext null
        AppUpdate(code, name, apk.toString(), digest)
    } finally { connection.disconnect() }
}

internal suspend fun downloadAppUpdate(context: Context, update: AppUpdate): File = withContext(Dispatchers.IO) {
    val folder = File(context.cacheDir, "updates").apply { mkdirs() }
    val apk = File(folder, "app-${update.versionCode}.apk")
    if (apk.isFile && sha256(apk) == update.sha256) return@withContext apk
    val partial = File(folder, "app-${update.versionCode}.partial")
    val connection = URL(update.apkUrl).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 8000
        connection.readTimeout = 20000
        check(connection.responseCode == 200) { "新版安装包暂时无法下载" }
        check(connection.contentLengthLong in 1L..250_000_000L) { "安装包大小异常" }
        connection.inputStream.use { input ->
            partial.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= 250_000_000L) { "安装包超过大小限制" }
                    output.write(buffer, 0, count)
                }
            }
        }
        check(sha256(partial) == update.sha256) { "安装包校验失败，请重试" }
        check(partial.renameTo(apk)) { "安装包保存失败" }
        apk
    } finally {
        connection.disconnect()
        partial.delete()
    }
}

internal fun installAppUpdate(context: Context, apk: File): Boolean {
    if (!context.packageManager.canRequestPackageInstalls()) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")))
        return false
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    })
    return true
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
