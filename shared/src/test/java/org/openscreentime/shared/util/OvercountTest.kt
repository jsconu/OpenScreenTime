package org.openscreentime.shared.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openscreentime.shared.model.WindowKind
import org.openscreentime.shared.model.classifyWindow
import java.util.Calendar

/**
 * A parent reported fourteen hours of screen time in a day, and a browser that went uncounted. These
 * pin the three causes found: a session whose end was never heard, a session carried across midnight,
 * and windows that are not apps being credited as if they were.
 */
class OvercountTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    /** Real local times, so the ledger's own idea of "midnight" lines up with the test's. */
    private fun at(day: Int, hourOfDay: Int, minuteOfHour: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, day, hourOfDay, minuteOfHour, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private class Phone(var nowMs: Long, var date: String, var screenOn: Boolean = true)

    private fun ledger(phone: Phone) = DayLedger(
        InMemoryKeyValueStore(),
        nowMs = { phone.nowMs },
        todayString = { phone.date },
        isScreenOn = { phone.screenOn }
    )

    @Test
    fun `a session whose screen-off was never heard does not count while the screen is off`() {
        val phone = Phone(at(22, 21), "2026-09-22")
        val ledger = ledger(phone)
        ledger.startSession()
        phone.nowMs += 20 * minute

        // The screen goes off, but the broadcast is missed - the service that hears it was killed.
        phone.screenOn = false
        phone.nowMs += 9 * hour

        assertTrue(
            "Nine locked hours must not appear as screen time; got ${ledger.liveTotalScreenTimeMs / hour}h",
            ledger.liveTotalScreenTimeMs < hour
        )
    }

    @Test
    fun `a session open at midnight counts on the new day only from midnight`() {
        val phone = Phone(at(22, 23, 30), "2026-09-22")
        val ledger = ledger(phone)
        ledger.startSession()

        // Still in use at 00:20.
        phone.nowMs = at(23, 0, 20)
        phone.date = "2026-09-23"

        assertEquals(
            "Only the twenty minutes after midnight belong to today",
            20 * minute,
            ledger.liveTotalScreenTimeMs
        )
    }

    @Test
    fun `a new day never starts with yesterday's evening already on the clock`() {
        val phone = Phone(at(22, 18), "2026-09-22")
        val ledger = ledger(phone)
        ledger.startSession()

        // Screen-off unheard, phone off overnight, first look at 07:00 with the screen on.
        phone.nowMs = at(23, 7)
        phone.date = "2026-09-23"

        assertTrue(
            "At most the seven hours since midnight, never the thirteen since 18:00; got ${ledger.liveTotalScreenTimeMs / hour}h",
            ledger.liveTotalScreenTimeMs <= 7 * hour
        )
    }

    @Test
    fun `an unlock after a missed screen-off starts fresh rather than adding the gap`() {
        val phone = Phone(at(22, 9), "2026-09-22")
        val ledger = ledger(phone)
        ledger.startSession()
        phone.nowMs += 5 * minute
        // Missed screen-off, then unlocked again four hours later.
        phone.nowMs += 4 * hour
        ledger.startSession()
        phone.nowMs += 10 * minute

        assertEquals(10 * minute, ledger.liveTotalScreenTimeMs)
    }

    // --- whose time is it ---

    private val home = setOf("com.google.android.apps.nexuslauncher")
    private val overlays = setOf("com.google.android.inputmethod.latin", "com.android.systemui")
    private fun kind(pkg: String) = classifyWindow(pkg, "org.openscreentime.parent", home, overlays)

    @Test
    fun `the keyboard does not take time away from the browser it is typing into`() {
        assertEquals(WindowKind.OVERLAY, kind("com.google.android.inputmethod.latin"))
    }

    @Test
    fun `pulling down the notification shade is not leaving the app`() {
        assertEquals(WindowKind.OVERLAY, kind("com.android.systemui"))
    }

    @Test
    fun `the home screen is screen time but nobody's app time`() {
        assertEquals(WindowKind.HOME, kind("com.google.android.apps.nexuslauncher"))
    }

    @Test
    fun `a browser is an app`() {
        assertEquals(WindowKind.APP, kind("com.android.chrome"))
        assertEquals(WindowKind.APP, kind("org.mozilla.firefox"))
    }

    @Test
    fun `this app never counts against anyone`() {
        assertEquals(WindowKind.SELF, kind("org.openscreentime.parent"))
    }
}
