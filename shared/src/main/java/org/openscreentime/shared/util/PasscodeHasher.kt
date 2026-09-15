package org.openscreentime.shared.util

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-HMAC-SHA256 hashing for the family passcode, deliberately slow so that
 * a leaked hash+salt (e.g. read off a paired kid device, which legitimately has
 * access to it for local "parent mode" verification) can't be brute-forced
 * offline in a fraction of a second the way a single round of SHA-256 could be
 * for a 4-6 digit PIN. Callers should run [hash]/[verify] off the main thread -
 * that cost is the point, but it means it shouldn't run on the UI thread.
 */
object PasscodeHasher {
    private const val SALT_BYTES = 16
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun randomSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(passcode: String, salt: String): String {
        val spec = PBEKeySpec(passcode.toCharArray(), salt.toByteArray(Charsets.UTF_8), ITERATIONS, KEY_LENGTH_BITS)
        val secret = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec)
        return secret.encoded.toHex()
    }

    fun verify(passcode: String, salt: String, expectedHash: String): Boolean =
        hash(passcode, salt) == expectedHash

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
