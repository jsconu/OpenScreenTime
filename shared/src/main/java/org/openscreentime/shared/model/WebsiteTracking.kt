package org.openscreentime.shared.model

/**
 * Optional website tracking (see #41). The website filter already answers this phone's DNS lookups, so
 * with a parent's say-so it can also count WHICH SITES were looked up while a browser was open. That is
 * deliberately coarse: site names only (never a page, search, or anything typed), counted as bursts of
 * activity rather than minutes, and only in browsers - lookups an app makes on its own are ignored.
 */

/** Package names of the common Android browsers - lookups only count while one of these is in front. */
val BROWSER_PACKAGES: Set<String> = setOf(
    "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
    "com.google.android.apps.chrome", "org.chromium.chrome",
    "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.focus",
    "com.sec.android.app.sbrowser", "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native",
    "com.brave.browser", "com.duckduckgo.mobile.android", "com.kiwibrowser.browser", "com.vivaldi.browser",
    "com.mi.globalbrowser", "com.huawei.browser", "com.android.browser"
)

/** How long after counting a site before a fresh lookup of it counts again (a page fires many lookups). */
const val WEBSITE_COUNT_WINDOW_MS = 30_000L

/** The most sites kept for a day. */
const val MAX_WEBSITES_PER_DAY = 300

private val TWO_PART_SUFFIXES = setOf(
    "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "com.au", "net.au", "org.au", "edu.au", "co.nz", "org.nz",
    "co.jp", "ne.jp", "or.jp", "co.in", "net.in", "org.in", "com.br", "com.mx", "co.za", "com.cn", "com.tr",
    "co.kr", "com.sg", "com.hk", "com.tw", "com.ar", "com.co", "co.id", "co.il", "com.ph", "com.my", "com.vn"
)

/** Background plumbing, ad networks and CDNs: real lookups, but not "a site someone visited". */
private val INFRASTRUCTURE_SUFFIXES = listOf(
    "gstatic.com", "googleapis.com", "googleusercontent.com", "googlevideo.com", "ggpht.com", "gvt1.com",
    "gvt2.com", "google-analytics.com", "googletagmanager.com", "googlesyndication.com",
    "googleadservices.com", "doubleclick.net", "crashlytics.com", "app-measurement.com", "firebaseio.com",
    "cloudfront.net", "akamaihd.net", "akamaized.net", "akamai.net", "akamaiedge.net", "edgekey.net",
    "edgesuite.net", "fastly.net", "fbcdn.net", "cdninstagram.com", "twimg.com", "sentry.io", "amazonaws.com",
    "cloudflare-dns.com", "msftconnecttest.com", "windowsupdate.com", "apple-dns.net", "mzstatic.com"
)

/**
 * The site a lookup belongs to: the name a person would recognize ("news.bbc.co.uk" -> "bbc.co.uk",
 * "m.youtube.com" -> "youtube.com"). A heuristic, not a full public-suffix list. Null for anything that
 * isn't a site name (an IP address, a single label, a reverse-lookup, an empty string).
 */
fun registrableDomain(name: String): String? {
    val cleaned = name.trim().trimEnd('.').lowercase()
    if (cleaned.isEmpty() || cleaned.length > 253) return null
    val labels = cleaned.split('.')
    if (labels.size < 2 || labels.any { it.isEmpty() }) return null
    if (labels.last().all { it.isDigit() }) return null // an IPv4 address
    if (cleaned.endsWith(".arpa") || cleaned.endsWith(".local") || cleaned.endsWith(".internal")) return null
    val lastTwo = labels.takeLast(2).joinToString(".")
    return if (labels.size >= 3 && lastTwo in TWO_PART_SUFFIXES) labels.takeLast(3).joinToString(".") else lastTwo
}

fun isInfrastructureDomain(domain: String): Boolean =
    INFRASTRUCTURE_SUFFIXES.any { domain == it || domain.endsWith(".$it") }

/**
 * The site to count for a lookup of [queryName] made while [foregroundPackage] was in front, or null when it
 * shouldn't count: not in a browser, not a site name, or background infrastructure.
 */
fun websiteToCount(foregroundPackage: String?, queryName: String): String? {
    if (foregroundPackage == null || foregroundPackage !in BROWSER_PACKAGES) return null
    val domain = registrableDomain(queryName) ?: return null
    return if (isInfrastructureDomain(domain)) null else domain
}

/** Keeps only the [max] busiest sites. */
fun capWebsites(counts: Map<String, Int>, max: Int = MAX_WEBSITES_PER_DAY): Map<String, Int> =
    if (counts.size <= max) counts else counts.entries.sortedByDescending { it.value }.take(max).associate { it.key to it.value }
