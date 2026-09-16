package org.openscreentime.shared.model

/**
 * True if [host] is exactly one of [blockedDomains], or a subdomain of one - blocking
 * "tiktok.com" also blocks "m.tiktok.com" and "www.tiktok.com" (see #19). Case-insensitive,
 * and tolerant of a trailing "." (some DNS queries encode the root as a trailing dot).
 */
fun isDomainBlocked(host: String, blockedDomains: List<String>): Boolean {
    val normalizedHost = normalizeDomain(host)
    if (normalizedHost.isEmpty()) return false
    return blockedDomains.any { blocked ->
        val normalizedBlocked = normalizeDomain(blocked)
        normalizedBlocked.isNotEmpty() &&
            (normalizedHost == normalizedBlocked || normalizedHost.endsWith(".$normalizedBlocked"))
    }
}

/**
 * Normalizes and appends [rawDomain] to [blockedDomains] - a no-op (returns [blockedDomains]
 * unchanged) if it's blank or already present. Pure: the caller still has to persist the
 * result. Both the parent app's ChildDetailScreen and the kid app's passcode-gated
 * ParentControlsScreen call this rather than each re-implementing the same trim/dedupe.
 */
fun addBlockedDomain(blockedDomains: List<String>, rawDomain: String): List<String> {
    val domain = normalizeDomain(rawDomain)
    if (domain.isEmpty() || domain in blockedDomains) return blockedDomains
    return blockedDomains + domain
}

/** Pure: the caller still has to persist the result. */
fun removeBlockedDomain(blockedDomains: List<String>, domain: String): List<String> =
    blockedDomains - domain

private fun normalizeDomain(raw: String): String = raw.trim().trimEnd('.').lowercase()
