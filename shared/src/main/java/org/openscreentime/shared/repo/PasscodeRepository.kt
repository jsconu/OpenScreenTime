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
            batch.update(doc.reference, mapOf("parentPasscodeHash" to hash, "parentPasscodeSalt" to salt))
        }
        batch.commit().await()
    }
}
