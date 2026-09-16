package org.openscreentime.shared.model

/**
 * True if [host] is exactly one of [blockedDomains], or a subdomain of one - blocking
 * "tiktok.com" also blocks "m.tiktok.com" and "www.tiktok.com" (see #19). Case-insensitive,
 * and tolerant of a trailing "." (some DNS queries encode the root as a trailing dot).
 */
fun isDomainBlocked(host: String, blockedDomains: List<String>): Boolean {
    val normalizedHost = host.trim().trimEnd('.').lowercase()
    if (normalizedHost.isEmpty()) return false
    return blockedDomains.any { blocked ->
        val normalizedBlocked = blocked.trim().trimEnd('.').lowercase()
        normalizedBlocked.isNotEmpty() &&
            (normalizedHost == normalizedBlocked || normalizedHost.endsWith(".$normalizedBlocked"))
    }
}
