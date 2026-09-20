import LocalAuthentication
import SwiftUI

/// The phone's own Face ID / Touch ID / passcode, used only to open the PARENT app, only if a parent turned it
/// on (Passcode settings). There is no kid app on iPhone, so unlike Android there is nothing to keep it out of.
enum AppLock {
    static let deviceAuthKey = "openscreentime.useDeviceAuth"

    static func deviceAuthAvailable() -> Bool {
        var error: NSError?
        return LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: &error)
    }

    /// Shows the system prompt; true only on a successful authentication.
    static func authenticateWithDevice(reason: String) async -> Bool {
        let context = LAContext()
        do {
            return try await context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason)
        } catch {
            return false
        }
    }
}

/// Sits between sign-in and the dashboard. With no family passcode set it does nothing; with one, the parent
/// app opens only after the passcode (or, if turned on, Face ID / Touch ID / the phone passcode) - the same
/// lock the Android parent app has. Unlocked once per launch.
struct AppLockGate<Content: View>: View {
    let repository: FamilyRepository
    let parentUid: String
    @ObservedObject var session: SessionStore
    @ViewBuilder let content: () -> Content

    @State private var passcode: PasscodeInfo?
    @State private var loaded = false

    var body: some View {
        Group {
            if !loaded {
                ProgressView()
            } else if let passcode, !session.unlocked {
                PasscodeLockView(
                    repository: repository,
                    parentUid: parentUid,
                    passcode: passcode,
                    onUnlocked: { session.unlocked = true },
                    onPasscodeReset: { info in
                        self.passcode = info
                        session.unlocked = true
                    }
                )
            } else {
                content()
            }
        }
        .task {
            // A transient failure (offline) fails open, the same as "no passcode set yet": it only affects
            // this app's own lock screen, and whoever has the phone unlocked already has physical access.
            do {
                passcode = try await repository.getParentPasscode(parentUid: parentUid)
            } catch {
                passcode = nil
            }
            loaded = true
        }
    }
}

struct PasscodeLockView: View {
    let repository: FamilyRepository
    let parentUid: String
    let passcode: PasscodeInfo
    let onUnlocked: () -> Void
    let onPasscodeReset: (PasscodeInfo) -> Void

    private static let maxAttempts = 5

    @State private var entered = ""
    @State private var errorMessage: String?
    @State private var attempts = 0
    @State private var checking = false
    @State private var showForgot = false

    private var locked: Bool { attempts >= Self.maxAttempts }
    private var deviceAuthOn: Bool {
        UserDefaults.standard.bool(forKey: AppLock.deviceAuthKey) && AppLock.deviceAuthAvailable()
    }

    var body: some View {
        VStack(spacing: 16) {
            Text("Enter passcode").font(.title2).bold()
            SecureField("Passcode", text: $entered)
                .keyboardType(.numberPad)
                .textFieldStyle(.roundedBorder)
                .disabled(locked)
            if let errorMessage {
                Text(errorMessage).foregroundStyle(.red).font(.footnote).multilineTextAlignment(.center)
            }
            Button(checking ? "Checking..." : "Unlock") { check() }
                .buttonStyle(.borderedProminent)
                .disabled(entered.isEmpty || locked || checking)
            if deviceAuthOn {
                Button("Use Face ID, Touch ID, or phone passcode") { deviceUnlock() }
            }
            Button("Forgot passcode?") { showForgot = true }
                .font(.footnote)
        }
        .padding(32)
        .task { if deviceAuthOn { deviceUnlock() } }
        .sheet(isPresented: $showForgot) {
            ForgotPasscodeSheet(repository: repository, parentUid: parentUid) { info in
                showForgot = false
                onPasscodeReset(info)
            } onCancel: {
                showForgot = false
            }
        }
    }

    private func deviceUnlock() {
        Task {
            if await AppLock.authenticateWithDevice(reason: "Unlock OpenScreenTime") { onUnlocked() }
        }
    }

    private func check() {
        checking = true
        let attempt = entered
        Task {
            let ok = PasscodeHasher.verify(passcode: attempt, salt: passcode.salt, expectedHash: passcode.hash)
            checking = false
            if ok {
                onUnlocked()
            } else {
                attempts += 1
                entered = ""
                errorMessage = attempts >= Self.maxAttempts
                    ? "Too many incorrect attempts. Try again later, or use \u{201C}Forgot passcode?\u{201D}."
                    : "Incorrect passcode."
            }
        }
    }
}

/// Replaces a forgotten family passcode: first proves the person knows the account password (so a child holding
/// the phone can't do it), then sets a new one for this account and every paired kid device. Recovery asks for
/// the account password and NOT the phone's own lock: anyone who knows the phone's PIN could otherwise reset
/// the passcode and walk into the parent controls.
private struct ForgotPasscodeSheet: View {
    let repository: FamilyRepository
    let parentUid: String
    let onReset: (PasscodeInfo) -> Void
    let onCancel: () -> Void

    @State private var verified = false
    @State private var password = ""
    @State private var newPasscode = ""
    @State private var confirm = ""
    @State private var busy = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                if !verified {
                    Section {
                        Text("To make sure it's you, enter the password for your OpenScreenTime account (the one you sign in with). This is not your phone's passcode.")
                            .font(.callout)
                        SecureField("Account password", text: $password)
                    }
                } else {
                    Section {
                        Text("This replaces the old passcode here and on every paired kid phone.")
                            .font(.callout)
                        SecureField("New passcode (4-6 digits)", text: $newPasscode).keyboardType(.numberPad)
                        SecureField("Confirm passcode", text: $confirm).keyboardType(.numberPad)
                    }
                }
                if let errorMessage {
                    Text(errorMessage).foregroundStyle(.red).font(.footnote)
                }
            }
            .navigationTitle(verified ? "Choose a new passcode" : "Reset your passcode")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: onCancel).disabled(busy)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(busy ? "Working..." : (verified ? "Save passcode" : "Continue")) { proceed() }
                        .disabled(busy || (verified ? !(4...6).contains(newPasscode.count) : password.isEmpty))
                }
            }
        }
    }

    private func proceed() {
        errorMessage = nil
        busy = true
        Task {
            defer { busy = false }
            do {
                if !verified {
                    if try await repository.verifyAccountPassword(password) {
                        verified = true
                        password = ""
                    } else {
                        errorMessage = "That isn't the account password."
                    }
                } else if newPasscode != confirm {
                    errorMessage = "Passcodes don't match."
                } else {
                    let salt = PasscodeHasher.randomSalt()
                    let hash = PasscodeHasher.hash(passcode: newPasscode, salt: salt)
                    try await repository.setParentPasscode(parentUid: parentUid, hash: hash, salt: salt)
                    onReset(PasscodeInfo(hash: hash, salt: salt))
                }
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }
}
