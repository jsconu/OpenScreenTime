package org.openscreentime.kid.data

import android.content.Context
import org.openscreentime.shared.nearby.NearbyLinkStore
import org.openscreentime.shared.util.SharedPrefsStore

/**
 * Where this phone keeps what it knows about the phone it is linked to (see [NearbyLinkStore]).
 * One file, separate from the profile and the usage, so unlinking cannot take anything else with it.
 */
class NearbyStore(context: Context) {
    val keyValueStore = SharedPrefsStore(
        context.applicationContext.getSharedPreferences("nearby_link", Context.MODE_PRIVATE)
    )
    val linkStore = NearbyLinkStore(keyValueStore)
}
