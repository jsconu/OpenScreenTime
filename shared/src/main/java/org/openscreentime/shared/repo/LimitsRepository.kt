package org.openscreentime.shared.repo

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.openscreentime.shared.model.ChildProfile

/** Reading/writing a child's limits, and the kid-initiated propose/approve/decline flow (see #14). */
internal class LimitsRepository(private val db: FirebaseFirestore) {

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

    suspend fun updateBedtimeWindow(parentUid: String, childId: String, startMinutes: Int?, endMinutes: Int?) {
        db.document(FirestorePaths.childDoc(parentUid, childId))
            .update(mapOf("bedtimeStartMinutes" to startMinutes, "bedtimeEndMinutes" to endMinutes))
            .await()
    }

    suspend fun updateBlockedDomains(parentUid: String, childId: String, domains: List<String>) {
        db.document(FirestorePaths.childDoc(parentUid, childId))
            .update("blockedDomains", domains).await()
    }

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

    suspend fun approveProposal(parentUid: String, childId: String, child: ChildProfile) {
        val updates = mutableMapOf<String, Any?>(
            "proposedDailyLimitMinutes" to null,
            "proposedAppLimits" to null
        )
        child.proposedDailyLimitMinutes?.let { updates["dailyLimitMinutes"] = it }
        child.proposedAppLimits?.let { updates["appLimits"] = it }
        db.document(FirestorePaths.childDoc(parentUid, childId)).update(updates).await()
    }

    suspend fun declineProposal(parentUid: String, childId: String) {
        db.document(FirestorePaths.childDoc(parentUid, childId))
            .update(mapOf("proposedDailyLimitMinutes" to null, "proposedAppLimits" to null))
            .await()
    }
}
