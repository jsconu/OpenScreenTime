package org.openscreentime.shared.model

/**
 * See #34 - during a bedtime window, only calls/texts to or from an always-allowed
 * contact (meant for a parent's number) get through; every other number is blocked. This
 * is bedtime-scoped only, not a general-purpose call blocker - outside the bedtime window
 * every number is allowed, same as today.
 */
fun isCallAllowedDuringBedtime(
    phoneNumber: String?,
    nowMinutesOfDay: Int,
    bedtimeStartMinutes: Int?,
    bedtimeEndMinutes: Int?,
    alwaysAllowedContacts: List<String>
): Boolean {
    if (!isInBedtimeWindow(nowMinutesOfDay, bedtimeStartMinutes, bedtimeEndMinutes)) return true
    val normalized = phoneNumber?.let(::normalizePhoneNumber) ?: return false
    return alwaysAllowedContacts.any { normalizePhoneNumber(it) == normalized }
}

/**
 * Strips everything but digits, then keeps the last 10 - loosely matches "+15551234567",
 * "(555) 123-4567", and "555-123-4567" against each other regardless of how a parent
 * typed a number in or how a carrier formats an incoming caller ID. Numbers shorter than
 * 10 digits (short codes, some international numbers) are kept as-is.
 */
fun normalizePhoneNumber(raw: String): String {
    val digitsOnly = raw.filter(Char::isDigit)
    return if (digitsOnly.length > 10) digitsOnly.takeLast(10) else digitsOnly
}

/**
 * Appends [rawNumber] to [contacts] - a no-op (returns [contacts] unchanged) if it's blank
 * or a number that already normalizes to an existing entry. Pure: the caller still has to
 * persist the result.
 */
fun addAllowedContact(contacts: List<String>, rawNumber: String): List<String> {
    val trimmed = rawNumber.trim()
    if (trimmed.isEmpty()) return contacts
    val normalized = normalizePhoneNumber(trimmed)
    if (normalized.isEmpty() || contacts.any { normalizePhoneNumber(it) == normalized }) return contacts
    return contacts + trimmed
}

/** Pure: the caller still has to persist the result. */
fun removeAllowedContact(contacts: List<String>, number: String): List<String> =
    contacts - number
