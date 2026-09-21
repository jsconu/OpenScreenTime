package org.openscreentime.shared.repo

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.model.InstalledApp

/** Reading and writing per-day usage stats. */
internal class StatsRepository(private val db: FirebaseFirestore) {

    fun listenDailyStats(
        parentUid: String,
        childId: String,
        date: String,
        onChange: (DailyStats) -> Unit
    ): ListenerRegistration =
        db.document(FirestorePaths.dailyStatsDoc(parentUid, childId, date))
            .addSnapshotListener { snap, _ ->
                onChange(DailyStats.fromMap(date, snap?.data ?: emptyMap()))
            }

    suspend fun getRecentDailyStats(parentUid: String, childId: String, days: Int): List<DailyStats> {
        val calendar = Calendar.getInstance()
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = format.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        val startStr = format.format(calendar.time)

        val snap = db.collection(FirestorePaths.dailyStatsCollection(parentUid, childId))
            .orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
            .whereGreaterThanOrEqualTo(FieldPath.documentId(), startStr)
            .whereLessThan(FieldPath.documentId(), todayStr)
            .get()
            .await()
        return snap.documents.map { DailyStats.fromMap(it.id, it.data ?: emptyMap()) }
    }

    suspend fun pushDailyStats(parentUid: String, childId: String, stats: DailyStats) {
        db.document(FirestorePaths.dailyStatsDoc(parentUid, childId, stats.date))
            .set(stats.toMap(), SetOptions.merge()).await()
    }

    /** The kid device's launchable apps (name and package only), replaced wholesale each time. */
    suspend fun pushInstalledApps(parentUid: String, childId: String, apps: List<InstalledApp>) {
        db.document(FirestorePaths.installedAppsDoc(parentUid, childId)).set(
            mapOf(
                "apps" to apps.map { mapOf("packageName" to it.packageName, "appName" to it.label) },
                "updatedAtMs" to System.currentTimeMillis()
            )
        ).await()
    }

    /**
     * Reads leniently: a doc written by another device is data, not a promise, so anything that
     * isn't a well-formed entry is skipped rather than allowed to crash the parent app.
     */
    fun listenInstalledApps(
        parentUid: String,
        childId: String,
        onChange: (List<InstalledApp>) -> Unit
    ): ListenerRegistration =
        db.document(FirestorePaths.installedAppsDoc(parentUid, childId))
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                val raw = (snap.data?.get("apps") as? List<*>).orEmpty()
                onChange(
                    raw.filterIsInstance<Map<*, *>>().mapNotNull {
                        val pkg = it["packageName"] as? String ?: return@mapNotNull null
                        InstalledApp(pkg, it["appName"] as? String ?: pkg)
                    }
                )
            }
}
