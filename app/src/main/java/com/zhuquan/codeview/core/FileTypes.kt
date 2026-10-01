package com.zhuquan.codeview.core

import java.util.Locale

/** File categories we know how to colour / preview. */
enum class FileKind(val badge: String, val argb: Long) {
    HTML("HTML", 0xFFE34F26),
    SVG("SVG", 0xFF6366F1),
    XML("XML", 0xFF0EA5E9),
    JSON("JSON", 0xFFF59E0B),
    CSS("CSS", 0xFF3B82F6),
    JS("JS", 0xFFEAB308),
    MARKDOWN("MD", 0xFF8B5CF6),
    KOTLIN("KT", 0xFFA855F7),
    JAVA("JAVA", 0xFFDC2626),
    PYTHON("PY", 0xFF22C55E),
    SHELL("SH", 0xFF64748B),
    TEXT("TXT", 0xFF94A3B8),
    OTHER("FILE", 0xFF94A3B8),
}

object FileTypes {

    private val BY_EXT = mapOf(
        "html" to FileKind.HTML, "htm" to FileKind.HTML, "xhtml" to FileKind.HTML,
        "svg" to FileKind.SVG,
        "xml" to FileKind.XML, "plist" to FileKind.XML, "xsd" to FileKind.XML,
        "json" to FileKind.JSON,
        "css" to FileKind.CSS,
        "js" to FileKind.JS, "mjs" to FileKind.JS, "cjs" to FileKind.JS, "ts" to FileKind.JS,
        "md" to FileKind.MARKDOWN, "markdown" to FileKind.MARKDOWN,
        "kt" to FileKind.KOTLIN, "kts" to FileKind.KOTLIN,
        "java" to FileKind.JAVA,
        "py" to FileKind.PYTHON,
        "sh" to FileKind.SHELL, "bash" to FileKind.SHELL,
        "txt" to FileKind.TEXT, "log" to FileKind.TEXT, "csv" to FileKind.TEXT,
    )

    /** Extensions offered as one-tap chips when creating a file. */
    val PRESETS = listOf("html", "svg", "xml", "json", "css", "js", "md", "kt", "py", "txt")

    fun kindOf(ext: String): FileKind = BY_EXT[ext.lowercase(Locale.ROOT)] ?: FileKind.OTHER

    fun extensionOf(name: String): String {
        val i = name.lastIndexOf('.')
        return if (i <= 0 || i == name.length - 1) "" else name.substring(i + 1).lowercase(Locale.ROOT)
    }

    fun kindOfFile(name: String): FileKind = kindOf(extensionOf(name))

    /** Keeps only characters that are safe in a file name; never empty. */
    fun sanitizeBase(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]+"), "-")
            .replace(Regex("\\s+"), "-")
            .trim(' ', '.', '-')
        return cleaned.ifEmpty { "untitled" }
    }

    fun sanitizeExt(raw: String): String =
        raw.trim().trimStart('.').lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_+-]"), "")

    /**
     * Builds the final file name. Typing "index.html" while the extension box says
     * "html" must not produce "index.html.html".
     */
    fun composeName(rawBase: String, ext: String): String {
        val cleanExt = sanitizeExt(ext)
        var base = sanitizeBase(rawBase)
        if (cleanExt.isNotEmpty() && base.length > cleanExt.length + 1 &&
            base.lowercase(Locale.ROOT).endsWith(".$cleanExt")
        ) {
            base = base.dropLast(cleanExt.length + 1)
        }
        return if (cleanExt.isEmpty()) base else "$base.$cleanExt"
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0)
    }

    /** Starter content so a freshly created file renders something immediately. */
    fun templateFor(ext: String): String = when (sanitizeExt(ext)) {
        "html", "htm" -> """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Hello</title>
              <style>
                body { margin: 0; padding: 24px; font-family: system-ui, sans-serif; }
                h1 { color: #4f46e5; }
              </style>
            </head>
            <body>
              <h1>Hello, CodeView</h1>
              <p>试试在预览里看到这段文字。</p>
            </body>
            </html>
        """.trimIndent()

        "svg" -> """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="200" height="200">
              <defs>
                <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
                  <stop offset="0" stop-color="#6366f1" />
                  <stop offset="1" stop-color="#22d3ee" />
                </linearGradient>
              </defs>
              <circle cx="100" cy="100" r="70" fill="url(#g)" />
              <circle cx="100" cy="100" r="40" fill="none" stroke="#fff" stroke-width="3" />
            </svg>
        """.trimIndent()

        "xml" -> """
            <?xml version="1.0" encoding="UTF-8"?>
            <note>
              <to>CodeView</to>
              <from>You</from>
              <body>贴一段 XML 进来，点预览看格式化结果。</body>
            </note>
        """.trimIndent()

        "json" -> """
            {
              "name": "CodeView",
              "version": "1.0.0",
              "tags": ["svg", "html", "xml"]
            }
        """.trimIndent()

        "css" -> "/* 贴 CSS 进来，用 HTML 预览 */\nbody {\n  color: #4f46e5;\n}\n"

        "js", "mjs", "cjs", "ts" -> "// 贴 JavaScript 进来\nconsole.log('hello');\n"

        "md" -> "# CodeView\n\n贴 Markdown 进来。\n"

        else -> ""
    }
}
