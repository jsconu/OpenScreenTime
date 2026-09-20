package org.openscreentime.shared.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins how each app's limits and today's usage become the snapshot the enforcement engine sees. The kid app and the
 * parent's own phone share this one builder, so these cases fix the two shapes: a child's phone (friction pause, a
 * parent's "more time" grant) and the parent's own (neither).
 */
class EnforcementInputsTest {

    private class FakeSettings(
        override val locked: Boolean = false,
        override val bedtimeStartMinutes: Int? = null,
        override val bedtimeEndMinutes: Int? = null,
        override val dailyLimitMinutes: Int = 120,
        private val appLimits: Map<String, Int> = emptyMap(),
        override val temporaryUnlockUntilMs: Long? = null,
        override val alwaysAllowedPackages: Set<String> = emptySet(),
        override val relockAtMs: Long? = null,
        override var foregroundPackage: String? = null
    ) : EnforcementSettings {
        override fun appLimitMinutes(packageName: String) = appLimits[packageName]
    }

    private class FakeUsage(
        override val liveTotalScreenTimeMs: Long = 0,
        override val dailyWarned: Boolean = false,
        override val appUsageMs: Map<String, Long> = emptyMap(),
        override val warnedApps: Set<String> = emptySet(),
        override val pausedApps: Set<String> = emptySet()
    ) : UsageReader

    private fun build(
        settings: EnforcementSettings,
        usage: UsageReader,
        pkg: String? = "com.example.game",
        supportsPause: Boolean = false
    ) = buildEnforcementInput(settings, usage, pkg, nowMs = 1_000L, nowMinutesOfDay = 600, supportsPause = supportsPause)

    @Test
    fun `settings and usage are copied into the snapshot`() {
        val input = build(
            FakeSettings(
                locked = true, bedtimeStartMinutes = 1260, bedtimeEndMinutes = 420, dailyLimitMinutes = 90,
                appLimits = mapOf("com.example.game" to 30), alwaysAllowedPackages = setOf("com.example.phone")
            ),
            FakeUsage(
                liveTotalScreenTimeMs = 5_000, dailyWarned = true,
                appUsageMs = mapOf("com.example.game" to 4_000), warnedApps = setOf("com.example.game")
            )
        )
        assertTrue(input.locked)
        assertEquals(1260, input.bedtimeStartMinutes)
        assertEquals(420, input.bedtimeEndMinutes)
        assertEquals(90, input.dailyLimitMinutes)
        assertEquals(5_000L, input.liveTotalScreenTimeMs)
        assertTrue(input.dailyWarned)
        assertEquals("com.example.game", input.foregroundPackage)
        assertEquals(30, input.appLimitMinutes)
        assertEquals(4_000L, input.appUsedMs)
        assertTrue(input.appWarned)
        assertEquals(setOf("com.example.phone"), input.alwaysAllowedPackages)
        assertEquals(600, input.nowMinutesOfDay)
        assertEquals(1_000L, input.nowMs)
    }

    @Test
    fun `a phone without a pause screen never supplies pause state`() {
        val input = build(
            FakeSettings(appLimits = mapOf("com.example.game" to 30)),
            FakeUsage(pausedApps = setOf("com.example.game")),
            supportsPause = false
        )
        assertNull(input.appAlreadyPaused)
    }

    @Test
    fun `a phone with a pause screen reports whether this app already paused today`() {
        val settings = FakeSettings(appLimits = mapOf("com.example.game" to 30))
        assertFalse(build(settings, FakeUsage(), supportsPause = true).appAlreadyPaused!!)
        assertTrue(
            build(settings, FakeUsage(pausedApps = setOf("com.example.game")), supportsPause = true).appAlreadyPaused!!
        )
    }

    @Test
    fun `nothing in the foreground means no app-level values`() {
        val input = build(
            FakeSettings(appLimits = mapOf("com.example.game" to 30)),
            FakeUsage(appUsageMs = mapOf("com.example.game" to 4_000)),
            pkg = null,
            supportsPause = true
        )
        assertNull(input.foregroundPackage)
        assertNull(input.appLimitMinutes)
        assertEquals(0L, input.appUsedMs)
        assertFalse(input.appWarned)
        assertNull(input.appAlreadyPaused)
    }

    @Test
    fun `an app with no limit has none in the snapshot, whatever it has used`() {
        val input = build(FakeSettings(), FakeUsage(appUsageMs = mapOf("com.example.game" to 4_000)))
        assertNull(input.appLimitMinutes)
        assertEquals(4_000L, input.appUsedMs)
    }

    @Test
    fun `a parent's more-time grant reaches the engine and skips a reached limit`() {
        val input = build(
            FakeSettings(dailyLimitMinutes = 60, temporaryUnlockUntilMs = 5_000L),
            FakeUsage(liveTotalScreenTimeMs = 61 * 60_000L)
        )
        assertEquals(5_000L, input.temporaryUnlockUntilMs)
        assertTrue(decideEnforcement(input).isEmpty())
    }

    @Test
    fun `the parent's own phone has no more-time grant, so a reached limit blocks`() {
        val input = build(
            FakeSettings(dailyLimitMinutes = 60),
            FakeUsage(liveTotalScreenTimeMs = 61 * 60_000L)
        )
        assertNull(input.temporaryUnlockUntilMs)
        assertEquals(listOf<EnforcementEvent>(EnforcementEvent.Block(BlockReason.DAILY_LIMIT)), decideEnforcement(input))
    }

    @Test
    fun `an app over half its limit pauses only on a phone with a pause screen`() {
        val settings = FakeSettings(appLimits = mapOf("com.example.game" to 30))
        val usage = FakeUsage(appUsageMs = mapOf("com.example.game" to 20 * 60_000L))
        assertEquals(
            listOf<EnforcementEvent>(EnforcementEvent.Pause("com.example.game")),
            decideEnforcement(build(settings, usage, supportsPause = true))
        )
        assertTrue(decideEnforcement(build(settings, usage, supportsPause = false)).isEmpty())
    }
}
