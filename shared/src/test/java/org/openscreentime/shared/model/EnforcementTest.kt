package org.openscreentime.shared.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnforcementTest {

    private val baseline = EnforcementInput(
        locked = false,
        bedtimeStartMinutes = null,
        bedtimeEndMinutes = null,
        nowMinutesOfDay = 600,
        dailyLimitMinutes = 120,
        liveTotalScreenTimeMs = 0,
        dailyWarned = false
    )

    @Test
    fun `nothing to do when well under every limit`() {
        assertEquals(emptyList<EnforcementEvent>(), decideEnforcement(baseline))
    }

    @Test
    fun `bedtime blocks and short-circuits everything else, even a reached daily limit`() {
        val input = baseline.copy(
            bedtimeStartMinutes = 1260,
            bedtimeEndMinutes = 420,
            nowMinutesOfDay = 0,
            liveTotalScreenTimeMs = 999 * 60_000L // way past the daily limit too
        )
        assertEquals(listOf(EnforcementEvent.Block(BlockReason.BEDTIME)), decideEnforcement(input))
    }

    @Test
    fun `parent lock blocks and short-circuits, checked after bedtime but before limits`() {
        val input = baseline.copy(locked = true, liveTotalScreenTimeMs = 999 * 60_000L)
        assertEquals(listOf(EnforcementEvent.Block(BlockReason.PARENT_LOCK)), decideEnforcement(input))
    }

    @Test
    fun `reaching the daily limit blocks and short-circuits app-level checks`() {
        val input = baseline.copy(
            liveTotalScreenTimeMs = 120 * 60_000L,
            foregroundPackage = "com.example",
            appLimitMinutes = 30,
            appUsedMs = 999 * 60_000L // would otherwise also block at the app level
        )
        assertEquals(listOf(EnforcementEvent.Block(BlockReason.DAILY_LIMIT)), decideEnforcement(input))
    }

    @Test
    fun `nearing the daily limit warns once, not a block`() {
        val input = baseline.copy(liveTotalScreenTimeMs = 116 * 60_000L) // 4 min left, threshold is 5
        assertEquals(listOf(EnforcementEvent.Warn(WarnKind.DAILY)), decideEnforcement(input))
    }

    @Test
    fun `does not re-warn for the daily limit once already warned`() {
        val input = baseline.copy(liveTotalScreenTimeMs = 116 * 60_000L, dailyWarned = true)
        assertEquals(emptyList<EnforcementEvent>(), decideEnforcement(input))
    }

    @Test
    fun `reaching an app limit blocks at the app level`() {
        val input = baseline.copy(foregroundPackage = "com.example", appLimitMinutes = 30, appUsedMs = 30 * 60_000L)
        assertEquals(listOf(EnforcementEvent.Block(BlockReason.APP_LIMIT)), decideEnforcement(input))
    }

    @Test
    fun `crossing half of an app limit pauses, only when the caller supplies pause state`() {
        val input = baseline.copy(
            foregroundPackage = "com.example",
            appLimitMinutes = 30,
            appUsedMs = 15 * 60_000L,
            appAlreadyPaused = false
        )
        assertEquals(listOf(EnforcementEvent.Pause("com.example")), decideEnforcement(input))
    }

    @Test
    fun `a caller that never supplies pause state never receives a pause event`() {
        val input = baseline.copy(
            foregroundPackage = "com.example",
            appLimitMinutes = 30,
            appUsedMs = 15 * 60_000L
            // appAlreadyPaused left at its default: null
        )
        assertEquals(emptyList<EnforcementEvent>(), decideEnforcement(input))
    }

    @Test
    fun `does not re-pause an app already paused today`() {
        val input = baseline.copy(
            foregroundPackage = "com.example",
            appLimitMinutes = 30,
            appUsedMs = 15 * 60_000L,
            appAlreadyPaused = true
        )
        assertEquals(emptyList<EnforcementEvent>(), decideEnforcement(input))
    }

    @Test
    fun `nearing an app limit warns, not a block or pause`() {
        val input = baseline.copy(
            foregroundPackage = "com.example",
            appLimitMinutes = 30,
            appUsedMs = 26 * 60_000L, // 4 min left
            appAlreadyPaused = true // already past the pause point, shouldn't re-fire
        )
        assertEquals(listOf(EnforcementEvent.Warn(WarnKind.APP)), decideEnforcement(input))
    }

    @Test
    fun `a daily warning and an app-level block can both fire in the same tick`() {
        // This combination looks odd, but it's the original behavior this was extracted
        // from - the daily-level and app-level checks are independent, not mutually
        // exclusive, and only a *daily* limit reached (not an app one) short-circuits.
        val input = baseline.copy(
            liveTotalScreenTimeMs = 116 * 60_000L,
            foregroundPackage = "com.example",
            appLimitMinutes = 30,
            appUsedMs = 30 * 60_000L
        )
        assertEquals(
            listOf(EnforcementEvent.Warn(WarnKind.DAILY), EnforcementEvent.Block(BlockReason.APP_LIMIT)),
            decideEnforcement(input)
        )
    }

    @Test
    fun `no foreground package means only daily-level checks run`() {
        val input = baseline.copy(foregroundPackage = null, appLimitMinutes = 30, appUsedMs = 999 * 60_000L)
        assertEquals(emptyList<EnforcementEvent>(), decideEnforcement(input))
    }

    @Test
    fun `reaching the daily limit blocks even with no foreground app, eg at the home screen`() {
        // Deliberate: the previous, duplicated implementation only checked bedtime/lock
        // while no app was in the foreground (e.g. sitting on the home screen), not the
        // daily limit - a kid already over their limit there wasn't blocked until they
        // next opened an app. Confirmed as a gap worth closing when this was unified.
        val input = baseline.copy(foregroundPackage = null, liveTotalScreenTimeMs = 120 * 60_000L)
        assertEquals(listOf(EnforcementEvent.Block(BlockReason.DAILY_LIMIT)), decideEnforcement(input))
    }

    @Test
    fun `a foreground app with no configured limit never triggers an app-level event`() {
        val input = baseline.copy(foregroundPackage = "com.example", appLimitMinutes = null, appUsedMs = 999 * 60_000L)
        assertEquals(emptyList<EnforcementEvent>(), decideEnforcement(input))
    }

    // --- Status tier ---

    @Test
    fun `well under every limit is a good status`() {
        assertEquals(
            StatusTier.GOOD,
            computeStatusTier(
                locked = false, isInBedtime = false, dailyLimitMinutes = 120,
                liveTotalScreenTimeMs = 10 * 60_000L, dailyUnlockGoal = null, unlockCount = 0
            )
        )
    }

    @Test
    fun `past 70 percent of the time limit is a caution status`() {
        assertEquals(
            StatusTier.CAUTION,
            computeStatusTier(
                locked = false, isInBedtime = false, dailyLimitMinutes = 100,
                liveTotalScreenTimeMs = 75 * 60_000L, dailyUnlockGoal = null, unlockCount = 0
            )
        )
    }

    @Test
    fun `past 70 percent of the unlock goal is also a caution status`() {
        assertEquals(
            StatusTier.CAUTION,
            computeStatusTier(
                locked = false, isInBedtime = false, dailyLimitMinutes = 100,
                liveTotalScreenTimeMs = 0, dailyUnlockGoal = 10, unlockCount = 8
            )
        )
    }

    @Test
    fun `locked or in bedtime is a stop status regardless of usage`() {
        assertEquals(
            StatusTier.STOP,
            computeStatusTier(
                locked = true, isInBedtime = false, dailyLimitMinutes = 100,
                liveTotalScreenTimeMs = 0, dailyUnlockGoal = null, unlockCount = 0
            )
        )
        assertEquals(
            StatusTier.STOP,
            computeStatusTier(
                locked = false, isInBedtime = true, dailyLimitMinutes = 100,
                liveTotalScreenTimeMs = 0, dailyUnlockGoal = null, unlockCount = 0
            )
        )
    }

    // --- Block screen copy ---

    @Test
    fun `bedtime copy names the wake time when known`() {
        val copy = blockScreenCopy(BlockReason.BEDTIME, bedtimeEndMinutes = 420, lockMessage = "unused", defaultMessage = "unused")
        assertEquals("It's bedtime", copy.title)
        assertTrue(copy.message.contains("7:00 AM"))
    }

    @Test
    fun `bedtime copy falls back gracefully when the wake time is unknown`() {
        val copy = blockScreenCopy(BlockReason.BEDTIME, bedtimeEndMinutes = null, lockMessage = "unused", defaultMessage = "unused")
        assertEquals("Screen time starts again in the morning.", copy.message)
    }

    @Test
    fun `parent lock copy uses the caller-supplied lock message, independent of the default message`() {
        val lockMessage = "A parent has paused screen time. Ask them to resume it."
        val copy = blockScreenCopy(BlockReason.PARENT_LOCK, bedtimeEndMinutes = null, lockMessage = lockMessage, defaultMessage = "unused")
        assertEquals(lockMessage, copy.message)
    }

    @Test
    fun `daily and app limit copy uses the caller-supplied default message`() {
        val kidMessage = "Ask a parent if you need more time."
        assertEquals(kidMessage, blockScreenCopy(BlockReason.DAILY_LIMIT, null, "unused", kidMessage).message)
        assertEquals(kidMessage, blockScreenCopy(BlockReason.APP_LIMIT, null, "unused", kidMessage).message)
    }

    // --- Wire format ---

    @Test
    fun `block reasons round-trip through their wire format`() {
        for (reason in BlockReason.entries) {
            assertEquals(reason, BlockReason.fromWireValue(reason.wireValue))
        }
    }

    @Test
    fun `an unrecognized or missing wire value falls back to APP_LIMIT`() {
        assertEquals(BlockReason.APP_LIMIT, BlockReason.fromWireValue(null))
        assertEquals(BlockReason.APP_LIMIT, BlockReason.fromWireValue("something_else"))
    }
}
