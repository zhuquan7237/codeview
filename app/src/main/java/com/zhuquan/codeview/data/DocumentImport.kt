package com.zhuquan.codeview.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Pulls documents in from the system file picker / share sheet.
 *
 * The app deliberately has no storage permission: everything arrives as a `content://`
 * URI granted per file, and is copied into the app's own store on import.
 */
object DocumentImport {

    class TooLarge : Exception()

    fun displayName(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null }
    }.getOrNull()

    private fun declaredSize(ctx: Context, uri: Uri): Long? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
    }.getOrNull()

    /** Reads at most [limit] bytes; throws [TooLarge] instead of filling memory. */
    private fun readBytes(ctx: Context, uri: Uri, limit: Int): ByteArray {
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("no stream")
        return input.use { ins ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                total += n
                if (total > limit) throw TooLarge()
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    }

    fun import(ctx: Context, repo: FileRepo, uri: Uri): ImportResult {
        val name = displayName(ctx, uri)?.takeIf { it.isNotBlank() } ?: "导入文件.txt"
        declaredSize(ctx, uri)?.let { if (it > MAX_IMPORT_BYTES) return ImportResult.TooLarge }
        val bytes = try {
            readBytes(ctx, uri, MAX_IMPORT_BYTES)
        } catch (e: TooLarge) {
            return ImportResult.TooLarge
        } catch (e: Exception) {
            return ImportResult.Failed
        }
        return repo.importBytes(name, bytes)
    }
}
