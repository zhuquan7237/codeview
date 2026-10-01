package com.zhuquan.codeview.core

import java.util.Base64

/** What the preview tab should show for a given file. */
sealed interface PreviewContent {
    /** A self-contained HTML page to hand to a WebView. */
    data class Web(val page: String) : PreviewContent

    /** Plain (optionally formatted) source, syntax highlighted. */
    data class Source(val text: String, val lang: Lang, val formatted: Boolean) : PreviewContent
}

/**
 * Pure (Android-free) document builder so the wrapping rules can be unit tested —
 * the WebView specifics that made SVG previews go blank live here on purpose.
 */
object Preview {

    private val SVG_TAG = Regex("<svg[\\s>/]", RegexOption.IGNORE_CASE)

    fun renderModeFor(name: String, content: String): Boolean {
        val kind = FileTypes.kindOfFile(name)
        return when (kind) {
            FileKind.HTML -> true
            FileKind.SVG -> SVG_TAG.containsMatchIn(content) || content.contains("<svg", true)
            FileKind.XML -> content.contains("<svg", true)
            else -> false
        }
    }

    fun build(name: String, content: String, dark: Boolean): PreviewContent {
        val kind = FileTypes.kindOfFile(name)
        return when {
            kind == FileKind.HTML -> PreviewContent.Web(wrapHtml(content, dark))
            kind == FileKind.SVG || (kind == FileKind.XML && content.contains("<svg", true)) ->
                PreviewContent.Web(wrapMarkup(content, dark))
            kind == FileKind.JSON -> {
                val pretty = TextFormat.prettyJson(content)
                PreviewContent.Source(pretty ?: content, Lang.JSON, pretty != null)
            }
            kind == FileKind.XML -> {
                val pretty = TextFormat.prettyXml(content)
                PreviewContent.Source(pretty, Lang.XML, TextFormat.looksMinifiedXml(content))
            }
            else -> PreviewContent.Source(content, langOf(kind), false)
        }
    }

    fun background(dark: Boolean): String = if (dark) "#0B1120" else "#FFFFFF"

    fun textColor(dark: Boolean): String = if (dark) "#E2E8F0" else "#0F172A"

    /**
     * Wrapper for SVG / SVG-in-XML. Do NOT switch this to the usual
     * `height:100% + flex centering + svg{max-height:100%}` recipe: Android WebView
     * computes a zero-size SVG there and paints an empty page.
     */
    fun wrapMarkup(svg: String, dark: Boolean): String {
        val bg = background(dark)
        return buildString(svg.length + 320) {
            append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">")
            append("<style>")
            append("html,body{margin:0;padding:0;background:$bg;text-align:center;}")
            append("svg{max-width:96vw;height:auto;}")
            append("</style></head><body>")
            append(svg)
            append("</body></html>")
        }
    }

    /** The user's HTML with a mobile viewport + colour scheme bolted on when missing. */
    fun wrapHtml(html: String, dark: Boolean): String {
        val head = StringBuilder()
        if (!html.contains("name=\"viewport\"", ignoreCase = true) && !html.contains("name='viewport'", ignoreCase = true)) {
            head.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">")
        }
        if (!html.contains("name=\"color-scheme\"", ignoreCase = true)) {
            head.append("<meta name=\"color-scheme\" content=\"light dark\">")
        }
        if (head.isEmpty()) return html
        val headIdx = html.indexOf("<head", ignoreCase = true)
        if (headIdx < 0) {
            val htmlIdx = html.indexOf("<html", ignoreCase = true)
            if (htmlIdx < 0) return head.toString() + html
            val gt = html.indexOf('>', htmlIdx)
            if (gt < 0) return head.toString() + html
            return html.substring(0, gt + 1) + head + html.substring(gt + 1)
        }
        val gt = html.indexOf('>', headIdx)
        if (gt < 0) return html
        return html.substring(0, gt + 1) + head + html.substring(gt + 1)
    }

    fun dataUrl(page: String): String =
        "data:text/html;charset=utf-8;base64," + Base64.getEncoder().encodeToString(page.toByteArray(Charsets.UTF_8))

    /** Pages above this size go to a temp file instead of a data: URL. */
    const val MAX_DATA_URL_CHARS = 700_000
}
