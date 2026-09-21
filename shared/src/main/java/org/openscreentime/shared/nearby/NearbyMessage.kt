package org.openscreentime.shared.nearby

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a kid's phone and a parent's phone say to each other over a nearby link (see [NearbyLink]).
 *
 * This is the whole vocabulary, and it is deliberately tiny: a local build has no server to
 * negotiate anything with, so the two phones only ever exchange a request and an answer to it.
 * Nothing here carries usage data - a parent who wants to see how a phone is being used walks over
 * and looks at it, or builds the cloud flavor.
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

    fun encode(): ByteArray = json.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** Null for anything this build does not understand, rather than throwing on a stray packet. */
        fun decode(bytes: ByteArray): NearbyMessage? =
            runCatching { json.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8)) }.getOrNull()
    }
}
