import Foundation

/// Mirrors `shared/model/DailyStats.kt`.
struct AppUsage: Identifiable, Equatable {
    var id: String { packageName }
    var packageName: String = ""
    var appName: String = ""
    var foregroundTimeMs: Int64 = 0
}

/// One site the device looked up while a browser was open (see #41), with how many bursts of activity.
struct SiteCount: Identifiable, Equatable {
    var id: String { site }
    let site: String
    let count: Int
}

/// One day's usage for a child, keyed by date (yyyy-MM-dd). Lives at
/// `parents/{parentUid}/children/{childId}/dailyStats/{date}` in Firestore.
struct DailyStats: Equatable {
    var date: String = ""
    var totalScreenTimeMs: Int64 = 0
    var unlockCount: Int = 0
    var appUsage: [AppUsage] = []
    /// See #41 - sites looked up in a browser today, busiest first; only present while website tracking is on.
    var websiteCounts: [SiteCount] = []

    static func from(date: String, map: [String: Any]) -> DailyStats {
        let rawList = map["appUsage"] as? [[String: Any]] ?? []
        let usage = rawList.map { entry in
            AppUsage(
                packageName: entry["packageName"] as? String ?? "",
                appName: entry["appName"] as? String ?? "",
                foregroundTimeMs: (entry["foregroundTimeMs"] as? Int64) ?? Int64(entry["foregroundTimeMs"] as? Int ?? 0)
            )
        }
        return DailyStats(
            date: date,
            totalScreenTimeMs: (map["totalScreenTimeMs"] as? Int64) ?? Int64(map["totalScreenTimeMs"] as? Int ?? 0),
            unlockCount: (map["unlockCount"] as? Int) ?? 0,
            appUsage: usage,
            websiteCounts: (map["websiteCounts"] as? [[String: Any]] ?? []).compactMap { entry in
                guard let site = entry["packageName"] as? String else { return nil }
                return SiteCount(site: site, count: (entry["count"] as? Int) ?? 0)
            }
        )
    }
}

func todayDateString() -> String {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    formatter.locale = Locale(identifier: "en_US_POSIX")
    return formatter.string(from: Date())
}
