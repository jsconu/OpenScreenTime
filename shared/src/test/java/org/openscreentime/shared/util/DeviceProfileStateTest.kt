package org.openscreentime.shared.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openscreentime.shared.model.ChildProfile

/** Pins how a phone's copy of its profile is taken, saved and restored - and that the saved keys never change. */
class DeviceProfileStateTest {

    private val profile = ChildProfile(
        dailyLimitMinutes = 90,
        appLimits = mapOf("com.example.game" to 30, "com.example.video" to 45),
        locked = false,
        parentPasscodeHash = "hash",
        parentPasscodeSalt = "salt",
        dailyUnlockGoal = 40,
        bedtimeStartMinutes = 1260,
        bedtimeEndMinutes = 420,
        blockedDomains = listOf("example.com", "other.org"),
        temporaryUnlockUntilMs = 123_456L,
        alwaysAllowedPackages = listOf("com.example.phone"),
        alwaysAllowedContacts = listOf("+15550100"),
        trackUnlocks = true,
        trackNotifications = true,
        trackWebsites = true,
        excludedFromTotalPackages = listOf("com.example.audiobook")
    )

    private fun fresh() = DeviceProfileState("test")

    @Test
    fun `a received profile is copied into the phone's state`() {
        val state = fresh()
        state.copyFrom(profile)
        assertEquals(90, state.dailyLimitMinutes)
        assertEquals(mapOf("com.example.game" to 30, "com.example.video" to 45), state.limitsCache)
        assertEquals(40, state.dailyUnlockGoal)
        assertEquals(1260, state.bedtimeStartMinutes)
        assertEquals(420, state.bedtimeEndMinutes)
        assertEquals(listOf("example.com", "other.org"), state.blockedDomains)
        assertEquals(123_456L, state.temporaryUnlockUntilMs)
        assertEquals(setOf("com.example.phone"), state.alwaysAllowedPackages)
        assertEquals(listOf("+15550100"), state.alwaysAllowedContacts)
        assertTrue(state.trackUnlocks && state.trackNotifications && state.trackWebsites)
        assertEquals("hash", state.parentPasscodeHash)
        assertEquals("salt", state.parentPasscodeSalt)
        assertEquals(setOf("com.example.audiobook"), state.excludedFromTotalPackages)
        assertFalse(state.lockedCache)
    }

    @Test
    fun `a lock cancels a pending timed re-lock, but an unlocked profile keeps it`() {
        val state = fresh()
        state.relockAtMs = 999L
        state.copyFrom(profile.copy(locked = false))
        assertEquals(999L, state.relockAtMs)
        state.copyFrom(profile.copy(locked = true))
        assertNull(state.relockAtMs)
        assertTrue(state.lockedCache)
    }

    @Test
    fun `what is saved can be restored exactly`() {
        val saved = fresh().also { it.copyFrom(profile); it.relockAtMs = 777L }.encode()
        val restored = fresh()
        restored.decode(saved)
        assertEquals(mapOf("com.example.game" to 30, "com.example.video" to 45), restored.limitsCache)
        assertEquals(90, restored.dailyLimitMinutes)
        assertEquals(40, restored.dailyUnlockGoal)
        assertEquals(1260, restored.bedtimeStartMinutes)
        assertEquals(420, restored.bedtimeEndMinutes)
        assertEquals(listOf("example.com", "other.org"), restored.blockedDomains)
        assertEquals(123_456L, restored.temporaryUnlockUntilMs)
        assertEquals(setOf("com.example.phone"), restored.alwaysAllowedPackages)
        assertEquals(listOf("+15550100"), restored.alwaysAllowedContacts)
        assertTrue(restored.trackUnlocks && restored.trackNotifications && restored.trackWebsites)
        assertEquals(777L, restored.relockAtMs)
        assertEquals(setOf("com.example.audiobook"), restored.excludedFromTotalPackages)
        assertEquals("hash", restored.parentPasscodeHash)
        assertEquals("salt", restored.parentPasscodeSalt)
    }

    @Test
    fun `unset values survive a save and restore as unset`() {
        val restored = fresh()
        restored.decode(fresh().also { it.copyFrom(ChildProfile()) }.encode())
        assertNull(restored.dailyUnlockGoal)
        assertNull(restored.bedtimeStartMinutes)
        assertNull(restored.bedtimeEndMinutes)
        assertNull(restored.temporaryUnlockUntilMs)
        assertNull(restored.relockAtMs)
        assertNull(restored.parentPasscodeHash)
        assertTrue(restored.limitsCache.isEmpty())
        assertTrue(restored.blockedDomains.isEmpty())
    }

    @Test
    fun `nothing saved yet leaves the defaults alone`() {
        val state = fresh()
        state.decode(emptyMap<String, Any?>())
        assertEquals(Int.MAX_VALUE, state.dailyLimitMinutes)
        assertFalse(state.lockedCache)
        assertNull(state.bedtimeStartMinutes)
    }

    @Test
    fun `a malformed saved limit is skipped, not fatal`() {
        val state = fresh()
        state.decode(mapOf("has" to true, "limits" to "com.ok=20\nbroken\nalso=notanumber\n=5"))
        assertEquals(mapOf("com.ok" to 20), state.limitsCache)
    }

    @Test
    fun `reset returns everything to the defaults`() {
        val state = fresh()
        state.copyFrom(profile.copy(locked = true))
        state.foregroundPackage = "com.example.game"
        state.reset()
        assertEquals(Int.MAX_VALUE, state.dailyLimitMinutes)
        assertFalse(state.lockedCache)
        assertNull(state.foregroundPackage)
        assertTrue(state.limitsCache.isEmpty())
        assertFalse(state.trackWebsites)
    }

    @Test
    fun `the saved keys are the ones already on people's phones`() {
        assertEquals(
            listOf(
                "has", "limits", "daily", "goal", "bedStart", "bedEnd", "domains", "tempUnlock", "packages",
                "contacts", "trackUnlocks", "trackNotifications", "trackWebsites", "locked", "relockAt", "pcHash", "pcSalt", "excluded", "parentUnlock"
            ),
            fresh().encode().keys.toList()
        )
    }

    @Test
    fun `a parent's passcode unlock survives a profile update and a save and restore`() {
        val state = fresh()
        state.parentUnlockUntilMs = 555L
        state.copyFrom(profile)
        assertEquals(555L, state.parentUnlockUntilMs)
        val restored = fresh()
        restored.decode(state.encode())
        assertEquals(555L, restored.parentUnlockUntilMs)
        restored.reset()
        assertNull(restored.parentUnlockUntilMs)
    }

    @Test
    fun `the guard uses the later of a more-time grant and a passcode unlock`() {
        val state = fresh()
        state.copyFrom(profile.copy(temporaryUnlockUntilMs = 1_000L))
        val settings = DeviceProfileSettings(state)
        assertEquals(1_000L, settings.temporaryUnlockUntilMs)
        state.parentUnlockUntilMs = 5_000L
        assertEquals(5_000L, settings.temporaryUnlockUntilMs)
        state.parentUnlockUntilMs = 500L
        assertEquals(1_000L, settings.temporaryUnlockUntilMs)
        state.parentUnlockUntilMs = null
        state.copyFrom(profile.copy(temporaryUnlockUntilMs = null))
        assertNull(settings.temporaryUnlockUntilMs)
    }

    @Test
    fun `the foreground guard sees the phone's current limits`() {
        val state = fresh()
        state.copyFrom(profile)
        val settings = DeviceProfileSettings(state)
        assertEquals(90, settings.dailyLimitMinutes)
        assertEquals(30, settings.appLimitMinutes("com.example.game"))
        assertNull(settings.appLimitMinutes("com.example.unlisted"))
        assertEquals(123_456L, settings.temporaryUnlockUntilMs)
        settings.foregroundPackage = "com.example.game"
        assertEquals("com.example.game", state.foregroundPackage)
    }
}
