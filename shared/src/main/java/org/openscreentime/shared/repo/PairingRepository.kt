package org.openscreentime.shared.repo

import com.google.firebase.FirebaseApp
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

        // Only the two writes that ARE the pairing go in the transaction: stamp this device
        // onto the child, and mark the code used. Anything else in here - however small -
        // fails the whole claim if the rules refuse it, which is how pairing broke before:
        // the linkedDevices write below was part of this transaction, so on a project whose
        // deployed firestore.rules predated that collection (#39), every claim was refused.
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
                parentUid to childId
            }.await()
        } catch (e: FirebaseFirestoreException) {
            // Expired, used, or nonexistent codes aren't readable at all (see firestore.rules),
            // so they surface as a permission error rather than one of the messages above.
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                // Say which step Firebase refused, so a tester (and we) can tell "no such live code" from
                // "the claim itself was refused" - both otherwise look like the same permission error.
                // Read-only: nothing here writes, so a failed attempt never burns a good code.
                val project = runCatching { FirebaseApp.getInstance().options.projectId }.getOrNull() ?: "unknown"
                val codeVisible = runCatching { codeRef.get().await().exists() }.getOrNull()
                val step = if (codeVisible != true) {
                    "this phone could not read that code (it isn't there, is used, or ran out)"
                } else {
                    // The code is live, so the refusal was the child profile itself. The kid device can't
                    // read that profile to say which, so name both causes a parent can actually act on.
                    "the code is live, but this phone wasn't allowed to link to that child - " +
                        "either it was already paired with another phone (remove it and add it again), " +
                        "or this project's security rules are older than the app (deploy firebase/firestore.rules)"
                }
                throw IllegalStateException(
                    "That code isn't active. Ask the parent for a new one. (Project $project: $step.)"
                )
            }
            throw e
        }

        // Lets this device later read the parent's self-tracked stats, if the parent has opted into self-tracking
        // (see #18). Its own doc under the parent; the security rules still check it against the code just claimed
        // (#39) - getAfter on that code doc sees it already marked used by this uid, so the binding holds just as
        // well outside the transaction. Deliberately best-effort: if it's refused, the phone is paired and works,
        // it just can't read the parent's own stats.
        runCatching {
            db.document(FirestorePaths.linkedDeviceDoc(parentUid, uid))
                .set(mapOf("code" to code, "createdAt" to FieldValue.serverTimestamp())).await()
        }

        val childSnap = db.document(FirestorePaths.childDoc(parentUid, childId)).get().await()
        val child = ChildProfile.fromMap(childId, childSnap.data ?: emptyMap())
        return parentUid to child
    }
}
