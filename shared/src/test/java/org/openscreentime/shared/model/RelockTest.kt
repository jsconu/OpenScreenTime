package org.openscreentime.shared.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelockTest {

    @Test
    fun `a timed unlock re-locks that many minutes later`() {
        assertEquals(1_000L + 15 * 60_000L, relockAtFor(15, 1_000L))
        assertEquals(1_000L + 60 * 60_000L, relockAtFor(60, 1_000L))
    }

    @Test
    fun `until I lock it again means no re-lock time`() {
        assertNull(relockAtFor(null, 1_000L))
    }

    @Test
    fun `a re-lock is due only at or after its time`() {
        assertFalse(relockDue(null, 5_000L))
        assertFalse(relockDue(10_000L, 9_999L))
        assertTrue(relockDue(10_000L, 10_000L))
        assertTrue(relockDue(10_000L, 20_000L))
    }

    @Test
    fun `the default is one of the offered choices`() {
        assertTrue(DEFAULT_RELOCK_MINUTES in RELOCK_CHOICES_MINUTES)
    }
}
