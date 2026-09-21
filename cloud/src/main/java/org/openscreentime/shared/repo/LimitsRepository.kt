package org.openscreentime.shared.repo

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import org.openscreentime.shared.model.AppList
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.FocusProfile
import org.openscreentime.shared.model.TrackingToggle

/** Reading/writing a child's limits, and the kid-initiated propose/approve/decline flow (see #14). */
internal class LimitsRepository(private val db: FirebaseFirestore) {

    suspend fun updateDailyLimit(parentUid: String, childId: String, minutes: Int) {
        db.document(FirestorePaths.childDoc(parentUid, childId))
            .update("dailyLimitMinutes", minutes).await()
    }

    /**
     * Sets ONE app's limit without rewriting the others, so two people editing different apps at the same
     * moment don't undo each other (see #39). Merged as a nested map key (not a dotted field path), since a
     * package name contains dots.
     */
    suspend fun setAppLimit(parentUid: String, childId: String, packageName: String, minutes: Int) {
        db.document(FirestorePaths.childDoc(parentUid, childId))
            .set(mapOf("appLimits" to mapOf(packageName to minutes)), SetOptions.merge()).await()
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

    // The list edits below add or remove ONE entry atomically (arrayUnion / arrayRemove) instead of
    // writing back a whole list computed from a possibly-stale screen, so two editors - a parent, and the
    // kid's passcode-gated Parent controls - can't silently undo each other (see #39).

    suspend fun addBlockedDomain(parentUid: String, childId: String, domain: String) = arrayEdit(parentUid, childId, "blockedDomains", domain, add = true)

    suspend fun removeBlockedDomain(parentUid: String, childId: String, domain: String) = arrayEdit(parentUid, childId, "blockedDomains", domain, add = false)

    /** Adds or removes ONE app from one of the per-app lists (see [AppList]), without touching the others in it. */
    suspend fun setAppListMember(parentUid: String, childId: String, list: AppList, packageName: String, member: Boolean) =
        arrayEdit(parentUid, childId, list.field, packageName, add = member)

    // "Dumb phone" (Focus mode), see #42. Parent-only writes.

    suspend fun setFocusMode(parentUid: String, childId: String, enabled: Boolean) {
        db.document(FirestorePaths.childDoc(parentUid, childId)).update("focusMode", enabled).await()
    }

    suspend fun setFocusProfile(parentUid: String, childId: String, profile: FocusProfile) {
        db.document(FirestorePaths.childDoc(parentUid, childId)).update("focusProfile", profile.wireValue).await()
    }

    private suspend fun arrayEdit(parentUid: String, childId: String, field: String, value: String, add: Boolean) {
        db.document(FirestorePaths.childDoc(parentUid, childId))
            .update(field, if (add) FieldValue.arrayUnion(value) else FieldValue.arrayRemove(value)).await()
    }

    /** See #35 - one of the parent-controlled tracking/display toggles. */
    suspend fun setTrackingToggle(parentUid: String, childId: String, toggle: TrackingToggle, enabled: Boolean) {
        db.document(FirestorePaths.childDoc(parentUid, childId))
            .update(toggle.field, enabled).await()
    }

    /** See #34 - phone numbers that can still call/text through a bedtime block. */
    suspend fun addAllowedContact(parentUid: String, childId: String, number: String) =
        arrayEdit(parentUid, childId, "alwaysAllowedContacts", number, add = true)

    suspend fun removeAllowedContact(parentUid: String, childId: String, number: String) =
        arrayEdit(parentUid, childId, "alwaysAllowedContacts", number, add = false)

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

    /** See #23 - kid-initiated, no passcode required, same autonomy-supportive pattern as [proposeLimits]. */
    suspend fun requestExtraTime(parentUid: String, childId: String, minutes: Int) {
        db.document(FirestorePaths.childDoc(parentUid, childId))
            .update("requestedExtraMinutes", minutes).await()
    }

    /** Grants [minutes] of temporary unlock starting now, and clears the pending request. */
    suspend fun grantExtraTime(parentUid: String, childId: String, minutes: Int) {
        db.document(FirestorePaths.childDoc(parentUid, childId))
            .update(
                mapOf(
                    "temporaryUnlockUntilMs" to System.currentTimeMillis() + minutes * 60_000L,
                    "requestedExtraMinutes" to null
                )
            )
            .await()
    }

    /** Clears a pending request without granting it. */
    suspend fun declineExtraTimeRequest(parentUid: String, childId: String) {
        db.document(FirestorePaths.childDoc(parentUid, childId))
            .update("requestedExtraMinutes", null).await()
    }
}
