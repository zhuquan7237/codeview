package com.zhuquan.codeview.ui

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
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

/**
 * What the loaded page actually produced. Used to tell the user "your SVG rendered empty"
 * instead of leaving them staring at a blank pane.
 */
data class RenderInfo(
    val hasSvg: Boolean,
    val svgWidth: Float,
    val svgHeight: Float,
    val bodyHeight: Int,
    val textLength: Int,
    val mediaCount: Int,
    /** Drawable descendants of the SVG. A truncated tag parses into zero of them. */
    val svgChildren: Int,
    val jsError: String? = null,
) {
    val looksEmpty: Boolean
        get() = if (hasSvg) svgChildren == 0 || (svgWidth < 1f && svgHeight < 1f)
        else mediaCount == 0 && textLength == 0 && bodyHeight <= 8
}

private const val PROBE = """
(function(){
  try {
    var s = document.querySelector('svg');
    var w = s ? Math.round(s.getBoundingClientRect().width) : -1;
    var h = s ? Math.round(s.getBoundingClientRect().height) : -1;
    var b = document.body ? document.body.scrollHeight : -1;
    var t = document.body ? (document.body.innerText || '').replace(/\s+/g,'').length : 0;
    var m = document.querySelectorAll('canvas,img,video,iframe,svg').length;
    var c = s ? s.querySelectorAll('*').length : -1;
    return (s?1:0)+'|'+w+'|'+h+'|'+b+'|'+t+'|'+m+'|'+c;
  } catch (e) { return '0|-1|-1|-1|0|0|-1'; }
})()
"""

/** Keeps the last loaded document so recomposition never triggers a reload. */
private class WebHolder {
    private var lastKey: String? = null
    var onInfo: ((RenderInfo) -> Unit)? = null
    private var lastError: String? = null

    fun load(web: WebView, page: String?, reloadKey: Long, cacheDir: File) {
        if (page == null) return
        val key = "$reloadKey:${page.length}:${page.hashCode()}"
        if (key == lastKey) return
        lastKey = key
        lastError = null
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

    fun report(web: WebView) {
        val cb = onInfo ?: return
        web.evaluateJavascript(PROBE) { raw ->
            val parts = raw?.trim()?.trim('"')?.split('|') ?: return@evaluateJavascript
            if (parts.size < 7) return@evaluateJavascript
            fun num(i: Int) = parts.getOrNull(i)?.toFloatOrNull() ?: -1f
            cb(
                RenderInfo(
                    hasSvg = num(0) > 0.5f,
                    svgWidth = num(1),
                    svgHeight = num(2),
                    bodyHeight = num(3).toInt(),
                    textLength = num(4).toInt(),
                    mediaCount = num(5).toInt(),
                    svgChildren = num(6).toInt(),
                    jsError = lastError,
                ),
            )
        }
    }

    fun reset() {
        lastKey = null
    }

    fun noteError(message: ConsoleMessage) {
        if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR && lastError == null) {
            lastError = message.message()?.take(180)
        }
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
    onRenderInfo: (RenderInfo) -> Unit = {},
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
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        holder.noteError(message)
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        holder.report(this@apply)
                    }
                }
                onCreated(this)
            }
        },
        update = { web ->
            holder.onInfo = onRenderInfo
            web.setBackgroundColor(background.toArgb())
            holder.load(web, page, reloadKey, web.context.cacheDir)
        },
    )
}
