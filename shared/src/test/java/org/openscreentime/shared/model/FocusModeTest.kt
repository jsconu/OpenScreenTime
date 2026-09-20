package org.openscreentime.shared.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusModeTest {
    private val essential = setOf("com.me.app", "com.dialer", "com.messages", "com.home")
    private val on = FocusConfig(enabled = true, allowed = setOf("com.extra"), travelAllowed = setOf("com.airline"))

    @Test
    fun `everything is allowed when focus mode is off`() {
        assertTrue(isFocusAllowed("com.game", FocusConfig(), essential, null, 0))
    }

    @Test
    fun `essentials, system parts and authenticators are always allowed`() {
        assertTrue(isFocusAllowed("com.dialer", on, essential, null, 0))
        assertTrue(isFocusAllowed("com.android.systemui", on, essential, null, 0))
        assertTrue(isFocusAllowed("com.google.android.apps.authenticator2", on, essential, null, 0))
    }

    @Test
    fun `other apps are blocked unless the parent added them`() {
        assertFalse(isFocusAllowed("com.game", on, essential, null, 0))
        assertTrue(isFocusAllowed("com.extra", on, essential, null, 0))
    }

    @Test
    fun `travel extras only count in the travel profile`() {
        assertFalse(isFocusAllowed("com.google.android.apps.maps", on, essential, null, 0))
        assertFalse(isFocusAllowed("com.airline", on, essential, null, 0))
        val travel = on.copy(profile = FocusProfile.TRAVEL)
        assertTrue(isFocusAllowed("com.google.android.apps.maps", travel, essential, null, 0))
        assertTrue(isFocusAllowed("com.airline", travel, essential, null, 0))
        assertFalse(isFocusAllowed("com.game", travel, essential, null, 0))
    }

    @Test
    fun `an all-apps window lifts the restriction until it ends`() {
        assertTrue(isFocusAllowed("com.game", on, essential, openUntilMs = 2_000, nowMs = 1_000))
        assertFalse(isFocusAllowed("com.game", on, essential, openUntilMs = 2_000, nowMs = 3_000))
    }

    @Test
    fun `nothing in front is never blocked`() {
        assertTrue(isFocusAllowed(null, on, essential, null, 0))
    }

    @Test
    fun `the home list puts essentials first, then the allowed apps A to Z`() {
        val list = focusHomePackages(
            installed = setOf("com.dialer", "com.messages", "com.extra", "com.game", "com.beemdevelopment.aegis"),
            essentialOrdered = listOf("com.dialer", "com.messages"),
            config = on
        )
        assertEquals(listOf("com.dialer", "com.messages", "com.beemdevelopment.aegis", "com.extra"), list)
    }

    @Test
    fun `an unknown wire value falls back to standard`() {
        assertEquals(FocusProfile.STANDARD, FocusProfile.fromWireValue("nonsense"))
        assertEquals(FocusProfile.TRAVEL, FocusProfile.fromWireValue("travel"))
    }
}
