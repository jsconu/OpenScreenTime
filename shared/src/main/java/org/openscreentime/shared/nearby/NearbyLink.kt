package org.openscreentime.shared.nearby

import java.util.Base64

/**
 * The link itself: a key two phones share, and the name each shows the other.
 *
 * It is agreed the only way that is safe without a server - in person. The parent's phone shows a
 * QR code carrying a freshly generated key, the kid's phone scans it, and from then on each knows
 * the other. Nothing about the link is registered anywhere, and no part of it ever leaves the two
 * phones.
 *
 * The same shape of secret is what an end-to-end encrypted cloud would need later (see
 * docs/LOCAL_AND_CLOUD.md), so this is deliberately not a Wi-Fi-only idea with a Wi-Fi-only key.
 */
data class NearbyLink(
    val key: ByteArray,
    /** What to call the phone at the other end, so a person can tell whose it is. */
    val peerName: String
) {
    init {
        require(key.size == NearbyEnvelope.KEY_BYTES) { "A nearby link key is ${NearbyEnvelope.KEY_BYTES} bytes" }
    }

    // Data classes compare arrays by identity, which would make two copies of the same link look
    // different. Worth overriding rather than leaving a trap for the next person.
    override fun equals(other: Any?): Boolean =
        this === other || (other is NearbyLink && key.contentEquals(other.key) && peerName == other.peerName)

    override fun hashCode(): Int = 31 * key.contentHashCode() + peerName.hashCode()

    override fun toString(): String = "NearbyLink(peerName=$peerName, key=<${key.size} bytes>)"

    companion object {

        /**
         * What the QR code on the parent's phone contains. Kept short so the code stays coarse and
         * easy to scan on a cracked screen in bad light: a scheme, the key, and the name.
         *
         * The key is in the QR in the clear, which is the point - a QR code is a private channel
         * between two phones held by the same person, in the same room, for a few seconds. Anyone
         * who can read it over their shoulder could equally pick up the phone.
         */
        fun toPayload(link: NearbyLink): String =
            "$SCHEME:${Base64.getUrlEncoder().withoutPadding().encodeToString(link.key)}:${link.peerName}"

        /** Null for anything that is not one of our codes, so scanning a random QR just does nothing. */
        fun fromPayload(payload: String): NearbyLink? {
            val parts = payload.split(":", limit = 3)
            if (parts.size < 2 || parts[0] != SCHEME) return null
            val key = runCatching { Base64.getUrlDecoder().decode(parts[1]) }.getOrNull()
                ?: return null
            if (key.size != NearbyEnvelope.KEY_BYTES) return null
            val name = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "A parent's phone"
            return NearbyLink(key, name)
        }

        /** Version-tagged, so a future format change is refused rather than half-understood. */
        const val SCHEME = "ost1"
    }
}
