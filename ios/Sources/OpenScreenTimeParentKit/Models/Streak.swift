import Foundation

/// Mirrors `shared/model/Streak.kt` exactly - consecutive recently-completed days (not
/// including today) under the currently-configured daily limit and unlock goal (see #13).
/// Display-only positive reinforcement: only ever counts up from zero, no "broken streak"
/// equivalent - see the design-principle note on the Android side.
func computeStreak(recentStats: [DailyStats], dailyLimitMinutes: Int, dailyUnlockGoal: Int?) -> Int {
    let byDate = Dictionary(uniqueKeysWithValues: recentStats.map { ($0.date, $0) })
    let limitMs = Int64(dailyLimitMinutes) * 60_000
    let calendar = Calendar(identifier: .gregorian)
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    formatter.locale = Locale(identifier: "en_US_POSIX")

    var streak = 0
    guard var cursor = calendar.date(byAdding: .day, value: -1, to: Date()) else { return 0 }
    while true {
        let dateStr = formatter.string(from: cursor)
        guard let stats = byDate[dateStr] else { break }
        let underTime = stats.totalScreenTimeMs <= limitMs
        let underUnlocks = dailyUnlockGoal == nil || stats.unlockCount <= dailyUnlockGoal!
        if !underTime || !underUnlocks { break }
        streak += 1
        // Never expected to fail for simple day arithmetic, but break rather than risk an
        // infinite loop (re-checking the same date forever) if it somehow ever does.
        guard let previous = calendar.date(byAdding: .day, value: -1, to: cursor) else { break }
        cursor = previous
    }
    return streak
}
