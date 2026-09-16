package org.openscreentime.shared.model

/**
 * The kid app's and the parent's self-tracking equivalent's limit-enforcement engine -
 * see the architecture review's Candidate 2. Previously this decision logic (which
 * combination of bedtime/lock/daily-limit/app-limit/warning/pause applies right now) was
 * duplicated near-verbatim in two AccessibilityServices, reachable only by running the
 * full Android app; the two copies had already drifted once (the friction-pause feature,
 * #12, silently existed only on the kid side). [decideEnforcement] is a pure function -
 * a snapshot of limits and today's usage in, the events that should fire out - so it's
 * directly unit-testable, and each app's AccessibilityService becomes a thin adapter that
 * calls it and acts on the result (launching its own overlay Activities, posting to its
 * own notification channels).
 */

/** Why a full-screen block is being shown. */
enum class BlockReason(val wireValue: String) {
    PARENT_LOCK("parent_lock"),
    BEDTIME("bedtime"),
    DAILY_LIMIT("daily_limit"),
    APP_LIMIT("app_limit");

    companion object {
        /** Falls back to APP_LIMIT for anything unrecognized, matching this project's
         * previous default when a block Intent's reason extra was missing. */
        fun fromWireValue(value: String?): BlockReason = entries.find { it.wireValue == value } ?: APP_LIMIT
    }
}

/** Which warning-notification threshold was crossed. */
enum class WarnKind { DAILY, APP }

/** One thing the enforcement engine decided should happen this tick. */
sealed interface EnforcementEvent {
    data class Block(val reason: BlockReason) : EnforcementEvent
    /** See #12 - a brief, dismissible breath, not a block. */
    data class Pause(val packageName: String) : EnforcementEvent
    data class Warn(val kind: WarnKind) : EnforcementEvent
}

/**
 * Everything [decideEnforcement] needs for one tick - a snapshot, not a live read, which
 * is what makes the function pure.
 */
data class EnforcementInput(
    val locked: Boolean,
    val bedtimeStartMinutes: Int?,
    val bedtimeEndMinutes: Int?,
    val nowMinutesOfDay: Int,
    val dailyLimitMinutes: Int,
    val liveTotalScreenTimeMs: Long,
    val dailyWarned: Boolean,
    val foregroundPackage: String? = null,
    val appLimitMinutes: Int? = null,
    val appUsedMs: Long = 0,
    val appWarned: Boolean = false,
    /**
     * null means this caller doesn't support the friction-pause feature (see #12) - the
     * parent app's self-tracking has no PauseOverlayActivity, so it simply never supplies
     * this, and the engine never returns [EnforcementEvent.Pause]. A non-null value means
     * "was this app already paused today" - Pause fires only when this is false.
     */
    val appAlreadyPaused: Boolean? = null,
    /** Wall-clock now, in epoch milliseconds - only needed to compare against [temporaryUnlockUntilMs]. */
    val nowMs: Long = 0,
    /**
     * See #23 - a parent-granted "more time" window, requested by the kid and granted by a
     * parent (never self-served). Non-null and in the future means bedtime and the daily/
     * app-limit checks are all skipped for this tick - a parent lock is deliberately NOT
     * bypassed: it's a separate, stronger signal the parent controls directly, not something
     * a time-limit exception should ever override.
     */
    val temporaryUnlockUntilMs: Long? = null
)

private const val WARNING_THRESHOLD_MS = 5 * 60_000L

/** How many minutes are left in the warning window - both apps' notification copy uses this. */
val WARNING_THRESHOLD_MINUTES = WARNING_THRESHOLD_MS / 60_000L

/**
 * Decides what should happen this tick, in priority order: a hard block (bedtime, parent
 * lock, or a limit already reached) always short-circuits everything else. Below that, a
 * daily-level event and an app-level event are independent and can both appear - e.g. a
 * daily-limit warning and an app-limit block can fire in the same tick. That combinational
 * behavior is carried over unchanged from the code this was extracted from, not a new
 * behavior introduced by this refactor - see EnforcementTest for the cases that pin it.
 *
 * Two deliberate exceptions to that:
 *
 * An active [EnforcementInput.temporaryUnlockUntilMs] (see #23) skips bedtime and every
 * daily/app-level check for this tick - but never a parent lock, which stays absolute
 * regardless.
 *
 * The daily-limit check runs even when [EnforcementInput.foregroundPackage] is null (e.g.
 * the kid is on the home screen, not inside any app). The previous, duplicated
 * implementation skipped the daily limit entirely whenever no app was in the foreground -
 * a real gap, confirmed worth closing when this was unified, not an accidental behavior
 * change.
 */
fun decideEnforcement(input: EnforcementInput): List<EnforcementEvent> {
    val temporarilyUnlocked = input.temporaryUnlockUntilMs?.let { it > input.nowMs } ?: false

    if (isInBedtimeWindow(input.nowMinutesOfDay, input.bedtimeStartMinutes, input.bedtimeEndMinutes) &&
        !temporarilyUnlocked
    ) {
        return listOf(EnforcementEvent.Block(BlockReason.BEDTIME))
    }
    if (input.locked) {
        return listOf(EnforcementEvent.Block(BlockReason.PARENT_LOCK))
    }
    if (temporarilyUnlocked) {
        return emptyList()
    }

    val events = mutableListOf<EnforcementEvent>()
    val dailyLimitMs = input.dailyLimitMinutes * 60_000L
    when {
        input.liveTotalScreenTimeMs >= dailyLimitMs ->
            return listOf(EnforcementEvent.Block(BlockReason.DAILY_LIMIT))
        dailyLimitMs - input.liveTotalScreenTimeMs <= WARNING_THRESHOLD_MS && !input.dailyWarned ->
            events += EnforcementEvent.Warn(WarnKind.DAILY)
    }

    val pkg = input.foregroundPackage
    val appLimitMinutes = input.appLimitMinutes
    if (pkg != null && appLimitMinutes != null) {
        val appLimitMs = appLimitMinutes * 60_000L
        when {
            input.appUsedMs >= appLimitMs -> events += EnforcementEvent.Block(BlockReason.APP_LIMIT)
            input.appUsedMs >= appLimitMs / 2 && input.appAlreadyPaused == false ->
                events += EnforcementEvent.Pause(pkg)
            appLimitMs - input.appUsedMs <= WARNING_THRESHOLD_MS && !input.appWarned ->
                events += EnforcementEvent.Warn(WarnKind.APP)
        }
    }
    return events
}

/** The calm status notification icon's tier - see #9. */
enum class StatusTier { GOOD, CAUTION, STOP }

/**
 * The same ratio-of-limit-used math behind the calm status icon (#9), factoring in both
 * the daily time limit and the informational unlock goal (#10) - whichever is closer to
 * being reached wins, since either one nearing its limit is worth a caution/stop icon.
 */
fun computeStatusTier(
    locked: Boolean,
    isInBedtime: Boolean,
    dailyLimitMinutes: Int,
    liveTotalScreenTimeMs: Long,
    dailyUnlockGoal: Int?,
    unlockCount: Int
): StatusTier {
    val dailyLimitMs = dailyLimitMinutes * 60_000L
    val timeRatio = if (dailyLimitMs > 0) liveTotalScreenTimeMs.toFloat() / dailyLimitMs else 0f
    val unlockRatio = if (dailyUnlockGoal != null && dailyUnlockGoal > 0) {
        unlockCount.toFloat() / dailyUnlockGoal
    } else 0f
    val ratio = maxOf(timeRatio, unlockRatio)
    return when {
        locked || isInBedtime || ratio >= 1f -> StatusTier.STOP
        ratio >= 0.7f -> StatusTier.CAUTION
        else -> StatusTier.GOOD
    }
}

/** A block screen's title and body text for one [BlockReason]. */
data class BlockScreenCopy(val title: String, val message: String)

/**
 * The block-screen copy for [reason] - see #16 for why bedtime gets its own message (the
 * wake time). [lockMessage] and [defaultMessage] are the two parts of the copy that
 * genuinely differ by app, so the caller supplies both rather than this function guessing
 * which app it's running in:
 * - [defaultMessage] covers DAILY_LIMIT and APP_LIMIT - the kid app says "Ask a parent if
 *   you need more time," the parent's self-tracking says "This is your own limit, from
 *   your own goals."
 * - [lockMessage] covers PARENT_LOCK - reachable on the kid app when an actual parent taps
 *   "Lock now," but also on the parent's own self-tracking when the parent locks their own
 *   device, where "ask a parent to resume it" doesn't fit since they *are* the parent.
 */
fun blockScreenCopy(
    reason: BlockReason,
    bedtimeEndMinutes: Int?,
    lockMessage: String,
    defaultMessage: String
): BlockScreenCopy {
    val title = when (reason) {
        BlockReason.DAILY_LIMIT -> "Screen time is up for today"
        BlockReason.PARENT_LOCK -> "Screen time has been paused"
        BlockReason.BEDTIME -> "It's bedtime"
        BlockReason.APP_LIMIT -> "This app's time limit is reached"
    }
    val message = when (reason) {
        BlockReason.PARENT_LOCK -> lockMessage
        BlockReason.BEDTIME -> {
            if (bedtimeEndMinutes != null) "Screen time starts again at ${formatMinutesOfDay(bedtimeEndMinutes)}."
            else "Screen time starts again in the morning."
        }
        BlockReason.DAILY_LIMIT, BlockReason.APP_LIMIT -> defaultMessage
    }
    return BlockScreenCopy(title, message)
}
