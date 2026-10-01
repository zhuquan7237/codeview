package com.zhuquan.codeview

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.IntentCompat
import com.zhuquan.codeview.data.FileRepo
import com.zhuquan.codeview.data.ShareInbox
import com.zhuquan.codeview.data.SharedPayload
import com.zhuquan.codeview.ui.AppTheme
import com.zhuquan.codeview.ui.CodeViewApp
import com.zhuquan.codeview.ui.CodeViewTheme
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val repo = FileRepo(File(filesDir, "files"))
        repo.ensureSamples()

        handleIntent(intent)

        setContent {
            CodeViewTheme {
                val pal = AppTheme.colors
                // Keep the window the same colour as the app: a mismatched window
                // background flashes a bright frame when the IME opens.
                SideEffect {
                    window.setBackgroundDrawable(ColorDrawable(pal.bg.toArgb()))
                }
                CodeViewApp(repo)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** "Share from another app" is the fastest way to preview code an AI just produced. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val uri: Uri? = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (!text.isNullOrBlank() || uri != null) {
                    ShareInbox.push(SharedPayload(text, uri, intent.type))
                }
            }
        }
    }
}
