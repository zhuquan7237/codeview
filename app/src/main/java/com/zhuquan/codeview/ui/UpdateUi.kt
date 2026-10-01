package com.zhuquan.codeview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhuquan.codeview.core.AppUpdate
import com.zhuquan.codeview.core.ReleaseInfo

/** Everything the home screen needs to know about the update flow. */
data class UpdateUiState(
    val currentVersion: String = "",
    val info: ReleaseInfo? = null,
    val checking: Boolean = false,
    val stage: Stage = Stage.Idle,
    val progress: Int = 0,
    val error: String? = null,
    val needsPermission: Boolean = false,
) {
    enum class Stage { Idle, Downloading, Ready, Failed }

    val busy: Boolean get() = stage == Stage.Downloading
}

/**
 * The only thing the app says about updates on its own: one card, only when a newer
 * build actually exists. Dismissing it remembers the version, so it does not nag.
 */
@Composable
fun UpdateBanner(
    state: UpdateUiState,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pal = AppTheme.colors
    val info = state.info ?: return
    Surface(
        color = pal.primarySoft,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, pal.primary.copy(alpha = 0.32f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onOpen() },
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                tint = pal.primary,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.busy) "正在下载 ${state.progress}%" else "新版本 ${info.version} 可用",
                    color = pal.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (state.busy) "保持在这个界面即可，下载完会拉起安装程序"
                    else "${AppUpdate.formatSize(info.size)} · 点按直接更新，不用再去仓库下载",
                    color = if (state.busy) pal.primary else pal.dim,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.busy) {
                    ProgressBar(percent = state.progress, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Icon(
                Icons.Filled.Close,
                contentDescription = "忽略此版本",
                tint = pal.faint,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDismiss() }
                    .padding(8.dp)
                    .size(15.dp),
            )
        }
    }
}

/** Footer line under the file list: shows the running version and checks on demand. */
@Composable
fun UpdateFooter(currentVersion: String, checking: Boolean, onClick: () -> Unit) {
    val pal = AppTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = !checking,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (checking) "正在检查更新…" else "CodeView $currentVersion · 检查更新",
            color = pal.faint,
            fontSize = 11.5.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    state: UpdateUiState,
    onStart: () -> Unit,
    onInstall: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pal = AppTheme.colors
    val info = state.info ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = pal.surface,
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 26.dp)) {
            Text("发现新版本 ${info.version}", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = pal.text)
            Text(
                "${AppUpdate.formatSize(info.size)} · 当前版本 ${state.currentVersion}",
                color = pal.faint,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )

            if (info.notes.isNotBlank()) {
                Surface(
                    color = pal.surfaceHi,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .heightIn(max = 220.dp),
                ) {
                    Text(
                        info.notes,
                        color = pal.dim,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(13.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            when (state.stage) {
                UpdateUiState.Stage.Downloading -> {
                    ProgressBar(percent = state.progress, modifier = Modifier.fillMaxWidth())
                    Text(
                        if (state.progress < 100) "正在下载 ${state.progress}%" else "正在校验安装包…",
                        color = pal.dim,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                UpdateUiState.Stage.Ready -> {
                    if (state.needsPermission) {
                        Text(
                            "下载完成。系统需要你允许 CodeView 安装应用，才能继续。",
                            color = pal.text,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
                            Surface(
                                color = pal.surfaceHi,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f).height(46.dp),
                                onClick = onSettings,
                            ) {
                                Box(contentAlignment = Alignment.Center) { Text("去设置", color = pal.dim, fontSize = 14.5.sp) }
                            }
                            Surface(
                                color = pal.primary,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1.3f).height(46.dp),
                                onClick = onInstall,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("我已允许，安装", color = pal.onPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    } else {
                        Text("下载完成，正在打开安装程序…", color = pal.dim, fontSize = 12.5.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
                            Surface(
                                color = pal.primary,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f).height(46.dp),
                                onClick = onInstall,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("重新打开安装程序", color = pal.onPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                UpdateUiState.Stage.Failed -> {
                    Text(state.error ?: "下载失败", color = pal.danger, fontSize = 12.5.sp, lineHeight = 19.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
                        Surface(
                            color = pal.surfaceHi,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(46.dp),
                            onClick = onDismiss,
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text("稍后", color = pal.dim, fontSize = 14.5.sp) }
                        }
                        Surface(
                            color = pal.primary,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1.3f).height(46.dp),
                            onClick = onStart,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("重试", color = pal.onPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                UpdateUiState.Stage.Idle -> {
                    Text(
                        "更新包会校验 sha256 后才交给系统安装；签名与当前版本一致，数据不会丢。",
                        color = pal.faint,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
                        Surface(
                            color = pal.surfaceHi,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(46.dp),
                            onClick = onDismiss,
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text("稍后", color = pal.dim, fontSize = 14.5.sp) }
                        }
                        Surface(
                            color = pal.primary,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1.3f).height(46.dp),
                            onClick = onStart,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("下载并安装", color = pal.onPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(percent: Int, modifier: Modifier = Modifier) {
    val pal = AppTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(pal.line),
    ) {
        Box(
            Modifier
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(pal.primary),
        )
    }
}


