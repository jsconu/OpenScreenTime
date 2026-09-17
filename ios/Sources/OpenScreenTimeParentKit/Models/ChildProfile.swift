import Foundation

/// Mirrors `shared/model/ChildProfile.kt` on the Android side field-for-field, since both
/// platforms read and write the same Firestore documents at
/// `parents/{parentUid}/children/{childId}`. Every field the Android apps can write is
/// represented here too, even where this app's v1 UI doesn't yet expose editing it (unlock
/// goals, bedtime, negotiated-limit proposals, streaks) - so a document synced from an
/// Android device round-trips through this app without data loss.
struct ChildProfile: Identifiable, Equatable {
    var id: String = ""
    var name: String = ""
    var pairingCode: String = ""
    var paired: Bool = false
    var deviceUid: String?
    var dailyLimitMinutes: Int = 120
    var appLimits: [String: Int] = [:]
    /// True while a parent has hit "Lock now" - blocks all apps on the kid device immediately.
    var locked: Bool = false
    /// A copy of the parent's passcode hash/salt, denormalized onto every child so a paired
    /// kid device can verify a passcode entered locally without reading the parent's account doc.
    var parentPasscodeHash: String?
    var parentPasscodeSalt: String?
    /// True for the one special child doc, per parent, that represents the parent's own
    /// device rather than a paired kid's (see #8). Always has the fixed id "self".
    var isSelf: Bool = false
    /// Informational only - never enforced/blocked (see #10).
    var dailyUnlockGoal: Int?
    /// A kid-proposed daily limit awaiting parent approval, or nil (see #14).
    var proposedDailyLimitMinutes: Int?
    var proposedAppLimits: [String: Int]?
    /// Minutes since local midnight (0-1439); either nil = no bedtime window set (see #15).
    var bedtimeStartMinutes: Int?
    var bedtimeEndMinutes: Int?
    /// Domains blocked device-wide, in any browser, via the kid device's local DNS-sinkhole
    /// VPN (Android only for now - see #19). Suffix-matched: "tiktok.com" also blocks
    /// "m.tiktok.com". This app can view/edit the list even though enforcement only runs
    /// on the Android kid app today.
    var blockedDomains: [String] = []
    /// A kid-requested "more time" amount in minutes (5 or 15), awaiting parent approval, or
    /// nil when there's no pending request (see #23). Requested from the Android kid app's
    /// block screen only - there's no iOS kid app yet (see #7) - but either parent, on either
    /// platform, should be able to see and grant it.
    var requestedExtraMinutes: Int?
    /// Epoch milliseconds until which the kid is temporarily let through bedtime and any
    /// daily/app-limit block - never a parent lock, which stays absolute. See #23.
    var temporaryUnlockUntilMs: Int64?
    /// Packages that stay usable no matter what - bypass the daily limit, every per-app
    /// limit, and bedtime, the same way `temporaryUnlockUntilMs` does, but a parent lock
    /// still always wins (see #28). Enforcement only runs on the Android apps today, same
    /// as every other limit field here - this app can view/edit the list regardless.
    var alwaysAllowedPackages: [String] = []

    func toMap() -> [String: Any] {
        var map: [String: Any] = [
            "name": name,
            "pairingCode": pairingCode,
            "paired": paired,
            "dailyLimitMinutes": dailyLimitMinutes,
            "appLimits": appLimits,
            "locked": locked,
            "isSelf": isSelf,
            "blockedDomains": blockedDomains,
            "alwaysAllowedPackages": alwaysAllowedPackages
        ]
        map["deviceUid"] = deviceUid
        map["parentPasscodeHash"] = parentPasscodeHash
        map["parentPasscodeSalt"] = parentPasscodeSalt
        map["dailyUnlockGoal"] = dailyUnlockGoal
        map["proposedDailyLimitMinutes"] = proposedDailyLimitMinutes
        map["proposedAppLimits"] = proposedAppLimits
        map["bedtimeStartMinutes"] = bedtimeStartMinutes
        map["bedtimeEndMinutes"] = bedtimeEndMinutes
        map["requestedExtraMinutes"] = requestedExtraMinutes
        map["temporaryUnlockUntilMs"] = temporaryUnlockUntilMs
        return map
    }

    static func from(id: String, map: [String: Any]) -> ChildProfile {
        ChildProfile(
            id: id,
            name: map["name"] as? String ?? "",
            pairingCode: map["pairingCode"] as? String ?? "",
            paired: map["paired"] as? Bool ?? false,
            deviceUid: map["deviceUid"] as? String,
            dailyLimitMinutes: (map["dailyLimitMinutes"] as? Int) ?? 120,
            appLimits: (map["appLimits"] as? [String: Int]) ?? [:],
            locked: map["locked"] as? Bool ?? false,
            parentPasscodeHash: map["parentPasscodeHash"] as? String,
            parentPasscodeSalt: map["parentPasscodeSalt"] as? String,
            isSelf: map["isSelf"] as? Bool ?? false,
            dailyUnlockGoal: map["dailyUnlockGoal"] as? Int,
            proposedDailyLimitMinutes: map["proposedDailyLimitMinutes"] as? Int,
            proposedAppLimits: map["proposedAppLimits"] as? [String: Int],
            bedtimeStartMinutes: map["bedtimeStartMinutes"] as? Int,
            bedtimeEndMinutes: map["bedtimeEndMinutes"] as? Int,
            blockedDomains: (map["blockedDomains"] as? [String]) ?? [],
            requestedExtraMinutes: map["requestedExtraMinutes"] as? Int,
            temporaryUnlockUntilMs: (map["temporaryUnlockUntilMs"] as? Int64)
                ?? (map["temporaryUnlockUntilMs"] as? Int).map(Int64.init),
            alwaysAllowedPackages: (map["alwaysAllowedPackages"] as? [String]) ?? []
        )
    }
}
