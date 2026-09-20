package org.openscreentime.shared.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins what calm mode may take out of the shade. A wrong rule here hides a call or a sign-in code. */
class NotificationHidingTest {

    private val own = "org.openscreentime.parent"
    private val essential = setOf("com.example.dialer", "com.example.messages", "com.example.keyboard")

    private fun hide(pkg: String, category: String? = null, ongoing: Boolean = false) =
        shouldHideNotification(NotificationFacts(pkg, category, ongoing), own, essential)

    @Test
    fun `an ordinary app's notification is hidden`() {
        assertTrue(hide("com.example.social"))
        assertTrue(hide("com.example.social", category = "social"))
        assertTrue(hide("com.example.shop", category = "promo"))
    }

    @Test
    fun `this app's own notifications are never hidden`() {
        assertFalse(hide(own))
    }

    @Test
    fun `ongoing notifications are never hidden`() {
        assertFalse(hide("com.example.music", ongoing = true))
        assertFalse(hide("com.example.timer", category = "progress", ongoing = true))
    }

    @Test
    fun `the phone's essential apps are never hidden`() {
        assertFalse(hide("com.example.dialer"))
        assertFalse(hide("com.example.messages", category = "msg"))
        assertFalse(hide("com.example.keyboard"))
    }

    @Test
    fun `sign-in code apps are never hidden`() {
        assertFalse(hide("com.google.android.apps.authenticator2"))
        assertFalse(hide("com.authy.authy"))
    }

    @Test
    fun `system components are never hidden`() {
        assertFalse(hide("android"))
        assertFalse(hide("com.android.systemui"))
        assertFalse(hide("com.android.phone"))
    }

    @Test
    fun `calls, alarms, navigation, system and error notifications are never hidden, from any app`() {
        for (category in listOf("call", "alarm", "navigation", "sys", "err", "transport", "missed_call")) {
            assertFalse("category $category", hide("com.example.videochat", category = category))
        }
    }

    @Test
    fun `a message from a chat app is hidden, since only the phone's own texting app is essential`() {
        assertTrue(hide("com.example.chat", category = "msg"))
    }
}
