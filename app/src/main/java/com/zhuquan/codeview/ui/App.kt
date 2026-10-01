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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.zhuquan.codeview.data.FileRepo

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

@Composable
fun CodeViewApp(repo: FileRepo) {
    val context = LocalContext.current
    var route by rememberSaveable { mutableStateOf(HOME) }
    var version by remember { mutableStateOf(0) }
    var showNew by remember { mutableStateOf(false) }

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
