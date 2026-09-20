import XCTest
@testable import OpenScreenTimeParentKit

final class BedtimeAndSortTests: XCTestCase {

    func testOvernightWindowSaysTonightAndTomorrowMorning() {
        XCTAssertEqual(
            describeBedtimeWindow(start: 21 * 60, end: 7 * 60),
            "9:00 PM tonight to 7:00 AM tomorrow morning (10 hours)"
        )
    }

    func testOvernightWindowEndingAfterNoonSaysJustTomorrow() {
        XCTAssertEqual(describeBedtimeWindow(start: 22 * 60, end: 13 * 60), "10:00 PM tonight to 1:00 PM tomorrow (15 hours)")
    }

    func testSameDayWindow() {
        XCTAssertEqual(describeBedtimeWindow(start: 60, end: 6 * 60 + 30), "1:00 AM to 6:30 AM the same day (5 hours 30 min)")
    }

    func testEqualStartAndEndIsNotAWindow() {
        XCTAssertEqual(describeBedtimeWindow(start: 600, end: 600), "")
    }

    private let apps = [
        AppUsage(packageName: "b", appName: "banana", foregroundTimeMs: 5_000),
        AppUsage(packageName: "a", appName: "Apple", foregroundTimeMs: 0),
        AppUsage(packageName: "c", appName: "Cherry", foregroundTimeMs: 60_000),
        AppUsage(packageName: "d", appName: "Date", foregroundTimeMs: 0),
    ]

    func testUsageOrderPutsMostUsedFirstAndBreaksTiesAlphabetically() {
        XCTAssertEqual(sortApps(apps, by: .usage).map(\.appName), ["Cherry", "banana", "Apple", "Date"])
    }

    func testNameOrderIgnoresCase() {
        XCTAssertEqual(sortApps(apps, by: .name).map(\.appName), ["Apple", "banana", "Cherry", "Date"])
    }

    func testInstalledAppsAreAddedWithZeroTimeAndUsedAppsKeepTheirs() {
        let merged = mergeUsageWithInstalled(
            usage: [AppUsage(packageName: "a.used", appName: "Used", foregroundTimeMs: 90_000)],
            installed: [
                InstalledApp(packageName: "a.used", label: "Used"),
                InstalledApp(packageName: "b.idle", label: "Idle"),
                InstalledApp(packageName: "c.alpha", label: "Alpha"),
            ]
        )
        XCTAssertEqual(merged.map(\.packageName), ["a.used", "c.alpha", "b.idle"])
        XCTAssertEqual(merged.first?.foregroundTimeMs, 90_000)
    }

    func testABlankLabelFallsBackToThePackageName() {
        let merged = mergeUsageWithInstalled(usage: [], installed: [InstalledApp(packageName: "x.y", label: " ")])
        XCTAssertEqual(merged.first?.appName, "x.y")
    }
}
