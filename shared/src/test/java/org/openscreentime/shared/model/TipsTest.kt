package org.openscreentime.shared.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TipsTest {

    @Test
    fun `both daily tip lists have exactly 365 entries, a full year's worth`() {
        assertEquals(365, KID_DAILY_TIPS.size)
        assertEquals(365, PARENT_DAILY_TIPS.size)
    }

    @Test
    fun `neither list has duplicate tips`() {
        assertEquals(KID_DAILY_TIPS.size, KID_DAILY_TIPS.toSet().size)
        assertEquals(PARENT_DAILY_TIPS.size, PARENT_DAILY_TIPS.toSet().size)
    }

    @Test
    fun `today's tip always comes from the right list`() {
        assertTrue(currentDayKidTip() in KID_DAILY_TIPS)
        assertTrue(currentDayParentTip() in PARENT_DAILY_TIPS)
    }

    @Test
    fun `the day index is stable within a day and advances exactly once every 24 hours`() {
        val dayMs = 86_400_000L
        val dayStart = currentDayIndex() * dayMs
        val laterSameDay = dayStart + dayMs - 1
        val nextDay = dayStart + dayMs

        assertEquals(currentDayIndex(dayStart), currentDayIndex(laterSameDay))
        assertEquals(currentDayIndex(dayStart) + 1, currentDayIndex(nextDay))
    }

    @Test
    fun `the tip shown stays the same all day, then changes`() {
        val dayMs = 86_400_000L
        val dayStart = currentDayIndex() * dayMs
        val laterSameDay = dayStart + dayMs - 1
        val tipAt = { millis: Long -> KID_DAILY_TIPS[currentDayIndex(millis).mod(KID_DAILY_TIPS.size)] }

        assertEquals(tipAt(dayStart), tipAt(laterSameDay))
    }
}
