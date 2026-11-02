package cn.liveroom.app

import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Downloads catalog effect_url files and checks the MP4 container before playback. */
class GiftEffectCache(private val root: File) {
    private val locks = HashMap<String, Any>()

    suspend fun prefetch(gifts: List<Gift>) {
        for (gift in gifts) {
            coroutineContext.ensureActive()
            val source = gift.yyevaSource() ?: continue
            runCatching { prepare(source) }
        }
    }

    fun prepare(source: YyevaSource): File {
        val key = cacheName(source.url)
        val lock = synchronized(locks) { locks.getOrPut(key) { Any() } }
        synchronized(lock) {
            root.mkdirs()
            val dest = File(root, key)
            if (giftEffectFileUsable(dest, source.md5)) return dest
            dest.delete()
            val partial = File(root, "$key.part")
            partial.delete()
            try {
                download(source.url, partial)
                if (!giftEffectFileUsable(partial, source.md5)) error("礼物特效文件校验失败")
                if (!partial.renameTo(dest) && !giftEffectFileUsable(dest, source.md5)) {
                    error("礼物特效文件未能写入缓存")
                }
            } catch (error: Throwable) {
                partial.delete()
                dest.delete()
                throw error
            }
            return dest
        }
    }
}

fun giftEffectFileUsable(file: File, expectedMd5: String): Boolean {
    if (!file.isFile) return false
    val length = file.length()
    if (length <= 0L || length > MAX_GIFT_EFFECT_BYTES) return false
    val header = ByteArray(12)
    file.inputStream().use { input -> if (input.read(header) != header.size) return false }
    if (!looksLikeMp4(header)) return false
    val expected = normalizeEffectMd5(expectedMd5)
    if (expected.isEmpty()) return true
    return md5(file) == expected
}

internal fun cacheName(url: String): String = md5(url.toByteArray(Charsets.UTF_8)) + ".mp4"

private fun download(url: String, dest: File) {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = true
    connection.connectTimeout = 8_000
    connection.readTimeout = 20_000
    connection.setRequestProperty("Accept", "video/mp4,application/octet-stream,*/*")
    try {
        val status = connection.responseCode
        if (status !in 200..299) error("礼物特效下载失败 HTTP $status")
        val advertised = connection.contentLengthLong
        if (advertised > MAX_GIFT_EFFECT_BYTES) error("礼物特效文件过大")
        connection.inputStream.use { input ->
            dest.outputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_GIFT_EFFECT_BYTES) error("礼物特效文件过大")
                    output.write(buffer, 0, read)
                }
            }
        }
    } finally {
        connection.disconnect()
    }
}

private fun md5(file: File): String = file.inputStream().use { md5(it) }

private fun md5(bytes: ByteArray): String = md5(bytes.inputStream())

private fun md5(input: InputStream): String {
    val digest = MessageDigest.getInstance("MD5")
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
