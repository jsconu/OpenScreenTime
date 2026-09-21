package org.openscreentime.shared.model

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class AppUsage(
    val packageName: String = "",
    val appName: String = "",
    val foregroundTimeMs: Long = 0
)

/**
 * One day's usage for a child, keyed by date (yyyy-MM-dd). Lives at
 * parents/{parentUid}/children/{childId}/dailyStats/{date} in Firestore.
 */
@Serializable
data class DailyStats(
    val date: String = "",
    val totalScreenTimeMs: Long = 0,
    val unlockCount: Int = 0,
    val appUsage: List<AppUsage> = emptyList(),
    val lastSyncedAtMs: Long = 0,
    /**
     * See #35 - only ever non-zero/non-empty while a parent has turned the matching tracking
     * toggle on for this profile; nothing is collected on the device otherwise.
     */
    val notificationCount: Int = 0,
    val notificationsByApp: List<AppCount> = emptyList(),
    val unlockFirstApps: List<AppCount> = emptyList(),
    /** See #41 - sites looked up in a browser today (AppCount.packageName is the site), only while tracked. */
    val websiteCounts: List<AppCount> = emptyList()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "totalScreenTimeMs" to totalScreenTimeMs,
        "unlockCount" to unlockCount,
        "appUsage" to appUsage.map {
            mapOf(
                "packageName" to it.packageName,
                "appName" to it.appName,
                "foregroundTimeMs" to it.foregroundTimeMs
            )
        },
        "lastSyncedAtMs" to lastSyncedAtMs,
        "notificationCount" to notificationCount,
        "notificationsByApp" to notificationsByApp.map(::appCountMap),
        "unlockFirstApps" to unlockFirstApps.map(::appCountMap),
        "websiteCounts" to websiteCounts.map(::appCountMap)
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(date: String, map: Map<String, Any?>): DailyStats {
            val rawList = (map["appUsage"] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
            val appUsage = rawList.map {
                AppUsage(
                    packageName = it["packageName"] as? String ?: "",
                    appName = it["appName"] as? String ?: "",
                    foregroundTimeMs = (it["foregroundTimeMs"] as? Number)?.toLong() ?: 0
                )
            }
            return DailyStats(
                date = date,
                totalScreenTimeMs = (map["totalScreenTimeMs"] as? Number)?.toLong() ?: 0,
                unlockCount = (map["unlockCount"] as? Number)?.toInt() ?: 0,
                appUsage = appUsage,
                lastSyncedAtMs = (map["lastSyncedAtMs"] as? Number)?.toLong() ?: 0,
                notificationCount = (map["notificationCount"] as? Number)?.toInt() ?: 0,
                notificationsByApp = appCounts(map["notificationsByApp"]),
                unlockFirstApps = appCounts(map["unlockFirstApps"]),
                websiteCounts = appCounts(map["websiteCounts"])
            )
        }

        @Suppress("UNCHECKED_CAST")
        private fun appCounts(raw: Any?): List<AppCount> =
            (raw as? List<*>)?.filterIsInstance<Map<*, *>>()?.map {
                AppCount(
                    packageName = it["packageName"] as? String ?: "",
                    appName = it["appName"] as? String ?: "",
                    count = (it["count"] as? Number)?.toInt() ?: 0
                )
            } ?: emptyList()
    }
}

private fun appCountMap(c: AppCount): Map<String, Any?> =
    mapOf("packageName" to c.packageName, "appName" to c.appName, "count" to c.count)

fun todayDateString(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
