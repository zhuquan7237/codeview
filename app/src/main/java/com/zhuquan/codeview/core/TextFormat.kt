package com.zhuquan.codeview.core

/** Pretty printers used by the "preview" tab for non-rendering formats. */
object TextFormat {

    /** Re-indents JSON with 2 spaces; returns null when the input is not a JSON object/array. */
    fun prettyJson(src: String, indent: String = "  "): String? {
        val firstMeaningful = src.firstOrNull { !it.isWhitespace() }
        if (firstMeaningful != '{' && firstMeaningful != '[') return null
        val sb = StringBuilder(src.length + src.length / 4)
        var depth = 0
        var inString = false
        var escaped = false
        var lastMeaningful = ' '

        for (ch in src) {
            if (inString) {
                sb.append(ch)
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> { inString = true; sb.append(ch); lastMeaningful = ch }
                '{', '[' -> {
                    depth++
                    sb.append(ch).append('\n').append(indent.repeat(depth))
                    lastMeaningful = ch
                }
                '}', ']' -> {
                    depth--
                    if (depth < 0) return null
                    if (lastMeaningful == '{' || lastMeaningful == '[') trimTrailingSpace(sb)
                    else sb.append('\n').append(indent.repeat(depth))
                    sb.append(ch)
                    lastMeaningful = ch
                }
                ',' -> { sb.append(ch).append('\n').append(indent.repeat(depth)); lastMeaningful = ch }
                ':' -> { sb.append(": "); lastMeaningful = ch }
                ' ', '\n', '\r', '\t' -> Unit
                else -> { sb.append(ch); lastMeaningful = ch }
            }
        }
        if (inString || depth != 0) return null
        return sb.toString().trimEnd()
    }

    /** Ordered-token re-indent of XML/HTML-ish markup. Text nodes stay inline. */
    fun prettyXml(src: String, indent: String = "  "): String {
        val sb = StringBuilder(src.length + src.length / 8)
        var depth = 0
        var i = 0
        var justWroteText = false
        val n = src.length

        fun newline() {
            if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
            sb.append(indent.repeat(depth.coerceAtLeast(0)))
            justWroteText = false
        }

        while (i < n) {
            val lt = src.indexOf('<', i)
            if (lt < 0) {
                val text = src.substring(i).trim()
                if (text.isNotEmpty()) { sb.append(text); justWroteText = true }
                break
            }
            val text = src.substring(i, lt).trim()
            if (text.isNotEmpty()) { sb.append(text); justWroteText = true }

            // comments / cdata / doctype / processing instruction
            val terminator = when {
                src.startsWith("<!--", lt) -> "-->"
                src.startsWith("<![CDATA[", lt) -> "]]>"
                else -> ">"
            }
            val close = src.indexOf(terminator, lt).let { if (it < 0) n else it + terminator.length }
            val raw = src.substring(lt, minOf(close, n))
            val trimmed = raw.trim()

            when {
                trimmed.startsWith("</") -> {
                    depth--
                    if (!justWroteText) newline()
                    sb.append(trimmed); justWroteText = false
                }
                trimmed.endsWith("/>") || trimmed.startsWith("<?") || trimmed.startsWith("<!") -> {
                    newline(); sb.append(trimmed); justWroteText = false
                }
                else -> {
                    newline(); sb.append(trimmed); depth++; justWroteText = false
                }
            }
            i = minOf(close, n)
        }
        return sb.toString().trimEnd()
    }

    private fun trimTrailingSpace(sb: StringBuilder) {
        while (sb.isNotEmpty() && (sb.last() == ' ' || sb.last() == '\n')) sb.deleteCharAt(sb.length - 1)
    }

    /** Cheap nearest-indent guess for text the user pasted without formatting. */
    fun looksMinifiedXml(src: String): Boolean {
        val head = src.take(4000)
        return head.count { it == '\n' } < 3 && head.count { it == '<' } > 6
    }

    fun looksMinifiedJson(src: String): Boolean {
        val head = src.take(4000)
        val structural = head.count { it == '{' || it == '[' || it == ',' }
        return head.count { it == '\n' } < 3 && structural > 3
    }
}
