package org.openscreentime.shared.repo

import org.openscreentime.shared.model.AppList
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.model.FocusProfile
import org.openscreentime.shared.model.InstalledApp
import org.openscreentime.shared.model.PasscodeInfo
import org.openscreentime.shared.model.TrackingToggle

/**
 * Everything the two apps ask of whatever is storing a family's profiles, limits and usage.
 *
 * There are two implementations, chosen by product flavor and never both in one build:
 *
 *  - **local** ([LocalFamilyRepository], in this module) keeps everything in this phone's own
 *    storage. Nothing leaves the device, there is no account, and a phone is managed in person.
 *  - **cloud** (`FirebaseFamilyRepository`, in the `:cloud` module) pairs a parent's phone with a
 *    child's over Firestore, which is what makes remote limits, remote locking and "more time"
 *    requests from elsewhere possible.
 *
 * The local build contains no Firebase code at all, which is why this interface lives here rather
 * than the class it used to be. Anything a local build can't do must fail softly - see the
 * [RemoteOnly] marker - rather than crash a screen that shouldn't have offered it.
 */
interface FamilyRepository {

    /** Whoever this phone is acting as: a signed-in parent, a paired device, or the local owner. */
    val currentUid: String?

    // --- Account (cloud only: a local build has no account to sign in to) ---

    @RemoteOnly suspend fun signUpParent(email: String, password: String): String
    @RemoteOnly suspend fun signInParent(email: String, password: String): String
    @RemoteOnly suspend fun sendPasswordReset(email: String)
    @RemoteOnly suspend fun verifyAccountPassword(password: String): Boolean
    @RemoteOnly suspend fun signInAnonymously(): String
    fun signOut()

    // --- Profiles ---

    @RemoteOnly suspend fun createChild(parentUid: String, name: String): ChildProfile
    @RemoteOnly suspend fun regeneratePairingCode(parentUid: String, childId: String): String
    @RemoteOnly suspend fun claimPairingCode(code: String): Pair<String, ChildProfile>
    @RemoteOnly suspend fun deleteChild(parentUid: String, childId: String)

    /** What this person is called on screen. A parent naming their own phone, or a child's. */
    suspend fun renameProfile(parentUid: String, childId: String, name: String)

    /** The owner's own tracked profile, created on first use. Works in both builds. */
    suspend fun getOrCreateSelfProfile(parentUid: String, name: String): ChildProfile

    /** The parent's self profile as seen from a kid device (#18). Null when there's nothing to show. */
    @RemoteOnly suspend fun getParentSelfProfile(parentUid: String): ChildProfile?

    fun listenChild(parentUid: String, childId: String, onChange: (ChildProfile) -> Unit): Registration
    @RemoteOnly fun listenChildren(parentUid: String, onChange: (List<ChildProfile>) -> Unit): Registration

    // --- Passcode ---

    suspend fun getParentPasscode(parentUid: String): PasscodeInfo?
    suspend fun setParentPasscode(parentUid: String, hash: String, salt: String)

    // --- Limits and state ---

    suspend fun setLocked(parentUid: String, childId: String, locked: Boolean)
    suspend fun updateDailyLimit(parentUid: String, childId: String, minutes: Int)
    suspend fun setAppLimit(parentUid: String, childId: String, packageName: String, minutes: Int)
    suspend fun updateDailyUnlockGoal(parentUid: String, childId: String, goal: Int?)
    suspend fun updateBedtimeWindow(parentUid: String, childId: String, startMinutes: Int?, endMinutes: Int?)
    suspend fun addBlockedDomain(parentUid: String, childId: String, domain: String)
    suspend fun removeBlockedDomain(parentUid: String, childId: String, domain: String)
    suspend fun setAppListMember(parentUid: String, childId: String, list: AppList, packageName: String, member: Boolean)
    suspend fun setTrackingToggle(parentUid: String, childId: String, toggle: TrackingToggle, enabled: Boolean)
    suspend fun setFocusMode(parentUid: String, childId: String, enabled: Boolean)
    suspend fun setFocusProfile(parentUid: String, childId: String, profile: FocusProfile)
    suspend fun addAllowedContact(parentUid: String, childId: String, number: String)
    suspend fun removeAllowedContact(parentUid: String, childId: String, number: String)

    // --- Negotiation between two phones (#14, #23) ---

    @RemoteOnly suspend fun proposeLimits(
        parentUid: String,
        childId: String,
        proposedDailyLimitMinutes: Int? = null,
        proposedAppLimits: Map<String, Int>? = null
    )
    @RemoteOnly suspend fun approveProposal(parentUid: String, childId: String, child: ChildProfile)
    @RemoteOnly suspend fun declineProposal(parentUid: String, childId: String)
    @RemoteOnly suspend fun requestExtraTime(parentUid: String, childId: String, minutes: Int)
    suspend fun grantExtraTime(parentUid: String, childId: String, minutes: Int)
    @RemoteOnly suspend fun declineExtraTimeRequest(parentUid: String, childId: String)

    // --- Usage ---

    fun listenDailyStats(parentUid: String, childId: String, date: String, onChange: (DailyStats) -> Unit): Registration
    suspend fun getRecentDailyStats(parentUid: String, childId: String, days: Int): List<DailyStats>
    suspend fun pushDailyStats(parentUid: String, childId: String, stats: DailyStats)
    suspend fun pushInstalledApps(parentUid: String, childId: String, apps: List<InstalledApp>)
    fun listenInstalledApps(parentUid: String, childId: String, onChange: (List<InstalledApp>) -> Unit): Registration

    // --- Feedback (#22) ---

    @RemoteOnly suspend fun submitFeedback(parentUid: String, text: String, appVersion: String, device: String)

    companion object {
        /** Must match the `duration.value(30, 'm')` window enforced in firebase/firestore.rules. */
        const val PAIRING_CODE_TTL_SECONDS = 30 * 60
    }
}

/**
 * Marks a call that only means something when two phones are paired. A local build has no second
 * phone, so these no-op or return nothing rather than failing: the screens that offer them are
 * already hidden in that flavor, and a stray call must not take a screen down.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class RemoteOnly

/** A live subscription. Cancelling it is [remove], to match what Firestore's own listeners call it. */
fun interface Registration {
    fun remove()
}

/** Ids that both implementations agree on. */
object Profiles {
    /** The owner's own tracked profile always lives at this fixed child id (#8). */
    const val SELF_CHILD_ID = "self"
}
