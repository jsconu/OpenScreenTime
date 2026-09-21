package org.openscreentime.shared.nearby

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a kid's phone and a parent's phone say to each other over a nearby link (see [NearbyLink]).
 *
 * This is the whole vocabulary. It covers what a family actually needs between two phones: the
 * kid's phone reports what it has been used for, the parent's phone sends back the limits it should
 * keep to, and either side can raise a request - more time, or a suggested change - that the other
 * answers. That is a sync, not just a message channel: after one exchange on the home Wi-Fi, the
 * parent's phone knows today's usage and the kid's phone knows the current limits.
 *
 * Every message is wrapped in an authenticated envelope before it leaves the phone (see
 * [NearbyEnvelope]); nothing on this link is accepted without the key the two phones agreed when
 * they were physically together.
 */
@Serializable
sealed class NearbyMessage {

    /** The id a reply quotes, so an answer can be matched to what it answers. */
    abstract val id: String

    /** "Can I have 15 more minutes?" - sent from a block screen, while the kid is actually blocked. */
    @Serializable
    data class MoreTimeRequest(override val id: String, val minutes: Int, val childName: String) : NearbyMessage()

    /** "Could my daily limit be 90 minutes?" - the negotiated-limits idea (#14), without a server. */
    @Serializable
    data class LimitSuggestion(
        override val id: String,
        val childName: String,
        val dailyLimitMinutes: Int? = null,
        val appLimits: Map<String, Int> = emptyMap()
    ) : NearbyMessage()

    /**
     * A parent's answer. [minutes] is set only when granting extra time, and [appliedLimits] only
     * when a suggestion was accepted, so a kid phone applies exactly what was agreed and no more.
     */
    @Serializable
    data class Answer(
        override val id: String,
        val granted: Boolean,
        val minutes: Int? = null,
        val dailyLimitMinutes: Int? = null,
        val appliedLimits: Map<String, Int> = emptyMap()
    ) : NearbyMessage()

    /**
     * The kid's phone telling the parent's phone how the day has gone. Sent whenever the two are on
     * the same network, so a parent sees usage without the child's phone having to reach a server -
     * and sees nothing at all while they are apart, which is the honest cost of keeping it local.
     */
    @Serializable
    data class UsageReport(
        override val id: String,
        val childName: String,
        val stats: org.openscreentime.shared.model.DailyStats,
        /** Earlier days the parent's phone may not have seen yet, oldest first. */
        val recentDays: List<org.openscreentime.shared.model.DailyStats> = emptyList()
    ) : NearbyMessage()

    /**
     * The parent's phone sending limits down to the kid's phone: the same fields a parent can
     * change in the app, applied on arrival. This is what makes the parent's phone a place to
     * manage from, rather than somewhere to read a report.
     */
    @Serializable
    data class LimitsUpdate(
        override val id: String,
        val dailyLimitMinutes: Int? = null,
        val appLimits: Map<String, Int>? = null,
        val locked: Boolean? = null,
        val bedtimeStartMinutes: Int? = null,
        val bedtimeEndMinutes: Int? = null,
        val temporaryUnlockUntilMs: Long? = null
    ) : NearbyMessage()

    fun encode(): ByteArray = json.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** Null for anything this build does not understand, rather than throwing on a stray packet. */
        fun decode(bytes: ByteArray): NearbyMessage? =
            runCatching { json.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8)) }.getOrNull()
    }
}
