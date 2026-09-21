package org.openscreentime.shared.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.util.InMemoryKeyValueStore

/**
 * The rules about who decides what, which are the part that would otherwise drift between the two
 * apps: limits come from the parent's phone, usage comes from the kid's phone, and a request is
 * never an answer to itself.
 */
class NearbySyncTest {

    private val parentWants = ChildProfile(
        id = "self",
        name = "Sam",
        dailyLimitMinutes = 90,
        appLimits = mapOf("com.example.game" to 30),
        locked = true,
        bedtimeStartMinutes = 1260,
        bedtimeEndMinutes = 420
    )

    private val onThePhone = ChildProfile(
        id = "self",
        name = "Sam",
        dailyLimitMinutes = 240,
        locked = false
    )

    @Test
    fun `a usage report is answered with the parents limits`() {
        var kept: NearbyMessage.UsageReport? = null

        val answer = NearbySync.answer(
            message = NearbySync.report("r1", "Sam", DailyStats(date = "2026-09-21", totalScreenTimeMs = 60_000), emptyList()),
            profile = parentWants,
            newId = { "a1" },
            onUsage = { kept = it },
            onRequest = { throw AssertionError("a usage report is not a request") }
        )

        assertNotNull("The parent's phone keeps what was reported", kept)
        val update = answer as NearbyMessage.LimitsUpdate
        assertEquals(90, update.dailyLimitMinutes)
        assertEquals(true, update.locked)
        assertEquals(1260, update.bedtimeStartMinutes)
    }

    @Test
    fun `the phone applies what it is told, not what it already had`() {
        val update = NearbySync.limitsFrom(parentWants, "a2")

        val applied = NearbySync.applyLimits(onThePhone, update)

        assertEquals("A looser local limit must not survive a sync", 90, applied.dailyLimitMinutes)
        assertTrue("A lock set by the parent arrives", applied.locked)
        assertEquals(mapOf("com.example.game" to 30), applied.appLimits)
    }

    @Test
    fun `a limit the parents build did not send is left alone`() {
        val onlyDaily = NearbyMessage.LimitsUpdate(id = "a3", dailyLimitMinutes = 45)
        val phone = onThePhone.copy(alwaysAllowedPackages = listOf("com.android.dialer"), blockedDomains = listOf("x.com"))

        val applied = NearbySync.applyLimits(phone, onlyDaily)

        assertEquals(45, applied.dailyLimitMinutes)
        assertEquals("An older build must not clear what it does not know about", listOf("com.android.dialer"), applied.alwaysAllowedPackages)
        assertEquals(listOf("x.com"), applied.blockedDomains)
    }

    @Test
    fun `a sync never rewrites what this phone measured about itself`() {
        val phone = onThePhone.copy(parentPasscodeHash = "hash", parentPasscodeSalt = "salt")

        val applied = NearbySync.applyLimits(phone, NearbySync.limitsFrom(parentWants, "a4"))

        assertEquals("hash", applied.parentPasscodeHash)
        assertEquals("Sam", applied.name)
    }

    @Test
    fun `asking for more time is acknowledged, never granted by the asking`() {
        var raised: NearbyMessage? = null

        val answer = NearbySync.answer(
            message = NearbyMessage.MoreTimeRequest("r5", minutes = 15, childName = "Sam"),
            profile = parentWants,
            newId = { "a5" },
            onUsage = { throw AssertionError("a request is not a usage report") },
            onRequest = { raised = it }
        )

        assertNotNull("A parent has to see it", raised)
        assertFalse(
            "A phone must not grant itself time by asking while a parent is asleep",
            (answer as NearbyMessage.Answer).granted
        )
        assertNull(answer.minutes)
    }

    @Test
    fun `a parents phone ignores messages only it is supposed to send`() {
        val ignored = NearbySync.answer(
            message = NearbyMessage.LimitsUpdate(id = "r6", dailyLimitMinutes = 5),
            profile = parentWants,
            newId = { "a6" },
            onUsage = { throw AssertionError() },
            onRequest = { throw AssertionError() }
        )

        assertNull("Nothing should be able to push limits INTO a parent's phone", ignored)
    }

    @Test
    fun `a link is remembered, and forgetting it is complete`() {
        val store = NearbyLinkStore(InMemoryKeyValueStore())
        val link = NearbyLink(NearbyEnvelope.newKey(), "Mum's phone")

        store.save(link)
        store.saveReport(
            NearbyMessage.UsageReport("r7", "Sam", DailyStats(date = "2026-09-21", totalScreenTimeMs = 1000)),
            atMs = 1_700_000_000_000
        )

        assertEquals(link, store.link())
        assertEquals("Sam", store.childName())
        assertEquals(1_700_000_000_000, store.lastSyncedAtMs)

        store.forget()

        assertNull("Without the key neither phone can reach the other", store.link())
        assertNull("A forgotten link leaves no usage behind either", store.lastStats())
        assertEquals(0, store.lastSyncedAtMs)
    }

    @Test
    fun `an unlinked phone reports no sync rather than a misleading zero date`() {
        val store = NearbyLinkStore(InMemoryKeyValueStore())

        assertFalse(store.isLinked)
        assertEquals("Never synced is 0, which a screen must show as 'not yet'", 0, store.lastSyncedAtMs)
    }
}
