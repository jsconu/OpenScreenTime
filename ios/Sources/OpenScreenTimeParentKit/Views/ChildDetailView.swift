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
                AppUsageSection(
                    repository: repository,
                    parentUid: parentUid,
                    childId: childId,
                    stats: stats,
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
        let appName = stats.appUsage.first { $0.packageName == item.packageName }?.appName ?? item.packageName
        let initialMinutes = child?.appLimits[item.packageName] ?? 60
        return MinutesInputSheet(title: "Daily limit for \(appName)", initialMinutes: initialMinutes) { minutes in
            saveAppLimit(packageName: item.packageName, minutes: minutes)
            editingApp = nil
        } onCancel: {
            editingApp = nil
        }
    }

    private func saveAppLimit(packageName: String, minutes: Int) {
        guard var updated = child?.appLimits else { return }
        updated[packageName] = minutes
        Task { try? await repository.updateAppLimits(parentUid: parentUid, childId: childId, appLimits: updated) }
    }

    private func startListening() {
        childListener = repository.listenChildren(parentUid: parentUid) { list in
            child = list.first { $0.id == childId }
        }
        statsListener = repository.listenDailyStats(parentUid: parentUid, childId: childId, date: todayDateString()) {
            stats = $0
        }
    }

    private func stopListening() {
        childListener?.remove()
        statsListener?.remove()
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
                Text("Bedtime: \(formatMinutesOfDay(start)) - \(formatMinutesOfDay(end))").font(.caption)
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
            let updated = child.blockedDomains + [domain]
            Task { try? await repository.updateBlockedDomains(parentUid: parentUid, childId: childId, domains: updated) }
        }
        newDomain = ""
    }

    private func removeDomain(_ domain: String) {
        let updated = child.blockedDomains.filter { $0 != domain }
        Task { try? await repository.updateBlockedDomains(parentUid: parentUid, childId: childId, domains: updated) }
    }
}

/// See #28 - "always allow" lets an app through the daily limit and bedtime, no matter
/// what; meant for a phone/calling app or maps, not a way to skip a limit day to day.
private struct AppUsageSection: View {
    let repository: FamilyRepository
    let parentUid: String
    let childId: String
    let stats: DailyStats
    let child: ChildProfile
    @Binding var editingApp: String?

    private var sortedUsage: [AppUsage] {
        stats.appUsage.sorted { $0.foregroundTimeMs > $1.foregroundTimeMs }
    }

    var body: some View {
        Section {
            if stats.appUsage.isEmpty {
                Text("No app usage synced yet.").foregroundStyle(.secondary)
            }
            ForEach(sortedUsage) { app in
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
        var updated = child.alwaysAllowedPackages
        if allowed {
            if !updated.contains(packageName) { updated.append(packageName) }
        } else {
            updated.removeAll { $0 == packageName }
        }
        Task {
            try? await repository.updateAlwaysAllowedPackages(parentUid: parentUid, childId: childId, packages: updated)
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

struct BedtimeWindowSheet: View {
    let onSave: (Int?, Int?) -> Void
    let onCancel: () -> Void

    @State private var startText: String
    @State private var endText: String

    init(
        initialStartMinutes: Int?,
        initialEndMinutes: Int?,
        onSave: @escaping (Int?, Int?) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.onSave = onSave
        self.onCancel = onCancel
        _startText = State(initialValue: initialStartMinutes.map(formatHHmm) ?? "")
        _endText = State(initialValue: initialEndMinutes.map(formatHHmm) ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Text("Blocks every app during this window, independent of the daily limit. Leave both blank to turn it off.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                TextField("Start (24h, e.g. 21:00)", text: $startText)
                TextField("End (24h, e.g. 07:00)", text: $endText)
            }
            .navigationTitle("Bedtime")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: onCancel)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let start = parseHHmm(startText)
                        let end = parseHHmm(endText)
                        if let start, let end {
                            onSave(start, end)
                        } else {
                            onSave(nil, nil)
                        }
                    }
                }
            }
        }
    }
}
