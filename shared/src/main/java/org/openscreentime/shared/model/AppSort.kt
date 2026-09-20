package org.openscreentime.shared.model

/** How a per-app list is ordered: by time used (the default, most used first), or A to Z by name. */
enum class AppSort { USAGE, NAME }

/**
 * [apps] ordered by [sort]. Usage order breaks ties (including all the zeroes for apps not used yet)
 * alphabetically, so the list never shuffles between refreshes.
 */
fun sortApps(apps: List<AppUsage>, sort: AppSort): List<AppUsage> = when (sort) {
    AppSort.USAGE -> apps.sortedWith(compareByDescending<AppUsage> { it.foregroundTimeMs }.thenBy { it.appName.lowercase() })
    AppSort.NAME -> apps.sortedBy { it.appName.lowercase() }
}
