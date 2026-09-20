package org.openscreentime.shared.model

/**
 * Stops a re-posted notification from being counted twice (see #39). Android re-posts the same notification
 * (same key) whenever an app updates it - marking it read, refreshing an unchanged card - and the counters
 * used to count every post. A real new message changes the text, so it still counts; an identical re-post
 * doesn't. Only a hash of the content is remembered, in memory, for the most recent [capacity] keys.
 */
class NotificationDeduper(private val capacity: Int = 200) {
    private val lastSeen = object : LinkedHashMap<String, Int>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?): Boolean = size > capacity
    }

    /** True the first time [key] is seen with this [title]/[text], and again whenever either changes. */
    @Synchronized
    fun shouldCount(key: String, title: String, text: String): Boolean {
        val hash = 31 * title.hashCode() + text.hashCode()
        val previous = lastSeen[key]
        if (previous == hash) return false
        lastSeen[key] = hash
        return true
    }
}
