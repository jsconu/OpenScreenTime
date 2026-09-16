import SwiftUI
import FirebaseFirestore
import UIKit

/// Mirrors the Android parent app's DashboardScreen.kt (v1 subset - see FamilyRepository's
/// header comment for what's not yet ported). One row per child, live from Firestore; an
/// unpaired child shows its pairing code inline until claimed.
struct DashboardView: View {
    let repository: FamilyRepository
    let parentUid: String
    @ObservedObject var session: SessionStore

    @State private var children: [ChildProfile] = []
    @State private var listener: ListenerRegistration?
    @State private var showAddChild = false
    @State private var newChildCode: String?
    @State private var errorMessage: String?
    @Environment(\.openURL) private var openURL

    var body: some View {
        childList
            .navigationTitle("Your children")
            .navigationDestination(for: String.self) { childId in
                ChildDetailView(repository: repository, parentUid: parentUid, childId: childId)
            }
            .toolbar { toolbarContent }
            .sheet(isPresented: $showAddChild) { addChildSheet }
            .sheet(item: newChildCodeBinding) { item in PairingCodeSheet(code: item.code) { newChildCode = nil } }
            .alert("Something went wrong", isPresented: errorMessageBinding) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(errorMessage ?? "")
            }
            .onAppear(perform: startListening)
            .onDisappear { listener?.remove() }
    }

    @ViewBuilder
    private var childList: some View {
        List {
            if children.isEmpty {
                Text("Add your first child to get started.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(children) { child in
                    NavigationLink(value: child.id) {
                        ChildRow(repository: repository, parentUid: parentUid, child: child)
                    }
                }
            }
        }
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .navigationBarTrailing) {
            NavigationLink("Passcode") {
                PasscodeSettingsView(repository: repository, parentUid: parentUid)
            }
        }
        ToolbarItem(placement: .navigationBarTrailing) {
            Button("Feedback", action: sendFeedbackEmail)
        }
        ToolbarItem(placement: .navigationBarTrailing) {
            Button("Sign out") { session.signOut() }
        }
        ToolbarItem(placement: .primaryAction) {
            Button {
                showAddChild = true
            } label: {
                Image(systemName: "plus")
            }
        }
    }

    private var addChildSheet: some View {
        AddChildSheet { name in
            Task { await createChild(name: name) }
        }
    }

    private var newChildCodeBinding: Binding<PairingCodeItem?> {
        Binding(get: { newChildCode.map(PairingCodeItem.init) }, set: { newChildCode = $0?.code })
    }

    private var errorMessageBinding: Binding<Bool> {
        Binding(get: { errorMessage != nil }, set: { _ in errorMessage = nil })
    }

    private func startListening() {
        listener = repository.listenChildren(parentUid: parentUid) { children = $0 }
    }

    /// See #21 - FeedbackConfig.feedbackEmail is set once by the outer app target's own
    /// init code, next to FirebaseApp.configure(); this package never holds the real address.
    private func sendFeedbackEmail() {
        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
        let device = UIDevice.current
        let body = "\n\n---\nApp version: \(appVersion)\nDevice: \(device.model), iOS \(device.systemVersion)"

        var components = URLComponents()
        components.scheme = "mailto"
        components.path = FeedbackConfig.feedbackEmail
        components.queryItems = [
            URLQueryItem(name: "subject", value: "OpenScreenTime feedback"),
            URLQueryItem(name: "body", value: body)
        ]
        if let url = components.url {
            openURL(url)
        }
    }

    private func createChild(name: String) async {
        do {
            let child = try await repository.createChild(parentUid: parentUid, name: name)
            showAddChild = false
            newChildCode = child.pairingCode
        } catch {
            showAddChild = false
            errorMessage = error.localizedDescription
        }
    }
}

private struct PairingCodeItem: Identifiable {
    var id: String { code }
    let code: String
}

private struct ChildRow: View {
    let repository: FamilyRepository
    let parentUid: String
    let child: ChildProfile

    @State private var stats = DailyStats()
    @State private var listener: ListenerRegistration?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(child.name).font(.headline)
            if !child.paired {
                Text("Waiting for device pairing (code: \(child.pairingCode))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                HStack(spacing: 16) {
                    StatColumn(label: "Screen time today", value: formatDuration(stats.totalScreenTimeMs))
                    StatColumn(label: "Unlocks", value: "\(stats.unlockCount)")
                    StatColumn(label: "Daily limit", value: "\(child.dailyLimitMinutes) min")
                }
                if child.locked {
                    Text("Screen time is paused").font(.caption).foregroundStyle(.red)
                }
            }
        }
        .onAppear {
            listener = repository.listenDailyStats(parentUid: parentUid, childId: child.id, date: todayDateString()) {
                stats = $0
            }
        }
        .onDisappear { listener?.remove() }
    }
}

private struct StatColumn: View {
    let label: String
    let value: String
    var body: some View {
        VStack(alignment: .leading) {
            Text(value).font(.subheadline).bold()
            Text(label).font(.caption2).foregroundStyle(.secondary)
        }
    }
}

private struct AddChildSheet: View {
    let onCreate: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""

    var body: some View {
        NavigationStack {
            Form {
                TextField("Child's name", text: $name)
            }
            .navigationTitle("Add a child")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Create") { onCreate(name) }.disabled(name.isEmpty)
                }
            }
        }
    }
}

private struct PairingCodeSheet: View {
    let code: String
    let onDone: () -> Void

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("Enter this code in OpenScreenTime Kid on your child's phone:")
                Text(code)
                    .font(.system(size: 36, weight: .bold, design: .monospaced))
            }
            .padding(32)
            .navigationTitle("Pairing code")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { onDone() }
                }
            }
        }
    }
}

func formatDuration(_ ms: Int64) -> String {
    let totalMinutes = ms / 60_000
    let h = totalMinutes / 60
    let m = totalMinutes % 60
    return h > 0 ? "\(h)h \(m)m" : "\(m)m"
}
