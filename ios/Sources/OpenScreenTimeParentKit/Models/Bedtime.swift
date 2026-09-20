import Foundation

/// Mirrors `shared/model/Bedtime.kt` exactly - same wraparound-safe window check, same
/// "equal start/end means not set" rule, same HH:MM edit format vs. h:mm AM/PM display
/// format split.

/// Formats minutes-since-midnight as a 12-hour clock time, e.g. 420 -> "7:00 AM".
func formatMinutesOfDay(_ minutes: Int) -> String {
    let hour24 = (minutes / 60) % 24
    let minute = minutes % 60
    let period = hour24 < 12 ? "AM" : "PM"
    let hour12raw = hour24 % 12
    let hour12 = hour12raw == 0 ? 12 : hour12raw
    return String(format: "%d:%02d %@", hour12, minute, period)
}

/// Parses a "HH:MM" 24-hour string (the bedtime editor's format) into minutes since
/// midnight, or nil if it's not valid.
func parseHHmm(_ text: String) -> Int? {
    let parts = text.trimmingCharacters(in: .whitespaces).split(separator: ":")
    guard parts.count == 2, let h = Int(parts[0]), let m = Int(parts[1]) else { return nil }
    guard (0...23).contains(h), (0...59).contains(m) else { return nil }
    return h * 60 + m
}

/// Formats minutes-since-midnight as a 24-hour "HH:MM" string, e.g. 420 -> "07:00".
func formatHHmm(_ minutes: Int) -> String {
    String(format: "%02d:%02d", minutes / 60, minutes % 60)
}

/// Length of a bedtime window in minutes, counting across midnight (a start of 21:00 and end of 07:00 is 600).
func bedtimeDurationMinutes(start: Int, end: Int) -> Int {
    end > start ? end - start : end + 24 * 60 - start
}

/// A plain-language description of a bedtime window that makes the night-to-next-day shape explicit,
/// e.g. "9:00 PM tonight to 7:00 AM tomorrow morning (10 hours)". Empty for start == end, which is not a
/// valid window. Mirrors `describeBedtimeWindow` in `shared/model/Bedtime.kt`.
func describeBedtimeWindow(start: Int, end: Int) -> String {
    if start == end { return "" }
    let duration = bedtimeDurationMinutes(start: start, end: end)
    let hours = duration / 60
    let minutes = duration % 60
    let hourText = "\(hours) hour\(hours == 1 ? "" : "s")"
    let length: String
    if minutes == 0 {
        length = hourText
    } else if hours == 0 {
        length = "\(minutes) min"
    } else {
        length = "\(hourText) \(minutes) min"
    }
    let noon = 12 * 60
    if end < start {
        let startLabel = start >= noon ? "\(formatMinutesOfDay(start)) tonight" : formatMinutesOfDay(start)
        let endLabel = end < noon ? "\(formatMinutesOfDay(end)) tomorrow morning" : "\(formatMinutesOfDay(end)) tomorrow"
        return "\(startLabel) to \(endLabel) (\(length))"
    }
    return "\(formatMinutesOfDay(start)) to \(formatMinutesOfDay(end)) the same day (\(length))"
}
