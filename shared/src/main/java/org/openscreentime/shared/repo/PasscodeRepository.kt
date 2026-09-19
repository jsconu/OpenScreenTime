package org.openscreentime.shared.repo

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import org.openscreentime.shared.model.PasscodeInfo

/** The family passcode: one hash/salt pair on the parent's own account doc, fanned out to every child doc. */
internal class PasscodeRepository(private val db: FirebaseFirestore) {

    suspend fun getParentPasscode(parentUid: String): PasscodeInfo? {
        val snap = db.document("${FirestorePaths.PARENTS}/$parentUid").get().await()
        val hash = snap.getString("passcodeHash") ?: return null
        val salt = snap.getString("passcodeSalt") ?: return null
        return PasscodeInfo(hash, salt)
    }

    suspend fun setParentPasscode(parentUid: String, hash: String, salt: String) {
        db.document("${FirestorePaths.PARENTS}/$parentUid")
            .set(mapOf("passcodeHash" to hash, "passcodeSalt" to salt), SetOptions.merge())
            .await()

        val children = db.collection(FirestorePaths.childrenCollection(parentUid)).get().await()
        if (children.isEmpty) return
        val batch = db.batch()
        for (doc in children.documents) {
            // The parent's own self profile has no kid device that needs to verify a passcode
            // locally, and it's readable by every linked kid device (#18) - so the verifier is
            // never stored there (and any earlier copy is cleared here).
            val fields = if (doc.id == FirestorePaths.SELF_CHILD_ID) {
                mapOf("parentPasscodeHash" to null, "parentPasscodeSalt" to null)
            } else {
                mapOf("parentPasscodeHash" to hash, "parentPasscodeSalt" to salt)
            }
            batch.update(doc.reference, fields)
        }
        batch.commit().await()
    }
}
