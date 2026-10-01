package com.zhuquan.codeview.core

import org.json.JSONObject

/** A newer build offered by one of the update sources. */
data class ReleaseInfo(
    val version: String,
    val apkUrl: String,
    val notes: String = "",
    val sha256: String = "",
    val size: Long = 0L,
    val source: String = "",
    /** Same build on other hosts; tried in order when the first URL fails. */
    val mirrors: List<String> = emptyList(),
)

/**
 * Pure update logic: version comparison and parsing of the two manifests we publish
 * (our own `codeview-latest.json` on the relay, and GitHub's `releases/latest`).
 *
 * No Android APIs in here on purpose — this is the part worth unit-testing.
 */
object AppUpdate {

    /** `"v1.2.0"` -> `[1, 2, 0]`; parts without digits are dropped. */
    fun versionParts(value: String): List<Int> =
        value.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '+', '_')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }

    /** Strictly newer than [current], compared segment by segment. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = versionParts(candidate)
        val b = versionParts(current)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    /**
     * Our own manifest:
     * `{ "version": "1.2.0", "apkUrl": "...", "sha256": "...", "size": 1120000, "notes": "..." }`
     */
    fun parseManifest(json: String, source: String = "manifest"): ReleaseInfo? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val version = root.optString("version").trim()
        val apkUrl = root.optString("apkUrl").trim()
        if (version.isBlank() || !apkUrl.startsWith("http")) return null
        return ReleaseInfo(
            version = version,
            apkUrl = apkUrl,
            notes = root.optString("notes").trim(),
            sha256 = root.optString("sha256").trim(),
            size = root.optLong("size", 0L),
            source = source,
        )
    }

    /** GitHub's `releases/latest`: tag is the version, the first `.apk` asset is the download. */
    fun parseGithub(json: String): ReleaseInfo? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val version = root.optString("tag_name").trim()
        if (version.isBlank()) return null
        val assets = root.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (!asset.optString("name").endsWith(".apk", ignoreCase = true)) continue
            val url = asset.optString("browser_download_url").trim()
            if (url.isBlank()) continue
            // GitHub exposes `digest: "sha256:<hex>"` for uploaded assets.
            val digest = asset.optString("digest").trim()
            val sha = if (digest.startsWith("sha256:")) digest.removePrefix("sha256:") else ""
            return ReleaseInfo(
                version = version,
                apkUrl = url,
                notes = root.optString("body").trim(),
                sha256 = sha,
                size = asset.optLong("size", 0L),
                source = "github",
            )
        }
        return null
    }

    /** Newest candidate that actually beats [current], or null when everything is stale. */
    fun pick(candidates: List<ReleaseInfo>, current: String): ReleaseInfo? =
        candidates
            .filter { isNewer(it.version, current) }
            .reduceOrNull { best, next -> if (isNewer(next.version, best.version)) next else best }

    /** `"1.2 MB"` — kept here so both the banner and the sheet format the same way. */
    fun formatSize(bytes: Long): String = when {
        bytes <= 0L -> "未知大小"
        bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "%.2f MB".format(bytes / 1024.0 / 1024.0)
    }
}
