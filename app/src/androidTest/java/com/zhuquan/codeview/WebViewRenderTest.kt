package com.zhuquan.codeview

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhuquan.codeview.core.Preview
import com.zhuquan.codeview.ui.CodeViewTheme
import com.zhuquan.codeview.ui.WebPreview
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the preview wrapper really paints inside a real Android WebView: the SVG
 * geometry is measured from the live DOM, not from a desktop browser.
 *
 * NOTE: deliberately does NOT use a Compose test rule — androidx.test Espresso 3.7.0
 * crashes on API 37 system images (`NoSuchMethodException: InputManager.getInstance`
 * from Espresso.onIdle), which would fail every test before a single assertion ran.
 */
@RunWith(AndroidJUnit4::class)
class WebViewRenderTest {

    private lateinit var scenario: ActivityScenario<ComponentActivity>

    private val svg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 60" width="120" height="60">
          <rect x="0" y="0" width="120" height="60" fill="#4f46e5"/>
          <circle cx="60" cy="30" r="20" fill="#ffffff"/>
        </svg>
    """.trimIndent()

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    /** Mounts the real [WebPreview] composable and polls [script] until it answers. */
    private fun mountAndMeasure(page: String, script: String): String {
        val ref = AtomicReference<WebView?>()
        scenario.onActivity { activity ->
            activity.setContent {
                CodeViewTheme(dark = false) {
                    WebPreview(
                        page = page,
                        reloadKey = 0L,
                        background = Color.White,
                        modifier = Modifier.fillMaxSize(),
                        onCreated = { ref.set(it) },
                    )
                }
            }
        }

        val mountDeadline = System.currentTimeMillis() + 20_000
        while (ref.get() == null && System.currentTimeMillis() < mountDeadline) Thread.sleep(50)
        val web = ref.get() ?: return "fail webview-never-created"

        var laidOut = false
        val attachDeadline = System.currentTimeMillis() + 10_000
        while (!laidOut && System.currentTimeMillis() < attachDeadline) {
            scenario.onActivity { laidOut = web.width > 0 && web.height > 0 }
            if (!laidOut) Thread.sleep(100)
        }
        if (!laidOut) return "fail webview-not-laid-out ${web.width}x${web.height}"

        var result = ""
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline) {
            val latch = CountDownLatch(1)
            scenario.onActivity {
                web.evaluateJavascript(script) { value ->
                    result = value ?: ""
                    latch.countDown()
                }
            }
            if (!latch.await(5, TimeUnit.SECONDS)) continue
            if (result.contains("ok ")) break
            Thread.sleep(200)
        }
        return result
    }

    @Test
    fun svgWrapperHasRealGeometry() {
        val page = Preview.wrapMarkup(svg, dark = false)
        val js = """
            (function(){
              var s = document.querySelector('svg');
              if (!s) return 'fail nosvg';
              var r = s.getBoundingClientRect();
              var c = document.querySelector('circle').getBoundingClientRect();
              if (r.width < 2 || r.height < 2) return 'fail tiny ' + r.width + 'x' + r.height;
              if (c.width < 2) return 'fail circle ' + c.width;
              return 'ok svg ' + Math.round(r.width) + 'x' + Math.round(r.height) +
                     ' viewport ' + document.documentElement.clientWidth;
            })()
        """.trimIndent()
        val measured = mountAndMeasure(page, js)
        assertTrue("SVG did not paint in WebView: $measured", measured.startsWith("\"ok"))
    }

    @Test
    fun injectedViewportAndInlineSvgSurviveAUserHtmlPage() {
        val html = """
            <html><head><title>t</title></head>
            <body style="margin:0">
              $svg
              <p id="mark">hello</p>
            </body></html>
        """.trimIndent()
        val page = Preview.wrapHtml(html, dark = false)
        val js = """
            (function(){
              var vp = document.querySelector('meta[name=viewport]');
              var mark = document.getElementById('mark');
              var r = mark ? mark.getBoundingClientRect() : null;
              if (!vp) return 'fail noviewport';
              if (!r || r.height < 2) return 'fail text';
              return 'ok viewport+' + Math.round(r.height);
            })()
        """.trimIndent()
        val measured = mountAndMeasure(page, js)
        assertTrue("HTML preview did not render correctly: $measured", measured.startsWith("\"ok"))
    }

    @Test
    fun oneMegabyteSvgStillLoads() {
        val big = StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">")
        repeat(15000) { big.append("<rect x=\"${it % 100}\" y=\"${it % 100}\" width=\"1\" height=\"1\" fill=\"#4f46e5\"/>") }
        big.append("</svg>")
        val page = Preview.wrapMarkup(big.toString(), dark = false)
        assertTrue("expected an oversized page", page.length > Preview.MAX_DATA_URL_CHARS)
        val js = "(function(){var s=document.querySelector('svg');if(!s)return 'fail nosvg';var r=s.getBoundingClientRect();" +
            "return r.width>2?('ok big '+Math.round(r.width)):'fail tiny '+r.width;})()"
        val measured = mountAndMeasure(page, js)
        assertTrue("oversized page failed to render: $measured", measured.startsWith("\"ok"))
    }
}
