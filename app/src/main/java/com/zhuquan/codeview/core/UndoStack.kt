package com.zhuquan.codeview.core

/** Minimal snapshot undo history for the editor (no dependency, unit-testable). */
class UndoStack(private val limit: Int = 120) {

    private val items = ArrayList<String>()
    private var cursor = -1

    val canUndo: Boolean get() = cursor > 0
    val canRedo: Boolean get() = cursor in 0 until items.size - 1

    fun reset(text: String) {
        items.clear()
        items.add(text)
        cursor = 0
    }

    /** Records a new state, collapsing duplicates and dropping the redo tail. */
    fun record(text: String) {
        if (cursor >= 0 && items[cursor] == text) return
        while (items.size > cursor + 1) items.removeAt(items.size - 1)
        items.add(text)
        if (items.size > limit) items.removeAt(0)
        cursor = items.size - 1
    }

    fun undo(): String? {
        if (!canUndo) return null
        cursor--
        return items[cursor]
    }

    fun redo(): String? {
        if (!canRedo) return null
        cursor++
        return items[cursor]
    }

    val size: Int get() = items.size
}
