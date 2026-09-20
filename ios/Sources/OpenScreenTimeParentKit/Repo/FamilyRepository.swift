import FirebaseAuth
import FirebaseFirestore
import Foundation

/// Single access point to Firebase Auth + Firestore for this app. See
/// `/firebase/firestore.rules` for the security rules this relies on - the same rules
/// the Android apps use, since all three apps share one Firestore project and data model.
///
/// Covers the parent-app scope from issue #7 plus the #26 parity pass: sign up/in,
/// dashboard, add-child + pairing-code display, child detail (stats, limits, lock, unlock
/// goal, bedtime, streaks, negotiated-proposal approve/decline, website blocking, "more
/// time" grant/decline), passcode settings, feedback, crash reporting. Deliberately does
/// not port kid-only methods (pairing-code claiming - there's no iOS kid app yet, see #7)
/// or self-tracking (needs the same OS-level monitoring capability the iOS kid app is
/// blocked on) - `shared/repo/FamilyRepository.kt` is the reference for those if that ever
/// changes.
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

    /// Emails a password-reset link. A missing account looks the same as success (no account
    /// enumeration through the sign-in screen); a malformed address or no connection still throws.
    func sendPasswordReset(email: String) async throws {
        do {
            try await auth.sendPasswordReset(withEmail: email.trimmingCharacters(in: .whitespaces))
        } catch let error as NSError where error.code == AuthErrorCode.userNotFound.rawValue {
            // Same outcome as a real account.
        }
    }

    /// True if `password` is the signed-in parent account's password, checked by re-authenticating
    /// (this doesn't sign anyone in or out). A wrong password is `false`; a network problem or too
    /// many attempts still throws. Used to recover a forgotten family passcode.
    func verifyAccountPassword(_ password: String) async throws -> Bool {
        guard let user = auth.currentUser, let email = user.email else { return false }
        do {
            try await user.reauthenticate(with: EmailAuthProvider.credential(withEmail: email, password: password))
            return true
        } catch let error as NSError
            where error.code == AuthErrorCode.wrongPassword.rawValue
                || error.code == AuthErrorCode.invalidCredential.rawValue
                || error.code == AuthErrorCode.userNotFound.rawValue {
            return false
        }
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
            // The parent's own "self" profile is readable by every linked kid device and has no kid
            // device that needs to verify a passcode, so it never carries the verifier (see #18).
            let fields: [String: Any] = doc.documentID == "self"
                ? ["parentPasscodeHash": NSNull(), "parentPasscodeSalt": NSNull()]
                : ["parentPasscodeHash": hash, "parentPasscodeSalt": salt]
            batch.updateData(fields, forDocument: doc.reference)
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

    /// The apps the kid's phone last reported as installed (empty until it has synced once). Read
    /// leniently: this doc is written by another device, so anything malformed is skipped, not fatal.
    func listenInstalledApps(
        parentUid: String,
        childId: String,
        onChange: @escaping ([InstalledApp]) -> Void
    ) -> ListenerRegistration {
        db.document(FirestorePaths.installedAppsDocPath(parentUid, childId)).addSnapshotListener { snapshot, error in
            if error != nil { return }
            let raw = snapshot?.data()?["apps"] as? [[String: Any]] ?? []
            onChange(raw.compactMap { entry in
                guard let pkg = entry["packageName"] as? String else { return nil }
                return InstalledApp(packageName: pkg, label: entry["appName"] as? String ?? pkg)
            })
        }
    }

    /// The `days` calendar dates before today, oldest first - used to compute a streak
    /// (see #13). Excludes today, since that day isn't over yet.
    func getRecentDailyStats(parentUid: String, childId: String, days: Int) async throws -> [DailyStats] {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        let todayStr = formatter.string(from: Date())
        let startDate = Calendar(identifier: .gregorian).date(byAdding: .day, value: -days, to: Date()) ?? Date()
        let startStr = formatter.string(from: startDate)

        let snapshot = try await db.collection(FirestorePaths.dailyStatsCollection(parentUid, childId))
            .order(by: FieldPath.documentID())
            .whereField(FieldPath.documentID(), isGreaterThanOrEqualTo: startStr)
            .whereField(FieldPath.documentID(), isLessThan: todayStr)
            .getDocuments()
        return snapshot.documents.map { DailyStats.from(date: $0.documentID, map: $0.data()) }
    }

    func updateDailyLimit(parentUid: String, childId: String, minutes: Int) async throws {
        try await db.document(FirestorePaths.childDoc(parentUid, childId)).updateData(["dailyLimitMinutes": minutes])
    }

    func updateAppLimits(parentUid: String, childId: String, appLimits: [String: Int]) async throws {
        try await db.document(FirestorePaths.childDoc(parentUid, childId)).updateData(["appLimits": appLimits])
    }

    /// See #10 - informational only, never enforced/blocked.
    func updateDailyUnlockGoal(parentUid: String, childId: String, goal: Int?) async throws {
        try await db.document(FirestorePaths.childDoc(parentUid, childId))
            .updateData(["dailyUnlockGoal": goal ?? NSNull()])
    }

    /// Both nil clears the bedtime window (see #15).
    func updateBedtimeWindow(parentUid: String, childId: String, startMinutes: Int?, endMinutes: Int?) async throws {
        try await db.document(FirestorePaths.childDoc(parentUid, childId)).updateData([
            "bedtimeStartMinutes": startMinutes ?? NSNull(),
            "bedtimeEndMinutes": endMinutes ?? NSNull()
        ])
    }

    /// Copies a pending proposal into the real limits and clears it. See #14. iOS never
    /// writes a proposal itself (that's kid-initiated, and there's no iOS kid app yet, see
    /// #7) - only approving/declining one an Android kid device already wrote.
    func approveProposal(parentUid: String, childId: String, child: ChildProfile) async throws {
        var updates: [String: Any] = [
            "proposedDailyLimitMinutes": NSNull(),
            "proposedAppLimits": NSNull()
        ]
        if let proposedDailyLimitMinutes = child.proposedDailyLimitMinutes {
            updates["dailyLimitMinutes"] = proposedDailyLimitMinutes
        }
        if let proposedAppLimits = child.proposedAppLimits {
            updates["appLimits"] = proposedAppLimits
        }
        try await db.document(FirestorePaths.childDoc(parentUid, childId)).updateData(updates)
    }

    /// Clears a pending proposal without applying it. See #14.
    func declineProposal(parentUid: String, childId: String) async throws {
        try await db.document(FirestorePaths.childDoc(parentUid, childId)).updateData([
            "proposedDailyLimitMinutes": NSNull(),
            "proposedAppLimits": NSNull()
        ])
    }

    /// Replaces the whole blocked-domains list (see #19, `ChildProfile.blockedDomains`).
    /// Enforcement only runs on the Android kid app today - this lets a parent on iOS
    /// manage the same list.
    func updateBlockedDomains(parentUid: String, childId: String, domains: [String]) async throws {
        try await db.document(FirestorePaths.childDoc(parentUid, childId)).updateData(["blockedDomains": domains])
    }

    /// Turns one parent-controlled tracking/display toggle on or off (see #35).
    func setTrackingToggle(parentUid: String, childId: String, toggle: TrackingToggle, enabled: Bool) async throws {
        try await db.document(FirestorePaths.childDoc(parentUid, childId)).updateData([toggle.rawValue: enabled])
    }

    /// Replaces the whole always-allowed list (see #28, `ChildProfile.alwaysAllowedPackages`).
    func updateAlwaysAllowedPackages(parentUid: String, childId: String, packages: [String]) async throws {
        try await db.document(FirestorePaths.childDoc(parentUid, childId))
            .updateData(["alwaysAllowedPackages": packages])
    }

    /// Grants `minutes` of temporary unlock starting now, and clears the pending request. See #23.
    func grantExtraTime(parentUid: String, childId: String, minutes: Int) async throws {
        let untilMs = Int64(Date().timeIntervalSince1970 * 1000) + Int64(minutes) * 60_000
        try await db.document(FirestorePaths.childDoc(parentUid, childId)).updateData([
            "temporaryUnlockUntilMs": untilMs,
            "requestedExtraMinutes": NSNull()
        ])
    }

    /// Clears a pending "more time" request without granting it. See #23.
    func declineExtraTimeRequest(parentUid: String, childId: String) async throws {
        try await db.document(FirestorePaths.childDoc(parentUid, childId)).updateData([
            "requestedExtraMinutes": NSNull()
        ])
    }

    /// In-app feedback (see #22/#26) - write-only. Nobody, not even the submitter, can read
    /// it back through the app; it's reviewed via the Firebase console. Deliberately not a
    /// mailto: link - that would show the destination address to every user who taps
    /// "Feedback," which is exactly what the Android app moved away from in #22.
    func submitFeedback(parentUid: String, text: String, appVersion: String, device: String) async throws {
        try await db.collection(FirestorePaths.feedback).addDocument(data: [
            "parentUid": parentUid,
            "text": text,
            "appVersion": appVersion,
            "device": device,
            "createdAt": FieldValue.serverTimestamp()
        ])
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
        // The installed-apps doc too (a plain delete, so a child that never had one is fine).
        try await db.document(FirestorePaths.installedAppsDocPath(parentUid, childId)).delete()
        try await db.document(FirestorePaths.childDoc(parentUid, childId)).delete()
    }

    private static func generatePairingCode() -> String {
        String(Int.random(in: 100_000...999_999))
    }
}
