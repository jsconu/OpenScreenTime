package org.openscreentime.shared.repo

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.DailyStats
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
        val child = ChildProfile(id = docRef.id, name = name, pairingCode = code, paired = false)
        docRef.set(child.toMap()).await()
        db.collection(FirestorePaths.PAIRING_CODES).document(code).set(
            mapOf("parentUid" to parentUid, "childId" to docRef.id, "used" to false)
        ).await()
        return child
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

    suspend fun deleteChild(parentUid: String, childId: String) {
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
}
