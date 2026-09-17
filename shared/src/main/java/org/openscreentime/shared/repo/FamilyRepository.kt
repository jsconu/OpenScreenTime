package org.openscreentime.shared.repo

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.model.PasscodeInfo

/**
 * Single access point to Firebase Auth + Firestore for both the parent and kid apps.
 * See /firebase/firestore.rules for the security rules this relies on.
 *
 * Internally composed of small per-domain repositories (session, passcodes, children,
 * limits, stats, pairing) rather than one flat implementation, but every call site still
 * injects just this one class: [currentUid] and several screens (e.g. ChildDetailScreen)
 * call across child/limits/stats concerns in the same place often enough that splitting
 * the *public* interface would cost call sites more than one flat implementation costs
 * maintainers here.
 */
class FamilyRepository(
    auth: FirebaseAuth = FirebaseAuth.getInstance(),
    db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val session = SessionRepository(auth)
    private val passcodes = PasscodeRepository(db)
    private val children = ChildrenRepository(db, passcodes)
    private val limits = LimitsRepository(db)
    private val stats = StatsRepository(db)
    private val pairing = PairingRepository(db, session)
    private val feedback = FeedbackRepository(db)

    val currentUid: String? get() = session.currentUid

    suspend fun signUpParent(email: String, password: String): String = session.signUpParent(email, password)

    suspend fun signInParent(email: String, password: String): String = session.signInParent(email, password)

    suspend fun signInAnonymously(): String = session.signInAnonymously()

    fun signOut() = session.signOut()

    // --- Parent side ---

    suspend fun createChild(parentUid: String, name: String): ChildProfile = children.createChild(parentUid, name)

    suspend fun setLocked(parentUid: String, childId: String, locked: Boolean) =
        children.setLocked(parentUid, childId, locked)

    /** Reads the parent's passcode hash/salt, or null if no passcode has been set yet. */
    suspend fun getParentPasscode(parentUid: String): PasscodeInfo? = passcodes.getParentPasscode(parentUid)

    /**
     * Sets or changes the family passcode. Writes it to the parent's own account doc, and
     * fans it out to every existing child doc so already-paired kid devices can verify it
     * locally (see [ChildProfile.parentPasscodeHash]/[ChildProfile.parentPasscodeSalt]).
     */
    suspend fun setParentPasscode(parentUid: String, hash: String, salt: String) =
        passcodes.setParentPasscode(parentUid, hash, salt)

    fun listenChildren(parentUid: String, onChange: (List<ChildProfile>) -> Unit): ListenerRegistration =
        children.listenChildren(parentUid, onChange)

    fun listenDailyStats(
        parentUid: String,
        childId: String,
        date: String,
        onChange: (DailyStats) -> Unit
    ): ListenerRegistration = stats.listenDailyStats(parentUid, childId, date, onChange)

    /**
     * The [days] calendar dates before today, oldest first - used to compute a streak
     * (see #13). Excludes today, since that day isn't over yet. A day with no synced
     * document simply isn't in the returned list; the caller (computeStreak) treats
     * that as "can't confirm it was under the limit," not as an automatic pass.
     */
    suspend fun getRecentDailyStats(parentUid: String, childId: String, days: Int): List<DailyStats> =
        stats.getRecentDailyStats(parentUid, childId, days)

    suspend fun updateDailyLimit(parentUid: String, childId: String, minutes: Int) =
        limits.updateDailyLimit(parentUid, childId, minutes)

    suspend fun updateAppLimits(parentUid: String, childId: String, appLimits: Map<String, Int>) =
        limits.updateAppLimits(parentUid, childId, appLimits)

    suspend fun updateDailyUnlockGoal(parentUid: String, childId: String, goal: Int?) =
        limits.updateDailyUnlockGoal(parentUid, childId, goal)

    /** Both null clears the bedtime window (see #15). */
    suspend fun updateBedtimeWindow(parentUid: String, childId: String, startMinutes: Int?, endMinutes: Int?) =
        limits.updateBedtimeWindow(parentUid, childId, startMinutes, endMinutes)

    /** Replaces the whole blocked-domains list (see #19, [ChildProfile.blockedDomains]). */
    suspend fun updateBlockedDomains(parentUid: String, childId: String, domains: List<String>) =
        limits.updateBlockedDomains(parentUid, childId, domains)

    /** Replaces the whole always-allowed list (see #28, [ChildProfile.alwaysAllowedPackages]). */
    suspend fun updateAlwaysAllowedPackages(parentUid: String, childId: String, packages: List<String>) =
        limits.updateAlwaysAllowedPackages(parentUid, childId, packages)

    /**
     * Kid-initiated, passcode-free suggestion (see #14) - writes only the two proposal
     * fields, never the real limits. Either parameter may be left null to leave that
     * half of the proposal untouched (e.g. proposing just a new daily limit keeps
     * whatever app-limit proposal, if any, was already pending).
     */
    suspend fun proposeLimits(
        parentUid: String,
        childId: String,
        proposedDailyLimitMinutes: Int? = null,
        proposedAppLimits: Map<String, Int>? = null
    ) = limits.proposeLimits(parentUid, childId, proposedDailyLimitMinutes, proposedAppLimits)

    /** Copies a pending proposal into the real limits and clears it. See #14. */
    suspend fun approveProposal(parentUid: String, childId: String, child: ChildProfile) =
        limits.approveProposal(parentUid, childId, child)

    /** Clears a pending proposal without applying it. See #14. */
    suspend fun declineProposal(parentUid: String, childId: String) = limits.declineProposal(parentUid, childId)

    /**
     * Kid-initiated, passcode-free "more time" request (see #23) - requested from the block
     * screen while the kid is actually blocked. Overwrites any previous pending request.
     */
    suspend fun requestExtraTime(parentUid: String, childId: String, minutes: Int) =
        limits.requestExtraTime(parentUid, childId, minutes)

    /** Grants [minutes] of temporary unlock starting now, and clears the pending request. See #23. */
    suspend fun grantExtraTime(parentUid: String, childId: String, minutes: Int) =
        limits.grantExtraTime(parentUid, childId, minutes)

    /** Clears a pending "more time" request without granting it. See #23. */
    suspend fun declineExtraTimeRequest(parentUid: String, childId: String) =
        limits.declineExtraTimeRequest(parentUid, childId)

    /**
     * Returns the parent's own tracked profile (see [ChildProfile.isSelf]), creating it on
     * first use. Unlike [createChild], this is claimed immediately - the parent app is
     * already signed in as parentUid, which already has full read/write on its own
     * children collection, so there's no pairing-code handshake to do.
     */
    suspend fun getOrCreateSelfProfile(parentUid: String, name: String): ChildProfile =
        children.getOrCreateSelfProfile(parentUid, name)

    /** Deletes a child and its usage history. Firestore doesn't cascade-delete
     * subcollections, so dailyStats docs are removed explicitly first. */
    suspend fun deleteChild(parentUid: String, childId: String) = children.deleteChild(parentUid, childId)

    // --- Kid side ---

    /** Resolves a pairing code and atomically links this device's anonymous uid to the child profile. */
    suspend fun claimPairingCode(code: String): Pair<String, ChildProfile> = pairing.claimPairingCode(code)

    /**
     * The parent's self-tracked profile (see #8), if they've opted into self-tracking
     * and this device is linked to their family (see #18 - claimPairingCode appends
     * this device's uid to linkedDeviceUids). Reads the fixed "self" doc id directly,
     * since a linked device has no permission to list/query the children collection.
     * Returns null both when the parent hasn't started self-tracking (the doc simply
     * doesn't exist) and when the security rule denies it (an unlinked device) -
     * Firestore surfaces both as a failure here, and either way there's nothing to
     * show, matching this feature's calm/non-intrusive framing.
     */
    suspend fun getParentSelfProfile(parentUid: String): ChildProfile? = children.getParentSelfProfile(parentUid)

    fun listenChild(parentUid: String, childId: String, onChange: (ChildProfile) -> Unit): ListenerRegistration =
        children.listenChild(parentUid, childId, onChange)

    suspend fun pushDailyStats(parentUid: String, childId: String, stats: DailyStats) =
        this.stats.pushDailyStats(parentUid, childId, stats)

    /** See #22 - write-only; nobody can read feedback back through the app. */
    suspend fun submitFeedback(parentUid: String, text: String, appVersion: String, device: String) =
        feedback.submit(parentUid, text, appVersion, device)

    companion object {
        /** Must match the `duration.value(30, 'm')` window enforced in firestore.rules. */
        const val PAIRING_CODE_TTL_SECONDS = 30 * 60
    }
}
