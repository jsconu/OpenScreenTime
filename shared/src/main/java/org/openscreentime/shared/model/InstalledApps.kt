package org.openscreentime.shared.model

/** An app the person can open from their launcher. */
data class InstalledApp(val packageName: String, val label: String)

/**
 * The list an app-limit screen should show: everything installed, not just what happened to be
 * used today. Apps with recorded usage keep their real time (and appear even if they can't be
 * launched any more); installed apps with none get zero. Most-used first, then A to Z.
 */
fun mergeUsageWithInstalled(usage: List<AppUsage>, installed: List<InstalledApp>): List<AppUsage> {
    val byPackage = LinkedHashMap<String, AppUsage>()
    usage.forEach { byPackage[it.packageName] = it }
    installed.forEach { app ->
        if (app.packageName !in byPackage) {
            byPackage[app.packageName] = AppUsage(app.packageName, app.label.ifBlank { app.packageName }, 0)
        }
    }
    return byPackage.values.sortedWith(
        compareByDescending<AppUsage> { it.foregroundTimeMs }.thenBy { it.appName.lowercase() }
    )
}
