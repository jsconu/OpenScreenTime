package org.openscreentime.shared.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDigestTest {

    private fun entry(
        key: String = "k1",
        packageName: String = "com.messages",
        appLabel: String = "Messages",
        title: String = "Mom",
        text: String = "On my way",
        postedAtMs: Long = 1_000
    ) = DigestNotification(key, packageName, appLabel, title, text, postedAtMs)

    @Test
    fun `own-package notifications are excluded`() {
        assertFalse(
            shouldIncludeInDigest(
                packageName = "org.openscreentime.kid",
                ownPackageName = "org.openscreentime.kid",
                isOngoing = false,
                isGroupSummary = false,
                title = "Screen time monitoring is on",
                text = "tracking"
            )
        )
    }

    @Test
    fun `ongoing and group-summary notifications are excluded`() {
        assertFalse(
            shouldIncludeInDigest("com.chat", "org.openscreentime.kid", isOngoing = true, isGroupSummary = false, title = "Hi", text = "")
        )
        assertFalse(
            shouldIncludeInDigest("com.chat", "org.openscreentime.kid", isOngoing = false, isGroupSummary = true, title = "Hi", text = "")
        )
    }

    @Test
    fun `blank title and text are excluded`() {
        assertFalse(
            shouldIncludeInDigest("com.chat", "org.openscreentime.kid", isOngoing = false, isGroupSummary = false, title = "  ", text = "")
        )
    }

    @Test
    fun `a normal notification from another app is included`() {
        assertTrue(
            shouldIncludeInDigest("com.chat", "org.openscreentime.kid", isOngoing = false, isGroupSummary = false, title = "Sam", text = "hey")
        )
    }

    @Test
    fun `upsert replaces the same key instead of duplicating`() {
        val first = entry(key = "n1", text = "old", postedAtMs = 1)
        val updated = entry(key = "n1", text = "new", postedAtMs = 2)
        val result = upsertDigestEntry(listOf(first), updated)
        assertEquals(1, result.size)
        assertEquals("new", result.single().text)
    }

    @Test
    fun `upsert drops the oldest rows once the cap is hit`() {
        val existing = (1..MAX_DIGEST_ENTRIES).map {
            entry(key = "k$it", postedAtMs = it.toLong())
        }
        val newest = entry(key = "newest", postedAtMs = 10_000)
        val result = upsertDigestEntry(existing, newest)
        assertEquals(MAX_DIGEST_ENTRIES, result.size)
        assertTrue(result.any { it.key == "newest" })
        assertFalse(result.any { it.key == "k1" })
    }

    @Test
    fun `groups are newest-app first and rows inside a group are newest first`() {
        val entries = listOf(
            entry(key = "m1", packageName = "com.msg", appLabel = "Messages", postedAtMs = 10),
            entry(key = "y1", packageName = "com.yt", appLabel = "YouTube", postedAtMs = 30),
            entry(key = "m2", packageName = "com.msg", appLabel = "Messages", postedAtMs = 20)
        )
        val groups = groupDigestByApp(entries)
        assertEquals(listOf("com.yt", "com.msg"), groups.map { it.packageName })
        assertEquals(listOf("m2", "m1"), groups[1].entries.map { it.key })
    }
}
