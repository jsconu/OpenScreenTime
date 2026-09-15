/// Mirrors `shared/repo/FirestorePaths.kt` exactly - both platforms read and write the
/// same Firestore documents, so these path-building rules must agree.
enum FirestorePaths {
    static let parents = "parents"
    static let children = "children"
    static let dailyStats = "dailyStats"
    static let pairingCodes = "pairingCodes"

    static func childrenCollection(_ parentUid: String) -> String {
        "\(parents)/\(parentUid)/\(children)"
    }

    static func childDoc(_ parentUid: String, _ childId: String) -> String {
        "\(childrenCollection(parentUid))/\(childId)"
    }

    static func dailyStatsCollection(_ parentUid: String, _ childId: String) -> String {
        "\(childDoc(parentUid, childId))/\(dailyStats)"
    }

    static func dailyStatsDoc(_ parentUid: String, _ childId: String, _ date: String) -> String {
        "\(dailyStatsCollection(parentUid, childId))/\(date)"
    }
}
