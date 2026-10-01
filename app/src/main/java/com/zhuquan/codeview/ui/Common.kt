package com.zhuquan.codeview.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhuquan.codeview.core.FileKind
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun Color.soft(alpha: Float = 0.13f) = this.copy(alpha = alpha)

/** Coloured extension badge: no images, no icon font, still recognisable. */
@Composable
fun KindBadge(kind: FileKind, size: Int = 44) {
    val base = Color(kind.argb)
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.3f).dp))
            .background(base.soft(if (AppTheme.colors.dark) 0.22f else 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = kind.badge,
            color = base,
            fontSize = (size * 0.28f).sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
        )
    }
}

@Composable
fun Pill(
    text: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val pal = AppTheme.colors
    val bg by animateColorAsState(if (selected) pal.primarySoft else Color.Transparent, label = "pillBg")
    val fg by animateColorAsState(
        when {
            !enabled -> pal.faint
            selected -> pal.primary
            else -> pal.dim
        },
        label = "pillFg",
    )
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = bg,
        border = BorderStroke(1.dp, if (selected) pal.primary.soft(0.35f) else pal.line),
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(11.dp))
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
    ) {
        Box(Modifier.padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
            Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** Two-state (or n-state) switcher with a sliding indicator. */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Int = 36,
) {
    val pal = AppTheme.colors
    Surface(
        color = pal.surfaceHi,
        shape = RoundedCornerShape(13.dp),
        modifier = modifier.height(height.dp),
    ) {
        BoxWithConstraints(Modifier.padding(3.dp)) {
            val each = (maxWidth - 0.dp) / options.size
            val x by animateDpAsState(
                targetValue = each * selected.coerceIn(0, options.size - 1),
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
                label = "segX",
            )
            Box(
                Modifier
                    .offset(x = x)
                    .width(each)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(pal.surface),
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                options.forEachIndexed { index, label ->
                    val active = index == selected
                    val fg by animateColorAsState(if (active) pal.text else pal.dim, label = "segFg")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = fg,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

data class SheetAction(val label: String, val hint: String? = null, val danger: Boolean = false, val icon: ImageVector? = null, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(title: String?, actions: List<SheetAction>, onDismiss: () -> Unit) {
    val pal = AppTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
        Column(Modifier.padding(bottom = 26.dp)) {
            if (title != null) {
                Text(
                    title,
                    color = pal.dim,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 10.dp),
                )
            }
            actions.forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            action.onClick()
                        }
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (action.icon != null) {
                        Icon(action.icon, contentDescription = null, tint = if (action.danger) pal.danger else pal.dim, modifier = Modifier.size(19.dp))
                    }
                    Text(
                        action.label,
                        color = if (action.danger) pal.danger else pal.text,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (action.hint != null) Text(action.hint, color = pal.faint, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun TextPromptDialog(
    title: String,
    label: String,
    initial: String,
    suffix: String? = null,
    confirmText: String = "确定",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val pal = AppTheme.colors
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pal.surface,
        titleContentColor = pal.text,
        textContentColor = pal.dim,
        title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = pal.text, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(pal.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(pal.surfaceHi)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(confirmText, color = pal.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = pal.dim) }
        },
    )
}

@Composable
fun EmptyState(title: String, subtitle: String, action: @Composable () -> Unit) {
    val pal = AppTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(pal.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Text("</>", color = pal.primary, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Text(title, color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
        Text(subtitle, color = pal.faint, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        Box(Modifier.padding(top = 18.dp)) { action() }
    }
}

fun relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - millis
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        diff < 172_800_000 -> "昨天"
        else -> {
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            val today = Calendar.getInstance()
            val fmt = if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)) "M月d日" else "yyyy年M月d日"
            SimpleDateFormat(fmt, Locale.CHINA).format(Date(millis))
        }
    }
}

fun readClipboard(context: Context): String {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
    val clip: ClipData? = cm.primaryClip
    if (clip == null || clip.itemCount == 0) return ""
    return clip.getItemAt(0).coerceToText(context).toString()
}
