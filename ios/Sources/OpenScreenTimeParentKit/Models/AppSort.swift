import Foundation

/// How a per-app list is ordered: by time used (the default, most used first), or A to Z by name.
/// Mirrors `shared/model/AppSort.kt`.
enum AppSort: String, CaseIterable, Identifiable {
    case usage
    case name

    var id: String { rawValue }
    var label: String { self == .usage ? "Most used" : "A to Z" }
}

/// `apps` ordered by `sort`. Usage order breaks ties (including all the zeroes for apps not used yet)
/// alphabetically, so the list never shuffles between refreshes.
func sortApps(_ apps: [AppUsage], by sort: AppSort) -> [AppUsage] {
    switch sort {
    case .usage:
        return apps.sorted { a, b in
            a.foregroundTimeMs != b.foregroundTimeMs
                ? a.foregroundTimeMs > b.foregroundTimeMs
                : a.appName.lowercased() < b.appName.lowercased()
        }
    case .name:
        return apps.sorted { $0.appName.lowercased() < $1.appName.lowercased() }
    }
}

/// An app the kid's phone reported as installed (name and package only). See `InstalledApp` in
/// `shared/model/InstalledApps.kt`.
struct InstalledApp: Equatable {
    let packageName: String
    let label: String
}

/// The list an app-limit screen should show: everything installed on the kid's phone, not just what happened
/// to be used today. Apps with recorded usage keep their real time; installed apps with none get zero.
func mergeUsageWithInstalled(usage: [AppUsage], installed: [InstalledApp]) -> [AppUsage] {
    var byPackage: [String: AppUsage] = [:]
    for app in usage { byPackage[app.packageName] = app }
    for app in installed where byPackage[app.packageName] == nil {
        let label = app.label.trimmingCharacters(in: .whitespaces).isEmpty ? app.packageName : app.label
        byPackage[app.packageName] = AppUsage(packageName: app.packageName, appName: label, foregroundTimeMs: 0)
    }
    return sortApps(Array(byPackage.values), by: .usage)
}
