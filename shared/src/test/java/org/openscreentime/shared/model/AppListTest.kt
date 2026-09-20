package org.openscreentime.shared.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the per-app lists: the Firestore field names never change, and each list reads and writes its own field. */
class AppListTest {

    @Test
    fun `the field names are the ones already stored in Firestore`() {
        assertEquals("alwaysAllowedPackages", AppList.ALWAYS_ALLOWED.field)
        assertEquals("focusAllowedPackages", AppList.FOCUS_ALLOWED.field)
        assertEquals("travelAllowedPackages", AppList.TRAVEL_ALLOWED.field)
        assertEquals("excludedFromTotalPackages", AppList.EXCLUDED_FROM_TOTAL.field)
    }

    @Test
    fun `each list reads its own field of the profile`() {
        val profile = ChildProfile(
            alwaysAllowedPackages = listOf("a"),
            focusAllowedPackages = listOf("f"),
            travelAllowedPackages = listOf("t"),
            excludedFromTotalPackages = listOf("x")
        )
        assertEquals(listOf("a"), profile.packages(AppList.ALWAYS_ALLOWED))
        assertEquals(listOf("f"), profile.packages(AppList.FOCUS_ALLOWED))
        assertEquals(listOf("t"), profile.packages(AppList.TRAVEL_ALLOWED))
        assertEquals(listOf("x"), profile.packages(AppList.EXCLUDED_FROM_TOTAL))
    }

    @Test
    fun `every list is saved under its field and read back`() {
        val profile = ChildProfile(
            alwaysAllowedPackages = listOf("a1", "a2"),
            focusAllowedPackages = listOf("f"),
            travelAllowedPackages = listOf("t"),
            excludedFromTotalPackages = listOf("x")
        )
        val map = profile.toMap()
        for (list in AppList.entries) {
            assertEquals(profile.packages(list), map[list.field])
        }
        val back = ChildProfile.fromMap("id", map)
        for (list in AppList.entries) {
            assertEquals(profile.packages(list), back.packages(list))
        }
    }

    @Test
    fun `a missing or malformed list reads as empty`() {
        val back = ChildProfile.fromMap("id", mapOf("focusAllowedPackages" to "not a list", "alwaysAllowedPackages" to listOf("a", 5)))
        assertTrue(back.packages(AppList.FOCUS_ALLOWED).isEmpty())
        assertTrue(back.packages(AppList.TRAVEL_ALLOWED).isEmpty())
        assertEquals(listOf("a"), back.packages(AppList.ALWAYS_ALLOWED))
    }
}
