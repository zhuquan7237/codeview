package com.zhuquan.codeview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhuquan.codeview.core.Highlighter
import com.zhuquan.codeview.core.Lang
import com.zhuquan.codeview.core.Tok
import com.zhuquan.codeview.core.Token

/** Files bigger than this skip highlighting: a keystroke must never cost a full scan. */
private const val HIGHLIGHT_LIMIT = 60_000

fun codeStyle(fontSize: Int = 13, lineHeight: Int = 20, color: androidx.compose.ui.graphics.Color) =
    TextStyle(
        fontFamily = Mono,
        fontSize = fontSize.sp,
        lineHeight = lineHeight.sp,
        color = color,
        letterSpacing = 0.sp,
    )

private fun colorFor(tok: Tok, pal: Pal): androidx.compose.ui.graphics.Color = when (tok) {
    Tok.KEYWORD -> pal.kw
    Tok.STRING -> pal.str
    Tok.COMMENT -> pal.com
    Tok.NUMBER -> pal.num
    Tok.TAG -> pal.tag
    Tok.ATTR -> pal.attr
    Tok.PUNCT -> pal.punc
    Tok.FUNCTION -> pal.fn
    Tok.PLAIN -> pal.text
}

fun buildAnnotated(text: String, tokens: List<Token>, pal: Pal): AnnotatedString = buildAnnotatedString {
    append(text)
    for (t in tokens) {
        val end = t.end.coerceAtMost(text.length)
        if (t.start >= end) continue
        addStyle(SpanStyle(color = colorFor(t.tok, pal)), t.start, end)
    }
}

/** Highlighting as a VisualTransformation: offsets are identity-mapped, styles only. */
class HighlightTransformation(
    private val lang: Lang,
    private val pal: Pal,
    private val limit: Int = HIGHLIGHT_LIMIT,
) : VisualTransformation {

    private var cachedText: String? = null
    private var cachedResult: TransformedText? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        cachedResult?.let { if (cachedText == raw) return it }
        val out = TransformedText(
            if (raw.length > limit) AnnotatedString(raw)
            else buildAnnotated(raw, Highlighter.scan(raw, lang), pal),
            OffsetMapping.Identity,
        )
        cachedText = raw
        cachedResult = out
        return out
    }
}

/** Read-only, un-wrapped code view with a synced line-number gutter. */
@Composable
fun CodeView(
    text: String,
    lang: Lang,
    pal: Pal,
    fontSize: Int = 13,
    modifier: Modifier = Modifier,
    showGutter: Boolean = true,
) {
    val style = codeStyle(fontSize, (fontSize * 1.55f).toInt(), pal.text)
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val annotated = remember(text, lang, pal) {
        if (text.length > HIGHLIGHT_LIMIT) AnnotatedString(text)
        else buildAnnotated(text, Highlighter.scan(text, lang), pal)
    }
    val lineCount = remember(text) { Highlighter.lineCount(text) }
    val gutterWidth = remember(lineCount) { (18 + 8 * lineCount.toString().length).dp }

    Box(modifier.background(pal.codeBg)) {
        Row(
            Modifier
                .fillMaxSize()
                .verticalScroll(vScroll)
                .horizontalScroll(hScroll),
        ) {
            if (showGutter) {
                Text(
                    text = remember(lineCount) { (1..lineCount).joinToString("\n") },
                    style = style.copy(color = pal.faint),
                    textAlign = TextAlign.End,
                    softWrap = false,
                    modifier = Modifier
                        .width(gutterWidth)
                        .padding(start = 6.dp, end = 10.dp, top = 12.dp, bottom = 24.dp),
                )
            }
            SelectionContainer {
                Text(
                    text = annotated,
                    style = style,
                    softWrap = false,
                    modifier = Modifier.padding(start = 2.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                )
            }
        }
    }
}

/** Editable code field with syntax highlighting. Soft-wraps so long lines stay readable. */
@Composable
fun CodeField(
    value: String,
    onValueChange: (String) -> Unit,
    lang: Lang,
    pal: Pal,
    fontSize: Int = 13,
    modifier: Modifier = Modifier,
) {
    val style = codeStyle(fontSize, (fontSize * 1.55f).toInt(), pal.text)
    val transformation = remember(lang, pal) { HighlightTransformation(lang, pal) }
    Box(modifier.background(pal.codeBg)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            visualTransformation = transformation,
            cursorBrush = SolidColor(pal.primary),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}
