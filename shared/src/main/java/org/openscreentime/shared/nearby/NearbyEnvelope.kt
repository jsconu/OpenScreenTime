package org.openscreentime.shared.nearby

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The sealed envelope every [NearbyMessage] travels in.
 *
 * This link can change what a child's phone allows, so it has to be worth trusting: anyone else on
 * the same Wi-Fi can reach the same port, and a home network is not a safe room. AES-GCM with the
 * key the two phones agreed while they were together (see [NearbyLink]) gives both properties that
 * matter - an eavesdropper learns nothing, and a message that was tampered with, or sent by
 * somebody without the key, fails to open at all rather than arriving subtly wrong.
 *
 * Wire format, all bytes, no framing cleverness: a 1-byte version, a 12-byte nonce, then the GCM
 * ciphertext with its 16-byte tag. The version byte is there so a future build can change the
 * scheme without a phone on the old one silently misreading it.
 *
 * Replay is handled a layer up, not here: every message carries an id, and a phone ignores an id it
 * has already acted on (see NearbyRequestLog), so re-sending a captured "grant 15 minutes" achieves
 * nothing.
 */
object NearbyEnvelope {

    const val VERSION: Byte = 1
    const val KEY_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    private val random = SecureRandom()

    fun seal(message: NearbyMessage, key: ByteArray): ByteArray {
        require(key.size == KEY_BYTES) { "A nearby link key is $KEY_BYTES bytes" }
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        }
        val sealed = cipher.doFinal(message.encode())
        return ByteArray(1 + NONCE_BYTES + sealed.size).also { out ->
            out[0] = VERSION
            nonce.copyInto(out, 1)
            sealed.copyInto(out, 1 + NONCE_BYTES)
        }
    }

    /**
     * The message inside, or null for anything that is not exactly what it claims to be: a
     * different version, a truncated packet, a key that does not match, or a single flipped bit.
     * There is deliberately no way to tell those apart from the outside - a caller that could would
     * be an oracle for guessing at the key.
     */
    fun open(bytes: ByteArray, key: ByteArray): NearbyMessage? {
        if (key.size != KEY_BYTES) return null
        if (bytes.size <= 1 + NONCE_BYTES) return null
        if (bytes[0] != VERSION) return null
        return runCatching {
            val nonce = bytes.copyOfRange(1, 1 + NONCE_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            }
            cipher.doFinal(bytes, 1 + NONCE_BYTES, bytes.size - 1 - NONCE_BYTES)
        }.getOrNull()?.let(NearbyMessage::decode)
    }

    /** A fresh link key. Used once, when a parent's phone shows the QR code a kid's phone scans. */
    fun newKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
}
