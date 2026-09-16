package org.openscreentime.shared.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainBlockingTest {

    @Test
    fun `an exact match is blocked`() {
        assertTrue(isDomainBlocked("tiktok.com", listOf("tiktok.com")))
    }

    @Test
    fun `a subdomain of a blocked domain is blocked`() {
        assertTrue(isDomainBlocked("m.tiktok.com", listOf("tiktok.com")))
        assertTrue(isDomainBlocked("www.tiktok.com", listOf("tiktok.com")))
        assertTrue(isDomainBlocked("a.b.tiktok.com", listOf("tiktok.com")))
    }

    @Test
    fun `a different domain that merely shares a suffix is not blocked`() {
        // "nottiktok.com" ends with "tiktok.com" as a raw string, but isn't a real subdomain.
        assertFalse(isDomainBlocked("nottiktok.com", listOf("tiktok.com")))
    }

    @Test
    fun `an unrelated domain is not blocked`() {
        assertFalse(isDomainBlocked("example.com", listOf("tiktok.com")))
    }

    @Test
    fun `an empty blocklist blocks nothing`() {
        assertFalse(isDomainBlocked("tiktok.com", emptyList()))
    }

    @Test
    fun `matching is case-insensitive`() {
        assertTrue(isDomainBlocked("TikTok.com", listOf("tiktok.com")))
        assertTrue(isDomainBlocked("tiktok.com", listOf("TikTok.COM")))
    }

    @Test
    fun `a trailing dot on either side is ignored`() {
        assertTrue(isDomainBlocked("tiktok.com.", listOf("tiktok.com")))
        assertTrue(isDomainBlocked("tiktok.com", listOf("tiktok.com.")))
    }

    @Test
    fun `a blank host is never blocked`() {
        assertFalse(isDomainBlocked("", listOf("tiktok.com")))
        assertFalse(isDomainBlocked("   ", listOf("tiktok.com")))
    }

    // --- addBlockedDomain ---

    @Test
    fun `adding a new domain normalizes and appends it`() {
        assertEquals(listOf("tiktok.com"), addBlockedDomain(emptyList(), "  TikTok.COM. "))
    }

    @Test
    fun `adding an already-present domain is a no-op`() {
        assertEquals(listOf("tiktok.com"), addBlockedDomain(listOf("tiktok.com"), "TikTok.com"))
    }

    @Test
    fun `adding a blank domain is a no-op`() {
        assertEquals(listOf("tiktok.com"), addBlockedDomain(listOf("tiktok.com"), "   "))
    }

    // --- removeBlockedDomain ---

    @Test
    fun `removing a domain drops only that entry`() {
        assertEquals(
            listOf("tiktok.com"),
            removeBlockedDomain(listOf("tiktok.com", "example.com"), "example.com")
        )
    }

    @Test
    fun `removing a domain that isn't present is a no-op`() {
        assertEquals(listOf("tiktok.com"), removeBlockedDomain(listOf("tiktok.com"), "example.com"))
    }
}
