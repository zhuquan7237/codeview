package com.zhuquan.codeview

import com.zhuquan.codeview.core.CodeExtract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "AI answer on the clipboard" path: fences, prose, truncated output, mislabelled files. */
class CodeExtractTest {

    private val answer = """
        这是你要的图，直接保存成 .svg 就行：

        ```svg
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
          <circle cx="5" cy="5" r="4" fill="#6366f1"/>
        </svg>
        ```

        需要改成别的颜色告诉我。
    """.trimIndent()

    @Test
    fun fenced_answer_keeps_only_the_code() {
        val out = CodeExtract.extract(answer)
        assertTrue(out.stripped)
        assertEquals("svg", out.fenceLang)
        assertTrue(out.code.startsWith("<svg"))
        assertTrue(out.code.endsWith("</svg>"))
        assertFalse(out.code.contains("这是你要的图"))
        assertFalse(out.code.contains("```"))
        assertEquals("svg", CodeExtract.guessExt(out.fenceLang, out.code))
    }

    @Test
    fun longest_block_wins_over_a_throwaway_snippet() {
        val text = """
            ```
            x
            ```
            真正的代码：
            ```html
            <!DOCTYPE html>
            <html><body><h1>Hi</h1></body></html>
            ```
        """.trimIndent()
        val out = CodeExtract.extract(text)
        assertTrue(out.code.contains("<h1>Hi</h1>"))
        assertEquals("html", out.fenceLang)
    }

    @Test
    fun truncated_answer_with_an_unclosed_fence_still_yields_code() {
        val out = CodeExtract.extract("说明\n```svg\n<svg xmlns=\"http://www.w3.org/2000/svg\"><circle r=")
        assertTrue(out.stripped)
        assertTrue(out.code.startsWith("<svg"))
        assertEquals("svg", CodeExtract.guessExt(out.fenceLang, out.code))
    }

    @Test
    fun plain_text_is_left_alone() {
        val out = CodeExtract.extract("  <svg xmlns=\"http://www.w3.org/2000/svg\"/>  ")
        assertFalse(out.stripped)
        assertNull(out.fenceLang)
        assertEquals("<svg xmlns=\"http://www.w3.org/2000/svg\"/>", out.code)
    }

    @Test
    fun windows_line_endings_and_bom_are_normalised() {
        val out = CodeExtract.extract("\uFEFF```json\r\n{\"a\":1}\r\n```")
        assertEquals("{\"a\":1}", out.code)
        assertEquals("json", CodeExtract.guessExt(out.fenceLang, out.code))
    }

    @Test
    fun sniffing_recognises_the_usual_ai_payloads() {
        assertEquals("svg", CodeExtract.sniffExt("<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"))
        assertEquals("svg", CodeExtract.sniffExt("<?xml version=\"1.0\"?>\n<svg width=\"1\"></svg>"))
        assertEquals("xml", CodeExtract.sniffExt("<?xml version=\"1.0\"?><note><to>x</to></note>"))
        assertEquals("html", CodeExtract.sniffExt("<!DOCTYPE html>\n<html></html>"))
        assertEquals("json", CodeExtract.sniffExt("{\"a\": 1}"))
        assertEquals("json", CodeExtract.sniffExt("[1,2,3]"))
        assertEquals("sh", CodeExtract.sniffExt("#!/bin/bash\necho hi"))
    }

    @Test
    fun sniffing_stays_quiet_when_it_cannot_tell() {
        assertNull(CodeExtract.sniffExt(""))
        assertNull(CodeExtract.sniffExt("   "))
        assertNull(CodeExtract.sniffExt("fun main() { println(1) }"))
    }

    @Test
    fun unknown_fence_language_falls_back_to_content_then_txt() {
        assertEquals("svg", CodeExtract.guessExt(null, "<svg></svg>"))
        assertEquals("txt", CodeExtract.guessExt(null, "hello world"))
        assertEquals("txt", CodeExtract.guessExt("brainfuck", "hello world"))
        assertEquals("py", CodeExtract.guessExt("python", "print(1)"))
    }

    @Test
    fun fence_detection_for_the_ui_hint() {
        assertTrue(CodeExtract.hasFence(answer))
        assertFalse(CodeExtract.hasFence("<svg></svg>"))
    }
}
