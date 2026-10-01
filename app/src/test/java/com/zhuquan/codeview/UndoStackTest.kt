package com.zhuquan.codeview

import com.zhuquan.codeview.core.UndoStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoStackTest {

    @Test
    fun walks_back_and_forward() {
        val stack = UndoStack()
        stack.reset("a")
        stack.record("ab")
        stack.record("abc")
        assertTrue(stack.canUndo)
        assertEquals("ab", stack.undo())
        assertEquals("a", stack.undo())
        assertFalse(stack.canUndo)
        assertNull(stack.undo())
        assertEquals("ab", stack.redo())
        assertEquals("abc", stack.redo())
        assertFalse(stack.canRedo)
        assertNull(stack.redo())
    }

    @Test
    fun ignores_duplicate_snapshots() {
        val stack = UndoStack()
        stack.reset("a")
        stack.record("a")
        stack.record("a")
        assertEquals(1, stack.size)
        stack.record("b")
        stack.record("b")
        assertEquals(2, stack.size)
    }

    @Test
    fun typing_after_undo_drops_the_redo_tail() {
        val stack = UndoStack()
        stack.reset("a")
        stack.record("ab")
        stack.record("abc")
        stack.undo()
        assertTrue(stack.canRedo)
        stack.record("abX")
        assertFalse(stack.canRedo)
        assertEquals("abX", stack.undo().let { stack.redo() })
    }

    @Test
    fun respects_the_limit() {
        val stack = UndoStack(limit = 5)
        stack.reset("0")
        for (i in 1..20) stack.record("$i")
        assertTrue(stack.size <= 5)
    }
}
