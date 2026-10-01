package com.zhuquan.codeview.core

import kotlin.math.max

/** Token buckets the UI paints with the theme's code palette. */
enum class Tok { PLAIN, KEYWORD, STRING, COMMENT, NUMBER, TAG, ATTR, PUNCT, FUNCTION }

data class Token(val start: Int, val end: Int, val tok: Tok)

enum class Lang { MARKUP, XML, CSS, JS, JSON, KOTLIN, JAVA, PYTHON, SHELL, MARKDOWN, PLAIN }

fun langOf(kind: FileKind): Lang = when (kind) {
    FileKind.HTML -> Lang.MARKUP
    FileKind.SVG, FileKind.XML -> Lang.XML
    FileKind.CSS -> Lang.CSS
    FileKind.JS -> Lang.JS
    FileKind.JSON -> Lang.JSON
    FileKind.KOTLIN -> Lang.KOTLIN
    FileKind.JAVA -> Lang.JAVA
    FileKind.PYTHON -> Lang.PYTHON
    FileKind.SHELL -> Lang.SHELL
    FileKind.MARKDOWN -> Lang.MARKDOWN
    else -> Lang.PLAIN
}

/**
 * Dependency-free syntax scanner. It is deliberately line/token based rather than a
 * real parser: it has to stay tiny (APK size) and fast enough to run on every keystroke.
 */
object Highlighter {

    private val KEYWORDS: Map<Lang, Set<String>> = mapOf(
        Lang.KOTLIN to setOf(
            "fun", "val", "var", "class", "object", "interface", "if", "else", "when", "for", "while",
            "return", "import", "package", "private", "public", "internal", "protected", "override",
            "suspend", "data", "sealed", "enum", "is", "as", "in", "null", "true", "false", "this",
            "super", "try", "catch", "finally", "throw", "typealias", "companion", "init", "by", "lazy",
        ),
        Lang.JAVA to setOf(
            "class", "interface", "enum", "extends", "implements", "public", "private", "protected",
            "static", "final", "void", "int", "long", "double", "float", "boolean", "char", "new",
            "return", "if", "else", "for", "while", "switch", "case", "break", "continue", "try",
            "catch", "finally", "throw", "throws", "import", "package", "null", "true", "false",
            "this", "super", "instanceof", "abstract", "synchronized",
        ),
        Lang.JS to setOf(
            "const", "let", "var", "function", "return", "if", "else", "for", "while", "do", "switch",
            "case", "break", "continue", "new", "class", "extends", "import", "export", "from",
            "default", "try", "catch", "finally", "throw", "typeof", "instanceof", "this", "null",
            "undefined", "true", "false", "async", "await", "yield", "of", "in", "delete", "void",
        ),
        Lang.PYTHON to setOf(
            "def", "class", "return", "if", "elif", "else", "for", "while", "import", "from", "as",
            "with", "try", "except", "finally", "raise", "lambda", "None", "True", "False", "and",
            "or", "not", "in", "is", "pass", "break", "continue", "global", "yield", "async", "await",
        ),
        Lang.SHELL to setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
            "function", "return", "export", "local", "echo", "cd", "sudo", "exit", "source",
        ),
        Lang.CSS to setOf(
            "important", "media", "import", "keyframes", "supports", "root", "from", "to",
        ),
        Lang.JSON to setOf("true", "false", "null"),
    )

    private fun spec(lang: Lang): Spec = when (lang) {
        Lang.MARKUP -> Spec(markup = true, lineComments = emptyList(), blockComments = listOf("<!--" to "-->")).withHtml()
        Lang.XML -> Spec(markup = true, lineComments = emptyList(), blockComments = listOf("<!--" to "-->"))
        Lang.CSS -> Spec(blockComments = listOf("/*" to "*/"), lineComments = emptyList(), strings = listOf('\'', '"'), keywords = KEYWORDS.getValue(Lang.CSS))
        Lang.JS -> Spec(blockComments = listOf("/*" to "*/"), lineComments = listOf("//"), strings = listOf('\'', '"', '`'), keywords = KEYWORDS.getValue(Lang.JS))
        Lang.JSON -> Spec(blockComments = emptyList(), lineComments = emptyList(), strings = listOf('"'), keywords = KEYWORDS.getValue(Lang.JSON), jsonLike = true)
        Lang.KOTLIN -> Spec(blockComments = listOf("/*" to "*/"), lineComments = listOf("//"), strings = listOf('"', '\''), keywords = KEYWORDS.getValue(Lang.KOTLIN))
        Lang.JAVA -> Spec(blockComments = listOf("/*" to "*/"), lineComments = listOf("//"), strings = listOf('"', '\''), keywords = KEYWORDS.getValue(Lang.JAVA))
        Lang.PYTHON -> Spec(lineComments = listOf("#"), strings = listOf('"', '\''), keywords = KEYWORDS.getValue(Lang.PYTHON))
        Lang.SHELL -> Spec(lineComments = listOf("#"), strings = listOf('"', '\''), keywords = KEYWORDS.getValue(Lang.SHELL))
        Lang.MARKDOWN -> Spec(lineComments = emptyList(), strings = emptyList(), markdown = true)
        Lang.PLAIN -> Spec()
    }

    private data class Spec(
        val lineComments: List<String> = emptyList(),
        val blockComments: List<Pair<String, String>> = emptyList(),
        val strings: List<Char> = emptyList(),
        val keywords: Set<String> = emptySet(),
        val markup: Boolean = false,
        val jsonLike: Boolean = false,
        val markdown: Boolean = false,
    ) {
        fun withHtml() = copy(strings = listOf('"', '\''))
    }

    /** Scans [code] and returns non-overlapping tokens in ascending order. */
    fun scan(code: String, lang: Lang): List<Token> {
        if (code.isEmpty() || lang == Lang.PLAIN) return emptyList()
        val s = spec(lang)
        val out = ArrayList<Token>(code.length / 6 + 8)
        val n = code.length
        var i = 0

        fun push(start: Int, end: Int, tok: Tok) {
            if (end > start) out.add(Token(start, end, tok))
        }

        if (s.markup) return scanMarkup(code, s, out)

        while (i < n) {
            val c = code[i]

            // comments
            var handled = false
            for (lc in s.lineComments) {
                if (code.startsWith(lc, i)) {
                    val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                    push(i, end, Tok.COMMENT); i = end; handled = true; break
                }
            }
            if (handled) continue
            for ((open, close) in s.blockComments) {
                if (code.startsWith(open, i)) {
                    val end = code.indexOf(close, i + open.length).let { if (it < 0) n else it + close.length }
                    push(i, end, Tok.COMMENT); i = end; handled = true; break
                }
            }
            if (handled) continue

            // strings
            if (c in s.strings) {
                var j = i + 1
                while (j < n) {
                    if (code[j] == '\\') { j += 2; continue }
                    if (code[j] == c) { j++; break }
                    if (code[j] == '\n' && c != '`') { break }
                    j++
                }
                val end = minOf(j, n)
                var kind = Tok.STRING
                // In JSON the quoted thing before a ':' is a key: colour it as one.
                if (s.jsonLike) {
                    var k = end
                    while (k < n && (code[k] == ' ' || code[k] == '\t')) k++
                    if (code.getOrNull(k) == ':') kind = Tok.ATTR
                }
                push(i, end, kind); i = end; continue
            }

            // numbers
            if (c.isDigit() || (c == '-' && i + 1 < n && code[i + 1].isDigit() && !isWordChar(code.getOrNull(i - 1)))) {
                var j = i
                if (code[j] == '-' || code[j] == '+') j++
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                push(i, j, Tok.NUMBER); i = j; continue
            }

            // words
            if (c.isLetter() || c == '_' || c == '$' || c == '@') {
                var j = i + 1
                while (j < n && isWordChar(code[j])) j++
                val word = code.substring(i, j)
                val nextNonSpace = run {
                    var k = j
                    while (k < n && (code[k] == ' ' || code[k] == '\t')) k++
                    code.getOrNull(k)
                }
                val tok = when {
                    s.keywords.contains(word) -> Tok.KEYWORD
                    s.jsonLike && nextNonSpace == ':' -> Tok.ATTR
                    nextNonSpace == '(' -> Tok.FUNCTION
                    word.firstOrNull()?.isUpperCase() == true -> Tok.TAG
                    else -> Tok.PLAIN
                }
                if (tok == Tok.KEYWORD || tok == Tok.FUNCTION || tok == Tok.TAG ||
                    (s.jsonLike && tok == Tok.ATTR)
                ) {
                    push(i, j, tok)
                }
                i = j; continue
            }

            // markdown headings / quotes
            if (s.markdown && (c == '#' || c == '>' || c == '-' || c == '*' || c == '+' || c == '`')) {
                val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                if (c == '#' || c == '>' || c == '`') push(i, end, if (c == '`') Tok.STRING else Tok.KEYWORD)
                i = max(i + 1, end); continue
            }

            if (c in "{}()[];,.") push(i, i + 1, Tok.PUNCT)
            i++
        }
        return out
    }

    private fun isWordChar(c: Char?): Boolean = c != null && (c.isLetterOrDigit() || c == '_' || c == '$')

    private fun scanMarkup(code: String, s: Spec, out: MutableList<Token>): List<Token> {
        val n = code.length
        var i = 0
        fun push(start: Int, end: Int, tok: Tok) { if (end > start) out.add(Token(start, end, tok)) }

        while (i < n) {
            val lt = code.indexOf('<', i)
            if (lt < 0) break
            if (code.startsWith("<!--", lt)) {
                val end = code.indexOf("-->", lt + 4).let { if (it < 0) n else it + 3 }
                push(lt, end, Tok.COMMENT); i = end; continue
            }
            if (code.startsWith("<![CDATA[", lt)) {
                val end = code.indexOf("]]>", lt + 9).let { if (it < 0) n else it + 3 }
                push(lt, end, Tok.STRING); i = end; continue
            }
            val closing = code.getOrNull(lt + 1) == '/'
            val nameStart = lt + if (closing) 2 else 1
            if (nameStart >= n || !(code[nameStart].isLetter() || code[nameStart] == '_' || code[nameStart] == '!' || code[nameStart] == '?')) {
                i = lt + 1; continue
            }
            var j = nameStart
            while (j < n && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '-' || code[j] == ':' || code[j] == '.' || code[j] == '!' || code[j] == '?')) j++
            push(lt, j, Tok.TAG)
            var k = j
            var guard = 0
            while (k < n && code[k] != '>' && guard++ < 20000) {
                val c = code[k]
                when {
                    c == '"' || c == '\'' -> {
                        var e = k + 1
                        while (e < n && code[e] != c) e++
                        e = minOf(e + 1, n)
                        push(k, e, Tok.STRING); k = e
                    }
                    c.isLetter() || c == '_' || c == ':' || c == '-' -> {
                        var e = k
                        while (e < n && (code[e].isLetterOrDigit() || code[e] == '_' || code[e] == ':' || code[e] == '-' || code[e] == '.')) e++
                        push(k, e, Tok.ATTR); k = e
                    }
                    else -> k++
                }
            }
            i = minOf(k + 1, n)
        }
        return out
    }

    /** Line indexes start offsets, used for gutters. */
    fun lineStarts(code: String): IntArray {
        val list = ArrayList<Int>(code.length / 24 + 4)
        list.add(0)
        for (idx in code.indices) if (code[idx] == '\n') list.add(idx + 1)
        return list.toIntArray()
    }

    fun lineCount(code: String): Int {
        if (code.isEmpty()) return 1
        var count = 1
        for (c in code) if (c == '\n') count++
        return count
    }
}
