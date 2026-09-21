package org.openscreentime.kid.nearby

import android.content.Context
import android.util.Log
import org.openscreentime.kid.data.NearbyStore
import org.openscreentime.kid.data.PairingStore
import org.openscreentime.kid.data.UsageStore
import org.openscreentime.kid.monitor.LiveChildState
import org.openscreentime.shared.nearby.LanNearbyTransport
import org.openscreentime.shared.nearby.NearbyMessage
import org.openscreentime.shared.nearby.NearbySeenIds
import org.openscreentime.shared.nearby.NearbySync
import kotlinx.coroutines.runBlocking
import org.openscreentime.shared.repo.FamilyRepository
import org.openscreentime.shared.repo.Profiles
import org.openscreentime.shared.util.buildDailyStats
import org.openscreentime.shared.util.trackingChoices
import java.util.UUID

/**
 * One attempt at reaching the linked parent's phone: send what this phone has been used for, and
 * apply whatever limits come back.
 *
 * **Failing is normal and must stay quiet.** The two phones are apart most of the day, and a kid's
 * phone that complained every time it could not find a parent's would be unusable. A failed attempt
 * changes nothing at all - this phone keeps enforcing the limits it already has, which is the
 * behaviour that makes a local build trustworthy in the first place.
 */
class NearbySyncRunner(private val context: Context) {

    private val store = NearbyStore(context)
    private val seen = NearbySeenIds(store.keyValueStore)

    /** True when the parent's phone answered, so a screen can say when this last worked. */
    fun syncNow(repository: FamilyRepository): Boolean {
        val link = store.linkStore.link() ?: return false
        val childName = PairingStore(context).childName ?: "This phone"
        val today = buildDailyStats(UsageStore(context), LiveChildState.trackingChoices(), System.currentTimeMillis())

        val answer = runCatching {
            LanNearbyTransport(context, link).ask(
                NearbySync.report(UUID.randomUUID().toString(), childName, today, emptyList())
            )
        }.onFailure { Log.d(TAG, "No parent phone reachable right now", it) }.getOrNull() ?: return false

        if (answer !is NearbyMessage.LimitsUpdate) return false
        // A limits update that has already been applied is a replay, and re-applying an old one
        // would quietly undo whatever the parent changed since.
        if (!seen.claim(answer.id)) return false

        apply(repository, answer)
        store.linkStore.recordSync(System.currentTimeMillis())
        return true
    }

    /** Asks for more time. The answer is never a grant - a parent decides, on their own phone. */
    fun requestMoreTime(minutes: Int): Boolean = send(
        NearbyMessage.MoreTimeRequest(
            id = UUID.randomUUID().toString(),
            minutes = minutes,
            childName = PairingStore(context).childName ?: "This phone"
        )
    )

    fun suggestLimit(dailyLimitMinutes: Int): Boolean = send(
        NearbyMessage.LimitSuggestion(
            id = UUID.randomUUID().toString(),
            childName = PairingStore(context).childName ?: "This phone",
            dailyLimitMinutes = dailyLimitMinutes
        )
    )

    /** True only when the parent's phone actually took it, so a screen never claims a message got through. */
    private fun send(message: NearbyMessage): Boolean {
        val link = store.linkStore.link() ?: return false
        val answer = runCatching { LanNearbyTransport(context, link).ask(message) }.getOrNull()
        return answer is NearbyMessage.Answer
    }

    /**
     * Only the fields the parent's phone actually sent are written - the same rule as
     * [NearbySync.applyLimits], expressed through the repository so the change is saved and every
     * listener (enforcement, the website filter, call screening) picks it up the usual way.
     */
    private fun apply(repository: FamilyRepository, update: NearbyMessage.LimitsUpdate) = runBlocking {
        val uid = repository.currentUid ?: return@runBlocking
        val childId = Profiles.SELF_CHILD_ID
        runCatching {
            update.dailyLimitMinutes?.let { repository.updateDailyLimit(uid, childId, it) }
            update.locked?.let { repository.setLocked(uid, childId, it) }
            if (update.bedtimeStartMinutes != null || update.bedtimeEndMinutes != null) {
                repository.updateBedtimeWindow(uid, childId, update.bedtimeStartMinutes, update.bedtimeEndMinutes)
            }
            update.appLimits?.forEach { (packageName, minutes) ->
                repository.setAppLimit(uid, childId, packageName, minutes)
            }
            // Without this, Parent controls on this phone has nothing to check a code against and
            // stays shut for good - see ParentModeUnlockScreen.
            val hash = update.parentPasscodeHash
            val salt = update.parentPasscodeSalt
            if (hash != null && salt != null) {
                repository.setParentPasscode(uid, hash, salt)
            }
            update.temporaryUnlockUntilMs?.let { until ->
                val minutes = ((until - System.currentTimeMillis()) / 60_000L).toInt()
                if (minutes > 0) repository.grantExtraTime(uid, childId, minutes)
            }
        }.onFailure { Log.w(TAG, "Could not apply the limits that arrived", it) }
    }

    private companion object {
        const val TAG = "NearbySync"
    }
}
