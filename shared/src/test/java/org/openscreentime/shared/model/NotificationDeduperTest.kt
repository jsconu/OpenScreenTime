package org.openscreentime.shared.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDeduperTest {

    @Test
    fun `the first post counts and an identical re-post does not`() {
        val d = NotificationDeduper()
        assertTrue(d.shouldCount("k1", "Sam", "hello"))
        assertFalse(d.shouldCount("k1", "Sam", "hello"))
    }

    @Test
    fun `a new message on the same conversation counts`() {
        val d = NotificationDeduper()
        assertTrue(d.shouldCount("k1", "Sam", "hello"))
        assertTrue(d.shouldCount("k1", "Sam", "are you there?"))
    }

    @Test
    fun `different notifications are counted independently`() {
        val d = NotificationDeduper()
        assertTrue(d.shouldCount("k1", "Sam", "hello"))
        assertTrue(d.shouldCount("k2", "Sam", "hello"))
    }

    @Test
    fun `it only remembers a bounded number of keys`() {
        val d = NotificationDeduper(capacity = 2)
        d.shouldCount("a", "t", "x")
        d.shouldCount("b", "t", "x")
        d.shouldCount("c", "t", "x") // evicts "a"
        assertTrue("an evicted key is treated as new", d.shouldCount("a", "t", "x"))
    }
}
