import SwiftUI
import FirebaseFirestore

/// Mirrors the Android parent app's ChildDetailScreen.kt (v1 subset). Full stats, the
/// daily-limit progress, and per-app limit editing.
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
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Group {
            if let child {
                List {
                    Section {
                        Button(child.locked ? "Resume screen time" : "End screen time now") {
                            if child.locked {
                                Task { try? await repository.setLocked(parentUid: parentUid, childId: childId, locked: false) }
                            } else {
                                showLockConfirm = true
                            }
                        }
                        .foregroundStyle(child.locked ? .primary : .red)
                    }

                    Section("Today") {
                        HStack(spacing: 24) {
                            StatBlock(label: "Screen time", value: formatDuration(stats.totalScreenTimeMs))
                            StatBlock(label: "Unlocks", value: "\(stats.unlockCount)")
                        }
                        ProgressView(value: min(1, Double(stats.totalScreenTimeMs) / 60_000 / Double(max(1, child.dailyLimitMinutes))))
                        Text("Daily limit: \(child.dailyLimitMinutes) min").font(.caption)
                        Button("Change daily limit") { showLimitDialog = true }
                    }

                    Section("App usage today") {
                        if stats.appUsage.isEmpty {
                            Text("No app usage synced yet.").foregroundStyle(.secondary)
                        }
                        ForEach(stats.appUsage.sorted(by: { $0.foregroundTimeMs > $1.foregroundTimeMs })) { app in
                            Button {
                                editingApp = app.packageName
                            } label: {
                                HStack {
                                    VStack(alignment: .leading) {
                                        Text(app.appName)
                                        if let limit = child.appLimits[app.packageName] {
                                            Text("\(formatDuration(app.foregroundTimeMs)) of \(limit)m limit").font(.caption).foregroundStyle(.secondary)
                                        } else {
                                            Text(formatDuration(app.foregroundTimeMs)).font(.caption).foregroundStyle(.secondary)
                                        }
                                    }
                                    Spacer()
                                    Text("Limit").font(.caption).foregroundStyle(.blue)
                                }
                            }
                            .foregroundStyle(.primary)
                        }
                    }

                    Section {
                        Button("Remove \(child.name)", role: .destructive) { showDeleteConfirm = true }
                    }
                }
            } else {
                ProgressView()
            }
        }
        .navigationTitle(child?.name ?? "")
        .onAppear {
            childListener = repository.listenChildren(parentUid: parentUid) { list in
                child = list.first { $0.id == childId }
            }
            statsListener = repository.listenDailyStats(parentUid: parentUid, childId: childId, date: todayDateString()) {
                stats = $0
            }
        }
        .onDisappear {
            childListener?.remove()
            statsListener?.remove()
        }
        .confirmationDialog("End screen time now?", isPresented: $showLockConfirm, titleVisibility: .visible) {
            Button("End now", role: .destructive) {
                Task { try? await repository.setLocked(parentUid: parentUid, childId: childId, locked: true) }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This blocks every app on \(child?.name ?? "their")'s device until you resume it.")
        }
        .confirmationDialog("Remove \(child?.name ?? "")?", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
            Button("Remove", role: .destructive) {
                Task {
                    try? await repository.deleteChild(parentUid: parentUid, childId: childId)
                    dismiss()
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This deletes \(child?.name ?? "")'s profile and all of their screen time history. This can't be undone.")
        }
        .sheet(isPresented: $showLimitDialog) {
            MinutesInputSheet(title: "Daily screen time limit", initialMinutes: child?.dailyLimitMinutes ?? 120) { minutes in
                Task { try? await repository.updateDailyLimit(parentUid: parentUid, childId: childId, minutes: minutes) }
                showLimitDialog = false
            } onCancel: {
                showLimitDialog = false
            }
        }
        .sheet(item: Binding(get: { editingApp.map(EditingApp.init) }, set: { editingApp = $0?.packageName })) { item in
            let appName = stats.appUsage.first { $0.packageName == item.packageName }?.appName ?? item.packageName
            MinutesInputSheet(
                title: "Daily limit for \(appName)",
                initialMinutes: child?.appLimits[item.packageName] ?? 60
            ) { minutes in
                if var updated = child?.appLimits { updated[item.packageName] = minutes
                    Task { try? await repository.updateAppLimits(parentUid: parentUid, childId: childId, appLimits: updated) }
                }
                editingApp = nil
            } onCancel: {
                editingApp = nil
            }
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
