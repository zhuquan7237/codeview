package com.zhuquan.codeview

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import com.zhuquan.codeview.data.FileRepo
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
}
