package com.zhuquan.codeview.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhuquan.codeview.core.CodeExtract
import com.zhuquan.codeview.core.FileKind
import com.zhuquan.codeview.core.FileTypes
import com.zhuquan.codeview.core.Highlighter
import com.zhuquan.codeview.core.Preview
import com.zhuquan.codeview.core.PreviewContent
import com.zhuquan.codeview.core.UndoStack
import com.zhuquan.codeview.core.langOf
import com.zhuquan.codeview.data.FileRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun EditorScreen(
    repo: FileRepo,
    initialName: String,
    onBack: () -> Unit,
    onShare: (String) -> Unit,
    onChanged: () -> Unit,
) {
    val pal = AppTheme.colors
    val context = LocalContext.current

    var name by remember(initialName) { mutableStateOf(initialName) }
    val initialText = remember(name) { repo.read(name) }
    var text by remember(name) { mutableStateOf(initialText) }
    var savedText by remember(name) { mutableStateOf(initialText) }
    val undo = remember(name) { UndoStack().apply { reset(initialText) } }
    var stackVersion by remember(name) { mutableStateOf(0) }
    var mode by remember(name) { mutableStateOf(if (Preview.renderModeFor(name, initialText)) 1 else 0) }
    var reloadKey by remember(name) { mutableStateOf(0L) }
    var previewDark by remember(name) { mutableStateOf(pal.dark) }
    var fontSize by rememberSaveable { mutableStateOf(13) }
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    // The platform code views are recreated on every tab switch: remember where we were.
    var editScroll by remember(name) { mutableStateOf(0) }
    var viewScroll by remember(name) { mutableStateOf(0) }
    var renderIssue by remember(name, reloadKey) { mutableStateOf<String?>(null) }

    val kind = FileTypes.kindOfFile(name)
    val lang = langOf(kind)
    val dirty = text != savedText
    val canUndo = remember(stackVersion) { undo.canUndo }
    val canRedo = remember(stackVersion) { undo.canRedo }
    val latestText by rememberUpdatedState(text)
    val latestName by rememberUpdatedState(name)

    // Debounced auto-save: no save button to forget.
    LaunchedEffect(text) {
        if (text != savedText) {
            delay(600)
            withContext(Dispatchers.IO) { repo.write(latestName, text) }
            savedText = text
        }
    }

    // Snapshot history, one entry per typing burst.
    LaunchedEffect(text) {
        delay(420)
        undo.record(text)
        stackVersion++
    }

    DisposableEffect(Unit) {
        onDispose { repo.write(latestName, latestText) }
    }

    BackHandler { onBack() }

    val preview = remember(text, name, previewDark) { Preview.build(name, text, previewDark) }

    // Content sniffing: a pasted SVG dropped into a .txt still gets rendered, and we say so.
    val sniffedKind = remember(name, text) { Preview.effectiveKind(name, text) }
    val sniffedExt = remember(sniffedKind) {
        when (sniffedKind) {
            FileKind.SVG -> "svg"
            FileKind.HTML -> "html"
            FileKind.XML -> "xml"
            FileKind.JSON -> "json"
            else -> null
        }
    }
    val sniffed = mode == 1 && sniffedKind != kind && sniffedExt != null && preview is PreviewContent.Web

    val hint: Hint? = when {
        mode == 1 && renderIssue != null -> Hint(renderIssue!!, "回到编辑", { mode = 0 })
        sniffed -> Hint("内容像 ${sniffedKind.badge}，已按 ${sniffedKind.badge} 预览", "另存为 .$sniffedExt") {
            repo.write(name, text)
            savedText = text
            val target = FileTypes.composeName(name.substringBeforeLast('.', name), sniffedExt!!)
            if (target != name && repo.rename(name, target)) {
                name = target
                onChanged()
            }
        }

        else -> null
    }

    // Keeps the last hint painted while the strip animates away.
    val shownHint = remember { mutableStateOf<Hint?>(null) }
    SideEffect { if (hint != null) shownHint.value = hint }

    Column(
        Modifier
            .fillMaxSize()
            .background(pal.bg)
            .safeDrawingPadding(),
    ) {
        // ---- top bar -------------------------------------------------------
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 6.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TapIcon(Icons.AutoMirrored.Filled.ArrowBack, "返回") { onBack() }
            Column(Modifier.weight(1f).padding(start = 2.dp)) {
                Text(
                    name,
                    color = pal.text,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(kind.badge, color = Color(kind.argb), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Sep()
                    Text(FileTypes.formatSize(text.toByteArray().size.toLong()), color = pal.faint, fontSize = 11.sp)
                    Sep()
                    Text(
                        if (dirty) "保存中…" else "已保存",
                        color = if (dirty) pal.primary else pal.faint,
                        fontSize = 11.sp,
                    )
                }
            }
            TapIcon(Icons.Filled.Share, "分享") { onShare(name) }
            TapIcon(Icons.Filled.MoreVert, "更多") { menuOpen = true }
        }

        // ---- mode switch + contextual actions ------------------------------
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Segmented(
                options = listOf("编辑", "预览"),
                selected = mode,
                onSelect = { mode = it },
                modifier = Modifier.width(146.dp),
            )
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "chips",
                ) { m ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (m == 0) {
                            Pill("撤销", enabled = canUndo) {
                                undo.undo()?.let { text = it }
                                stackVersion++
                            }
                            Pill("重做", enabled = canRedo) {
                                undo.redo()?.let { text = it }
                                stackVersion++
                            }
                            Pill("粘贴") {
                                val clip = readClipboard(context)
                                if (clip.isNotEmpty()) {
                                    val ex = CodeExtract.extract(clip)
                                    if (ex.stripped) toast(context, "已自动提取代码块，忽略说明文字")
                                    val insert = if (text.isBlank()) ex.code else text + "\n" + ex.code
                                    text = insert
                                } else {
                                    toast(context, "剪贴板是空的")
                                }
                            }
                        } else {
                            Pill("重新渲染") { reloadKey++ }
                            if (preview is PreviewContent.Web) {
                                Pill(if (previewDark) "深色背景" else "浅色背景") { previewDark = !previewDark }
                            }
                        }
                    }
                }
            }
        }

        // ---- contextual hint strip -----------------------------------------
        AnimatedVisibility(
            visible = hint != null,
            enter = fadeIn(tween(180)) + expandVertically(tween(200)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
        ) {
            shownHint.value?.let { h ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(h.message, color = pal.dim, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
                    Pill(h.action, onClick = h.onClick)
                }
            }
        }

        // ---- content --------------------------------------------------------
        Box(Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp)) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it / 7 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally { -it / 7 } + fadeOut(tween(160)))
                    } else {
                        (slideInHorizontally { -it / 7 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally { it / 7 } + fadeOut(tween(160)))
                    }
                },
                label = "mode",
                modifier = Modifier.fillMaxSize(),
            ) { m ->
                if (m == 0) {
                    CodeField(
                        value = text,
                        onValueChange = { text = it },
                        lang = lang,
                        pal = pal,
                        fontSize = fontSize,
                        scrollY = editScroll,
                        onScroll = { editScroll = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    PreviewPane(
                        content = preview,
                        reloadKey = reloadKey,
                        background = if (previewDark) Color(0xFF0B1120) else Color.White,
                        fontSize = fontSize,
                        scrollY = viewScroll,
                        onScroll = { viewScroll = it },
                        onRenderInfo = { info ->
                            renderIssue = when {
                                info.jsError != null -> "脚本报错：${info.jsError}"
                                info.looksEmpty -> "渲染区域为空 · 内容可能不完整或被截断"
                                else -> null
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // ---- status bar -----------------------------------------------------
        Row(
            Modifier
                .fillMaxWidth()
                .background(pal.surface)
                .padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildString {
                    append("${Highlighter.lineCount(text)} 行")
                    append(" · ${text.length} 字符")
                    if (mode == 1) append(" · 预览")
                },
                color = pal.faint,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("A-", enabled = fontSize > 10) { fontSize-- }
                Text("$fontSize", color = pal.dim, fontSize = 11.sp)
                Pill("A+", enabled = fontSize < 18) { fontSize++ }
            }
        }
    }

    if (menuOpen) {
        ActionSheet(
            title = name,
            actions = listOf(
                SheetAction("重命名") { menuOpen = false; renameOpen = true },
                SheetAction("复制副本") { menuOpen = false; repo.write(name, text); repo.duplicate(name); onChanged() },
                SheetAction("分享代码") { menuOpen = false; onShare(name) },
                SheetAction("用剪贴板替换全文") {
                    menuOpen = false
                    val clip = readClipboard(context)
                    if (clip.isNotEmpty()) {
                        val ex = CodeExtract.extract(clip)
                        if (ex.stripped) toast(context, "已自动提取代码块，忽略说明文字")
                        text = ex.code
                    } else {
                        toast(context, "剪贴板是空的")
                    }
                },
                SheetAction("删除", danger = true, icon = Icons.Filled.Delete) { menuOpen = false; deleteOpen = true },
            ),
            onDismiss = { menuOpen = false },
        )
    }

    if (renameOpen) {
        TextPromptDialog(
            title = "重命名",
            label = "文件名",
            initial = name,
            onConfirm = { typed ->
                repo.write(name, text)
                savedText = text
                val typedExt = FileTypes.extensionOf(typed)
                val target = if (typedExt.isEmpty()) FileTypes.composeName(typed, FileTypes.extensionOf(name))
                else FileTypes.composeName(typed, typedExt)
                if (target.isNotBlank() && target != name && repo.rename(name, target)) {
                    name = target
                    onChanged()
                }
                renameOpen = false
            },
            onDismiss = { renameOpen = false },
        )
    }

    if (deleteOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteOpen = false },
            containerColor = pal.surface,
            titleContentColor = pal.text,
            textContentColor = pal.dim,
            title = { Text("删除文件", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("$name 将被永久删除。", fontSize = 14.sp) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    repo.delete(name)
                    deleteOpen = false
                    onChanged()
                    onBack()
                }) { Text("删除", color = pal.danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteOpen = false }) { Text("取消", color = pal.dim) }
            },
        )
    }
}

/** One-line banner above the preview: what we guessed, what went wrong. */
private data class Hint(val message: String, val action: String, val onClick: () -> Unit)

@Composable
private fun PreviewPane(
    content: PreviewContent,
    reloadKey: Long,
    background: Color,
    fontSize: Int,
    scrollY: Int,
    onScroll: (Int) -> Unit,
    onRenderInfo: (RenderInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = AppTheme.colors
    when (content) {
        is PreviewContent.Web -> WebPreview(
            page = content.page,
            reloadKey = reloadKey,
            background = background,
            onRenderInfo = onRenderInfo,
            modifier = modifier,
        )

        is PreviewContent.Source -> Column(modifier.background(pal.codeBg)) {
            val strip = content.note
                ?: if (content.formatted) "已自动格式化（原文未改动）" else null
            if (strip != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(pal.surfaceHi)
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strip, color = pal.faint, fontSize = 10.5.sp)
                }
            }
            CodeView(
                text = content.text,
                lang = content.lang,
                pal = pal,
                fontSize = fontSize,
                scrollY = scrollY,
                onScroll = onScroll,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TapIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val pal = AppTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(10.dp),
    ) {
        Icon(icon, contentDescription = label, tint = pal.dim, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Sep() {
    val pal = AppTheme.colors
    Box(Modifier.padding(horizontal = 6.dp).size(2.5.dp).clip(RoundedCornerShape(2.dp)).background(pal.line))
}
