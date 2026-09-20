package org.openscreentime.shared.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteTrackingTest {

    @Test
    fun `a subdomain reduces to the site`() {
        assertEquals("youtube.com", registrableDomain("m.youtube.com"))
        assertEquals("example.org", registrableDomain("WWW.Example.org."))
    }

    @Test
    fun `two-part suffixes keep three labels`() {
        assertEquals("bbc.co.uk", registrableDomain("news.bbc.co.uk"))
        assertEquals("abc.net.au", registrableDomain("www.abc.net.au"))
    }

    @Test
    fun `things that are not sites give null`() {
        assertNull(registrableDomain(""))
        assertNull(registrableDomain("localhost"))
        assertNull(registrableDomain("192.168.1.10"))
        assertNull(registrableDomain("4.3.2.1.in-addr.arpa"))
        assertNull(registrableDomain("a..b.com"))
    }

    @Test
    fun `infrastructure domains are recognised, with subdomains`() {
        assertTrue(isInfrastructureDomain("gstatic.com"))
        assertTrue(isInfrastructureDomain("fonts.gstatic.com"))
        assertTrue(!isInfrastructureDomain("youtube.com"))
    }

    @Test
    fun `only lookups made while a browser is in front count`() {
        assertEquals("reddit.com", websiteToCount("com.android.chrome", "www.reddit.com"))
        assertNull(websiteToCount("com.instagram.android", "www.reddit.com"))
        assertNull(websiteToCount(null, "www.reddit.com"))
    }

    @Test
    fun `background infrastructure in a browser does not count`() {
        assertNull(websiteToCount("com.android.chrome", "fonts.gstatic.com"))
        assertNull(websiteToCount("org.mozilla.firefox", "ad.doubleclick.net"))
    }

    @Test
    fun `the cap keeps the busiest sites`() {
        val counts = (1..10).associate { "site$it.com" to it }
        val capped = capWebsites(counts, max = 3)
        assertEquals(setOf("site10.com", "site9.com", "site8.com"), capped.keys)
    }
}
