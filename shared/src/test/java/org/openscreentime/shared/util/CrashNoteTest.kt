package org.openscreentime.shared.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashNoteTest {
    @Test
    fun `a note starts with the app, version and thread, then the trace`() {
        val note = CrashNote.describe("OpenScreenTime", "0.2.0", "main", "  java.lang.IllegalStateException: boom\n\tat a.b.C.d(C.kt:1)  ")
        assertEquals(
            "OpenScreenTime 0.2.0 crashed on thread main\njava.lang.IllegalStateException: boom\n\tat a.b.C.d(C.kt:1)",
            note
        )
    }

    @Test
    fun `a very long trace is cut so it stays easy to paste`() {
        val note = CrashNote.describe("App", "1", "main", "x".repeat(20_000))
        assertTrue(note.length < 6_100)
        assertTrue(note.endsWith("(cut)"))
    }
}
