package com.zhuquan.codeview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhuquan.codeview.ui.readClipboard
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The paste path lives in [readClipboard]; on Android 10+ the clipboard is only
 * readable while the app holds input focus, so this runs against the real activity.
 */
@RunWith(AndroidJUnit4::class)
class ClipboardTest {

    @Test
    fun readClipboardReturnsWhatWasCopied() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val manager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                manager.setPrimaryClip(ClipData.newPlainText("code", "clip-marker-42"))
                assertEquals("clip-marker-42", readClipboard(activity))

                manager.setPrimaryClip(ClipData.newPlainText("code", ""))
                assertEquals("", readClipboard(activity))

                manager.clearPrimaryClip()
                assertEquals("", readClipboard(activity))
            }
        } finally {
            scenario.close()
        }
    }
}
