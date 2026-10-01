package com.zhuquan.codeview

import com.zhuquan.codeview.core.Highlighter
import com.zhuquan.codeview.core.Lang
import com.zhuquan.codeview.core.Preview
import com.zhuquan.codeview.core.PreviewContent
import com.zhuquan.codeview.core.Tok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewTest {

    private val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><circle cx="5" cy="5" r="4"/></svg>"""

    @Test
    fun svg_wrapper_avoids_the_webview_zero_size_recipe() {
        val page = Preview.wrapMarkup(svg, dark = false)
        assertTrue(page.contains("text-align:center"))
        assertTrue(page.contains("max-width:96vw"))
        // the recipe that makes Android WebView paint a blank page
        assertFalse(page.contains("height:100%"))
        assertFalse(page.contains("display:flex"))
        assertFalse(page.contains("max-height:100%"))
        assertTrue(page.contains(svg))
        assertTrue(page.contains("#FFFFFF"))
    }

    @Test
    fun wrapper_follows_the_theme_background() {
        assertTrue(Preview.wrapMarkup(svg, dark = true).contains("#0B1120"))
    }

    @Test
    fun data_url_is_self_contained_and_round_trips() {
        val page = Preview.wrapMarkup(svg, false)
        val url = Preview.dataUrl(page)
        assertTrue(url.startsWith("data:text/html;charset=utf-8;base64,"))
        val decoded = String(java.util.Base64.getDecoder().decode(url.substringAfter(',')))
        assertEquals(page, decoded)
    }

    @Test
    fun html_preview_injects_a_viewport_only_once() {
        val plain = Preview.wrapHtml("<html><head><title>t</title></head><body>hi</body></html>", false)
        assertTrue(plain.contains("width=device-width"))
        assertTrue(plain.indexOf("viewport") < plain.indexOf("<title>"))

        val already = Preview.wrapHtml("<html><head><meta name=\"viewport\" content=\"width=device-width\"></head><body>x</body></html>", false)
        assertEquals(1, Regex("viewport").findAll(already).count())
    }

    @Test
    fun html_without_head_still_gets_the_viewport() {
        val out = Preview.wrapHtml("<body>hi</body>", false)
        assertTrue(out.contains("viewport"))
    }

    @Test
    fun render_mode_routing() {
        assertTrue(Preview.renderModeFor("a.html", "<h1>x</h1>"))
        assertTrue(Preview.renderModeFor("a.svg", svg))
        assertTrue(Preview.renderModeFor("a.xml", "<root>$svg</root>"))
        assertFalse(Preview.renderModeFor("a.xml", "<note><to>x</to></note>"))
        assertFalse(Preview.renderModeFor("a.json", "{}"))
        assertFalse(Preview.renderModeFor("a.kt", "fun main() {}"))
    }

    @Test
    fun build_routes_each_kind_to_the_right_pane() {
        assertTrue(Preview.build("a.svg", svg, false) is PreviewContent.Web)
        assertTrue(Preview.build("a.html", "<html></html>", false) is PreviewContent.Web)
        val json = Preview.build("a.json", """{"a":1}""", false) as PreviewContent.Source
        assertTrue(json.formatted)
        assertTrue(json.text.contains("\n"))
        val kt = Preview.build("a.kt", "fun main() {}", false) as PreviewContent.Source
        assertFalse(kt.formatted)
        assertEquals("fun main() {}", kt.text)
    }
}

class HighlighterTest {

    @Test
    fun markup_tags_and_attributes_are_tokenised() {
        val code = """<svg width="10"><!-- c --><circle r='4'/></svg>"""
        val tokens = Highlighter.scan(code, Lang.XML)
        fun slice(t: com.zhuquan.codeview.core.Token) = code.substring(t.start, t.end)
        assertTrue(tokens.any { it.tok == Tok.TAG && slice(it) == "<svg" })
        assertTrue(tokens.any { it.tok == Tok.ATTR && slice(it) == "width" })
        assertTrue(tokens.any { it.tok == Tok.STRING && slice(it) == "\"10\"" })
        assertTrue(tokens.any { it.tok == Tok.COMMENT && slice(it).startsWith("<!--") })
        assertTrue(tokens.any { it.tok == Tok.TAG && slice(it) == "</svg" })
    }

    @Test
    fun tokens_never_overlap_and_stay_in_bounds() {
        val code = "fun main() {\n  val s = \"/*not a comment*/\"; // real\n  /* block */ return 1.5\n}"
        val tokens = Highlighter.scan(code, Lang.KOTLIN)
        var last = 0
        for (t in tokens) {
            assertTrue("start ${t.start} >= last $last", t.start >= last)
            assertTrue(t.end > t.start)
            assertTrue(t.end <= code.length)
            last = t.end
        }
        assertTrue(tokens.any { it.tok == Tok.KEYWORD && code.substring(it.start, it.end) == "fun" })
        assertTrue(tokens.any { it.tok == Tok.STRING })
        assertTrue(tokens.any { it.tok == Tok.COMMENT && code.substring(it.start, it.end).contains("real") })
    }

    @Test
    fun json_keys_are_distinguishable_from_values() {
        val code = """{"key": "value", "n": 3, "b": true}"""
        val tokens = Highlighter.scan(code, Lang.JSON)
        fun slice(t: com.zhuquan.codeview.core.Token) = code.substring(t.start, t.end)
        assertTrue(tokens.any { it.tok == Tok.ATTR && slice(it) == "\"key\"" })
        assertTrue(tokens.any { it.tok == Tok.STRING && slice(it) == "\"value\"" })
        assertTrue(tokens.any { it.tok == Tok.NUMBER && slice(it) == "3" })
        assertTrue(tokens.any { it.tok == Tok.KEYWORD && slice(it) == "true" })
    }

    @Test
    fun unterminated_constructs_do_not_crash() {
        for (src in listOf("<svg", "\"unterminated", "/* open", "a <!-- b")) {
            Highlighter.scan(src, Lang.XML)
            Highlighter.scan(src, Lang.KOTLIN)
        }
    }

    @Test
    fun line_helpers() {
        assertEquals(1, Highlighter.lineCount(""))
        assertEquals(1, Highlighter.lineCount("abc"))
        assertEquals(3, Highlighter.lineCount("a\nb\n"))
        assertEquals(listOf(0, 2, 4).toIntArray().toList(), Highlighter.lineStarts("a\nb\n").toList())
    }
}
