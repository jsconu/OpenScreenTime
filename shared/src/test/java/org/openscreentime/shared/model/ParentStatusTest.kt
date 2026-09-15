package org.openscreentime.shared.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ParentStatusTest {

    private val profile = ChildProfile(dailyLimitMinutes = 100, dailyUnlockGoal = 10)

    @Test
    fun `well under the limit reads as on track`() {
        val stats = DailyStats(totalScreenTimeMs = 30 * 60_000L, unlockCount = 2)
        assertEquals("On track today", calmParentStatusLabel(profile, stats))
    }

    @Test
    fun `past 70 percent of the time limit reads as getting close`() {
        val stats = DailyStats(totalScreenTimeMs = 75 * 60_000L, unlockCount = 2)
        assertEquals("Getting close to their goal", calmParentStatusLabel(profile, stats))
    }

    @Test
    fun `past 70 percent of the unlock goal also reads as getting close`() {
        val stats = DailyStats(totalScreenTimeMs = 0, unlockCount = 8)
        assertEquals("Getting close to their goal", calmParentStatusLabel(profile, stats))
    }

    @Test
    fun `at or over the limit reads as paused for today`() {
        val stats = DailyStats(totalScreenTimeMs = 100 * 60_000L, unlockCount = 0)
        assertEquals("Paused for today", calmParentStatusLabel(profile, stats))
    }

    @Test
    fun `an explicit lock reads as paused regardless of usage`() {
        val stats = DailyStats(totalScreenTimeMs = 0, unlockCount = 0)
        assertEquals("Paused for today", calmParentStatusLabel(profile.copy(locked = true), stats))
    }
}
