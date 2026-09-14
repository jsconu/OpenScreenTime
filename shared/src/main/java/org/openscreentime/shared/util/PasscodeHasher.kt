package org.openscreentime.shared.util

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Salted SHA-256 hashing for the family passcode. This is meant to keep a
 * casual look at Firestore from revealing the passcode, and to stop a kid
 * from guessing it by brute force through the UI (see the rate limiting in
 * the passcode-entry screens) - it is not meant to resist a determined
 * offline attack, which is out of scope for a 4-6 digit family PIN.
 */
object PasscodeHasher {
    private const val SALT_BYTES = 16

    fun randomSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(passcode: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((salt + passcode).toByteArray(Charsets.UTF_8))
        return bytes.toHex()
    }

    fun verify(passcode: String, salt: String, expectedHash: String): Boolean =
        hash(passcode, salt) == expectedHash

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
