# OpenScreenTime Parent for iOS

| App | Status |
| --- | --- |
| Android parent | Ready |
| Android kid | Ready |
| **iOS parent (this app)** | **Built, not yet in the App Store** - build it yourself with Xcode for now |
| iOS kid | Future project - community welcome (see #7) |

**Beta note - no iOS kid app yet.** This is the *parent* app only. There is no
OpenScreenTime Kid for iPhone: Apple has not granted this project the Family Controls
entitlement that a kid-side screen-time app on iOS requires (see #7 for the full
requirements analysis). In practice this means:
- A child paired through this app must use OpenScreenTime Kid on an **Android** phone -
  the pairing code this app generates won't work with anything on iOS.
- Two parents on any mix of Android/iOS can manage the same Android kid device together
  (see the root README's "Multiple parents" section) - this app is fully usable for that.
- Self-tracking (a parent tracking their own device the way they track a kid's) isn't
  available here either, for the same entitlement reason - it needs the same OS-level
  monitoring capability.
- If your child uses an iPhone rather than Android, OpenScreenTime can't manage it at all
  yet. Apple's own Settings > Screen Time > Communication Limits, set up directly on the
  child's iPhone, is the closest built-in equivalent for restricting who they can call or
  text - this app has no way to configure that for you, it's purely a pointer to Apple's
  own feature.

See [issue #7](../../../issues/7) for the requirements analysis this was scoped against,
and [issue #26](../../../issues/26) for the parity pass that brought it in line with most
of the Android parent app: sign up/in, dashboard, add-child + pairing-code display, child
detail (stats, limits, lock, unlock goal, bedtime, streaks, negotiated-proposal approve/
decline, website blocking, always-allowed apps, "more time" grant/decline), passcode
settings, feedback, crash reporting. It does **not** cover self-tracking - that needs the same OS-level monitoring
capability the iOS kid app is blocked on (see #7) - or off-screen-idea tips or an
appearance (theme/text size) settings screen, both lower-priority and not yet ported.
The in-app **help bot** (#36, the "?" on the dashboard) is here too: a Swift port of the same
offline matcher reading a byte-identical copy of the shared cited knowledge base
(`Sources/OpenScreenTimeParentKit/Resources/knowledge.json`; edit the one under
`shared/src/main/resources/helpbot/` and copy it over - tests on both platforms enforce it).
The optional unlock/notification **tracking toggles** (#35) are here too, but only as switches -
the counting happens on the Android devices and the report that displays it isn't ported. The
Android parent app's **weekly report** (#29, a rolling 4-week bar chart and by-app
trend) also hasn't been ported here yet - a sizable SwiftUI/Swift Charts lift on its own,
tracked as a follow-up rather than bundled into #26's parity pass. Bedtime call/text
blocking (#34) and uninstall protection (#31) are Android-kid-device-only features with no
iOS equivalent to port, since there's no iOS kid app for them to run on.

This was written and CI-verified (see `.github/workflows/ios-ci.yml`, which actually
builds and tests it on a macOS runner) without a local Mac/Xcode available - there is no
substitute for opening it in Xcode and trying it against a real Firebase project before
relying on it. In particular, double-check `PasscodeHasher.swift` against a real device
before trusting it for anything beyond the numeric passcodes this app's own UI enforces -
see the comment on its test vector for exactly what is and isn't verified.

## What this is

`OpenScreenTimeParentKit` is a Swift Package containing all of the actual app logic and
UI: the Firestore data models (mirroring `shared/model/*.kt`), the repository layer
(mirroring `shared/repo/FamilyRepository.kt`), the passcode hasher (mirroring
`shared/util/PasscodeHasher.kt` - same PBKDF2-HMAC-SHA256 parameters, so a passcode set on
one platform verifies correctly on the other), and the SwiftUI screens. Packaging it this
way means it can be built and tested by CI without a full Xcode project checked into the
repo, which would otherwise be a binary-ish, mostly Xcode-generated artifact that's risky
to hand-write blind.

Turning it into an installable app needs one more thing that only Xcode can produce: the
actual `.xcodeproj`/`.app` shell (entitlements, asset catalog, `Info.plist`, code signing).
That's a one-time, roughly 10-minute step:

## One-time setup (needs a Mac with Xcode)

1. In Xcode: **File > New > Project > iOS > App**. Name it `OpenScreenTimeParent`,
   interface **SwiftUI**, language **Swift**. Save it anywhere outside this `ios/`
   folder (e.g. a sibling `ios-app/` directory) to avoid the new project's own files
   colliding with this package's.
2. **File > Add Package Dependencies... > Add Local...** and select this `ios/` folder.
   Add the `OpenScreenTimeParentKit` library to your new app target.
3. Replace the generated `ContentView`/`@main App` body with:
   ```swift
   import SwiftUI
   import OpenScreenTimeParentKit

   @main
   struct OpenScreenTimeParentApp: App {
       init() {
           FirebaseApp.configure() // import FirebaseCore
       }
       var body: some Scene {
           WindowGroup { RootView() }
       }
   }
   ```
4. Add the Firebase iOS SDK's `FirebaseCore` product to the app target too (it comes in
   transitively via the package, but `FirebaseApp.configure()` needs it imported directly
   in the app target).
5. Get a `GoogleService-Info.plist` for this app from the Firebase console (the same
   Firebase project the Android apps use - see `firebase.json` at the repo root) and drag
   it into the Xcode project, making sure it's added to the app target.
6. For crash reporting (Crashlytics, see #21): add the "Upload Crashlytics Symbols" Run
   Script build phase described in
   [Firebase's Crashlytics setup docs](https://firebase.google.com/docs/crashlytics/get-started?platform=ios)
   to the app target - this actually uploads dSYMs so a crash's stack trace is
   symbolicated in the Firebase console. `FirebaseCrashlytics` is already a dependency of
   `OpenScreenTimeParentKit`, so no separate package addition is needed here.
7. Build and run on a simulator or device.

## Local development

```bash
cd ios
swift build   # compiles the package
swift test    # runs PasscodeHasherTests, etc.
```

`swift build`/`swift test` alone can't produce a runnable iOS app (see above), but they're
enough to catch compile errors and test failures without Xcode - which is also exactly
what `.github/workflows/ios-ci.yml` runs (via `xcodebuild`, which can target a Package.swift
directly) on every push that touches this directory.

## Face ID note for the Xcode app target

The optional "Unlock with Face ID, Touch ID, or your phone passcode" setting (Passcode) uses the system
`LocalAuthentication` prompt. Add an `NSFaceIDUsageDescription` entry to the app target's Info.plist, for
example "OpenScreenTime uses Face ID to open the app when you choose to unlock with it." Without it, Face ID
is unavailable (Touch ID and the phone passcode still work).
