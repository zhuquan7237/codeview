package com.zhuquan.codeview.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.zhuquan.codeview.core.AppUpdate
import com.zhuquan.codeview.core.ReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * In-app updates, no third-party dependency (the whole app is ~1 MB on purpose).
 *
 * Three sources are probed and the highest version wins — the relay mirror answers
 * from mainland China, the direct server port is fastest when it is reachable, and
 * GitHub is the fallback for networks that reach it. Whichever URL is used, the
 * download is checked against the published sha256 before the installer sees it.
 */
object Updater {

    private const val TAG = "CodeViewUpdate"
    private const val UA = "CodeView"

    /** Cloudflare-fronted mirror on the server (reachable without a VPN). */
    const val RELAY_MANIFEST = "https://relay.zhuquan.xyz/dl/codeview-latest.json"
    const val RELAY_APK = "https://relay.zhuquan.xyz/dl/codeview.apk"

    /** Direct server port — same files, no CDN in between. */
    const val CN_MANIFEST = "https://cn.zhuquan.xyz:8443/dl/codeview-latest.json"
    const val CN_APK = "https://cn.zhuquan.xyz:8443/dl/codeview.apk"

    const val GITHUB_LATEST = "https://api.github.com/repos/zhuquan7237/codeview/releases/latest"

    /** Don't re-check on every launch. */
    private const val RECHECK_AFTER_MS = 6 * 60 * 60 * 1000L

    // ---------------------------------------------------------------- checking

    fun checkedRecently(context: Context): Boolean =
        System.currentTimeMillis() - prefs(context).getLong("lastCheck", 0L) < RECHECK_AFTER_MS

    fun markChecked(context: Context) {
        prefs(context).edit().putLong("lastCheck", System.currentTimeMillis()).apply()
    }

    /** Version the user chose to skip, so a dismissed banner doesn't come back. */
    fun dismissed(context: Context): String = prefs(context).getString("dismissed", "").orEmpty()

    fun dismissVersion(context: Context, version: String) {
        prefs(context).edit().putString("dismissed", version).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("update", Context.MODE_PRIVATE)

    /** What a check learned: a candidate (or null), and whether any source answered at all. */
    data class CheckResult(val info: ReleaseInfo?, val reachable: Boolean)

    /**
     * Highest version any source offers that is newer than [current], with mirror URLs
     * attached. Returns null when everything is already up to date, so the caller can
     * stay silent — being current is not something the user needs to be told on launch.
     */
    suspend fun check(current: String): CheckResult = withContext(Dispatchers.IO) {
        val found = mutableListOf<ReleaseInfo>()
        var reachable = false
        fun probe(tag: String, url: String, parse: (String) -> ReleaseInfo?) {
            // 只有真的拿到响应才算"网络通"；解析失败（清单格式换了）仍是通。
            // 清单文件名是固定的，CDN 边缘可能给旧内容 —— 带时间戳绕过缓存。
            val body = runCatching { get(url, bustCache = true) }.getOrElse { error ->
                android.util.Log.i(TAG, "源[$tag] 失败：${error.javaClass.simpleName}: ${error.message}")
                null
            } ?: return
            reachable = true
            val one = runCatching { parse(body) }.getOrNull()
            android.util.Log.i(TAG, "源[$tag] → ${one?.version ?: "无"}")
            one?.let(found::add)
        }
        probe("relay", RELAY_MANIFEST) { AppUpdate.parseManifest(it, "relay") }
        probe("github", GITHUB_LATEST) { AppUpdate.parseGithub(it) }
        // 8443 直连排最后：只有服务器防火墙放行时它才通，通了才是最快的路。
        probe("cn", CN_MANIFEST) { AppUpdate.parseManifest(it, "cn") }

        val best = AppUpdate.pick(found, current) ?: return@withContext CheckResult(null, reachable)
        val mirrors = buildList {
            // 先 GitHub 资产（实测手机网络能下），再镜像，最后直连端口。
            found.filter { it.source == "github" }.forEach { add(it.apkUrl) }
            add(RELAY_APK)
            add(CN_APK)
        }.filter { it != best.apkUrl }.distinct()
        android.util.Log.i(TAG, "check：当前=$current 候选=${found.map { it.version }} 采用=${best.version}")
        CheckResult(best.copy(mirrors = mirrors), true)
    }

    // -------------------------------------------------------------- downloading

    /**
     * Streams the APK into the cache dir, then verifies size (when known) and sha256
     * (when published). A wrong hash deletes the file — a half-written or substituted
     * package must never reach the installer.
     */
    suspend fun download(
        context: Context,
        info: ReleaseInfo,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val urls = (listOf(info.apkUrl) + info.mirrors).distinct()
        var last: Exception? = null
        for (url in urls) {
            try {
                return@withContext downloadFrom(context, info, url, onProgress)
            } catch (error: Exception) {
                last = error
                android.util.Log.i(TAG, "下载失败 $url：${error.message}")
            }
        }
        throw last ?: IOException("没有可用的下载地址")
    }

    private fun downloadFrom(
        context: Context,
        info: ReleaseInfo,
        url: String,
        onProgress: (Int) -> Unit,
    ): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "codeview-${info.version}.apk")
        val connection = open(url, timeoutMs = 20_000)
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val declared = info.size.takeIf { it > 0 } ?: connection.contentLengthLong
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (declared > 0) {
                            val percent = ((copied * 100) / declared).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (info.size > 0 && target.length() != info.size) {
            val got = target.length()
            target.delete()
            throw IOException("下载不完整（$got/${info.size} 字节）")
        }
        if (info.sha256.isNotBlank() && !sha256(target).equals(info.sha256, ignoreCase = true)) {
            target.delete()
            throw IOException("校验失败（sha256 不匹配），已丢弃安装包")
        }
        onProgress(100)
        return target
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun get(url: String, bustCache: Boolean = false): String {
        val target = if (bustCache) "$url${if (url.contains('?')) '&' else '?'}t=${System.currentTimeMillis()}" else url
        val connection = open(target, timeoutMs = 8_000)
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, timeoutMs: Int): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json, application/vnd.android.package-archive, */*")
            setRequestProperty("Cache-Control", "no-cache")
        }

    // ---------------------------------------------------------------- install

    /** Whether this app may hand an APK to the system installer (Android O+). */
    fun canInstall(context: Context): Boolean =
        runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    /** Opens the per-app "install unknown apps" switch. */
    fun openInstallSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Hands the downloaded file to the system package installer. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
