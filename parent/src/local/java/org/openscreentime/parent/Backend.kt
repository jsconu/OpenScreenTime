package org.openscreentime.parent

import android.app.Application
import android.content.Context
import org.openscreentime.parent.data.UsageStore
import org.openscreentime.parent.monitor.SelfDeviceState
import org.openscreentime.shared.repo.FamilyRepository
import org.openscreentime.shared.repo.LocalFamilyRepository
import org.openscreentime.shared.repo.Profiles
import org.openscreentime.shared.util.buildDailyStats
import org.openscreentime.shared.util.trackingChoices

/**
 * The local flavor: this phone tracks and limits itself, and nothing else. No account, no children,
 * no Firebase SDK in the build at all - the `:cloud` module is not a dependency of this flavor.
 *
 * What is left is the parent app as a tool for your own phone: your usage, your limits and bedtime,
 * dumb phone mode, the calm list and the weekly report, none of which ever needed a network. What is
 * gone is everything that needs a second phone: adding a child, pairing, seeing a child's usage and
 * locking their phone from here.
 *
 * The cloud flavor has its own copy of this file at `src/cloud/java/.../Backend.kt`.
 */
object Backend {

    /** Lets a screen leave out what only makes sense with a second phone, rather than offering it. */
    const val IS_LOCAL = true

    fun createRepository(app: Application): FamilyRepository = LocalFamilyRepository(
        context = app,
        todayStats = {
            buildDailyStats(UsageStore(app), SelfDeviceState.trackingChoices(), System.currentTimeMillis())
        }
    )

    /** Nothing to wire up: there is no backend service to point anywhere. */
    fun onAppCreate(app: Application) = Unit

    /**
     * Which profile this phone is keeping. Nullable only to match the cloud flavor, where it is null
     * until self-tracking has been started; locally this phone always has its own profile.
     */
    fun profileIds(context: Context): Pair<String, String>? = LOCAL_UID to Profiles.SELF_CHILD_ID

    private const val LOCAL_UID = "local"
}
