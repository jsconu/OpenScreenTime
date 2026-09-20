import SwiftUI

/// Mirrors the Android parent app's PasscodeSettingsScreen.kt. Hashed with
/// PBKDF2-HMAC-SHA256 (see PasscodeHasher.swift) on save, then fanned out to every
/// existing child doc so already-paired kid devices (on either platform) pick it up.
struct PasscodeSettingsView: View {
    let repository: FamilyRepository
    let parentUid: String

    @State private var newPasscode = ""
    @State private var confirmPasscode = ""
    @State private var errorMessage: String?
    @State private var isSaving = false
    @State private var savedConfirmation = false
    @State private var useDeviceAuth = UserDefaults.standard.bool(forKey: AppLock.deviceAuthKey)
    @State private var deviceAuthAvailable = AppLock.deviceAuthAvailable()

    private var passcodesValid: Bool {
        newPasscode.count >= 4 && newPasscode.count <= 6 && newPasscode == confirmPasscode
    }

    var body: some View {
        Form {
            Section {
                Text(
                    "This locks the app, and can be entered on a paired kid device to unlock " +
                    "\u{201C}Parent controls\u{201D} there. Set a new one to replace it."
                )
                .font(.footnote)
                .foregroundStyle(.secondary)
            }
            if deviceAuthAvailable {
                Section {
                    Toggle("Unlock with Face ID, Touch ID, or your phone passcode", isOn: $useDeviceAuth)
                        .onChange(of: useDeviceAuth) { wanted in
                            if wanted {
                                // Prove it works (and that it's really the owner) before turning it on.
                                Task {
                                    let ok = await AppLock.authenticateWithDevice(reason: "Turn on unlock with Face ID, Touch ID, or your phone passcode")
                                    if ok {
                                        UserDefaults.standard.set(true, forKey: AppLock.deviceAuthKey)
                                    } else {
                                        useDeviceAuth = false
                                    }
                                }
                            } else {
                                UserDefaults.standard.set(false, forKey: AppLock.deviceAuthKey)
                            }
                        }
                } footer: {
                    Text("Anyone whose face, fingerprint, or phone passcode works on this iPhone will be able to open the app, so leave this off if a child can unlock it. The family passcode still works, and it's still what a kid's phone asks for.")
                }
            }
            Section {
                SecureField("New passcode (4-6 digits)", text: $newPasscode)
                    .keyboardType(.numberPad)
                SecureField("Confirm passcode", text: $confirmPasscode)
                    .keyboardType(.numberPad)
            }
            if let errorMessage {
                Text(errorMessage).foregroundStyle(.red).font(.footnote)
            }
            if savedConfirmation {
                Text("Passcode saved.").foregroundStyle(.green).font(.footnote)
            }
            Section {
                Button("Save passcode") { save() }
                    .disabled(!passcodesValid || isSaving)
            }
        }
        .navigationTitle("Family passcode")
    }

    private func save() {
        errorMessage = nil
        savedConfirmation = false
        guard passcodesValid else {
            errorMessage = "Enter a 4-6 digit passcode and confirm it."
            return
        }
        isSaving = true
        Task {
            let salt = PasscodeHasher.randomSalt()
            let hash = PasscodeHasher.hash(passcode: newPasscode, salt: salt)
            do {
                try await repository.setParentPasscode(parentUid: parentUid, hash: hash, salt: salt)
                newPasscode = ""
                confirmPasscode = ""
                savedConfirmation = true
            } catch {
                errorMessage = error.localizedDescription
            }
            isSaving = false
        }
    }
}
