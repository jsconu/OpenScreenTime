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
}
