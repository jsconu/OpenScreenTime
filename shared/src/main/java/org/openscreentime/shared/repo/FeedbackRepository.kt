package org.openscreentime.shared.repo

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * In-app feedback (see #22) - write-only from the client. Nobody, not even the parent who
 * submitted it, can read it back through the app; it's reviewed via the Firebase console.
 * Deliberately not a mailto: link - that would show the destination address to every user
 * who ever taps "Feedback," which the address-masking work in #21 was specifically trying
 * to avoid.
 */
internal class FeedbackRepository(private val db: FirebaseFirestore) {
    suspend fun submit(parentUid: String, text: String, appVersion: String, device: String) {
        db.collection(FirestorePaths.FEEDBACK).add(
            mapOf(
                "parentUid" to parentUid,
                "text" to text,
                "appVersion" to appVersion,
                "device" to device,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }
}
