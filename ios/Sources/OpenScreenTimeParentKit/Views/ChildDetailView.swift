import SwiftUI
import FirebaseFirestore

/// Mirrors the Android parent app's ChildDetailScreen.kt (see #26 for the parity pass that
/// brought this in line: unlock goal, bedtime, streaks, and negotiated-proposal approve/
/// decline joined the daily-limit/app-limit/lock/website-blocking/extra-time sections
/// already here).
///
/// Broken into several small extracted subviews rather than one large `body` - Swift's
/// type-checker times out ("unable to type-check this expression in reasonable time") on
/// a single view-builder expression this size once it mixes List/Section nesting with
/// ternaries and numeric-literal inference; splitting it up is the standard fix, not
/// just a style preference.
struct ChildDetailView: View {
    let repository: FamilyRepository
    let parentUid: String
    let childId: String

    @State private var child: ChildProfile?
    @State private var stats = DailyStats()
    @State private var streakDays = 0
    @State private var childListener: ListenerRegistration?
    @State private var statsListener: ListenerRegistration?
    @State private var installedListener: ListenerRegistration?
    @State private var installedApps: [InstalledApp] = []
    @State private var showLimitDialog = false
    @State private var showUnlockGoalDialog = false
    @State private var showBedtimeDialog = false
    @State private var editingApp: String?
    @State private var showLockConfirm = false
    @State private var showDeleteConfirm = false
    @State private var newBlockedDomain = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        content
            .navigationTitle(child?.name ?? "")
            .onAppear(perform: startListening)
            .onDisappear(perform: stopListening)
            .confirmationDialog("End screen time now?", isPresented: $showLockConfirm, titleVisibility: .visible) {
                lockConfirmActions
            } message: {
                Text("This blocks every app on \(child?.name ?? "their")'s device until you resume it.")
            }
            .confirmationDialog("Remove \(child?.name ?? "")?", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
                deleteConfirmActions
            } message: {
                Text("This deletes \(child?.name ?? "")'s profile and all of their screen time history. This can't be undone.")
            }
            .sheet(isPresented: $showLimitDialog) { limitDialogSheet }
            .sheet(isPresented: $showUnlockGoalDialog) { unlockGoalDialogSheet }
            .sheet(isPresented: $showBedtimeDialog) { bedtimeDialogSheet }
            .sheet(item: editingAppBinding) { item in appLimitSheet(for: item) }
            .task(id: streakDependencyKey) { await loadStreak() }
    }

    /// See #13 - recomputed whenever the limit or goal it's measured against changes,
    /// same trigger as the Android app's LaunchedEffect(childId, dailyLimitMinutes,
    /// dailyUnlockGoal).
    private var streakDependencyKey: String {
        "\(child?.dailyLimitMinutes ?? 0)-\(child?.dailyUnlockGoal.map(String.init) ?? "none")"
    }

    private func loadStreak() async {
        guard let child else { return }
        let recent = try? await repository.getRecentDailyStats(parentUid: parentUid, childId: childId, days: 14)
        streakDays = computeStreak(
            recentStats: recent ?? [],
            dailyLimitMinutes: child.dailyLimitMinutes,
            dailyUnlockGoal: child.dailyUnlockGoal
        )
    }

    @ViewBuilder
    private var content: some View {
        if let child {
            List {
                PendingProposalSection(repository: repository, parentUid: parentUid, childId: childId, child: child)
                ExtraTimeRequestSection(repository: repository, parentUid: parentUid, childId: childId, child: child)
                LockSection(repository: repository, parentUid: parentUid, childId: childId, child: child, showLockConfirm: $showLockConfirm)
                TodaySection(
                    stats: stats,
                    child: child,
                    streakDays: streakDays,
                    showLimitDialog: $showLimitDialog,
                    showUnlockGoalDialog: $showUnlockGoalDialog,
                    showBedtimeDialog: $showBedtimeDialog
                )
                WebsiteBlockingSection(
                    repository: repository,
                    parentUid: parentUid,
                    childId: childId,
                    child: child,
                    newDomain: $newBlockedDomain
                )
                if child.trackWebsites {
                    WebsitesLookedUpSection(stats: stats)
                }
                TrackingSection(repository: repository, parentUid: parentUid, childId: childId, child: child)
                AppUsageSection(
                    repository: repository,
                    parentUid: parentUid,
                    childId: childId,
                    stats: stats,
                    installedApps: installedApps,
                    child: child,
                    editingApp: $editingApp
                )
                Section {
                    Button("Remove \(child.name)", role: .destructive) { showDeleteConfirm = true }
                }
            }
        } else {
            ProgressView()
        }
    }

    @ViewBuilder
    private var lockConfirmActions: some View {
        Button("End now", role: .destructive) {
            Task { try? await repository.setLocked(parentUid: parentUid, childId: childId, locked: true) }
        }
        Button("Cancel", role: .cancel) {}
    }

    @ViewBuilder
    private var deleteConfirmActions: some View {
        Button("Remove", role: .destructive) {
            Task {
                try? await repository.deleteChild(parentUid: parentUid, childId: childId)
                dismiss()
            }
        }
        Button("Cancel", role: .cancel) {}
    }

    private var limitDialogSheet: some View {
        MinutesInputSheet(title: "Daily screen time limit", initialMinutes: child?.dailyLimitMinutes ?? 120) { minutes in
            Task { try? await repository.updateDailyLimit(parentUid: parentUid, childId: childId, minutes: minutes) }
            showLimitDialog = false
        } onCancel: {
            showLimitDialog = false
        }
    }

    private var unlockGoalDialogSheet: some View {
        UnlockGoalInputSheet(initialGoal: child?.dailyUnlockGoal) { goal in
            Task { try? await repository.updateDailyUnlockGoal(parentUid: parentUid, childId: childId, goal: goal) }
            showUnlockGoalDialog = false
        } onCancel: {
            showUnlockGoalDialog = false
        }
    }

    private var bedtimeDialogSheet: some View {
        BedtimeWindowSheet(
            initialStartMinutes: child?.bedtimeStartMinutes,
            initialEndMinutes: child?.bedtimeEndMinutes
        ) { start, end in
            Task {
                try? await repository.updateBedtimeWindow(
                    parentUid: parentUid, childId: childId, startMinutes: start, endMinutes: end
                )
            }
            showBedtimeDialog = false
        } onCancel: {
            showBedtimeDialog = false
        }
    }

    private var editingAppBinding: Binding<EditingApp?> {
        Binding(get: { editingApp.map(EditingApp.init) }, set: { editingApp = $0?.packageName })
    }

    private func appLimitSheet(for item: EditingApp) -> some View {
        let appName = mergeUsageWithInstalled(usage: stats.appUsage, installed: installedApps)
            .first { $0.packageName == item.packageName }?.appName ?? item.packageName
        let initialMinutes = child?.appLimits[item.packageName] ?? 60
        return MinutesInputSheet(title: "Daily limit for \(appName)", initialMinutes: initialMinutes) { minutes in
            saveAppLimit(packageName: item.packageName, minutes: minutes)
            editingApp = nil
        } onCancel: {
            editingApp = nil
        }
    }

    private func saveAppLimit(packageName: String, minutes: Int) {
        Task { try? await repository.setAppLimit(parentUid: parentUid, childId: childId, packageName: packageName, minutes: minutes) }
    }

    private func startListening() {
        childListener = repository.listenChildren(parentUid: parentUid) { list in
            child = list.first { $0.id == childId }
        }
        statsListener = repository.listenDailyStats(parentUid: parentUid, childId: childId, date: todayDateString()) {
            stats = $0
        }
        installedListener = repository.listenInstalledApps(parentUid: parentUid, childId: childId) {
            installedApps = $0
        }
    }

    private func stopListening() {
        childListener?.remove()
        statsListener?.remove()
        installedListener?.remove()
    }
}

/// A kid-requested "more time" extension, awaiting a Grant or Decline (see #23). Requested
/// from the Android kid app's block screen only, but either parent should be able to grant
/// it from either platform.
/// A kid-proposed limit change awaiting approval or decline (see #14) - written passcode-free
/// by an Android kid device (there's no iOS kid app yet, see #7), but either parent should be
/// able to act on it from either platform.
private struct PendingProposalSection: View {
    let repository: FamilyRepository
    let parentUid: String
    let childId: String
    let child: ChildProfile

    private var hasPendingProposal: Bool {
        child.proposedDailyLimitMinutes != nil || child.proposedAppLimits != nil
    }

    var body: some View {
        if hasPendingProposal {
            Section {
                Text("\(child.name) suggested a change")
                if let proposed = child.proposedDailyLimitMinutes {
                    Text("New daily limit: \(proposed) min (currently \(child.dailyLimitMinutes) min)")
                        .font(.caption)
                }
                if child.proposedAppLimits != nil {
                    Text("Suggested app limits included").font(.caption)
                }
                HStack {
                    Button("Approve") {
                        Task { try? await repository.approveProposal(parentUid: parentUid, childId: childId, child: child) }
                    }
                    Button("Decline", role: .cancel) {
                        Task { try? await repository.declineProposal(parentUid: parentUid, childId: childId) }
                    }
                }
            }
        }
    }
}

private struct ExtraTimeRequestSection: View {
    let repository: FamilyRepository
    let parentUid: String
    let childId: String
    let child: ChildProfile

    var body: some View {
        if let requested = child.requestedExtraMinutes {
            Section {
                Text("\(child.name) is asking for \(requested) more minutes")
                HStack {
                    Button("Grant \(requested) min") {
                        Task { try? await repository.grantExtraTime(parentUid: parentUid, childId: childId, minutes: requested) }
                    }
                    Button("Decline", role: .cancel) {
                        Task { try? await repository.declineExtraTimeRequest(parentUid: parentUid, childId: childId) }
                    }
                }
            }
        }
    }
}

private struct LockSection: View {
    let repository: FamilyRepository
    let parentUid: String
    let childId: String
    let child: ChildProfile
    @Binding var showLockConfirm: Bool

    var body: some View {
        Section {
            Button(child.locked ? "Resume screen time" : "End screen time now") {
                if child.locked {
                    Task { try? await repository.setLocked(parentUid: parentUid, childId: childId, locked: false) }
                } else {
                    showLockConfirm = true
                }
            }
            // Explicit Color (not the bare .primary/.red shorthand) so both ternary
            // branches resolve to the same concrete ShapeStyle type - .primary alone
            // infers as HierarchicalShapeStyle, which .red can't also satisfy.
            .foregroundStyle(child.locked ? Color.primary : Color.red)
        }
    }
}

private struct TodaySection: View {
    let stats: DailyStats
    let child: ChildProfile
    let streakDays: Int
    @Binding var showLimitDialog: Bool
    @Binding var showUnlockGoalDialog: Bool
    @Binding var showBedtimeDialog: Bool

    private var progress: Double {
        let usedMinutes = Double(stats.totalScreenTimeMs) / 60_000
        let limitMinutes = Double(max(1, child.dailyLimitMinutes))
        return min(1, usedMinutes / limitMinutes)
    }

    var body: some View {
        Section("Today") {
            HStack(spacing: 24) {
                StatBlock(label: "Screen time", value: formatDuration(stats.totalScreenTimeMs))
                StatBlock(label: "Unlocks", value: "\(stats.unlockCount)")
            }
            if streakDays > 0 {
                Text("\(streakDays) day\(streakDays == 1 ? "" : "s") in a row under goal")
                    .font(.caption)
            }
            ProgressView(value: progress)
            Text("Daily limit: \(child.dailyLimitMinutes) min").font(.caption)
            Button("Change daily limit") { showLimitDialog = true }
        }
        Section {
            if let goal = child.dailyUnlockGoal {
                Text("Unlock goal: \(goal) a day").font(.caption)
            } else {
                Text("No unlock goal set").font(.caption)
            }
            Text("Informational only - never blocks. Today's unlocks: \(stats.unlockCount).")
                .font(.caption2)
                .foregroundStyle(.secondary)
            Button("Change unlock goal") { showUnlockGoalDialog = true }
        }
        Section {
            if let start = child.bedtimeStartMinutes, let end = child.bedtimeEndMinutes {
                Text("Bedtime: \(describeBedtimeWindow(start: start, end: end))").font(.caption)
            } else {
                Text("No bedtime set").font(.caption)
            }
            Text("Blocks every app during this window, independent of the daily limit.")
                .font(.caption2)
                .foregroundStyle(.secondary)
            Button("Change bedtime") { showBedtimeDialog = true }
        }
    }
}

/// Domains blocked device-wide, in any browser, via the kid device's local DNS-sinkhole
/// VPN (see #19). Suffix-matched, so one entry covers every subdomain. Enforcement only
/// runs on the Android kid app today; this lets a parent on iOS manage the same list.
private struct WebsiteBlockingSection: View {
    let repository: FamilyRepository
    let parentUid: String
    let childId: String
    let child: ChildProfile
    @Binding var newDomain: String

    var body: some View {
        Section("Blocked websites") {
            Text("Blocks a domain and its subdomains in any browser on the Android kid app.")
                .font(.caption)
                .foregroundStyle(.secondary)
            HStack {
                TextField("e.g. tiktok.com", text: $newDomain)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button("Block", action: addDomain)
                    .disabled(newDomain.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            if child.blockedDomains.isEmpty {
                Text("No websites blocked.").foregroundStyle(.secondary)
            }
            ForEach(child.blockedDomains.sorted(), id: \.self) { domain in
                HStack {
                    Text(domain)
                    Spacer()
                    Button("Remove", role: .destructive) { removeDomain(domain) }
                }
            }
        }
    }

    private func addDomain() {
        let domain = newDomain
            .trimmingCharacters(in: .whitespaces)
            .trimmingCharacters(in: CharacterSet(charactersIn: "."))
            .lowercased()
        if !domain.isEmpty && !child.blockedDomains.contains(domain) {
            Task { try? await repository.addBlockedDomain(parentUid: parentUid, childId: childId, domain: domain) }
        }
        newDomain = ""
    }

    private func removeDomain(_ domain: String) {
        Task { try? await repository.removeBlockedDomain(parentUid: parentUid, childId: childId, domain: domain) }
    }
}

/// The four parent-controlled tracking toggles on a `ChildProfile`; the raw value is the Firestore field name.
enum TrackingToggle: String {
    case trackUnlocks
    case trackNotifications
    case showUnlocksOnKid
    case showNotificationsOnKid
    case trackWebsites
}

/// See #35 - optional tracking categories, off by default. Serves a kid's profile and (on Android,
/// where self-tracking exists) the parent's own; the "show on their phone" options only apply to a
/// kid. This app only flips the toggles - collection happens on the Android devices, and the
/// weekly report that displays the results isn't ported to iOS yet.
private struct TrackingSection: View {
    let repository: FamilyRepository
    let parentUid: String
    let childId: String
    let child: ChildProfile

    private func binding(_ toggle: TrackingToggle, _ current: Bool) -> Binding<Bool> {
        Binding(
            get: { current },
            set: { enabled in
                Task {
                    try? await repository.setTrackingToggle(
                        parentUid: parentUid, childId: childId, toggle: toggle, enabled: enabled
                    )
                }
            }
        )
    }

    var body: some View {
        Section {
            Toggle("Track unlocks", isOn: binding(.trackUnlocks, child.trackUnlocks))
            if !child.isSelf && child.trackUnlocks {
                Toggle("Show unlocks on \(child.name)'s phone", isOn: binding(.showUnlocksOnKid, child.showUnlocksOnKid))
            }
            Toggle("Track websites", isOn: binding(.trackWebsites, child.trackWebsites))
            Toggle("Track notification counts", isOn: binding(.trackNotifications, child.trackNotifications))
            if !child.isSelf && child.trackNotifications {
                Toggle(
                    "Show notification counts on \(child.name)'s phone",
                    isOn: binding(.showNotificationsOnKid, child.showNotificationsOnKid)
                )
            }
        } header: {
            Text("Optional tracking")
        } footer: {
            Text(
                "Off by default. The plain daily unlock count is always kept for the unlock goal; " +
                "turning a category on adds more detail. Notification " +
                "tracking counts only, never content. Website tracking counts site names only - never pages, " +
                "searches or time - and needs the website filter turned on on that Android phone. Anything " +
                "shown on their phone carries a note that watching counts can make phone use feel more compulsive."
            )
        }
    }
}

/// See #28 - "always allow" lets an app through the daily limit and bedtime, no matter
/// what; meant for a phone/calling app or maps, not a way to skip a limit day to day.
private struct AppUsageSection: View {
    let repository: FamilyRepository
    let parentUid: String
    let childId: String
    let stats: DailyStats
    let installedApps: [InstalledApp]
    let child: ChildProfile
    @Binding var editingApp: String?
    @State private var sort: AppSort = .usage

    /// Every app the kid's phone reported, not only ones already used today, in the chosen order.
    private var displayedApps: [AppUsage] {
        sortApps(mergeUsageWithInstalled(usage: stats.appUsage, installed: installedApps), by: sort)
    }

    var body: some View {
        Section {
            Picker("Sort", selection: $sort) {
                ForEach(AppSort.allCases) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)
            if displayedApps.isEmpty {
                Text("No apps to show yet.").foregroundStyle(.secondary)
            }
            ForEach(displayedApps) { app in
                AppUsageRow(
                    app: app,
                    limitMinutes: child.appLimits[app.packageName],
                    alwaysAllowed: child.alwaysAllowedPackages.contains(app.packageName),
                    onTap: { editingApp = app.packageName },
                    onToggleAlwaysAllowed: { allowed in toggleAlwaysAllowed(app.packageName, allowed) }
                )
            }
        } header: {
            Text("App usage today")
        } footer: {
            Text("\"Always allow\" lets an app through the daily limit and bedtime, no matter what.")
        }
    }

    private func toggleAlwaysAllowed(_ packageName: String, _ allowed: Bool) {
        Task {
            try? await repository.setAlwaysAllowedPackage(parentUid: parentUid, childId: childId, packageName: packageName, allowed: allowed)
        }
    }
}

private struct AppUsageRow: View {
    let app: AppUsage
    let limitMinutes: Int?
    let alwaysAllowed: Bool
    let onTap: () -> Void
    let onToggleAlwaysAllowed: (Bool) -> Void

    private var subtitle: String {
        if let limitMinutes {
            return "\(formatDuration(app.foregroundTimeMs)) of \(limitMinutes)m limit"
        }
        return formatDuration(app.foregroundTimeMs)
    }

    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(app.appName)
                Text(subtitle).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Toggle("Always allow", isOn: Binding(get: { alwaysAllowed }, set: onToggleAlwaysAllowed))
                .labelsHidden()
            Button("Limit", action: onTap).font(.caption).foregroundStyle(.blue)
        }
    }
}

private struct EditingApp: Identifiable {
    var id: String { packageName }
    let packageName: String
}

private struct StatBlock: View {
    let label: String
    let value: String
    var body: some View {
        VStack(alignment: .leading) {
            Text(value).font(.title3).bold()
            Text(label).font(.caption).foregroundStyle(.secondary)
        }
    }
}

struct MinutesInputSheet: View {
    let title: String
    let initialMinutes: Int
    let onSave: (Int) -> Void
    let onCancel: () -> Void

    @State private var text: String

    init(title: String, initialMinutes: Int, onSave: @escaping (Int) -> Void, onCancel: @escaping () -> Void) {
        self.title = title
        self.initialMinutes = initialMinutes
        self.onSave = onSave
        self.onCancel = onCancel
        _text = State(initialValue: String(initialMinutes))
    }

    var body: some View {
        NavigationStack {
            Form {
                TextField("Minutes per day", text: $text)
                    .keyboardType(.numberPad)
            }
            .navigationTitle(title)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: onCancel)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        if let minutes = Int(text) { onSave(minutes) }
                    }
                }
            }
        }
    }
}

struct UnlockGoalInputSheet: View {
    let onSave: (Int?) -> Void
    let onCancel: () -> Void

    @State private var text: String

    init(initialGoal: Int?, onSave: @escaping (Int?) -> Void, onCancel: @escaping () -> Void) {
        self.onSave = onSave
        self.onCancel = onCancel
        _text = State(initialValue: initialGoal.map(String.init) ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Text("Informational only - never enforced or blocked, just shown alongside actual unlocks.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                TextField("Unlocks per day (blank = no goal)", text: $text)
                    .keyboardType(.numberPad)
            }
            .navigationTitle("Daily unlock goal")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: onCancel)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { onSave(Int(text)) }
                }
            }
        }
    }
}

/// Sets a bedtime in two plain steps, each with a 12-hour clock and AM/PM: first when bedtime STARTS (that
/// evening or night), then when it ENDS (the next morning). A live sentence underneath spells out the result -
/// "9:00 PM tonight to 7:00 AM tomorrow morning (10 hours)" - so nobody has to reason about a 24-hour clock or a
/// window that crosses midnight. Mirrors `BedtimeWindowDialog.kt`. `onSave` gets (nil, nil) to turn bedtime off.
struct BedtimeWindowSheet: View {
    let hasExisting: Bool
    let onSave: (Int?, Int?) -> Void
    let onCancel: () -> Void

    @State private var step = 0
    @State private var startTime: Date
    @State private var endTime: Date

    init(
        initialStartMinutes: Int?,
        initialEndMinutes: Int?,
        onSave: @escaping (Int?, Int?) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.hasExisting = initialStartMinutes != nil && initialEndMinutes != nil
        self.onSave = onSave
        self.onCancel = onCancel
        _startTime = State(initialValue: Self.date(fromMinutes: initialStartMinutes ?? 21 * 60))
        _endTime = State(initialValue: Self.date(fromMinutes: initialEndMinutes ?? 7 * 60))
    }

    private static func date(fromMinutes minutes: Int) -> Date {
        Calendar.current.date(bySettingHour: minutes / 60, minute: minutes % 60, second: 0, of: Date()) ?? Date()
    }

    private static func minutes(from date: Date) -> Int {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
    }

    private var startMinutes: Int { Self.minutes(from: startTime) }
    private var endMinutes: Int { Self.minutes(from: endTime) }
    private var sameTime: Bool { startMinutes == endMinutes }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(step == 0
                        ? "Step 1 of 2 - the evening or night apps lock. Pick the time and AM or PM (for example 9:00 PM)."
                        : "Step 2 of 2 - the next morning, when apps unlock again. Pick the time and AM or PM (for example 7:00 AM).")
                        .font(.callout)
                    DatePicker(
                        step == 0 ? "Bedtime starts" : "Bedtime ends",
                        selection: step == 0 ? $startTime : $endTime,
                        displayedComponents: .hourAndMinute
                    )
                    .datePickerStyle(.wheel)
                    // Forces a 12-hour clock with AM/PM, whatever the phone's regional setting.
                    .environment(\.locale, Locale(identifier: "en_US"))
                }
                Section {
                    Text(sameTime ? "Start and end can't be the same time." : describeBedtimeWindow(start: startMinutes, end: endMinutes))
                        .font(.subheadline).bold()
                        .foregroundStyle(sameTime ? Color.red : Color.accentColor)
                    Text("Blocks every app during this window, independent of the daily limit.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                if hasExisting {
                    Section {
                        Button("Turn bedtime off", role: .destructive) { onSave(nil, nil) }
                    }
                }
            }
            .navigationTitle(step == 0 ? "Bedtime starts" : "Bedtime ends")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    if step == 0 {
                        Button("Cancel", action: onCancel)
                    } else {
                        Button("Back") { step = 0 }
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if step == 0 {
                        Button("Next") { step = 1 }
                    } else {
                        Button("Save") { onSave(startMinutes, endMinutes) }
                            .disabled(sameTime)
                    }
                }
            }
        }
    }
}

/// See #41 - which sites the device looked up while a browser was open today. Shown only while "Track websites"
/// is on. Site names only: no pages, searches or time, and one entry means "a burst of activity", not a visit
/// count you can multiply into minutes. Anything that uses its own DNS (a browser's Secure DNS, Android's
/// Private DNS) doesn't show up.
private struct WebsitesLookedUpSection: View {
    let stats: DailyStats

    var body: some View {
        Section {
            if stats.websiteCounts.isEmpty {
                Text("Nothing yet. Sites appear here once the website filter is on for that phone and a browser has been used.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            ForEach(stats.websiteCounts.prefix(25)) { site in
                HStack {
                    Text(site.site)
                    Spacer()
                    Text("\(site.count)").foregroundStyle(.secondary)
                }
            }
        } header: {
            Text("Websites looked up today")
        } footer: {
            Text("Site names only, counted while a browser was open - not pages, searches, or time spent. A bigger number means more activity, not a visit count. Anything a browser looks up privately isn't seen.")
        }
    }
}
