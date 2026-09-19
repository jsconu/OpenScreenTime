package org.openscreentime.shared.model

/**
 * A parent can lift a "Lock now" from the locked phone itself with the family passcode (see the
 * lock screen's "Parent unlock"). Rather than ending the lock for good, they choose how long: after
 * that the phone locks itself again. [RELOCK_CHOICES_MINUTES] are the timed choices; "until I lock it
 * again" is represented by a null re-lock time.
 */
val RELOCK_CHOICES_MINUTES = listOf(15, 30, 60)

/** The default choice in the unlock prompt. */
const val DEFAULT_RELOCK_MINUTES = 15

/** When to lock again if a parent chose [minutes] at [nowMs]; null means "until I lock it again". */
fun relockAtFor(minutes: Int?, nowMs: Long): Long? = minutes?.let { nowMs + it * 60_000L }

/** True once a scheduled re-lock has come due. */
fun relockDue(relockAtMs: Long?, nowMs: Long): Boolean = relockAtMs != null && nowMs >= relockAtMs
