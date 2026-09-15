import SwiftUI

/// The package's entry point view. A thin Xcode app project's `@main App` should show
/// this as its root - see `ios/README.md` for the one-time setup that turns this package
/// into an installable app.
public struct RootView: View {
    @StateObject private var session: SessionStore

    public init(repository: FamilyRepository = FamilyRepository()) {
        _session = StateObject(wrappedValue: SessionStore(repository: repository))
    }

    public var body: some View {
        Group {
            if let parentUid = session.parentUid {
                NavigationStack {
                    DashboardView(repository: session.repository, parentUid: parentUid, session: session)
                }
            } else {
                AuthView(repository: session.repository, session: session)
            }
        }
    }
}

/// Tracks the signed-in parent's uid across the app - mirrors the kid app's simple
/// `paired`/`signedIn` boolean state hoisted to the activity root on Android.
@MainActor
final class SessionStore: ObservableObject {
    let repository: FamilyRepository
    @Published var parentUid: String?

    init(repository: FamilyRepository) {
        self.repository = repository
        self.parentUid = repository.currentUid
    }

    func signOut() {
        try? repository.signOut()
        parentUid = nil
    }
}
