package com.zhuquan.codeview

import com.zhuquan.codeview.core.FileKind
import com.zhuquan.codeview.core.FileTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTypesTest {

    @Test
    fun extensionOf_handles_dots_and_missing_parts() {
        assertEquals("svg", FileTypes.extensionOf("logo.svg"))
        assertEquals("tar", FileTypes.extensionOf("a.b.tar"))
        assertEquals("", FileTypes.extensionOf("README"))
        assertEquals("", FileTypes.extensionOf(".gitignore"))
        assertEquals("", FileTypes.extensionOf("trailing."))
        assertEquals("html", FileTypes.extensionOf("INDEX.HTML"))
    }

    @Test
    fun kindOf_maps_known_and_unknown() {
        assertEquals(FileKind.SVG, FileTypes.kindOf("svg"))
        assertEquals(FileKind.HTML, FileTypes.kindOf("HTML"))
        assertEquals(FileKind.OTHER, FileTypes.kindOf("zzz"))
        assertEquals(FileKind.JSON, FileTypes.kindOfFile("data.json"))
    }

    @Test
    fun composeName_does_not_double_the_extension() {
        assertEquals("index.html", FileTypes.composeName("index", "html"))
        assertEquals("index.html", FileTypes.composeName("index.html", "html"))
        assertEquals("index.html", FileTypes.composeName("index.HTML", "html"))
        assertEquals("logo.svg", FileTypes.composeName("logo", ".svg"))
    }

    @Test
    fun composeName_sanitizes_and_falls_back() {
        assertEquals("untitled.txt", FileTypes.composeName("", "txt"))
        assertEquals("untitled.txt", FileTypes.composeName("   ", "txt"))
        assertEquals("my-a-b.svg", FileTypes.composeName("my/a:b", "svg"))
        assertEquals("noext", FileTypes.composeName("noext", ""))
    }

    @Test
    fun sanitizeExt_keeps_only_safe_chars() {
        assertEquals("vue", FileTypes.sanitizeExt(".Vue"))
        assertEquals("tsx", FileTypes.sanitizeExt(" tsx "))
        // separators are stripped, so an extension can never escape the store directory
        assertEquals("etc", FileTypes.sanitizeExt("../etc"))
        assertFalse(FileTypes.composeName("a", "../b").contains('/'))
        assertFalse(FileTypes.composeName("a", "../b").contains(".."))
    }

    @Test
    fun formatSize_switches_units() {
        assertEquals("512 B", FileTypes.formatSize(512))
        assertEquals("1.0 KB", FileTypes.formatSize(1024))
        assertEquals("1.0 MB", FileTypes.formatSize(1024L * 1024L))
    }

    @Test
    fun templates_exist_for_the_important_kinds() {
        assertTrue(FileTypes.templateFor("svg").contains("<svg"))
        assertTrue(FileTypes.templateFor("html").contains("<html"))
        assertTrue(FileTypes.templateFor("xml").contains("<?xml"))
        assertEquals("", FileTypes.templateFor("weird"))
    }
}
