package org.openscreentime.shared.model

/** "1h 23m" (or just "23m" under an hour) - both apps' stat displays use this. */
fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
