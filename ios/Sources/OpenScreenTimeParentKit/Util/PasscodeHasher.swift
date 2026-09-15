import CryptoKit
import Foundation
import Security

/// PBKDF2-HMAC-SHA256 hashing for the family passcode - must byte-for-byte match
/// `shared/util/PasscodeHasher.kt` on the Android side (same iteration count, same salt
/// encoding, same hex output), since either platform's parent app may need to verify a
/// passcode that was originally set on the other one. Notably: the salt is stored as a hex
/// *string*, and hashing uses the UTF-8 bytes of that hex string as the PBKDF2 salt input -
/// not the raw bytes the hex decodes to. That's what the Kotlin implementation does
/// (`salt.toByteArray(Charsets.UTF_8)` on an already-hex-encoded string), so this mirrors
/// it exactly rather than "fixing" it, or the two platforms would disagree on every hash.
///
/// Implemented as a plain PBKDF2 (RFC 2898) on top of CryptoKit's `HMAC<SHA256>`, rather
/// than CommonCrypto's `CCKeyDerivationPBKDF` - CommonCrypto needs a bridging/module-map
/// shim to use from a Swift package, which is one more thing to get right unverified.
enum PasscodeHasher {
    private static let saltBytes = 16
    private static let iterations = 120_000
    private static let keyLengthBytes = 32 // 256 bits

    static func randomSalt() -> String {
        var bytes = [UInt8](repeating: 0, count: saltBytes)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return bytes.map { String(format: "%02x", $0) }.joined()
    }

    static func hash(passcode: String, salt: String) -> String {
        let derived = pbkdf2SHA256(
            password: Data(passcode.utf8),
            salt: Data(salt.utf8),
            iterations: iterations,
            keyLength: keyLengthBytes
        )
        return derived.map { String(format: "%02x", $0) }.joined()
    }

    static func verify(passcode: String, salt: String, expectedHash: String) -> Bool {
        hash(passcode: passcode, salt: salt) == expectedHash
    }

    private static func pbkdf2SHA256(password: Data, salt: Data, iterations: Int, keyLength: Int) -> Data {
        let hLen = 32
        let blockCount = Int(ceil(Double(keyLength) / Double(hLen)))
        let key = SymmetricKey(data: password)
        var derivedKey = Data()

        for blockIndex in 1...blockCount {
            let blockIndexBytes = withUnsafeBytes(of: UInt32(blockIndex).bigEndian) { Data($0) }
            var previousU = Data(HMAC<SHA256>.authenticationCode(for: salt + blockIndexBytes, using: key))
            var blockResult = previousU

            if iterations > 1 {
                for _ in 2...iterations {
                    let nextU = Data(HMAC<SHA256>.authenticationCode(for: previousU, using: key))
                    for i in 0..<blockResult.count {
                        blockResult[i] ^= nextU[i]
                    }
                    previousU = nextU
                }
            }
            derivedKey.append(blockResult)
        }
        return derivedKey.prefix(keyLength)
    }
}
