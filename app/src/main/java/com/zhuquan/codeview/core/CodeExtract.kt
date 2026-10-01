package com.zhuquan.codeview.core

/**
 * Turns "what is actually on the clipboard after an AI answer" into something renderable.
 *
 * Chat models almost never hand over a bare file: they wrap the code in ``` fences and
 * put prose around it. Pasting that straight into a .svg file renders the explanation
 * instead of the picture, so we strip it here.
 *
 * Pure Kotlin on purpose: every rule below is unit tested.
 */
object CodeExtract {

    data class Extracted(
        /** The code itself, without fences or surrounding prose. */
        val code: String,
        /** Language tag written after the opening fence, if any. */
        val fenceLang: String?,
        /** True when fences/prose were removed. */
        val stripped: Boolean,
    )

    private val FENCE_LINE = Regex("^[ \\t]*```([A-Za-z0-9+#._-]*)[ \\t]*$")
    private val FENCE_ANY = Regex("(?m)^[ \\t]*```([A-Za-z0-9+#._-]*)[ \\t]*$")

    private val EXT_BY_FENCE_LANG = mapOf(
        "html" to "html", "htm" to "html", "xhtml" to "html",
        "svg" to "svg",
        "xml" to "xml", "plist" to "xml",
        "json" to "json", "json5" to "json",
        "css" to "css", "scss" to "css", "less" to "css",
        "js" to "js", "javascript" to "js", "mjs" to "js", "cjs" to "js", "jsx" to "js",
        "ts" to "ts", "typescript" to "ts", "tsx" to "ts",
        "md" to "md", "markdown" to "md",
        "kt" to "kt", "kotlin" to "kt", "kts" to "kt",
        "java" to "java",
        "py" to "py", "python" to "py",
        "sh" to "sh", "bash" to "sh", "shell" to "sh", "zsh" to "sh",
        "yaml" to "yml", "yml" to "yml",
        "sql" to "sql", "toml" to "toml", "ini" to "ini",
    )

    /** Extension implied by a Markdown fence language tag (```svg -> svg). */
    fun extForFenceLang(lang: String?): String? =
        lang?.trim()?.lowercase()?.let { EXT_BY_FENCE_LANG[it] }

    /**
     * Picks the code out of a pasted answer.
     *
     * - One or more fenced blocks: the **longest** one wins (chat answers often contain a
     *   throwaway snippet before the real payload).
     * - Odd fence count (the answer was cut off mid-code): the unterminated tail is treated
     *   as a block, because that is exactly what a truncated copy looks like.
     * - No fences: the text is returned as is, only trimmed.
     */
    fun extract(raw: String): Extracted {
        val text = raw.removePrefix("\uFEFF").replace("\r\n", "\n")
        val blocks = ArrayList<Pair<String?, String>>()
        var lang: String? = null
        var open = false
        val body = StringBuilder()

        for (line in text.split("\n")) {
            val m = FENCE_LINE.matchEntire(line)
            if (m != null) {
                if (!open) {
                    open = true
                    lang = m.groupValues[1].ifBlank { null }
                    body.setLength(0)
                } else {
                    blocks.add(lang to body.toString())
                    open = false
                    lang = null
                }
                continue
            }
            if (open) {
                if (body.isNotEmpty()) body.append('\n')
                body.append(line)
            }
        }
        if (open) blocks.add(lang to body.toString())

        if (blocks.isEmpty()) return Extracted(text.trim(), null, false)
        val best = blocks.maxByOrNull { it.second.length } ?: return Extracted(text.trim(), null, false)
        return Extracted(best.second.trim(), best.first, true)
    }

    /** True when the text has a fence marker at all — used to decide whether to offer extraction. */
    fun hasFence(text: String): Boolean = FENCE_ANY.containsMatchIn(text)

    /**
     * What does this text actually look like? Used when the file extension lies
     * (a `.txt` holding an SVG pasted from a chat, which is the common case).
     */
    fun sniffExt(text: String): String? {
        val t = text.trimStart().removePrefix("\uFEFF")
        if (t.isEmpty()) return null
        val head = t.take(600)
        val lower = head.lowercase()
        return when {
            lower.startsWith("<!doctype html") || lower.startsWith("<html") -> "html"
            lower.startsWith("<?xml") && t.contains("<svg", true) -> "svg"
            lower.startsWith("<?xml") -> "xml"
            lower.startsWith("<svg") -> "svg"
            lower.startsWith("<!--") && t.contains("<svg", true) -> "svg"
            lower.startsWith("<!doctype svg") || lower.startsWith("<svg:") -> "svg"
            lower.contains("<svg") && t.contains("</svg>", true) -> "svg"
            lower.startsWith("<") && t.contains("</") -> "xml"
            lower.startsWith("{") || lower.startsWith("[") -> "json"
            lower.startsWith("#!") -> "sh"
            else -> null
        }
    }

    /** Extension to file a pasted answer under: fence tag first, then content sniffing. */
    fun guessExt(fenceLang: String?, code: String): String =
        extForFenceLang(fenceLang) ?: sniffExt(code) ?: "txt"
}
