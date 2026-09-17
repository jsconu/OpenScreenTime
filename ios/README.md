# OpenScreenTime Parent for iOS

See [issue #7](../../../issues/7) for the requirements analysis this was scoped against.
This covers that issue's iOS-parent-app checklist: sign up/in, dashboard, add-child +
pairing-code display, child detail (stats, limits, lock), and passcode settings. It does
**not** yet cover the newer Android-parent features (self-tracking, bedtime windows,
negotiated-limit proposals, unlock goals, streaks, appearance settings) - those can be
ported later the same way, using `shared/repo/FamilyRepository.kt` as the reference.

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
