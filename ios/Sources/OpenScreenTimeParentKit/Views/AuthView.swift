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

            Button(isSignUp ? "Create account" : "Sign in") {
                submit()
            }
            .buttonStyle(.borderedProminent)
            .disabled(email.isEmpty || password.isEmpty || isSubmitting)

            Button(isSignUp ? "Already have an account? Sign in" : "Need an account? Create one") {
                isSignUp.toggle()
                errorMessage = nil
            }
            .font(.footnote)
        }
        .padding(32)
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
