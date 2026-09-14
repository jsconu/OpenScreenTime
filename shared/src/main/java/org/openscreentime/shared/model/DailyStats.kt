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
    val lastSyncedAtMs: Long = 0
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
        "lastSyncedAtMs" to lastSyncedAtMs
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
                lastSyncedAtMs = (map["lastSyncedAtMs"] as? Long) ?: 0
            )
        }
    }
}

fun todayDateString(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
