package org.openscreentime.shared.model

import org.junit.Assert.assertTrue
import org.junit.Test

class AlternativeActivitiesTest {

    @Test
    fun `random suggestion always comes from the list`() {
        repeat(50) {
            assertTrue(randomAlternativeActivity() in ALTERNATIVE_ACTIVITIES)
        }
    }

    @Test
    fun `no tip or suggestion sends a person to a phone or screen`() {
        // The whole point of these is something to do *instead of* a screen (see #16, #17), so none may
        // mention a phone, or ask for a call, text, video, music, or similar.
        val banned = Regex(
            """\b(phones?|devices?|tv|text|texting|call|video|stream|music|playlist|podcast|app|online)\b""",
            RegexOption.IGNORE_CASE
        )
        val all = ALTERNATIVE_ACTIVITIES + KID_DAILY_TIPS + PARENT_DAILY_TIPS
        val offenders = all.filter { banned.containsMatchIn(it) }
        assertTrue("These involve a device: $offenders", offenders.isEmpty())
    }
}
