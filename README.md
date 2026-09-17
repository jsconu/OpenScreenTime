# OpenScreenTime

OpenScreenTime is a free, open-source, no-nonsense screen time platform for families. The project is designed to work across Android and iOS, starting with a functional Android implementation for parents and kids.

No account fees, no ads, no dark patterns. MIT licensed — fork it, self-host
your own backend, send patches back.

**Project status: Early functional prototype**

Android pairing, screen-time tracking, limits, locking, and parent controls are implemented. Cross-platform support and real-device testing are ongoing.

**Why OpenScreenTime?**

Most screen-time tools are built around subscriptions, engagement, and increasingly complex feature sets. OpenScreenTime takes a different approach: give parents a small set of useful controls, make the software transparent, and get out of the way.

The goal isn't to maximize time spent in an app. It's to help families use technology intentionally.

OpenScreenTime is MIT licensed so families and developers can inspect it, modify it, self-host it, and contribute improvements.

## How it works

- **`kid`** — installed on the child's phone. Runs quietly in the background,
  measures screen time and unlocks, enforces the limits a parent has set, and
  syncs a daily summary to the cloud. Optionally, the kid can turn on a local
  notification digest (plain text, grouped by app, never synced to a parent).
- **`parent`** — installed on the parent's phone. Shows live stats for each
  paired child and lets the parent change the daily limit or a per-app limit
  at any time, and mark specific apps "always allowed" so they stay usable
  (a phone/calling app, maps) even once the daily limit or bedtime hits.
- **`shared`** — the data models and Firestore access code both apps use.
- **`ios`** — an iOS build of the parent app (Swift/SwiftUI), for a parent on
  an iPhone pairing with an Android kid device. See [`ios/README.md`](ios/README.md)
  for setup and current scope; there is no iOS kid app yet (see
  [issue #7](https://github.com/jsconu/OpenScreenTime/issues/7) for why that's a
  much bigger, separate undertaking).

Sync between the two apps runs on [Firebase](https://firebase.google.com)
(Firestore + Authentication). There is no custom server to host — each
person running their own copy of this app points it at their own free
Firebase project.

**Multiple parents:** two parents (any mix of Android and iOS) see and
control the same child by signing into the same parent account
(email/password) on each of their own devices — nothing extra to set up.

```
parent app  <---sync--->  Firebase (Firestore + Auth)  <---sync--->  kid app
```

### Pairing

1. In the parent app, tap "+" and name the child. This shows a 6-digit
   pairing code and creates a child profile in Firestore.
2. In the kid app, enter that code. The kid app signs in anonymously to
   Firebase and atomically claims the code, linking that device to the child
   profile. No child email or personal info is ever collected.

### Family passcode, "Parent controls," and locking

Set a passcode from the parent app (top bar -> Passcode). It does two things:

- It locks the parent app itself on next launch (a saved hash, checked
  locally - no extra network round trip).
- The same passcode can be entered on a paired kid device (Status screen ->
  "Parent controls") to unlock the same limit editors and lock button
  *locally on that device*, without needing the parent's phone in hand. The
  kid device verifies it against a hash synced from Firestore, entirely
  offline.

Either app can hit "Lock now" / "End screen time now" at any point to block
every app on the kid's device immediately, regardless of the daily limit -
useful for dinner, bedtime, or just needing quiet right now. "Resume" undoes
it. The kid app also posts a one-time notification when screen time or an
app is within 5 minutes of its limit, so a limit is rarely a total surprise.

### What "screen time" means here

- **Screen time** = time spent unlocked and interactive, measured from
  `ACTION_USER_PRESENT` (unlock) to the next `ACTION_SCREEN_OFF`. Time spent
  sitting on the lock screen doesn't count.
- **Unlocks** = number of `ACTION_USER_PRESENT` broadcasts per day.
- **Per-app time** = attributed via an `AccessibilityService` watching
  foreground window changes — this is also how app and daily limits are
  enforced (a full-screen block is shown once a limit is hit).

Everything resets at local midnight on the kid's device.

## Project status

This is an early, functional scaffold: the pairing flow, background
tracking, limit enforcement, and the parent dashboard are all implemented,
but it hasn't been through a real build/device test pass yet (see
[Building](#building) below) or a security audit. Treat it as a solid
starting point to build on, not a finished product — contributions very
welcome, especially around real-device testing, notification nudges before a
limit hits, and weekly/historical stats.

## Building

You'll need [Android Studio](https://developer.android.com/studio)
(Ladybug or newer) with a JDK 17 and Android SDK 35.

1. **Create a Firebase project** at [console.firebase.google.com](https://console.firebase.google.com)
   (the free Spark plan is enough — no credit card required).
2. In the Firebase console, register **two** Android apps in that one
   project:
   - Package name `org.openscreentime.kid`
   - Package name `org.openscreentime.parent`
3. Download `google-services.json` (it will contain both apps) and copy the
   **same file** into both `kid/google-services.json` and
   `parent/google-services.json`. These are gitignored — never commit them.
4. In the Firebase console, enable **Authentication** providers:
   Email/Password (for parents) and Anonymous (for kid devices).
5. Enable **Firestore Database** (production mode), then publish the rules
   in [`firebase/firestore.rules`](firebase/firestore.rules) — either paste
   them into the console's Rules tab, or install the
   [Firebase CLI](https://firebase.google.com/docs/cli) and run
   `firebase deploy --only firestore:rules` from a directory containing a
   `firebase.json` pointing at that file.
6. Open the project root in Android Studio and let it sync. Run the `kid`
   configuration on one device/emulator and `parent` on another (or the
   same emulator with two profiles) to test pairing end-to-end.

> **Note on the Gradle wrapper:** this repo doesn't check in the
> `gradle-wrapper.jar` binary. Android Studio doesn't need it to sync or
> run the project. If you want to build from the command line, generate it
> once with `gradle wrapper --gradle-version 8.9` (requires a local Gradle
> install), after which `./gradlew` will work normally.

On the kid's device, after installing, the app will ask you to grant a few
things from its status screen: **Accessibility service**, **display over
other apps**, **notifications**, **battery optimization**, and the
**website filter**. Those are required for tracking and limit enforcement.
An optional **calm notification list** can be turned on separately; it is
not part of that core checklist.

## Security & privacy notes for anyone deploying this

- The kid app never reads screen *content* — the accessibility service only
  observes which app's window is in front, nothing more
  (`canRetrieveWindowContent="false"`).
- Firestore security rules (`firebase/firestore.rules`) are written so a kid
  device can only ever read/write the one child record it claimed via
  pairing — never another family's data. Please review them yourself before
  relying on this for anything sensitive; this is a community project, not
  an audited product.
- This kind of app is inherently powerful (it can see app usage and block
  apps). Use it thoughtfully and talk to your kid about it — it's meant to
  support a conversation about healthy screen time, not to be sprung on
  someone unannounced.
- All three apps report crashes to Firebase Crashlytics (same Firebase
  project as everything else): stack traces plus device model/OS/app
  version, never any family data. CI/emulator builds never report.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Issues and PRs welcome.

## License

MIT — see [LICENSE](LICENSE). Free forever, use it however you like.
