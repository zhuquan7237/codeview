package com.zhuquan.codeview

import com.zhuquan.codeview.data.FileRepo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileRepoTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun creates_reads_and_lists() {
        val repo = FileRepo(tmp.root)
        assertTrue(repo.create("a.svg", "<svg/>"))
        assertEquals("<svg/>", repo.read("a.svg"))
        assertEquals(1, repo.list().size)
        assertFalse(repo.create("a.svg", "overwrite?"))
        assertEquals("<svg/>", repo.read("a.svg"))
    }

    @Test
    fun unique_name_never_collides() {
        val repo = FileRepo(tmp.root)
        repo.create("a.svg", "1")
        assertEquals("a-2.svg", repo.uniqueName("a.svg"))
        repo.create("a-2.svg", "2")
        assertEquals("a-3.svg", repo.uniqueName("a.svg"))
    }

    @Test
    fun duplicate_copies_content() {
        val repo = FileRepo(tmp.root)
        repo.create("a.txt", "hello")
        val copy = repo.duplicate("a.txt")
        assertNotNull(copy)
        assertEquals("hello", repo.read(copy!!))
        assertEquals(2, repo.list().size)
    }

    @Test
    fun rename_and_delete() {
        val repo = FileRepo(tmp.root)
        repo.create("a.txt", "x")
        assertTrue(repo.rename("a.txt", "b.md"))
        assertFalse(repo.exists("a.txt"))
        assertTrue(repo.exists("b.md"))
        assertTrue(repo.delete("b.md"))
        assertFalse(repo.exists("b.md"))
        assertFalse(repo.delete("b.md"))
    }

    @Test
    fun rejects_path_traversal_and_directories() {
        val repo = FileRepo(tmp.root)
        assertFalse(repo.write("../escape.txt", "nope"))
        assertFalse(repo.write("sub/dir.txt", "nope"))
        assertFalse(repo.create("..", "nope"))
        assertNull(repo.duplicate("../nope.txt"))
        assertFalse(repo.exists("../whatever"))
    }

    @Test
    fun missing_file_reads_empty_and_samples_populate_once() {
        val repo = FileRepo(tmp.root)
        assertEquals("", repo.read("nope.txt"))
        repo.ensureSamples()
        val first = repo.list().size
        assertTrue(first >= 2)
        repo.ensureSamples()
        assertEquals(first, repo.list().size)
    }
}
