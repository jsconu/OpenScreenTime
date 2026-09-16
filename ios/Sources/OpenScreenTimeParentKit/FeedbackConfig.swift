import Foundation

/// The feedback email's real destination address never enters this repo, in any form -
/// unlike the Android build (which reads it from a gitignored `local.properties`), this
/// package's real installable app lives entirely outside this repo (see ios/README.md's
/// one-time Xcode setup). Set this once, from that outer app's own init code, right next
/// to `FirebaseApp.configure()` (see #21).
public enum FeedbackConfig {
    public static var feedbackEmail = "configure-FeedbackConfig.feedbackEmail-in-your-app-target@example.com"
}
