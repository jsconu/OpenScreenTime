package org.openscreentime.shared.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BedtimeTest {

    @Test
    fun `no window set never blocks`() {
        assertFalse(isInBedtimeWindow(nowMinutesOfDay = 0, startMinutes = null, endMinutes = null))
        assertFalse(isInBedtimeWindow(nowMinutesOfDay = 0, startMinutes = 100, endMinutes = null))
        assertFalse(isInBedtimeWindow(nowMinutesOfDay = 0, startMinutes = null, endMinutes = 100))
    }

    @Test
    fun `equal start and end is treated as not set, not an all-day block`() {
        assertFalse(isInBedtimeWindow(nowMinutesOfDay = 500, startMinutes = 480, endMinutes = 480))
    }

    @Test
    fun `same-day window (start before end) blocks only inside the range`() {
        // 13:00 (780) to 15:00 (900)
        assertFalse(isInBedtimeWindow(nowMinutesOfDay = 779, startMinutes = 780, endMinutes = 900))
        assertTrue(isInBedtimeWindow(nowMinutesOfDay = 780, startMinutes = 780, endMinutes = 900))
        assertTrue(isInBedtimeWindow(nowMinutesOfDay = 850, startMinutes = 780, endMinutes = 900))
        assertFalse(isInBedtimeWindow(nowMinutesOfDay = 900, startMinutes = 780, endMinutes = 900))
    }

    @Test
    fun `overnight window spanning midnight blocks on both sides of midnight`() {
        // 21:00 (1260) to 07:00 (420)
        assertTrue(isInBedtimeWindow(nowMinutesOfDay = 1260, startMinutes = 1260, endMinutes = 420))
        assertTrue(isInBedtimeWindow(nowMinutesOfDay = 0, startMinutes = 1260, endMinutes = 420))
        assertTrue(isInBedtimeWindow(nowMinutesOfDay = 419, startMinutes = 1260, endMinutes = 420))
        assertFalse(isInBedtimeWindow(nowMinutesOfDay = 420, startMinutes = 1260, endMinutes = 420))
        assertFalse(isInBedtimeWindow(nowMinutesOfDay = 900, startMinutes = 1260, endMinutes = 420))
        assertFalse(isInBedtimeWindow(nowMinutesOfDay = 1259, startMinutes = 1260, endMinutes = 420))
    }

    @Test
    fun `formats minutes of day as a 12-hour clock time`() {
        assertEquals("12:00 AM", formatMinutesOfDay(0))
        assertEquals("7:00 AM", formatMinutesOfDay(420))
        assertEquals("12:00 PM", formatMinutesOfDay(720))
        assertEquals("9:30 PM", formatMinutesOfDay(1290))
        assertEquals("11:59 PM", formatMinutesOfDay(1439))
    }

    @Test
    fun `parses a valid HH-MM string into minutes since midnight`() {
        assertEquals(0, parseHHmm("00:00"))
        assertEquals(420, parseHHmm("07:00"))
        assertEquals(1439, parseHHmm("23:59"))
        assertEquals(420, parseHHmm(" 07:00 "))
    }

    @Test
    fun `rejects invalid HH-MM strings rather than guessing`() {
        assertNull(parseHHmm(""))
        assertNull(parseHHmm("7"))
        assertNull(parseHHmm("7:00:00"))
        assertNull(parseHHmm("24:00"))
        assertNull(parseHHmm("07:60"))
        assertNull(parseHHmm("ab:cd"))
    }

    @Test
    fun `formats minutes of day as a 24-hour HH-MM string`() {
        assertEquals("00:00", formatHHmm(0))
        assertEquals("07:00", formatHHmm(420))
        assertEquals("23:59", formatHHmm(1439))
    }

    @Test
    fun `formatHHmm and parseHHmm round-trip`() {
        for (minutes in listOf(0, 1, 59, 60, 420, 720, 1259, 1439)) {
            assertEquals(minutes, parseHHmm(formatHHmm(minutes)))
        }
    }
}
