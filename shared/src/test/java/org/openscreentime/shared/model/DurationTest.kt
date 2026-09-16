package org.openscreentime.shared.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationTest {

    @Test
    fun `under an hour shows only minutes`() {
        assertEquals("23m", formatDuration(23 * 60_000L))
    }

    @Test
    fun `an hour or more shows hours and minutes`() {
        assertEquals("1h 23m", formatDuration(83 * 60_000L))
    }

    @Test
    fun `zero is zero minutes`() {
        assertEquals("0m", formatDuration(0))
    }

    @Test
    fun `exact hours still shows a zero-minutes remainder`() {
        assertEquals("2h 0m", formatDuration(120 * 60_000L))
    }
}
