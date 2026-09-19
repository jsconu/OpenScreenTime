import SwiftUI

/// Firebase email/password auth - mirrors the Android parent app's AuthScreen.kt. Same
/// screen toggles between create-account and sign-in copy.
struct AuthView: View {
    let repository: FamilyRepository
    @ObservedObject var session: SessionStore

    @State private var email = ""
    @State private var password = ""
    @State private var isSignUp = true
    @State private var errorMessage: String?
    @State private var infoMessage: String?
    @State private var isSubmitting = false

    var body: some View {
        VStack(spacing: 16) {
            Text("OpenScreenTime")
                .font(.title2).bold()

            TextField("Email", text: $email)
                .textContentType(.emailAddress)
                .keyboardType(.emailAddress)
                .autocapitalization(.none)
                .textFieldStyle(.roundedBorder)

            SecureField("Password", text: $password)
                .textContentType(isSignUp ? .newPassword : .password)
                .textFieldStyle(.roundedBorder)

            if let errorMessage {
                Text(errorMessage)
                    .foregroundStyle(.red)
                    .font(.footnote)
            }

            if let infoMessage {
                Text(infoMessage)
                    .font(.footnote)
            }

            Button(isSignUp ? "Create account" : "Sign in") {
                submit()
            }
            .buttonStyle(.borderedProminent)
            .disabled(email.isEmpty || password.isEmpty || isSubmitting)

            if !isSignUp {
                Button("Forgot password?") {
                    sendReset()
                }
                .font(.footnote)
                .disabled(isSubmitting)
            }

            Button(isSignUp ? "Already have an account? Sign in" : "Need an account? Create one") {
                isSignUp.toggle()
                errorMessage = nil
                infoMessage = nil
            }
            .font(.footnote)
        }
        .padding(32)
    }

    private func sendReset() {
        errorMessage = nil
        infoMessage = nil
        guard !email.trimmingCharacters(in: .whitespaces).isEmpty else {
            errorMessage = "Enter your email above first, then tap Forgot password."
            return
        }
        isSubmitting = true
        Task {
            do {
                try await repository.sendPasswordReset(email: email)
                // Same message whether or not an account exists.
                infoMessage = "If there's an account for that email, a link to reset the password is on its way. Check your spam folder too."
            } catch {
                errorMessage = error.localizedDescription
            }
            isSubmitting = false
        }
    }

    private func submit() {
        errorMessage = nil
        isSubmitting = true
        Task {
            do {
                let uid: String
                if isSignUp {
                    uid = try await repository.signUpParent(email: email, password: password)
                } else {
                    uid = try await repository.signInParent(email: email, password: password)
                }
                session.parentUid = uid
            } catch {
                errorMessage = error.localizedDescription
            }
            isSubmitting = false
        }
    }
}
