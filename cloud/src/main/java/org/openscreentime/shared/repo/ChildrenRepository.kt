package org.openscreentime.shared.repo

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import org.openscreentime.shared.model.ChildProfile
import kotlin.random.Random

/** Child-profile lifecycle: creating, locking, listening, deleting, and the parent's own self-tracked profile. */
internal class ChildrenRepository(
    private val db: FirebaseFirestore,
    private val passcodes: PasscodeRepository
) {
    suspend fun createChild(parentUid: String, name: String): ChildProfile {
        val code = generatePairingCode()
        val docRef = db.collection(FirestorePaths.childrenCollection(parentUid)).document()
        val existingPasscode = passcodes.getParentPasscode(parentUid)
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

    /**
     * A fresh pairing code for a child that isn't paired yet. A code only works for 30 minutes, so a parent whose
     * code ran out needs another without deleting and re-adding the child. The new code doc is written first, then
     * the child is pointed at it.
     */
    suspend fun regeneratePairingCode(parentUid: String, childId: String): String {
        val code = generatePairingCode()
        db.collection(FirestorePaths.PAIRING_CODES).document(code).set(
            mapOf(
                "parentUid" to parentUid,
                "childId" to childId,
                "used" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        db.document(FirestorePaths.childDoc(parentUid, childId)).update("pairingCode", code).await()
        return code
    }

    suspend fun rename(parentUid: String, childId: String, name: String) {
        db.document(FirestorePaths.childDoc(parentUid, childId)).update("name", name.trim()).await()
    }

    suspend fun setLocked(parentUid: String, childId: String, locked: Boolean) {
        db.document(FirestorePaths.childDoc(parentUid, childId))
            .update("locked", locked).await()
    }

    fun listenChildren(parentUid: String, onChange: (List<ChildProfile>) -> Unit): ListenerRegistration =
        db.collection(FirestorePaths.childrenCollection(parentUid))
            .addSnapshotListener { snap, error ->
                // An error snapshot isn't "no children" - ignore it and keep what's on screen.
                if (error != null || snap == null) return@addSnapshotListener
                onChange(snap.documents.map { ChildProfile.fromMap(it.id, it.data ?: emptyMap()) })
            }

    fun listenChild(parentUid: String, childId: String, onChange: (ChildProfile) -> Unit): ListenerRegistration =
        db.document(FirestorePaths.childDoc(parentUid, childId))
            .addSnapshotListener { snap, error ->
                // An error snapshot must not be delivered as an all-defaults profile: the kid
                // device would read that as "not locked, no bedtime" and drop enforcement.
                if (error != null || snap == null) return@addSnapshotListener
                onChange(ChildProfile.fromMap(childId, snap.data ?: emptyMap()))
            }

    suspend fun getOrCreateSelfProfile(parentUid: String, name: String): ChildProfile {
        val docRef = db.document(FirestorePaths.childDoc(parentUid, FirestorePaths.SELF_CHILD_ID))
        val existing = docRef.get().await()
        if (existing.exists()) {
            // Older versions copied the passcode verifier onto this doc, which every linked kid
            // device can read (#18). Clear it if it's still there.
            if (existing.getString("parentPasscodeHash") != null) {
                try {
                    docRef.update(mapOf("parentPasscodeHash" to null, "parentPasscodeSalt" to null)).await()
                } catch (e: Exception) {
                    // Best effort - the next passcode change clears it too.
                }
            }
            return ChildProfile.fromMap(existing.id, existing.data ?: emptyMap())
        }

        val self = ChildProfile(
            id = FirestorePaths.SELF_CHILD_ID,
            name = name,
            paired = true,
            deviceUid = parentUid,
            isSelf = true
        )
        docRef.set(self.toMap()).await()
        return self
    }

    suspend fun getParentSelfProfile(parentUid: String): ChildProfile? = try {
        val snap = db.document(FirestorePaths.childDoc(parentUid, FirestorePaths.SELF_CHILD_ID)).get().await()
        if (snap.exists()) ChildProfile.fromMap(snap.id, snap.data ?: emptyMap()) else null
    } catch (e: Exception) {
        null
    }

    /** Firestore doesn't cascade-delete subcollections, so dailyStats docs are removed explicitly first. */
    suspend fun deleteChild(parentUid: String, childId: String) {
        val statsSnap = db.collection(FirestorePaths.dailyStatsCollection(parentUid, childId)).get().await()
        if (!statsSnap.isEmpty) {
            val batch = db.batch()
            for (doc in statsSnap.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
        }
        // Same for the installed-apps doc (a plain delete, so a child that never had one is fine).
        db.document(FirestorePaths.installedAppsDoc(parentUid, childId)).delete().await()
        db.document(FirestorePaths.childDoc(parentUid, childId)).delete().await()
    }

    private fun generatePairingCode(): String = (100000 + Random.nextInt(900000)).toString()
}
