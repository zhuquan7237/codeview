package com.zhuquan.codeview.data

import com.zhuquan.codeview.core.FileKind
import com.zhuquan.codeview.core.FileTypes
import java.io.File
import java.io.IOException

data class CodeItem(val name: String, val size: Long, val modified: Long) {
    val ext: String get() = FileTypes.extensionOf(name)
    val kind: FileKind get() = FileTypes.kindOf(ext)
}

/** Outcome of pulling a file in from outside the app (picker / share sheet). */
sealed interface ImportResult {
    data class Ok(val name: String) : ImportResult
    data object TooLarge : ImportResult
    data object Empty : ImportResult
    data object Binary : ImportResult
    data object Failed : ImportResult
}

/** 4 MB: enough for any code pasted out of a chat, small enough to never hurt the phone. */
const val MAX_IMPORT_BYTES = 4 * 1024 * 1024

/**
 * Flat file store under a single directory. Everything the app creates lives here,
 * so no storage permission is ever needed. Pure java.io => unit testable.
 */
class FileRepo(private val root: File) {

    init {
        if (!root.exists()) root.mkdirs()
    }

    fun list(): List<CodeItem> {
        val files = root.listFiles() ?: return emptyList()
        return files.filter { it.isFile }
            .map { CodeItem(it.name, it.length(), it.lastModified()) }
            .sortedByDescending { it.modified }
    }

    /** Rejects anything that would escape [root]. */
    private fun fileFor(name: String): File? {
        if (name.isBlank() || name.contains('/') || name.contains('\\') || name.contains("..")) return null
        val f = File(root, name)
        return if (f.parentFile?.canonicalPath == root.canonicalPath) f else null
    }

    fun exists(name: String): Boolean = fileFor(name)?.exists() == true

    fun read(name: String): String {
        val f = fileFor(name) ?: return ""
        return try {
            if (f.exists()) f.readText(Charsets.UTF_8) else ""
        } catch (e: IOException) {
            ""
        }
    }

    fun write(name: String, text: String): Boolean {
        val f = fileFor(name) ?: return false
        return try {
            f.writeText(text, Charsets.UTF_8)
            true
        } catch (e: IOException) {
            false
        }
    }

    fun create(name: String, text: String): Boolean {
        val f = fileFor(name) ?: return false
        if (f.exists()) return false
        return write(name, text)
    }

    /** "a.svg" -> "a-2.svg" until an unused name is found. */
    fun uniqueName(name: String): String {
        if (!exists(name)) return name
        val base = FileTypes.composeName(name.substringBeforeLast('.', name), "")
        val ext = FileTypes.extensionOf(name)
        var i = 2
        while (i < 9999) {
            val candidate = if (ext.isEmpty()) "$base-$i" else "$base-$i.$ext"
            if (!exists(candidate)) return candidate
            i++
        }
        return "$base-${System.currentTimeMillis()}.$ext"
    }

    fun rename(oldName: String, newName: String): Boolean {
        if (oldName == newName) return true
        val from = fileFor(oldName) ?: return false
        val to = fileFor(newName) ?: return false
        if (!from.exists() || to.exists()) return false
        return from.renameTo(to)
    }

    fun duplicate(name: String): String? {
        val src = fileFor(name) ?: return null
        if (!src.exists()) return null
        val target = uniqueName(name)
        return if (src.copyTo(File(root, target), overwrite = false).exists()) target else null
    }

    fun delete(name: String): Boolean = fileFor(name)?.delete() == true

    /**
     * Pulls an outside file into the store.
     *
     * Imported names keep their spelling (a picker gives real names like
     * `diagram (1).svg`), only characters that are illegal on the filesystem are replaced.
     */
    fun importBytes(name: String, bytes: ByteArray): ImportResult {
        if (bytes.isEmpty()) return ImportResult.Empty
        if (bytes.size > MAX_IMPORT_BYTES) return ImportResult.TooLarge
        if (looksBinary(bytes)) return ImportResult.Binary
        val target = uniqueName(importName(name))
        return if (write(target, String(bytes, Charsets.UTF_8))) ImportResult.Ok(target) else ImportResult.Failed
    }

    /** Same, but for text that arrived through a share intent or the clipboard. */
    fun importText(text: String, baseName: String, ext: String): ImportResult {
        val clean = FileTypes.composeName(baseName, ext)
        val target = uniqueName(clean)
        return if (write(target, text)) ImportResult.Ok(target) else ImportResult.Failed
    }

    private fun importName(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]+"), "-")
            .trim(' ', '.', '-')
        val fallback = if (cleaned.isEmpty()) "导入文件" else cleaned
        return if (FileTypes.extensionOf(fallback).isEmpty()) "$fallback.txt" else fallback
    }

    /** A NUL byte in the head of a text file means it is not text. */
    private fun looksBinary(bytes: ByteArray): Boolean {
        val head = minOf(bytes.size, 4096)
        for (i in 0 until head) if (bytes[i] == 0.toByte()) return true
        return false
    }

    fun totalSize(): Long = root.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L

    /** First launch: give the user something that renders. */
    fun ensureSamples() {
        if ((root.listFiles()?.size ?: 0) > 0) return
        create("示例.svg", FileTypes.templateFor("svg"))
        create("示例.html", FileTypes.templateFor("html"))
        create("示例.xml", FileTypes.templateFor("xml"))
    }
}
