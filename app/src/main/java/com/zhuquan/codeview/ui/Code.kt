package com.zhuquan.codeview.ui

import android.content.Context
import android.graphics.Color as AColor
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.zhuquan.codeview.core.Highlighter
import com.zhuquan.codeview.core.Lang
import com.zhuquan.codeview.core.Tok
import com.zhuquan.codeview.core.Token

/** Files bigger than this skip highlighting: a keystroke must never cost a full scan. */
const val HIGHLIGHT_LIMIT = 60_000

/**
 * Code surfaces are built on **platform TextView/EditText on purpose**.
 *
 * Compose's `BasicTextField` / `Text` lay a document out as one giant paragraph and repaint
 * every line each frame — a 3000-line file scrolled at ~7fps here (measured with
 * `dumpsys gfxinfo`). `android.text.Layout` only draws the lines inside the clip rect, which
 * is what every real Android code editor relies on.
 */

private fun Context.px(v: Int): Int = (v * resources.displayMetrics.density).toInt()

private fun tokColor(tok: Tok, pal: Pal): Int = when (tok) {
    Tok.KEYWORD -> pal.kw
    Tok.STRING -> pal.str
    Tok.COMMENT -> pal.com
    Tok.NUMBER -> pal.num
    Tok.TAG -> pal.tag
    Tok.ATTR -> pal.attr
    Tok.PUNCT -> pal.punc
    Tok.FUNCTION -> pal.fn
    Tok.PLAIN -> pal.text
}.toArgb()

private fun buildSpannable(text: String, tokens: List<Token>, pal: Pal): SpannableString {
    val sp = SpannableString(text)
    for (t in tokens) {
        val end = t.end.coerceAtMost(text.length)
        if (t.start >= end) continue
        sp.setSpan(
            ForegroundColorSpan(tokColor(t.tok, pal)),
            t.start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    return sp
}

private fun applyTextStyle(tv: TextView, pal: Pal, fontSize: Int) {
    if (tv.currentTextColor != pal.text.toArgb()) tv.setTextColor(pal.text.toArgb())
    val want = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        fontSize.toFloat(),
        tv.resources.displayMetrics,
    )
    if (tv.textSize != want) tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())
}

private fun gutterKeyOf(text: String, fontSize: Int) = "${System.identityHashCode(text)}|$fontSize"

// ---------------------------------------------------------------- read-only view

/** Non-wrapping code view: vertical + horizontal scroll, synced line-number gutter. */
private class CodeReadView(ctx: Context) : ScrollView(ctx) {

    private val hScroll = HorizontalScrollView(ctx)
    private val row = LinearLayout(ctx)
    private val gutter = TextView(ctx)
    private val code = TextView(ctx)

    var onScroll: ((Int) -> Unit)? = null

    private var key: String? = null
    private var gutterKey: String? = null

    init {
        isFillViewport = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        hScroll.isHorizontalScrollBarEnabled = false
        hScroll.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        row.orientation = LinearLayout.HORIZONTAL

        for (tv in listOf(gutter, code)) {
            tv.typeface = Typeface.MONOSPACE
            tv.includeFontPadding = false
            tv.setLineSpacing(0f, 1.16f)
        }
        code.setHorizontallyScrolling(true)
        code.setTextIsSelectable(true)
        code.highlightColor = 0x554F46E5
        code.setPadding(ctx.px(10), ctx.px(10), ctx.px(22), ctx.px(28))
        gutter.gravity = Gravity.END
        gutter.setPadding(ctx.px(10), ctx.px(10), ctx.px(10), ctx.px(28))
        row.addView(
            gutter,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        row.addView(
            code,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        hScroll.addView(
            row,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(
            hScroll,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        setOnScrollChangeListener { _, _, _, _, _ -> onScroll?.invoke(scrollY) }
    }

    fun bind(
        text: String,
        lang: Lang,
        pal: Pal,
        fontSize: Int,
        showGutter: Boolean,
        restoreScroll: Int,
    ) {
        val newKey = "${System.identityHashCode(text)}|$lang|$fontSize|${pal.text.toArgb()}|$showGutter"
        if (newKey != key) {
            key = newKey
            gutterKey = null
            code.text = if (text.length <= HIGHLIGHT_LIMIT) {
                buildSpannable(text, Highlighter.scan(text, lang), pal)
            } else {
                text
            }
            applyTextStyle(code, pal, fontSize)
            gutter.setTextColor(pal.faint.toArgb())
            applyTextStyle(gutter, pal.copy(text = pal.faint), fontSize)
            gutter.visibility = if (showGutter) View.VISIBLE else View.GONE
            if (restoreScroll > 0) post { scrollTo(0, restoreScroll) }
        }
        if (showGutter) {
            val gKey = gutterKeyOf(text, fontSize)
            if (gKey != gutterKey) {
                gutterKey = gKey
                gutter.text = (1..Highlighter.lineCount(text)).joinToString("\n")
            }
        }
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
    scrollY: Int = 0,
    onScroll: (Int) -> Unit = {},
) {
    AndroidView(
        modifier = modifier.background(pal.codeBg),
        factory = { CodeReadView(it) },
        update = { v ->
            v.onScroll = onScroll
            v.bind(text, lang, pal, fontSize, showGutter, scrollY)
        },
    )
}

// ------------------------------------------------------------------ editable view

/** Editable code field. Re-colours only the touched lines, so typing stays cheap. */
private class CodeEditView(ctx: Context) : EditText(ctx) {

    var onValue: ((String) -> Unit)? = null
    var onScroll: ((Int) -> Unit)? = null

    private var lang: Lang = Lang.PLAIN
    private var pal: Pal = LightPal
    private var suppress = false
    private var boundKey: String? = null

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (suppress || s == null) return
            if (s.length <= HIGHLIGHT_LIMIT && s is Spannable) highlightRegion(s, start, start + count)
            onValue?.invoke(s.toString())
        }

        override fun afterTextChanged(s: Editable?) = Unit
    }

    init {
        background = ColorDrawable(AColor.TRANSPARENT)
        typeface = Typeface.MONOSPACE
        gravity = Gravity.TOP or Gravity.START
        includeFontPadding = false
        setLineSpacing(0f, 1.16f)
        setPadding(ctx.px(14), ctx.px(12), ctx.px(14), ctx.px(28))
        setHorizontallyScrolling(false)
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        isVerticalScrollBarEnabled = true
        highlightColor = 0x554F46E5
        inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        // Enter inserts a newline, and landscape must not open the full-screen
        // "extract editor" that hides the code behind the keyboard.
        imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION or
            android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
        addTextChangedListener(watcher)
        setOnScrollChangeListener { _, _, _, _, _ -> onScroll?.invoke(scrollY) }
    }

    private fun lineStart(cs: CharSequence, index: Int): Int {
        var i = index.coerceIn(0, cs.length)
        while (i > 0 && cs[i - 1] != '\n') i--
        return i
    }

    private fun lineEnd(cs: CharSequence, index: Int): Int {
        var i = index.coerceIn(0, cs.length)
        while (i < cs.length && cs[i] != '\n') i++
        return i
    }

    /** Re-colours the edited lines (+1 each way) instead of the whole document. */
    private fun highlightRegion(cs: Spannable, from: Int, to: Int) {
        if (lang == Lang.PLAIN) return
        val n = cs.length
        var start = lineStart(cs, from)
        var end = lineEnd(cs, to)
        if (start > 0) start = lineStart(cs, start - 1)
        if (end < n) end = lineEnd(cs, (end + 1).coerceAtMost(n))
        if (end <= start) return
        for (span in cs.getSpans(start, end, ForegroundColorSpan::class.java)) cs.removeSpan(span)
        val slice = cs.subSequence(start, end).toString()
        for (t in Highlighter.scan(slice, lang)) {
            val s0 = start + t.start
            val s1 = start + t.end
            if (s1 > n || s0 >= s1) continue
            cs.setSpan(
                ForegroundColorSpan(tokColor(t.tok, pal)),
                s0,
                s1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private fun repaintAll() {
        val sp = text
        if (sp !is Spannable || sp.length > HIGHLIGHT_LIMIT) return
        for (span in sp.getSpans(0, sp.length, ForegroundColorSpan::class.java)) sp.removeSpan(span)
        for (t in Highlighter.scan(sp.toString(), lang)) {
            if (t.end > sp.length || t.start >= t.end) continue
            sp.setSpan(
                ForegroundColorSpan(tokColor(t.tok, pal)),
                t.start,
                t.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    fun bind(text: String, lang: Lang, pal: Pal, fontSize: Int, restoreScroll: Int) {
        this.lang = lang
        if (this.pal != pal) {
            this.pal = pal
            highlightColor = pal.primary.copy(alpha = 0.35f).toArgb()
        }
        val key = "${System.identityHashCode(text)}|$lang|$fontSize|${pal.text.toArgb()}"
        if (text != this.text.toString()) {
            suppress = true
            setText(
                if (text.length <= HIGHLIGHT_LIMIT) buildSpannable(text, Highlighter.scan(text, lang), pal)
                else text,
            )
            setSelection(this.text.length)
            suppress = false
            boundKey = key
        } else if (key != boundKey) {
            boundKey = key
            repaintAll()
        }
        applyTextStyle(this, pal, fontSize)
        if (restoreScroll > 0 && scrollY != restoreScroll) post { scrollTo(0, restoreScroll) }
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
    scrollY: Int = 0,
    onScroll: (Int) -> Unit = {},
) {
    AndroidView(
        modifier = modifier.background(pal.codeBg),
        factory = { CodeEditView(it) },
        update = { v ->
            v.onValue = onValueChange
            v.onScroll = onScroll
            v.bind(value, lang, pal, fontSize, scrollY)
        },
    )
}
