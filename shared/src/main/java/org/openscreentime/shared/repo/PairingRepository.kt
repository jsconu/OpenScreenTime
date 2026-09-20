package org.openscreentime.shared.repo

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import org.openscreentime.shared.model.ChildProfile

/** Resolving a pairing code and linking a kid device to a child profile (see #18). */
internal class PairingRepository(
    private val db: FirebaseFirestore,
    private val session: SessionRepository
) {
    suspend fun claimPairingCode(code: String): Pair<String, ChildProfile> {
        val uid = session.signInAnonymously()
        val codeRef = db.collection(FirestorePaths.PAIRING_CODES).document(code)

        val (parentUid, childId) = try {
            db.runTransaction { txn ->
            val codeSnap = txn.get(codeRef)
            require(codeSnap.exists()) { "That code doesn't match any account." }
            val used = codeSnap.getBoolean("used") ?: false
            require(!used) { "That code has already been used." }
            val createdAt = codeSnap.getTimestamp("createdAt")
            val ageMs = createdAt?.let { Timestamp.now().seconds - it.seconds } ?: 0
            // Mirrors the security rule's own TTL check (see firestore.rules) so a
            // stale code fails with a clear message instead of a raw permission error.
            require(ageMs < FamilyRepository.PAIRING_CODE_TTL_SECONDS) { "That code has expired. Ask the parent for a new one." }
            val parentUid = codeSnap.getString("parentUid")!!
            val childId = codeSnap.getString("childId")!!

            val childRef = db.document(FirestorePaths.childDoc(parentUid, childId))
            txn.update(childRef, mapOf("deviceUid" to uid, "paired" to true))
            txn.update(codeRef, mapOf("used" to true, "claimedByUid" to uid))
            // Lets this device later read the parent's self-tracked stats, if the parent has opted into
            // self-tracking (see #18). Its own doc under the parent, created in this same transaction so
            // the security rules can check it against the code being claimed (see #39) - it used to be an
            // array on the parent doc that any signed-in user could add themselves to.
            val linkedRef = db.document(FirestorePaths.linkedDeviceDoc(parentUid, uid))
            txn.set(linkedRef, mapOf("code" to code, "createdAt" to FieldValue.serverTimestamp()))
            parentUid to childId
            }.await()
        } catch (e: FirebaseFirestoreException) {
            // Expired, used, or nonexistent codes aren't readable at all (see firestore.rules),
            // so they surface as a permission error rather than one of the messages above.
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                throw IllegalStateException("That code isn't active. Ask the parent for a new one.")
            }
            throw e
        }

        val childSnap = db.document(FirestorePaths.childDoc(parentUid, childId)).get().await()
        val child = ChildProfile.fromMap(childId, childSnap.data ?: emptyMap())
        return parentUid to child
    }
}
