package org.openscreentime.shared.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallBlockingTest {

    // --- isCallAllowedDuringBedtime ---

    @Test
    fun `outside bedtime every number is allowed`() {
        assertTrue(
            isCallAllowedDuringBedtime(
                phoneNumber = "5551234567",
                nowMinutesOfDay = 720,
                bedtimeStartMinutes = null,
                bedtimeEndMinutes = null,
                alwaysAllowedContacts = emptyList()
            )
        )
    }

    @Test
    fun `during bedtime an always-allowed number is allowed`() {
        assertTrue(
            isCallAllowedDuringBedtime(
                phoneNumber = "+15551234567",
                nowMinutesOfDay = 0,
                bedtimeStartMinutes = 1260,
                bedtimeEndMinutes = 420,
                alwaysAllowedContacts = listOf("(555) 123-4567")
            )
        )
    }

    @Test
    fun `during bedtime any other number is blocked`() {
        assertFalse(
            isCallAllowedDuringBedtime(
                phoneNumber = "5559990000",
                nowMinutesOfDay = 0,
                bedtimeStartMinutes = 1260,
                bedtimeEndMinutes = 420,
                alwaysAllowedContacts = listOf("5551234567")
            )
        )
    }

    @Test
    fun `during bedtime an unknown or missing number is blocked`() {
        assertFalse(
            isCallAllowedDuringBedtime(
                phoneNumber = null,
                nowMinutesOfDay = 0,
                bedtimeStartMinutes = 1260,
                bedtimeEndMinutes = 420,
                alwaysAllowedContacts = listOf("5551234567")
            )
        )
    }

    @Test
    fun `during bedtime an empty always-allowed list blocks everyone`() {
        assertFalse(
            isCallAllowedDuringBedtime(
                phoneNumber = "5551234567",
                nowMinutesOfDay = 0,
                bedtimeStartMinutes = 1260,
                bedtimeEndMinutes = 420,
                alwaysAllowedContacts = emptyList()
            )
        )
    }

    // --- normalizePhoneNumber ---

    @Test
    fun `normalization strips formatting and keeps the last 10 digits`() {
        assertEquals("5551234567", normalizePhoneNumber("+1 (555) 123-4567"))
        assertEquals("5551234567", normalizePhoneNumber("555-123-4567"))
        assertEquals("5551234567", normalizePhoneNumber("5551234567"))
    }

    @Test
    fun `a short number is kept as-is, not padded or truncated`() {
        assertEquals("911", normalizePhoneNumber("911"))
    }

    // --- addAllowedContact ---

    @Test
    fun `adding a new number appends it unmodified, not normalized`() {
        assertEquals(listOf("(555) 123-4567"), addAllowedContact(emptyList(), "(555) 123-4567"))
    }

    @Test
    fun `adding a number that already normalizes to an existing entry is a no-op`() {
        assertEquals(listOf("5551234567"), addAllowedContact(listOf("5551234567"), "+1 (555) 123-4567"))
    }

    @Test
    fun `adding a blank number is a no-op`() {
        assertEquals(listOf("5551234567"), addAllowedContact(listOf("5551234567"), "   "))
    }

    // --- removeAllowedContact ---

    @Test
    fun `removing a number drops only that entry`() {
        assertEquals(
            listOf("5551234567"),
            removeAllowedContact(listOf("5551234567", "5559990000"), "5559990000")
        )
    }

    @Test
    fun `removing a number that isn't present is a no-op`() {
        assertEquals(listOf("5551234567"), removeAllowedContact(listOf("5551234567"), "5559990000"))
    }
}
