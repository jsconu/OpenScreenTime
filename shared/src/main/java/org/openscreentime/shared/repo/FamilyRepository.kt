package org.openscreentime.shared.repo

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.model.PasscodeInfo
import kotlin.random.Random

/**
 * Single access point to Firebase Auth + Firestore for both the parent and kid apps.
 * See /firebase/firestore.rules for the security rules this relies on.
 */
class FamilyRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun signUpParent(email: String, password: String): String {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        return result.user!!.uid
    }

    suspend fun signInParent(email: String, password: String): String {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user!!.uid
    }

    suspend fun signInAnonymously(): String {
        auth.currentUser?.let { return it.uid }
        val result = auth.signInAnonymously().await()
        return result.user!!.uid
    }

    fun signOut() = auth.signOut()

    // --- Parent side ---

    suspend fun createChild(parentUid: String, name: String): ChildProfile {
        val code = generatePairingCode()
        val docRef = db.collection(FirestorePaths.childrenCollection(parentUid)).document()
        val existingPasscode = getParentPasscode(parentUid)
        val child = ChildProfile(
            id = docRef.id,
            name = name,
            pairingCode = code,
            paired = false,
            parentPasscodeHash = existingPasscode?.hash,
            parentPasscodeSalt = existingPasscode?.salt
        )
        docRef.set(child.toMap()).await()
        db.collection(FirestorePaths.PAIRING_CODES).document(code).set(
            mapOf(
                "parentUid" to parentUid,
                "childId" to docRef.id,
                "used" to false,
                // Must be the server's clock, not a client-supplied value - the
                // security rules require an exact match on this, see firestore.rules.
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        return child
    }

    suspend fun setLocked(parentUid: String, childId: String, locked: Boolean) {
        db.document(FirestorePaths.childDoc(parentUid, childId))
            .update("locked", locked).await()
    }

    /** Reads the parent's passcode hash/salt, or null if no passcode has been set yet. */
    suspend fun getParentPasscode(parentUid: String): PasscodeInfo? {
        val snap = db.document("${FirestorePaths.PARENTS}/$parentUid").get().await()
        val hash = snap.getString("passcodeHash") ?: return null
        val salt = snap.getString("passcodeSalt") ?: return null
        return PasscodeInfo(hash, salt)
    }

    /**
     * Sets or changes the family passcode. Writes it to the parent's own account doc, and
     * fans it out to every existing child doc so already-paired kid devices can verify it
     * locally (see [ChildProfile.parentPasscodeHash]/[ChildProfile.parentPasscodeSalt]).
     */
    suspend fun setParentPasscode(parentUid: String, hash: String, salt: String) {
        db.document("${FirestorePaths.PARENTS}/$parentUid")
            .set(mapOf("passcodeHash" to hash, "passcodeSalt" to salt), SetOptions.merge())
            .await()

        val children = db.collection(FirestorePaths.childrenCollection(parentUid)).get().await()
        if (children.isEmpty) return
        val batch = db.batch()
        for (doc in children.documents) {
            batch.update(doc.reference, mapOf("parentPasscodeHash" to hash, "parentPasscodeSalt" to salt))
        }
        batch.commit().await()
    }

    fun listenChildren(parentUid: String, onChange: (List<ChildProfile>) -> Unit): ListenerRegistration =
        db.collection(FirestorePaths.childrenCollection(parentUid))
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.map { ChildProfile.fromMap(it.id, it.data ?: emptyMap()) } ?: emptyList()
                onChange(list)
            }

    fun listenDailyStats(
        parentUid: String,
        childId: String,
        date: String,
        onChange: (DailyStats) -> Unit
    ): ListenerRegistration =
        db.document(FirestorePaths.dailyStatsDoc(parentUid, childId, date))
            .addSnapshotListener { snap, _ ->
                onChange(DailyStats.fromMap(date, snap?.data ?: emptyMap()))
            }

    /**
     * The [days] calendar dates before today, oldest first - used to compute a streak
     * (see #13). Excludes today, since that day isn't over yet. A day with no synced
     * document simply isn't in the returned list; the caller (computeStreak) treats
     * that as "can't confirm it was under the limit," not as an automatic pass.
     */
    suspend fun getRecentDailyStats(parentUid: String, childId: String, days: Int): List<DailyStats> {
        val calendar = Calendar.getInstance()
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = format.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        val startStr = format.format(calendar.time)

        val snap = db.collection(FirestorePaths.dailyStatsCollection(parentUid, childId))
            .orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
            .whereGreaterThanOrEqualTo(FieldPath.documentId(), startStr)
            .whereLessThan(FieldPath.documentId(), todayStr)
            .get()
            .await()
        return snap.documents.map { DailyStats.fromMap(it.id, it.data ?: emptyMap()) }
    }

    suspend fun updateDailyLimit(parentUid: String, childId: String, minutes: Int) {
        db.document(FirestorePaths.childDoc(parentUid, childId))
            .update("dailyLimitMinutes", minutes).await()
    }

    suspend fun updateAppLimits(parentUid: String, childId: String, appLimits: Map<String, Int>) {
        db.document(FirestorePaths.childDoc(parentUid, childId))
            .update("appLimits", appLimits).await()
    }

    suspend fun updateDailyUnlockGoal(parentUid: String, childId: String, goal: Int?) {
        db.document(FirestorePaths.childDoc(parentUid, childId))
            .update("dailyUnlockGoal", goal).await()
    }

    /** Both null clears the bedtime window (see #15). */
    suspend fun updateBedtimeWindow(parentUid: String, childId: String, startMinutes: Int?, endMinutes: Int?) {
        db.document(FirestorePaths.childDoc(parentUid, childId))
            .update(mapOf("bedtimeStartMinutes" to startMinutes, "bedtimeEndMinutes" to endMinutes))
            .await()
    }

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
    ) {
        val updates = mutableMapOf<String, Any?>()
        if (proposedDailyLimitMinutes != null) updates["proposedDailyLimitMinutes"] = proposedDailyLimitMinutes
        if (proposedAppLimits != null) updates["proposedAppLimits"] = proposedAppLimits
        if (updates.isEmpty()) return
        db.document(FirestorePaths.childDoc(parentUid, childId)).update(updates).await()
    }

    /** Copies a pending proposal into the real limits and clears it. See #14. */
    suspend fun approveProposal(parentUid: String, childId: String, child: ChildProfile) {
        val updates = mutableMapOf<String, Any?>(
            "proposedDailyLimitMinutes" to null,
            "proposedAppLimits" to null
        )
        child.proposedDailyLimitMinutes?.let { updates["dailyLimitMinutes"] = it }
        child.proposedAppLimits?.let { updates["appLimits"] = it }
        db.document(FirestorePaths.childDoc(parentUid, childId)).update(updates).await()
    }

    /** Clears a pending proposal without applying it. See #14. */
    suspend fun declineProposal(parentUid: String, childId: String) {
        db.document(FirestorePaths.childDoc(parentUid, childId))
            .update(mapOf("proposedDailyLimitMinutes" to null, "proposedAppLimits" to null))
            .await()
    }

    /**
     * Returns the parent's own tracked profile (see [ChildProfile.isSelf]), creating it on
     * first use. Unlike [createChild], this is claimed immediately - the parent app is
     * already signed in as parentUid, which already has full read/write on its own
     * children collection, so there's no pairing-code handshake to do.
     */
    suspend fun getOrCreateSelfProfile(parentUid: String, name: String): ChildProfile {
        val docRef = db.document(FirestorePaths.childDoc(parentUid, FirestorePaths.SELF_CHILD_ID))
        val existing = docRef.get().await()
        if (existing.exists()) return ChildProfile.fromMap(existing.id, existing.data ?: emptyMap())

        val existingPasscode = getParentPasscode(parentUid)
        val self = ChildProfile(
            id = FirestorePaths.SELF_CHILD_ID,
            name = name,
            paired = true,
            deviceUid = parentUid,
            isSelf = true,
            parentPasscodeHash = existingPasscode?.hash,
            parentPasscodeSalt = existingPasscode?.salt
        )
        docRef.set(self.toMap()).await()
        return self
    }

    /** Deletes a child and its usage history. Firestore doesn't cascade-delete
     * subcollections, so dailyStats docs are removed explicitly first. */
    suspend fun deleteChild(parentUid: String, childId: String) {
        val statsSnap = db.collection(FirestorePaths.dailyStatsCollection(parentUid, childId)).get().await()
        if (!statsSnap.isEmpty) {
            val batch = db.batch()
            for (doc in statsSnap.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
        }
        db.document(FirestorePaths.childDoc(parentUid, childId)).delete().await()
    }

    // --- Kid side ---

    /** Resolves a pairing code and atomically links this device's anonymous uid to the child profile. */
    suspend fun claimPairingCode(code: String): Pair<String, ChildProfile> {
        val uid = signInAnonymously()
        val codeRef = db.collection(FirestorePaths.PAIRING_CODES).document(code)

        val (parentUid, childId) = db.runTransaction { txn ->
            val codeSnap = txn.get(codeRef)
            require(codeSnap.exists()) { "That code doesn't match any account." }
            val used = codeSnap.getBoolean("used") ?: false
            require(!used) { "That code has already been used." }
            val createdAt = codeSnap.getTimestamp("createdAt")
            val ageMs = createdAt?.let { Timestamp.now().seconds - it.seconds } ?: 0
            // Mirrors the security rule's own TTL check (see firestore.rules) so a
            // stale code fails with a clear message instead of a raw permission error.
            require(ageMs < PAIRING_CODE_TTL_SECONDS) { "That code has expired. Ask the parent for a new one." }
            val parentUid = codeSnap.getString("parentUid")!!
            val childId = codeSnap.getString("childId")!!

            val childRef = db.document(FirestorePaths.childDoc(parentUid, childId))
            txn.update(childRef, mapOf("deviceUid" to uid, "paired" to true))
            txn.update(codeRef, mapOf("used" to true, "claimedByUid" to uid))
            // Lets this device later read the parent's self-tracked stats, if the parent
            // has opted into self-tracking (see #18 - visible by default, not a separate
            // opt-in). set(merge) rather than update(), since the parent doc may not
            // exist yet (e.g. a parent who hasn't set a passcode has no doc at all).
            val parentRef = db.document("${FirestorePaths.PARENTS}/$parentUid")
            txn.set(parentRef, mapOf("linkedDeviceUids" to FieldValue.arrayUnion(uid)), SetOptions.merge())
            parentUid to childId
        }.await()

        val childSnap = db.document(FirestorePaths.childDoc(parentUid, childId)).get().await()
        val child = ChildProfile.fromMap(childId, childSnap.data ?: emptyMap())
        return parentUid to child
    }

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
    suspend fun getParentSelfProfile(parentUid: String): ChildProfile? = try {
        val snap = db.document(FirestorePaths.childDoc(parentUid, FirestorePaths.SELF_CHILD_ID)).get().await()
        if (snap.exists()) ChildProfile.fromMap(snap.id, snap.data ?: emptyMap()) else null
    } catch (e: Exception) {
        null
    }

    fun listenChild(parentUid: String, childId: String, onChange: (ChildProfile) -> Unit): ListenerRegistration =
        db.document(FirestorePaths.childDoc(parentUid, childId))
            .addSnapshotListener { snap, _ ->
                onChange(ChildProfile.fromMap(childId, snap?.data ?: emptyMap()))
            }

    suspend fun pushDailyStats(parentUid: String, childId: String, stats: DailyStats) {
        db.document(FirestorePaths.dailyStatsDoc(parentUid, childId, stats.date))
            .set(stats.toMap(), SetOptions.merge()).await()
    }

    private fun generatePairingCode(): String = (100000 + Random.nextInt(900000)).toString()

    companion object {
        /** Must match the `duration.value(30, 'm')` window enforced in firestore.rules. */
        const val PAIRING_CODE_TTL_SECONDS = 30 * 60
    }
}
