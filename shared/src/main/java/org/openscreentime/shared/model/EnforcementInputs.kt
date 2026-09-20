package org.openscreentime.shared.model

/**
 * What the foreground guard needs to know about a person's limits, however this phone keeps them: the kid app reads
 * a child's live profile, the parent app reads its own "self" profile. Each app supplies one small adapter; the guard
 * (see `BaseAppLimitAccessibilityService`) and [buildEnforcementInput] never learn where the values live.
 */
interface EnforcementSettings {
    val locked: Boolean
    val bedtimeStartMinutes: Int?
    val bedtimeEndMinutes: Int?
    val dailyLimitMinutes: Int
    fun appLimitMinutes(packageName: String): Int?

    /** A parent-granted "more time" window (see #23). Only a child's phone has one; the default is none. */
    val temporaryUnlockUntilMs: Long? get() = null
    val alwaysAllowedPackages: Set<String>

    /** When a timed "Parent unlock" runs out and the phone should lock again, if one is running. */
    val relockAtMs: Long?

    /** The package in front right now (written by the guard, read by the website filter). */
    var foregroundPackage: String?
}

/** Today's usage on this phone, as far as the limit checks are concerned. */
interface UsageReader {
    val liveTotalScreenTimeMs: Long
    val dailyWarned: Boolean
    val appUsageMs: Map<String, Long>
    val warnedApps: Set<String>
    val pausedApps: Set<String>
}

/**
 * One tick's snapshot for [decideEnforcement]. [supportsPause] is true only where a friction-pause screen exists
 * (the kid app, see #12); everywhere else the pause state stays unknown and the engine never returns a pause.
 */
fun buildEnforcementInput(
    settings: EnforcementSettings,
    usage: UsageReader,
    packageName: String?,
    nowMs: Long,
    nowMinutesOfDay: Int,
    supportsPause: Boolean
): EnforcementInput = EnforcementInput(
    locked = settings.locked,
    bedtimeStartMinutes = settings.bedtimeStartMinutes,
    bedtimeEndMinutes = settings.bedtimeEndMinutes,
    nowMinutesOfDay = nowMinutesOfDay,
    dailyLimitMinutes = settings.dailyLimitMinutes,
    liveTotalScreenTimeMs = usage.liveTotalScreenTimeMs,
    dailyWarned = usage.dailyWarned,
    foregroundPackage = packageName,
    appLimitMinutes = packageName?.let { settings.appLimitMinutes(it) },
    appUsedMs = packageName?.let { usage.appUsageMs[it] } ?: 0,
    appWarned = packageName?.let { it in usage.warnedApps } ?: false,
    appAlreadyPaused = if (supportsPause) packageName?.let { it in usage.pausedApps } else null,
    nowMs = nowMs,
    temporaryUnlockUntilMs = settings.temporaryUnlockUntilMs,
    alwaysAllowedPackages = settings.alwaysAllowedPackages
)
