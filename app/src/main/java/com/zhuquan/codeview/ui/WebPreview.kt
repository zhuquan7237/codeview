package com.zhuquan.codeview.ui

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.zhuquan.codeview.core.Preview
import java.io.File

/** Keeps the last loaded document so recomposition never triggers a reload. */
private class WebHolder {
    private var lastKey: String? = null

    fun load(web: WebView, page: String?, reloadKey: Long, cacheDir: File) {
        if (page == null) return
        val key = "$reloadKey:${page.length}:${page.hashCode()}"
        if (key == lastKey) return
        lastKey = key
        if (page.length <= Preview.MAX_DATA_URL_CHARS) {
            web.settings.allowFileAccess = false
            web.loadUrl(Preview.dataUrl(page))
        } else {
            // Oversized pages (a pasted bundle, a huge inline SVG) go through a temp file.
            val file = File(cacheDir, "cv-preview.html")
            runCatching { file.writeText(page) }
            web.settings.allowFileAccess = true
            web.loadUrl("file://" + file.absolutePath)
        }
    }

    fun reset() {
        lastKey = null
    }
}

/** Renders preview pages. Geometry/scale tricks live in [Preview], not here. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPreview(
    page: String?,
    reloadKey: Long,
    background: Color,
    modifier: Modifier = Modifier,
    onCreated: (WebView) -> Unit = {},
) {
    val holder = remember { WebHolder() }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.mediaPlaybackRequiresUserGesture = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                setBackgroundColor(AColor.TRANSPARENT)
                webViewClient = WebViewClient()
                onCreated(this)
            }
        },
        update = { web ->
            web.setBackgroundColor(background.toArgb())
            holder.load(web, page, reloadKey, web.context.cacheDir)
        },
    )
}
