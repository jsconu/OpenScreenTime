package org.openscreentime.shared.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppUsage(
    val packageName: String = "",
    val appName: String = "",
    val foregroundTimeMs: Long = 0
)

/**
 * One day's usage for a child, keyed by date (yyyy-MM-dd). Lives at
 * parents/{parentUid}/children/{childId}/dailyStats/{date} in Firestore.
 */
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
    val unlockFirstApps: List<AppCount> = emptyList()
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
        "unlockFirstApps" to unlockFirstApps.map(::appCountMap)
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(date: String, map: Map<String, Any?>): DailyStats {
            val rawList = map["appUsage"] as? List<Map<String, Any?>> ?: emptyList()
            val appUsage = rawList.map {
                AppUsage(
                    packageName = it["packageName"] as? String ?: "",
                    appName = it["appName"] as? String ?: "",
                    foregroundTimeMs = (it["foregroundTimeMs"] as? Long) ?: 0
                )
            }
            return DailyStats(
                date = date,
                totalScreenTimeMs = (map["totalScreenTimeMs"] as? Long) ?: 0,
                unlockCount = (map["unlockCount"] as? Long)?.toInt() ?: 0,
                appUsage = appUsage,
                lastSyncedAtMs = (map["lastSyncedAtMs"] as? Long) ?: 0,
                notificationCount = (map["notificationCount"] as? Long)?.toInt() ?: 0,
                notificationsByApp = appCounts(map["notificationsByApp"]),
                unlockFirstApps = appCounts(map["unlockFirstApps"])
            )
        }

        @Suppress("UNCHECKED_CAST")
        private fun appCounts(raw: Any?): List<AppCount> =
            (raw as? List<Map<String, Any?>>)?.map {
                AppCount(
                    packageName = it["packageName"] as? String ?: "",
                    appName = it["appName"] as? String ?: "",
                    count = (it["count"] as? Long)?.toInt() ?: 0
                )
            } ?: emptyList()
    }
}

private fun appCountMap(c: AppCount): Map<String, Any?> =
    mapOf("packageName" to c.packageName, "appName" to c.appName, "count" to c.count)

fun todayDateString(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
