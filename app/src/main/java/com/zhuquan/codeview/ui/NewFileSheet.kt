package com.zhuquan.codeview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhuquan.codeview.core.FileTypes

/**
 * Create-a-file sheet: name + any extension (presets or free text) and where the
 * initial content comes from — template, empty, or the clipboard.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NewFileSheet(
    onCreate: (name: String, content: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val pal = AppTheme.colors
    val context = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var base by remember { mutableStateOf("") }
    var ext by remember { mutableStateOf("html") }
    var custom by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf(0) } // 0 template, 1 blank, 2 clipboard
    var clipText by remember { mutableStateOf("") }
    var clipNote by remember { mutableStateOf<String?>(null) }

    val finalName = FileTypes.composeName(base, ext)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = pal.surface,
        contentColor = pal.text,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(pal.line))
            }
        },
    ) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, bottom = 14.dp)) {
            Text("新建文件", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = pal.text)

            // name + extension
            Surface(
                color = pal.surfaceHi,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(48.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (base.isEmpty()) Text("文件名", color = pal.faint, fontSize = 14.5.sp)
                        BasicTextField(
                            value = base,
                            onValueChange = { base = it },
                            singleLine = true,
                            textStyle = TextStyle(color = pal.text, fontSize = 14.5.sp),
                            cursorBrush = SolidColor(pal.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        if (ext.isEmpty()) "" else ".$ext",
                        color = pal.primary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Text(
                "最终文件名：$finalName",
                color = pal.faint,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 7.dp, start = 2.dp),
            )

            // extension picker
            Text("后缀名", color = pal.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FileTypes.PRESETS.forEach { preset ->
                    Pill(text = preset, selected = !custom && ext == preset) {
                        custom = false
                        ext = preset
                    }
                }
                Pill(text = "自定义", selected = custom) {
                    custom = true
                    if (ext.isEmpty() || FileTypes.PRESETS.contains(ext)) ext = ""
                }
            }
            if (custom) {
                Surface(
                    color = pal.surfaceHi,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(46.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
                        Text(".", color = pal.faint, fontSize = 14.5.sp)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (ext.isEmpty()) Text("任意后缀，如 vue / tsx / svg", color = pal.faint, fontSize = 14.sp)
                            BasicTextField(
                                value = ext,
                                onValueChange = { ext = FileTypes.sanitizeExt(it).take(12) },
                                singleLine = true,
                                textStyle = TextStyle(color = pal.text, fontSize = 14.5.sp),
                                cursorBrush = SolidColor(pal.primary),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // initial content
            Text("初始内容", color = pal.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(text = "示例模板", selected = source == 0) { source = 0 }
                Pill(text = "空白", selected = source == 1) { source = 1 }
                Pill(text = "粘贴剪贴板", selected = source == 2) {
                    source = 2
                    val clip = readClipboard(context)
                    clipText = clip
                    clipNote = if (clip.isBlank()) "剪贴板里没有文本" else "已读取 ${clip.length} 个字符"
                }
            }
            clipNote?.let {
                Text(it, color = if (clipText.isBlank()) pal.danger else pal.faint, fontSize = 11.5.sp, modifier = Modifier.padding(top = 8.dp, start = 2.dp))
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = pal.surfaceHi,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                    onClick = onDismiss,
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("取消", color = pal.dim, fontSize = 15.sp) }
                }
                Surface(
                    color = pal.primary,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1.4f).height(48.dp),
                    onClick = {
                        val content = when (source) {
                            0 -> FileTypes.templateFor(ext)
                            2 -> clipText
                            else -> ""
                        }
                        onCreate(finalName, content)
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("创建并打开", color = pal.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}
