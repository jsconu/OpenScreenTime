package org.openscreentime.kid

import android.app.Application
import android.content.Context
import org.openscreentime.kid.data.UsageStore
import org.openscreentime.kid.monitor.LiveChildState
import org.openscreentime.shared.repo.FamilyRepository
import org.openscreentime.shared.repo.LocalFamilyRepository
import org.openscreentime.shared.repo.Profiles
import org.openscreentime.shared.util.buildDailyStats
import org.openscreentime.shared.util.trackingChoices

/**
 * The local flavor: this phone is the whole system. No account, no pairing, no Firebase SDK in the
 * build at all - the `:cloud` module is not a dependency of this flavor, so there is nothing here
 * that could talk to a server even by mistake.
 *
 * Limits are set on this phone, behind the family passcode, in Parent controls. What the phone does
 * by itself is unchanged: it counts time, enforces limits and bedtime, runs dumb phone mode and
 * keeps the calm list, none of which ever needed a network.
 *
 * The cloud flavor has its own copy of this file at `src/cloud/java/.../Backend.kt`.
 */
object Backend {

    /** Lets a screen leave out what only makes sense with a second phone, rather than offering it. */
    const val IS_LOCAL = true

    fun createRepository(app: Application): FamilyRepository = LocalFamilyRepository(
        context = app,
        todayStats = {
            buildDailyStats(UsageStore(app), LiveChildState.trackingChoices(), System.currentTimeMillis())
        },
        defaultName = "This phone",
        isSelfProfile = false
    )

    /** Nothing to wire up: there is no backend service to point anywhere. */
    fun onAppCreate(app: Application) = Unit

    /**
     * Which profile this phone is keeping. Nullable only to match the cloud flavor, where it is null
     * until a phone has been paired; locally there is exactly one profile and it is always here.
     */
    fun profileIds(context: Context): Pair<String, String>? = LOCAL_UID to Profiles.SELF_CHILD_ID

    private const val LOCAL_UID = "local"
}
