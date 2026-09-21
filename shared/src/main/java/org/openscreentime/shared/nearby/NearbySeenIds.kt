package org.openscreentime.shared.nearby

import org.openscreentime.shared.util.KeyValueStore

/**
 * Which messages this phone has already acted on.
 *
 * Sealing a message proves who wrote it, not when. Without this, anyone who captured one grant of
 * fifteen minutes off the network could replay it every evening forever, and the kid phone would
 * apply it every time - the bytes really are from the parent's phone, after all. A phone therefore
 * acts on an id once and ignores it ever after.
 *
 * Bounded on purpose: only the most recent [LIMIT] ids are kept, oldest dropped first, because this
 * has to live in a phone's preferences and a link that has exchanged thousands of messages has no
 * use for the first of them. An attacker who could wait out [LIMIT] fresh messages to retry an old
 * one could simply ask the parent instead.
 */
class NearbySeenIds(private val store: KeyValueStore) {

    /** True the first time an id is offered, false every time after - the caller acts only on true. */
    fun claim(id: String): Boolean {
        val seen = order()
        if (id in seen) return false
        store.edit {
            putString(KEY_ORDER, (seen + id).takeLast(LIMIT).joinToString(SEPARATOR))
        }
        return true
    }

    fun hasSeen(id: String): Boolean = id in order()

    private fun order(): List<String> =
        store.getString(KEY_ORDER, null)?.split(SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()

    private companion object {
        const val KEY_ORDER = "nearby_seen_ids"
        const val SEPARATOR = "\n"
        const val LIMIT = 200
    }
}
