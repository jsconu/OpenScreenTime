import SwiftUI
import FirebaseFirestore

/// Mirrors the Android parent app's ChildDetailScreen.kt (v1 subset). Full stats, the
/// daily-limit progress, and per-app limit editing.
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
    @State private var childListener: ListenerRegistration?
    @State private var statsListener: ListenerRegistration?
    @State private var showLimitDialog = false
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
            .sheet(item: editingAppBinding) { item in appLimitSheet(for: item) }
    }

    @ViewBuilder
    private var content: some View {
        if let child {
            List {
                ExtraTimeRequestSection(repository: repository, parentUid: parentUid, childId: childId, child: child)
                LockSection(repository: repository, parentUid: parentUid, childId: childId, child: child, showLockConfirm: $showLockConfirm)
                TodaySection(stats: stats, child: child, showLimitDialog: $showLimitDialog)
                WebsiteBlockingSection(
                    repository: repository,
                    parentUid: parentUid,
                    childId: childId,
                    child: child,
                    newDomain: $newBlockedDomain
                )
                AppUsageSection(stats: stats, child: child, editingApp: $editingApp)
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
    @Binding var showLimitDialog: Bool

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
            ProgressView(value: progress)
            Text("Daily limit: \(child.dailyLimitMinutes) min").font(.caption)
            Button("Change daily limit") { showLimitDialog = true }
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

private struct AppUsageSection: View {
    let stats: DailyStats
    let child: ChildProfile
    @Binding var editingApp: String?

    private var sortedUsage: [AppUsage] {
        stats.appUsage.sorted { $0.foregroundTimeMs > $1.foregroundTimeMs }
    }

    var body: some View {
        Section("App usage today") {
            if stats.appUsage.isEmpty {
                Text("No app usage synced yet.").foregroundStyle(.secondary)
            }
            ForEach(sortedUsage) { app in
                AppUsageRow(app: app, limitMinutes: child.appLimits[app.packageName]) {
                    editingApp = app.packageName
                }
            }
        }
    }
}

private struct AppUsageRow: View {
    let app: AppUsage
    let limitMinutes: Int?
    let onTap: () -> Void

    private var subtitle: String {
        if let limitMinutes {
            return "\(formatDuration(app.foregroundTimeMs)) of \(limitMinutes)m limit"
        }
        return formatDuration(app.foregroundTimeMs)
    }

    var body: some View {
        Button(action: onTap) {
            HStack {
                VStack(alignment: .leading) {
                    Text(app.appName)
                    Text(subtitle).font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Text("Limit").font(.caption).foregroundStyle(.blue)
            }
        }
        .foregroundStyle(.primary)
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
