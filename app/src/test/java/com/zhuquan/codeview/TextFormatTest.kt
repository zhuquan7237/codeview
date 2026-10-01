package com.zhuquan.codeview

import com.zhuquan.codeview.core.TextFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFormatTest {

    @Test
    fun prettyJson_indents_and_collapses_repeats() {
        val out = TextFormat.prettyJson("""{"a":1,"b":[1,2],"c":{"d":true}}""")
        assertEquals(
            """
            {
              "a": 1,
              "b": [
                1,
                2
              ],
              "c": {
                "d": true
              }
            }
            """.trimIndent(),
            out,
        )
    }

    @Test
    fun prettyJson_keeps_braces_inside_strings() {
        val out = TextFormat.prettyJson("""{"k":"}{[]","n":"a\"b"}""")
        assertTrue(out!!.contains("\"}{[]\""))
        assertTrue(out.contains("""a\"b"""))
        assertTrue(out.contains("\"n\": \"a\\\"b\""))
    }

    @Test
    fun prettyJson_handles_empty_containers() {
        assertEquals("{}", TextFormat.prettyJson("{}"))
        assertEquals("[]", TextFormat.prettyJson("[]"))
        assertEquals("{\n  \"a\": {}\n}", TextFormat.prettyJson("""{"a":{}}"""))
    }

    @Test
    fun prettyJson_rejects_broken_input() {
        assertNull(TextFormat.prettyJson("""{"a":1"""))
        assertNull(TextFormat.prettyJson("""{"a":1}}"""))
        assertNull(TextFormat.prettyJson("""{"a":"unterminated}"""))
        assertNull(TextFormat.prettyJson("not json at all"))
        assertNull(TextFormat.prettyJson(""))
    }

    @Test
    fun prettyXml_reindents_a_minified_document() {
        val out = TextFormat.prettyXml("<note><to>A</to><from>B</from></note>")
        val lines = out.split("\n")
        assertEquals("<note>", lines[0])
        assertEquals("  <to>A</to>", lines[1])
        assertEquals("  <from>B</from>", lines[2])
        assertEquals("</note>", lines[3])
    }

    @Test
    fun prettyXml_keeps_comments_and_declarations_intact() {
        val src = "<?xml version=\"1.0\"?><!-- hi > there --><a><b/></a>"
        val out = TextFormat.prettyXml(src)
        assertTrue(out.startsWith("<?xml version=\"1.0\"?>"))
        assertTrue(out.contains("<!-- hi > there -->"))
        assertTrue(out.contains("<b/>"))
        assertEquals(0, out.lines().last { it.isNotEmpty() }.count { it == '<' } - 1)
    }

    @Test
    fun minified_detectors() {
        assertTrue(TextFormat.looksMinifiedXml("<a><b><c><d><e><f><g></g></f></e></d></c></b></a>"))
        assertTrue(TextFormat.looksMinifiedJson("""{"a":1,"b":2,"c":3,"d":4}"""))
        assertTrue(!TextFormat.looksMinifiedXml("<a>\n  <b/>\n  <c/>\n</a>"))
    }
}
