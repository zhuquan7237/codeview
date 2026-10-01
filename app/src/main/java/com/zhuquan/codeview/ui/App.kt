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
import com.zhuquan.codeview.core.ReleaseInfo
import com.zhuquan.codeview.BuildConfig
import com.zhuquan.codeview.data.DocumentImport
import com.zhuquan.codeview.data.FileRepo
import com.zhuquan.codeview.data.ImportResult
import com.zhuquan.codeview.data.ShareInbox
import com.zhuquan.codeview.data.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    // --- updates -------------------------------------------------------------
    // Silent on launch (throttled), and on demand from the footer line. The only
    // thing the app ever volunteers is a single card, and only when a newer build
    // really exists.
    val currentVersion = BuildConfig.VERSION_NAME
    var update by remember { mutableStateOf<ReleaseInfo?>(null) }
    var checking by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf(UpdateUiState.Stage.Idle) }
    var needsPermission by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var showUpdateSheet by remember { mutableStateOf(false) }
    val progressFlow = remember { MutableStateFlow(0) }
    val progress by progressFlow.collectAsState()
    var downloaded by remember { mutableStateOf<File?>(null) }

    fun checkForUpdate(manual: Boolean) {
        if (checking) return
        checking = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { Updater.check(currentVersion) }
            }.getOrNull()
            checking = false
            when {
                result == null || !result.reachable ->
                    if (manual) toast(context, "检查失败：网络不可达")
                result.info == null -> {
                    update = null
                    if (manual) toast(context, "已是最新版本（$currentVersion）")
                }
                else -> {
                    val found = result.info
                    val skipped = Updater.dismissed(context) == found.version
                    // A version the user already waved off stays quiet unless they ask again.
                    if (!skipped || manual) update = found
                    if (manual) {
                        toast(
                            context,
                            if (skipped) "发现新版本 ${found.version}（此前已忽略）" else "发现新版本 ${found.version}",
                        )
                    }
                }
            }
            Updater.markChecked(context)
        }
    }

    fun installDownloaded() {
        val file = downloaded ?: return
        if (!Updater.canInstall(context)) {
            needsPermission = true
            return
        }
        needsPermission = false
        runCatching { Updater.install(context, file) }.onFailure {
            updateError = it.message ?: "无法打开安装程序"
            stage = UpdateUiState.Stage.Failed
        }
    }

    fun startDownload() {
        val info = update ?: return
        stage = UpdateUiState.Stage.Downloading
        updateError = null
        needsPermission = false
        progressFlow.value = 0
        scope.launch {
            val file = try {
                withContext(Dispatchers.IO) {
                    Updater.download(context, info) { percent -> progressFlow.value = percent }
                }
            } catch (error: Exception) {
                stage = UpdateUiState.Stage.Failed
                updateError = "${error.message ?: "下载失败"}（已尝试 ${1 + info.mirrors.size} 个地址）"
                return@launch
            }
            downloaded = file
            stage = UpdateUiState.Stage.Ready
            if (Updater.canInstall(context)) {
                runCatching { Updater.install(context, file) }
                    .onFailure { updateError = it.message }
            } else {
                needsPermission = true
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!Updater.checkedRecently(context)) checkForUpdate(manual = false)
    }

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
                updateState = UpdateUiState(
                    currentVersion = currentVersion,
                    info = update,
                    checking = checking,
                    stage = stage,
                    progress = progress,
                    error = updateError,
                    needsPermission = needsPermission,
                ),
                onCheckUpdate = { checkForUpdate(manual = true) },
                onUpdate = { showUpdateSheet = true },
                onDismissUpdate = {
                    update?.let { Updater.dismissVersion(context, it.version) }
                    update = null
                },
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

    if (showUpdateSheet && update != null) {
        UpdateSheet(
            state = UpdateUiState(
                currentVersion = currentVersion,
                info = update,
                checking = checking,
                stage = stage,
                progress = progress,
                error = updateError,
                needsPermission = needsPermission,
            ),
            onStart = { if (stage == UpdateUiState.Stage.Ready) installDownloaded() else startDownload() },
            onInstall = { installDownloaded() },
            onSettings = { Updater.openInstallSettings(context) },
            onDismiss = { showUpdateSheet = false },
        )
    }
}
