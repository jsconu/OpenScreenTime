package org.openscreentime.shared.repo

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
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

    /**
     * Returns the parent's own tracked profile (see [ChildProfile.isSelf]), creating it on
     * first use. Unlike [createChild], this is claimed immediately - the parent app is
     * already signed in as parentUid, which already has full read/write on its own
     * children collection, so there's no pairing-code handshake to do.
     */
    suspend fun getOrCreateSelfProfile(parentUid: String, name: String): ChildProfile {
        val existing = db.collection(FirestorePaths.childrenCollection(parentUid))
            .whereEqualTo("isSelf", true)
            .limit(1)
            .get()
            .await()
        existing.documents.firstOrNull()?.let { return ChildProfile.fromMap(it.id, it.data ?: emptyMap()) }

        val docRef = db.collection(FirestorePaths.childrenCollection(parentUid)).document()
        val existingPasscode = getParentPasscode(parentUid)
        val self = ChildProfile(
            id = docRef.id,
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
            parentUid to childId
        }.await()

        val childSnap = db.document(FirestorePaths.childDoc(parentUid, childId)).get().await()
        val child = ChildProfile.fromMap(childId, childSnap.data ?: emptyMap())
        return parentUid to child
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
