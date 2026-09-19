package org.openscreentime.shared.util

import android.content.Context

/**
 * Failed-passcode counter for the app's passcode gates (the kid device's "Parent controls" and the
 * lock screens' parent unlock), shared so they count together. Kept on disk, not in
 * Compose state: the earlier in-memory counter reset every time the screen was left and
 * re-entered, so it didn't slow anyone down. After [MAX_ATTEMPTS] misses the gate is closed
 * for [LOCKOUT_MS], and the count only clears on a correct passcode.
 *
 * This is a speed bump for a curious kid, not a security boundary - the passcode hash is
 * readable by the paired device (see the note at the top of firestore.rules), and a device
 * clock can be changed.
 */
class PasscodeAttemptStore(context: Context) {
    private val prefs = context.getSharedPreferences("passcode_attempts", Context.MODE_PRIVATE)

    val lockedUntilMs: Long get() = prefs.getLong("lockedUntilMs", 0L)

    fun isLocked(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs < lockedUntilMs

    fun minutesRemaining(nowMs: Long = System.currentTimeMillis()): Int =
        (((lockedUntilMs - nowMs) + 59_999L) / 60_000L).toInt().coerceAtLeast(1)

    /** Returns true when this miss tripped the lockout. */
    fun recordFailure(nowMs: Long = System.currentTimeMillis()): Boolean {
        val failures = prefs.getInt("failures", 0) + 1
        return if (failures >= MAX_ATTEMPTS) {
            prefs.edit().putInt("failures", 0).putLong("lockedUntilMs", nowMs + LOCKOUT_MS).apply()
            true
        } else {
            prefs.edit().putInt("failures", failures).apply()
            false
        }
    }

    fun recordSuccess() = prefs.edit().clear().apply()

    companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MS = 15L * 60_000L
    }
}
