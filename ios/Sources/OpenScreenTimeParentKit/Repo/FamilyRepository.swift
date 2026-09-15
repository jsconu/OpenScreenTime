import FirebaseAuth
import FirebaseFirestore
import Foundation

/// Single access point to Firebase Auth + Firestore for this app. See
/// `/firebase/firestore.rules` for the security rules this relies on - the same rules
/// the Android apps use, since all three apps share one Firestore project and data model.
///
/// This covers the parent-app v1 scope from issue #7: sign up/in, dashboard, add-child +
/// pairing-code display, child detail (stats, limits, lock), passcode settings. It
/// deliberately does not yet port the kid-only or newer Android-parent methods (pairing-
/// code claiming, self-tracking, bedtime windows, negotiated-limit proposals, unlock
/// goals) - `shared/repo/FamilyRepository.kt` is the reference for those when this app
/// grows to cover them.
public final class FamilyRepository {
    private let auth: Auth
    private let db: Firestore

    public init(auth: Auth = Auth.auth(), db: Firestore = Firestore.firestore()) {
        self.auth = auth
        self.db = db
    }

    var currentUid: String? { auth.currentUser?.uid }

    @discardableResult
    func signUpParent(email: String, password: String) async throws -> String {
        let result = try await auth.createUser(withEmail: email, password: password)
        return result.user.uid
    }

    @discardableResult
    func signInParent(email: String, password: String) async throws -> String {
        let result = try await auth.signIn(withEmail: email, password: password)
        return result.user.uid
    }

    func signOut() throws {
        try auth.signOut()
    }

    // MARK: - Parent side

    func createChild(parentUid: String, name: String) async throws -> ChildProfile {
        let code = Self.generatePairingCode()
        let docRef = db.collection(FirestorePaths.childrenCollection(parentUid)).document()
        let existingPasscode = try await getParentPasscode(parentUid: parentUid)
        var child = ChildProfile(id: docRef.documentID, name: name, pairingCode: code, paired: false)
        child.parentPasscodeHash = existingPasscode?.hash
        child.parentPasscodeSalt = existingPasscode?.salt

        try await docRef.setData(child.toMap())
        try await db.collection(FirestorePaths.pairingCodes).document(code).setData([
            "parentUid": parentUid,
            "childId": docRef.documentID,
            "used": false,
            // Must be the server's clock, not a client-supplied value - the security
            // rules require an exact match on this, see firestore.rules.
            "createdAt": FieldValue.serverTimestamp()
        ])
        return child
    }

    func setLocked(parentUid: String, childId: String, locked: Bool) async throws {
        try await db.document(FirestorePaths.childDoc(parentUid, childId)).updateData(["locked": locked])
    }

    /// Reads the parent's passcode hash/salt, or nil if no passcode has been set yet.
    func getParentPasscode(parentUid: String) async throws -> PasscodeInfo? {
        let snap = try await db.document("\(FirestorePaths.parents)/\(parentUid)").getDocument()
        guard let hash = snap.get("passcodeHash") as? String,
              let salt = snap.get("passcodeSalt") as? String else { return nil }
        return PasscodeInfo(hash: hash, salt: salt)
    }

    /// Sets or changes the family passcode. Writes it to the parent's own account doc, and
    /// fans it out to every existing child doc so already-paired kid devices (on either
    /// platform) can verify it locally.
    func setParentPasscode(parentUid: String, hash: String, salt: String) async throws {
        try await db.document("\(FirestorePaths.parents)/\(parentUid)")
            .setData(["passcodeHash": hash, "passcodeSalt": salt], merge: true)

        let children = try await db.collection(FirestorePaths.childrenCollection(parentUid)).getDocuments()
        guard !children.documents.isEmpty else { return }
        let batch = db.batch()
        for doc in children.documents {
            batch.updateData(["parentPasscodeHash": hash, "parentPasscodeSalt": salt], forDocument: doc.reference)
        }
        try await batch.commit()
    }

    func listenChildren(parentUid: String, onChange: @escaping ([ChildProfile]) -> Void) -> ListenerRegistration {
        db.collection(FirestorePaths.childrenCollection(parentUid)).addSnapshotListener { snapshot, _ in
            let list = snapshot?.documents.map { ChildProfile.from(id: $0.documentID, map: $0.data()) } ?? []
            onChange(list)
        }
    }

    func listenDailyStats(
        parentUid: String,
        childId: String,
        date: String,
        onChange: @escaping (DailyStats) -> Void
    ) -> ListenerRegistration {
        db.document(FirestorePaths.dailyStatsDoc(parentUid, childId, date)).addSnapshotListener { snapshot, _ in
            onChange(DailyStats.from(date: date, map: snapshot?.data() ?? [:]))
        }
    }

    func updateDailyLimit(parentUid: String, childId: String, minutes: Int) async throws {
        try await db.document(FirestorePaths.childDoc(parentUid, childId)).updateData(["dailyLimitMinutes": minutes])
    }

    func updateAppLimits(parentUid: String, childId: String, appLimits: [String: Int]) async throws {
        try await db.document(FirestorePaths.childDoc(parentUid, childId)).updateData(["appLimits": appLimits])
    }

    /// Deletes a child and its usage history. Firestore doesn't cascade-delete
    /// subcollections, so dailyStats docs are removed explicitly first.
    func deleteChild(parentUid: String, childId: String) async throws {
        let statsSnap = try await db.collection(FirestorePaths.dailyStatsCollection(parentUid, childId)).getDocuments()
        if !statsSnap.documents.isEmpty {
            let batch = db.batch()
            for doc in statsSnap.documents {
                batch.deleteDocument(doc.reference)
            }
            try await batch.commit()
        }
        try await db.document(FirestorePaths.childDoc(parentUid, childId)).delete()
    }

    private static func generatePairingCode() -> String {
        String(Int.random(in: 100_000...999_999))
    }
}
