package com.zhuquan.codeview.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.zhuquan.codeview.core.CodeExtract
import com.zhuquan.codeview.data.DocumentImport
import com.zhuquan.codeview.data.FileRepo
import com.zhuquan.codeview.data.ImportResult
import com.zhuquan.codeview.data.ShareInbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val HOME = "home"
private const val EDIT = "edit:"

fun shareCode(context: Context, name: String, content: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, name)
        putExtra(Intent.EXTRA_TEXT, content)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "分享代码")) }
}

fun toast(context: Context, message: String) {
    runCatching { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
}

fun importMessage(result: ImportResult): String = when (result) {
    is ImportResult.Ok -> "已导入 ${result.name}"
    ImportResult.TooLarge -> "文件太大（上限 4 MB）"
    ImportResult.Empty -> "没有可用的内容"
    ImportResult.Binary -> "不支持二进制文件"
    ImportResult.Failed -> "导入失败"
}

/**
 * Pulls code out of the clipboard and files it. Chat answers arrive fenced, so the
 * Markdown wrapper is stripped and the extension comes from the fence tag or the content.
 */
fun createFromClipboard(context: Context, repo: FileRepo): ImportResult {
    val raw = readClipboard(context)
    if (raw.isBlank()) return ImportResult.Empty
    val extracted = CodeExtract.extract(raw)
    val ext = CodeExtract.guessExt(extracted.fenceLang, extracted.code)
    return repo.importText(extracted.code, "粘贴代码", ext)
}

@Composable
fun CodeViewApp(repo: FileRepo) {
    val context = LocalContext.current
    var route by rememberSaveable { mutableStateOf(HOME) }
    var version by remember { mutableStateOf(0) }
    var showNew by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun open(newName: String) {
        version++
        route = EDIT + newName
    }

    // --- import from the system picker ---------------------------------------
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val results = withContext(Dispatchers.IO) { uris.map { DocumentImport.import(context, repo, it) } }
            val imported = results.filterIsInstance<ImportResult.Ok>()
            version++
            when {
                imported.isEmpty() -> toast(context, importMessage(results.first()))
                imported.size == 1 -> {
                    toast(context, "已导入 ${imported.first().name}")
                    open(imported.first().name)
                }
                else -> {
                    toast(context, "已导入 ${imported.size} 个文件")
                    open(imported.last().name)
                }
            }
        }
    }

    // --- "share to CodeView" from another app --------------------------------
    val shared by ShareInbox.flow.collectAsState()
    LaunchedEffect(shared) {
        val payload = shared ?: return@LaunchedEffect
        // NB: clear the inbox *after* the work. Clearing first re-keys this effect,
        // which cancels the coroutine at the resumption point and swallows the import.
        val target = withContext(Dispatchers.IO) {
            when {
                payload.uri != null -> DocumentImport.import(context, repo, payload.uri)
                !payload.text.isNullOrBlank() -> {
                    val extracted = CodeExtract.extract(payload.text)
                    val ext = CodeExtract.guessExt(extracted.fenceLang, extracted.code)
                    repo.importText(extracted.code, "分享代码", ext)
                }
                else -> ImportResult.Empty
            }
        }
        ShareInbox.clear()
        if (target is ImportResult.Ok) open(target.name) else toast(context, importMessage(target))
    }

    AnimatedContent(
        targetState = route,
        transitionSpec = {
            if (targetState.startsWith(EDIT)) {
                // into a file: the list slips back, the editor arrives
                (slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(tween(280)) { -it / 10 } + fadeOut(tween(180)))
            } else {
                (slideInHorizontally(tween(280)) { -it / 10 } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(tween(280)) { it / 4 } + fadeOut(tween(180)))
            }
        },
        label = "route",
        modifier = Modifier.fillMaxSize(),
    ) { current ->
        if (current == HOME) {
            HomeScreen(
                repo = repo,
                version = version,
                onOpen = { route = EDIT + it },
                onNew = { showNew = true },
                onImport = { runCatching { picker.launch(arrayOf("*/*")) } },
                onPasteCreate = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { createFromClipboard(context, repo) }
                        if (result is ImportResult.Ok) {
                            toast(context, "已从剪贴板新建 ${result.name}")
                            open(result.name)
                        } else {
                            toast(context, importMessage(result))
                        }
                    }
                },
                onChanged = { version++ },
                onShare = { shareCode(context, it, repo.read(it)) },
            )
        } else {
            EditorScreen(
                repo = repo,
                initialName = current.removePrefix(EDIT),
                onBack = { route = HOME; version++ },
                onShare = { shareCode(context, it, repo.read(it)) },
                onChanged = { version++ },
            )
        }
    }

    if (showNew) {
        NewFileSheet(
            onCreate = { name, content ->
                val target = repo.uniqueName(name)
                repo.create(target, content)
                showNew = false
                version++
                route = EDIT + target
            },
            onDismiss = { showNew = false },
        )
    }
}
