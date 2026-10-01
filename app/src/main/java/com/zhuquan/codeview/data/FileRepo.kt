package com.zhuquan.codeview.data

import com.zhuquan.codeview.core.FileKind
import com.zhuquan.codeview.core.FileTypes
import java.io.File
import java.io.IOException

data class CodeItem(val name: String, val size: Long, val modified: Long) {
    val ext: String get() = FileTypes.extensionOf(name)
    val kind: FileKind get() = FileTypes.kindOf(ext)
}

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

    fun totalSize(): Long = root.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L

    /** First launch: give the user something that renders. */
    fun ensureSamples() {
        if ((root.listFiles()?.size ?: 0) > 0) return
        create("示例.svg", FileTypes.templateFor("svg"))
        create("示例.html", FileTypes.templateFor("html"))
        create("示例.xml", FileTypes.templateFor("xml"))
    }
}
