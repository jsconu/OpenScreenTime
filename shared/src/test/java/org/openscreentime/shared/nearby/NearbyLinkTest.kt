package org.openscreentime.shared.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openscreentime.shared.util.InMemoryKeyValueStore

/**
 * The nearby link is what lets a kid's phone ask a parent's phone for more time with no server
 * involved. It can change what a child's phone allows, so these are the tests that matter: that a
 * message only opens with the right key, that a changed one does not open at all, and that a
 * captured one cannot be used twice.
 */
class NearbyLinkTest {

    private val key = NearbyEnvelope.newKey()

    @Test
    fun `a request survives the round trip intact`() {
        val request = NearbyMessage.MoreTimeRequest(id = "r1", minutes = 15, childName = "Sam")

        val opened = NearbyEnvelope.open(NearbyEnvelope.seal(request, key), key)

        assertEquals(request, opened)
    }

    @Test
    fun `an answer carrying agreed limits survives the round trip intact`() {
        val answer = NearbyMessage.Answer(
            id = "r2",
            granted = true,
            minutes = 15,
            dailyLimitMinutes = 90,
            appliedLimits = mapOf("com.example.game" to 30)
        )

        assertEquals(answer, NearbyEnvelope.open(NearbyEnvelope.seal(answer, key), key))
    }

    @Test
    fun `somebody else on the same wifi cannot read a message`() {
        val sealed = NearbyEnvelope.seal(NearbyMessage.MoreTimeRequest("r3", 15, "Sam"), key)

        assertNull(NearbyEnvelope.open(sealed, NearbyEnvelope.newKey()))
    }

    @Test
    fun `a message with one bit changed does not open at all`() {
        val sealed = NearbyEnvelope.seal(NearbyMessage.Answer("r4", granted = true, minutes = 15), key)
        // Somewhere in the ciphertext, past the version byte and the nonce.
        sealed[sealed.size - 2] = (sealed[sealed.size - 2].toInt() xor 0x01).toByte()

        assertNull("A tampered message must be refused, not delivered wrong", NearbyEnvelope.open(sealed, key))
    }

    @Test
    fun `a truncated or empty packet is refused rather than crashing`() {
        val sealed = NearbyEnvelope.seal(NearbyMessage.MoreTimeRequest("r5", 15, "Sam"), key)

        assertNull(NearbyEnvelope.open(ByteArray(0), key))
        assertNull(NearbyEnvelope.open(sealed.copyOfRange(0, 8), key))
    }

    @Test
    fun `a message from a future version is refused rather than half understood`() {
        val sealed = NearbyEnvelope.seal(NearbyMessage.MoreTimeRequest("r6", 15, "Sam"), key)
        sealed[0] = (NearbyEnvelope.VERSION + 1).toByte()

        assertNull(NearbyEnvelope.open(sealed, key))
    }

    @Test
    fun `sealing the same message twice never produces the same bytes`() {
        val message = NearbyMessage.MoreTimeRequest("r7", 15, "Sam")

        // A fresh nonce each time: otherwise an observer could tell that the same request was
        // repeated, and reusing a nonce with the same key breaks GCM outright.
        assertNotEquals(
            NearbyEnvelope.seal(message, key).toList(),
            NearbyEnvelope.seal(message, key).toList()
        )
    }

    @Test
    fun `a captured grant cannot be replayed`() {
        val seen = NearbySeenIds(InMemoryKeyValueStore())

        assertTrue("The first delivery is acted on", seen.claim("grant-1"))
        assertFalse("A replay of the same grant is ignored", seen.claim("grant-1"))
        assertTrue("A genuinely new grant still gets through", seen.claim("grant-2"))
    }

    @Test
    fun `the seen list stays bounded without forgetting what just happened`() {
        val seen = NearbySeenIds(InMemoryKeyValueStore())
        repeat(500) { seen.claim("id-$it") }

        assertTrue("What just happened is still known", seen.hasSeen("id-499"))
        assertFalse("The oldest are dropped rather than growing forever", seen.hasSeen("id-0"))
    }

    @Test
    fun `a QR payload round trips`() {
        val link = NearbyLink(key, "Mum's phone")

        assertEquals(link, NearbyLink.fromPayload(NearbyLink.toPayload(link)))
    }

    @Test
    fun `scanning somebody elses QR code simply does nothing`() {
        assertNull(NearbyLink.fromPayload("https://example.com"))
        assertNull(NearbyLink.fromPayload(""))
        assertNull(NearbyLink.fromPayload("ost1:not-base64!!:Phone"))
        // Right shape, wrong key length - a code from some other scheme that happens to collide.
        assertNull(NearbyLink.fromPayload("ost1:c2hvcnQ:Phone"))
    }

    @Test
    fun `a payload without a name still links, with something to call the phone`() {
        val payload = NearbyLink.toPayload(NearbyLink(key, "")).removeSuffix(":")

        val link = NearbyLink.fromPayload(payload)

        assertEquals(key.toList(), link?.key?.toList())
        assertTrue("A person needs some name for the other phone", link?.peerName?.isNotBlank() == true)
    }
}
