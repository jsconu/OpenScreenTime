import XCTest
@testable import OpenScreenTimeParentKit

final class PasscodeHasherTests: XCTestCase {

    /// Reference vector computed independently with Python's `hashlib.pbkdf2_hmac`
    /// (password "1234" UTF-8-encoded, salt the literal hex string below UTF-8-encoded,
    /// 120,000 iterations, SHA-256, 32-byte output) - not run against a live JVM (no
    /// Java available in this environment), so this confirms the PBKDF2 algorithm itself
    /// is implemented correctly, not byte-for-byte parity with `PasscodeHasher.kt`'s
    /// actual JVM provider. That parity only matters for the password-encoding step
    /// (the salt-encoding step is identical UTF-8-bytes-of-a-hex-string on both sides
    /// either way), and every passcode this app accepts is 4-6 ASCII digits - digits
    /// encode identically in UTF-8, ASCII, and Latin-1, so the JVM's specific char[]
    /// encoding choice can't actually produce a different result here. Verify against a
    /// real device/JVM before shipping if this assumption ever needs to cover non-numeric
    /// passcodes.
    func testMatchesKnownPBKDF2Vector() {
        let hash = PasscodeHasher.hash(passcode: "1234", salt: "deadbeefcafef00d0011223344556677")
        XCTAssertEqual(hash, "674f340ab287fd09e1bc32ec36766ad16680adea48b18aa5b354ee63ef15f83b")
    }

    func testVerifyRoundTrips() {
        let salt = PasscodeHasher.randomSalt()
        let hash = PasscodeHasher.hash(passcode: "5678", salt: salt)
        XCTAssertTrue(PasscodeHasher.verify(passcode: "5678", salt: salt, expectedHash: hash))
        XCTAssertFalse(PasscodeHasher.verify(passcode: "0000", salt: salt, expectedHash: hash))
    }

    func testRandomSaltsAreUnique() {
        let salts = Set((0..<20).map { _ in PasscodeHasher.randomSalt() })
        XCTAssertEqual(salts.count, 20)
    }
}
