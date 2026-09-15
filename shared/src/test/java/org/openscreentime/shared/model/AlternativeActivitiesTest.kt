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
}
