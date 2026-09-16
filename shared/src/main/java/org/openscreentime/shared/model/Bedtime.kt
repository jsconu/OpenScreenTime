package org.openscreentime.shared.model

import java.util.Calendar

/**
 * True when [nowMinutesOfDay] falls within the [startMinutes]-[endMinutes] bedtime window
 * (see #15). Either null means no window is set. Handles a window that spans midnight
 * (e.g. 21:00-07:00, stored as 1260-420) - a plain "start <= now < end" comparison only
 * works when start < end, so this branches on which side of midnight the window falls.
 * Equal start/end is treated as "not set" rather than a degenerate 24-hour block, since
 * that's almost certainly an unintended input, not an intentional all-day lock.
 */
fun isInBedtimeWindow(nowMinutesOfDay: Int, startMinutes: Int?, endMinutes: Int?): Boolean {
    if (startMinutes == null || endMinutes == null || startMinutes == endMinutes) return false
    return if (startMinutes < endMinutes) {
        nowMinutesOfDay in startMinutes until endMinutes
    } else {
        nowMinutesOfDay >= startMinutes || nowMinutesOfDay < endMinutes
    }
}

/** Minutes since local midnight right now (0-1439). */
fun nowMinutesOfDay(): Int {
    val calendar = Calendar.getInstance()
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}

/** Formats minutes-since-midnight as a 12-hour clock time, e.g. 420 -> "7:00 AM". */
fun formatMinutesOfDay(minutes: Int): String {
    val hour24 = (minutes / 60) % 24
    val minute = minutes % 60
    val period = if (hour24 < 12) "AM" else "PM"
    val hour12 = when (val h = hour24 % 12) { 0 -> 12; else -> h }
    return "$hour12:${minute.toString().padStart(2, '0')} $period"
}

/**
 * Parses a "HH:MM" 24-hour string (the bedtime-picker's edit format) into minutes since
 * midnight, or null if it's not valid. Distinct from [formatMinutesOfDay]'s 12-hour AM/PM
 * display format - this pair is for editing a value, that one for showing it back.
 */
fun parseHHmm(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

/** Formats minutes-since-midnight as a 24-hour "HH:MM" string, e.g. 420 -> "07:00". */
fun formatHHmm(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
