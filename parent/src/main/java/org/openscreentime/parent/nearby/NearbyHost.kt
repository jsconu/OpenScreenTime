package org.openscreentime.parent.nearby

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.openscreentime.parent.data.NearbyStore
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.nearby.LanNearbyTransport
import org.openscreentime.shared.nearby.NearbyMessage
import org.openscreentime.shared.nearby.NearbySeenIds
import org.openscreentime.shared.nearby.NearbySync
import java.io.Closeable
import java.util.UUID

/**
 * The parent's phone listening for its linked kid's phone.
 *
 * Deliberately started and stopped with the app process rather than run as a service of its own:
 * one more always-on foreground notification, to hold a socket that is only useful while both
 * phones are on the same Wi-Fi, is a bad trade on a phone's battery and on a person's attention.
 * The practical effect is that a report arrives while the parent's app is running or its
 * self-tracking service is alive, and otherwise waits for the next time the kid's phone tries -
 * which it keeps doing.
 */
class NearbyHost(private val context: Context) : Closeable {

    private val store = NearbyStore(context)
    private val seen = NearbySeenIds(store.keyValueStore)
    private val scope = CoroutineScope(Dispatchers.IO)
    private var listening: Closeable? = null

    /** What this phone currently wants for the linked child, read fresh for every exchange. */
    var currentProfile: () -> ChildProfile? = { null }

    /** Called on a background thread when a request needs a person; the app raises a notification. */
    var onRequest: (NearbyMessage) -> Unit = {}

    fun start() {
        val link = store.linkStore.link() ?: return
        if (listening != null) return
        scope.launch {
            listening = runCatching {
                LanNearbyTransport(context, link).host { message -> answer(message) }
            }.onFailure { Log.w(TAG, "Could not listen for a linked phone", it) }.getOrNull()
        }
    }

    private fun answer(message: NearbyMessage): NearbyMessage? {
        // A message this phone has already acted on is a replay, whoever sent it.
        if (!seen.claim(message.id)) return null
        val profile = currentProfile() ?: return null
        return NearbySync.answer(
            message = message,
            profile = profile,
            newId = { UUID.randomUUID().toString() },
            onUsage = { store.linkStore.saveReport(it, System.currentTimeMillis()) },
            onRequest = { onRequest(it) }
        )
    }

    override fun close() {
        listening?.let { runCatching { it.close() } }
        listening = null
    }

    private companion object {
        const val TAG = "NearbyHost"
    }
}
