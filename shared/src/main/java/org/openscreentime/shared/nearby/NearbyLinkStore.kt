package org.openscreentime.shared.nearby

import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.util.KeyValueStore
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * What a phone remembers about the phone it is linked to: the key, its name, when they last
 * managed to talk, and - on a parent's phone - the last usage the other one reported.
 *
 * **When they last talked is not a detail.** Two phones on a local link are often apart, and a
 * parent looking at numbers from Tuesday must not think they are looking at now. Every screen that
 * shows synced usage is expected to show [lastSyncedAtMs] beside it, which is why this keeps it
 * rather than leaving each screen to guess.
 */
class NearbyLinkStore(private val store: KeyValueStore) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val isLinked: Boolean get() = link() != null

    fun link(): NearbyLink? {
        val encoded = store.getString(KEY_KEY, null) ?: return null
        val key = runCatching { Base64.getUrlDecoder().decode(encoded) }.getOrNull() ?: return null
        if (key.size != NearbyEnvelope.KEY_BYTES) return null
        return NearbyLink(key, store.getString(KEY_PEER_NAME, null) ?: "The other phone")
    }

    fun save(link: NearbyLink) = store.edit {
        putString(KEY_KEY, Base64.getUrlEncoder().withoutPadding().encodeToString(link.key))
        putString(KEY_PEER_NAME, link.peerName)
    }

    /** Forgetting a link is the whole of unlinking: without the key, neither phone can reach the other. */
    fun forget() = store.edit {
        remove(KEY_KEY)
        remove(KEY_PEER_NAME)
        remove(KEY_LAST_SYNCED)
        remove(KEY_LAST_STATS)
        remove(KEY_CHILD_NAME)
    }

    /** 0 when these two phones have never managed to reach each other. */
    val lastSyncedAtMs: Long get() = store.getLong(KEY_LAST_SYNCED, 0)

    fun recordSync(atMs: Long) = store.edit { putLong(KEY_LAST_SYNCED, atMs) }

    // --- The parent's copy of what the kid's phone reported ---

    fun saveReport(report: NearbyMessage.UsageReport, atMs: Long) = store.edit {
        putString(KEY_LAST_STATS, json.encodeToString(report.stats))
        putString(KEY_CHILD_NAME, report.childName)
        putLong(KEY_LAST_SYNCED, atMs)
    }

    fun lastStats(): DailyStats? = store.getString(KEY_LAST_STATS, null)
        ?.let { runCatching { json.decodeFromString<DailyStats>(it) }.getOrNull() }

    fun childName(): String? = store.getString(KEY_CHILD_NAME, null)

    private companion object {
        const val KEY_KEY = "nearby_key"
        const val KEY_PEER_NAME = "nearby_peer_name"
        const val KEY_LAST_SYNCED = "nearby_last_synced"
        const val KEY_LAST_STATS = "nearby_last_stats"
        const val KEY_CHILD_NAME = "nearby_child_name"
    }
}
