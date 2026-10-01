package com.zhuquan.codeview

import com.zhuquan.codeview.data.FileRepo
import com.zhuquan.codeview.data.ImportResult
import com.zhuquan.codeview.data.MAX_IMPORT_BYTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Importing files that came from outside the app (picker, share sheet, clipboard). */
class FileRepoImportTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun repo() = FileRepo(File(tmp.root, "files"))

    @Test
    fun imported_file_keeps_its_name_and_content() {
        val repo = repo()
        val result = repo.importBytes("diagram (1).svg", "<svg></svg>".toByteArray())
        assertEquals(ImportResult.Ok("diagram (1).svg"), result)
        assertEquals("<svg></svg>", repo.read("diagram (1).svg"))
    }

    @Test
    fun a_second_import_of_the_same_name_gets_a_suffix() {
        val repo = repo()
        repo.importBytes("a.svg", "<svg/>".toByteArray())
        val second = repo.importBytes("a.svg", "<svg/>".toByteArray())
        assertTrue(second is ImportResult.Ok)
        assertEquals("a-2.svg", (second as ImportResult.Ok).name)
    }

    @Test
    fun illegal_characters_are_replaced_and_a_missing_extension_is_added() {
        val repo = repo()
        val slashed = repo.importBytes("has/slash.svg", "<svg/>".toByteArray())
        assertEquals("has-slash.svg", (slashed as ImportResult.Ok).name)
        val noExt = repo.importBytes("README", "hello".toByteArray())
        assertEquals("README.txt", (noExt as ImportResult.Ok).name)
    }

    @Test
    fun empty_too_large_and_binary_payloads_are_refused() {
        val repo = repo()
        assertEquals(ImportResult.Empty, repo.importBytes("a.svg", ByteArray(0)))
        assertEquals(ImportResult.TooLarge, repo.importBytes("big.txt", ByteArray(MAX_IMPORT_BYTES + 1)))
        assertEquals(ImportResult.Binary, repo.importBytes("pic.png", byteArrayOf(0x89.toByte(), 0x50, 0x00, 0x4E, 0x47)))
        assertEquals(0, repo.list().size)
    }

    @Test
    fun text_imports_are_named_from_their_guessed_extension() {
        val repo = repo()
        val result = repo.importText("<svg></svg>", "粘贴代码", "svg")
        assertEquals("粘贴代码.svg", (result as ImportResult.Ok).name)
        val again = repo.importText("<svg></svg>", "粘贴代码", "svg")
        assertEquals("粘贴代码-2.svg", (again as ImportResult.Ok).name)
    }

    @Test
    fun nothing_can_escape_the_store_directory() {
        val repo = repo()
        val result = repo.importBytes("../../evil.sh", "rm -rf /".toByteArray())
        assertTrue(result is ImportResult.Ok)
        assertEquals(1, repo.list().size)
        assertTrue(File(tmp.root, "files").listFiles()!!.all { it.parentFile == File(tmp.root, "files") })
    }
}
