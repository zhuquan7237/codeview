package com.zhuquan.codeview.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhuquan.codeview.core.FileTypes
import com.zhuquan.codeview.data.CodeItem
import com.zhuquan.codeview.data.FileRepo

@Composable
fun HomeScreen(
    repo: FileRepo,
    version: Int,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onImport: () -> Unit,
    onPasteCreate: () -> Unit,
    onChanged: () -> Unit,
    onShare: (String) -> Unit,
) {
    val pal = AppTheme.colors
    val items = remember(version, repo) { repo.list() }
    var query by remember { mutableStateOf("") }
    var sheetFor by remember { mutableStateOf<String?>(null) }
    var renameFor by remember { mutableStateOf<String?>(null) }
    var deleteFor by remember { mutableStateOf<String?>(null) }

    val shown = remember(items, query) {
        if (query.isBlank()) items
        else items.filter { it.name.contains(query, true) }
    }
    val totalBytes = remember(items) { items.sumOf { it.size } }

    Box(Modifier.fillMaxSize().background(pal.bg).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "CodeView",
                        color = pal.text,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        if (items.isEmpty()) "新建文件，粘贴代码，直接看效果"
                        else "${items.size} 个文件 · ${FileTypes.formatSize(totalBytes)}",
                        color = pal.faint,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Pill("导入", onClick = onImport)
                Spacer(Modifier.width(8.dp))
                Pill("剪贴板", onClick = onPasteCreate)
            }

            if (items.size >= 2) {
                Surface(
                    color = pal.surfaceHi,
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .height(42.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = pal.faint,
                            modifier = Modifier.padding(start = 12.dp, end = 8.dp).size(17.dp),
                        )
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text("搜索文件名", color = pal.faint, fontSize = 13.5.sp)
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = TextStyle(color = pal.text, fontSize = 13.5.sp),
                                cursorBrush = SolidColor(pal.primary),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (query.isNotEmpty()) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "清除",
                                tint = pal.faint,
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .size(17.dp)
                                    .clickable { query = "" },
                            )
                        }
                    }
                }
            }

            if (shown.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(bottom = 60.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    EmptyState(
                        title = if (items.isEmpty()) "还没有文件" else "没有匹配的文件",
                        subtitle = if (items.isEmpty())
                            "新建一个文件，把 AI 给的代码粘进去就能预览；已有的文件也可以直接导入。"
                        else "换个关键词试试。",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Pill(text = "新建文件", selected = true, onClick = onNew)
                            Pill(text = "导入文件", onClick = onImport)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(shown, key = { it.name }) { item ->
                        FileRow(item = item, onClick = { onOpen(item.name) }, onMore = { sheetFor = item.name })
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onNew,
            containerColor = pal.primary,
            contentColor = pal.onPrimary,
            icon = { Icon(Icons.Filled.Add, contentDescription = null, tint = pal.onPrimary) },
            text = { Text("新建文件", fontWeight = FontWeight.Medium, color = pal.onPrimary) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    sheetFor?.let { name ->
        ActionSheet(
            title = name,
            actions = listOf(
                SheetAction("打开", icon = Icons.Filled.Create) { sheetFor = null; onOpen(name) },
                SheetAction("重命名") { sheetFor = null; renameFor = name },
                SheetAction("复制副本") { sheetFor = null; repo.duplicate(name); onChanged() },
                SheetAction("分享代码", icon = Icons.Filled.Share) { sheetFor = null; onShare(name) },
                SheetAction("删除", danger = true, icon = Icons.Filled.Delete) { sheetFor = null; deleteFor = name },
            ),
            onDismiss = { sheetFor = null },
        )
    }

    renameFor?.let { old ->
        TextPromptDialog(
            title = "重命名",
            label = "文件名",
            initial = old,
            onConfirm = { typed ->
                val target = FileTypes.composeName(typed, FileTypes.extensionOf(typed))
                if (target.isNotBlank() && repo.rename(old, target)) onChanged()
                renameFor = null
            },
            onDismiss = { renameFor = null },
        )
    }

    deleteFor?.let { name ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteFor = null },
            containerColor = pal.surface,
            titleContentColor = pal.text,
            textContentColor = pal.dim,
            title = { Text("删除文件", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("$name 将被永久删除。", fontSize = 14.sp) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    repo.delete(name)
                    deleteFor = null
                    onChanged()
                }) { Text("删除", color = pal.danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteFor = null }) { Text("取消", color = pal.dim) }
            },
        )
    }
}

@Composable
private fun FileRow(item: CodeItem, onClick: () -> Unit, onMore: () -> Unit) {
    val pal = AppTheme.colors
    Surface(
        color = pal.surface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, pal.line),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KindBadge(item.kind)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    color = pal.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                    Text(
                        item.ext.uppercase(),
                        color = pal.faint,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Dot()
                    Text(FileTypes.formatSize(item.size), color = pal.faint, fontSize = 11.5.sp)
                    Dot()
                    Text(relativeTime(item.modified), color = pal.faint, fontSize = 11.5.sp)
                }
            }
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "更多",
                tint = pal.faint,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onMore() }
                    .padding(8.dp)
                    .size(18.dp),
            )
        }
    }
}

@Composable
private fun Dot() {
    val pal = AppTheme.colors
    Box(
        Modifier.padding(horizontal = 6.dp).size(2.5.dp).clip(RoundedCornerShape(2.dp)).background(pal.faint),
    )
}
