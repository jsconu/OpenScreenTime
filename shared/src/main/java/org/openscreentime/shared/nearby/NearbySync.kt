package org.openscreentime.shared.nearby

import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.DailyStats

/**
 * One exchange between a kid's phone and a parent's phone, and all the decisions in it.
 *
 * The shape is deliberately a single round trip, because every extra one is another chance for two
 * phones to lose each other: **the kid's phone reports, the parent's phone answers with limits.**
 * After that one exchange the parent knows today's usage and the kid knows what it is allowed, so
 * there is no separate "pull" and nothing to reconcile.
 *
 * The rules about who wins are here rather than scattered through the screens, because they are the
 * part that would otherwise quietly differ between the two apps:
 *
 *  - **Limits always come from the parent's phone.** A kid phone applies what it is told, and never
 *    argues from its own copy - otherwise a kid could keep a stale, looser limit by staying away.
 *  - **Usage always comes from the kid's phone.** A parent's copy is a report of what that phone
 *    saw, never something the parent's phone calculates or corrects.
 *  - **Requests are separate from limits**, so answering "no" to fifteen more minutes never
 *    silently rewrites a daily limit.
 *
 * Kept free of Android and of sockets so the whole protocol can be tested as plain functions; the
 * transport ([NearbyTransport]) only moves the bytes.
 */
object NearbySync {

    /** What the kid's phone sends when it gets a chance: today, plus any days the parent may have missed. */
    fun report(id: String, childName: String, today: DailyStats, recentDays: List<DailyStats>): NearbyMessage =
        NearbyMessage.UsageReport(id = id, childName = childName, stats = today, recentDays = recentDays)

    /** What the parent's phone sends back: the limits this child's phone should be keeping to. */
    fun limitsFrom(profile: ChildProfile, id: String): NearbyMessage.LimitsUpdate = NearbyMessage.LimitsUpdate(
        id = id,
        dailyLimitMinutes = profile.dailyLimitMinutes,
        appLimits = profile.appLimits,
        locked = profile.locked,
        bedtimeStartMinutes = profile.bedtimeStartMinutes,
        bedtimeEndMinutes = profile.bedtimeEndMinutes,
        temporaryUnlockUntilMs = profile.temporaryUnlockUntilMs,
        parentPasscodeHash = profile.parentPasscodeHash,
        parentPasscodeSalt = profile.parentPasscodeSalt
    )

    /**
     * The kid's phone applying what the parent's phone just said.
     *
     * Only the fields the parent actually sent are touched, so a build that does not know about a
     * newer limit leaves it alone instead of clearing it. Everything this phone decides for itself -
     * what it has been used for, its passcode copy, which apps are installed - is untouched.
     */
    fun applyLimits(profile: ChildProfile, update: NearbyMessage.LimitsUpdate): ChildProfile = profile.copy(
        dailyLimitMinutes = update.dailyLimitMinutes ?: profile.dailyLimitMinutes,
        appLimits = update.appLimits ?: profile.appLimits,
        locked = update.locked ?: profile.locked,
        bedtimeStartMinutes = if (update.bedtimeStartMinutes != null) update.bedtimeStartMinutes else profile.bedtimeStartMinutes,
        bedtimeEndMinutes = if (update.bedtimeEndMinutes != null) update.bedtimeEndMinutes else profile.bedtimeEndMinutes,
        temporaryUnlockUntilMs = update.temporaryUnlockUntilMs ?: profile.temporaryUnlockUntilMs,
        parentPasscodeHash = update.parentPasscodeHash ?: profile.parentPasscodeHash,
        parentPasscodeSalt = update.parentPasscodeSalt ?: profile.parentPasscodeSalt
    )

    /**
     * The parent's phone deciding what to say back to whatever just arrived.
     *
     * [profile] is what the parent currently wants for this child. [onUsage] is called with a report
     * so the parent's app can keep it; [onRequest] with anything a person has to answer, which is
     * deliberately not answered here - a request waits for a parent, and the kid's phone is told
     * only that it arrived.
     */
    fun answer(
        message: NearbyMessage,
        profile: ChildProfile,
        newId: () -> String,
        onUsage: (NearbyMessage.UsageReport) -> Unit,
        onRequest: (NearbyMessage) -> Unit
    ): NearbyMessage? = when (message) {
        is NearbyMessage.UsageReport -> {
            onUsage(message)
            limitsFrom(profile, newId())
        }
        is NearbyMessage.MoreTimeRequest, is NearbyMessage.LimitSuggestion -> {
            onRequest(message)
            // Received, not granted. Anything else would let a phone grant itself time by asking
            // while a parent is asleep.
            NearbyMessage.Answer(id = message.id, granted = false)
        }
        // A parent's phone has no use for these: they are what it sends, not what it receives.
        is NearbyMessage.Answer, is NearbyMessage.LimitsUpdate -> null
    }
}
