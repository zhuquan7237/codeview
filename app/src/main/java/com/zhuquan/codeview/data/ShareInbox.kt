package com.zhuquan.codeview.data

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

/** Something another app handed us: pasted text, or a document URI. */
data class SharedPayload(val text: String?, val uri: Uri?, val mime: String?)

/**
 * Landing spot for `ACTION_SEND` payloads. MainActivity pushes on the main thread,
 * the Compose tree consumes in a LaunchedEffect and clears it.
 */
object ShareInbox {
    val flow = MutableStateFlow<SharedPayload?>(null)

    fun push(payload: SharedPayload) {
        flow.value = payload
    }

    fun clear() {
        flow.value = null
    }
}
