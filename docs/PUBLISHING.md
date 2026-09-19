# Publishing to Google Play (internal testing)

This is the low-risk path: a **private internal-testing track** on Google Play, with a small,
invited group of testers, backed by a **Firebase project that only you run**. Nothing here makes
the apps public. Play's rules change - confirm anything marked *(verify)* in Play Console.

Everything the code needs is already in the repo (release signing hooks, R8 minification, the
Accessibility disclosure screen, version numbers). What's left are the account and console steps
only you can do. Tick them off in order.

## 1. Firebase (your own, dedicated project)

- [ ] Create a **new Firebase project** just for the Play build. Keep it separate from any personal
      project. Testers' data will live here.
- [ ] Register two Android apps: `org.openscreentime.kid` and `org.openscreentime.parent`.
- [ ] Download `google-services.json` and copy the **same file** into `kid/` and `parent/`. Both are
      gitignored - never commit them.
- [ ] Authentication -> enable **Anonymous** (kid devices) and **Email/Password** (parents).
- [ ] Create the **Firestore** database in production mode.
- [ ] Deploy the security rules. From the repo root, with the [Firebase CLI](https://firebase.google.com/docs/cli)
      logged in and pointed at your project (`firebase use <project-id>`):

      firebase deploy --only firestore:rules

- [ ] Google Cloud console -> Billing -> **Budgets & alerts**: set a small budget with email alerts.
- [ ] *(Optional, recommended later)* **App Check** with Play Integrity. Needs code changes, tracked in
      [#39](https://github.com/jsconu/OpenScreenTime/issues/39).

## 2. Signing key (once, on your machine)

The upload key never goes in git and never passes through anyone else. Create it with the JDK that
ships with Android Studio (its `jbr/bin/keytool`), or any JDK 17:

    keytool -genkeypair -v -keystore openscreentime-upload.jks -alias upload \
      -keyalg RSA -keysize 2048 -validity 10000

- [ ] Back up `openscreentime-upload.jks` and its passwords in **two separate places**.
- [ ] Create `keystore.properties` in the repo root (it is gitignored):

      storeFile=openscreentime-upload.jks
      storePassword=YOUR_STORE_PASSWORD
      keyAlias=upload
      keyPassword=YOUR_KEY_PASSWORD

      (`storeFile` is resolved relative to the repo root. You can use the environment variables
      `OST_KEYSTORE_FILE`, `OST_KEYSTORE_PASSWORD`, `OST_KEY_ALIAS` and `OST_KEY_PASSWORD` instead.)

With Play App Signing (step 4), Google holds the real app-signing key and this is only the *upload*
key - so if you lose it, Google can reset it. Still back it up.

## 3. Build and test a release

- [ ] In Android Studio: **Build -> Generate Signed App Bundle / APK -> Android App Bundle**, once
      for `kid` and once for `parent` (or run `gradle :kid:bundleRelease :parent:bundleRelease`).
      Outputs land in `kid/build/outputs/bundle/release/` and `parent/build/outputs/bundle/release/`.
- [ ] Install a **release** build on a real phone (`bundletool` or build a release APK) and test the
      whole flow, not only debug builds. Minification can break things that debug doesn't:
      - pairing a kid phone, expired/used codes, passcode set and entered
      - limits, bedtime, the block screen and its OK button, "Lock now"
      - website filter (VPN), notification listener, uninstall protection, bedtime calls
      - the help bot answers, the weekly report, tracking toggles
      - a test crash reaches Crashlytics
- [ ] Bump `versionCode` (in `kid/build.gradle.kts` and `parent/build.gradle.kts`) for **every** upload.
      They are currently `2` / `0.2.0`.
- [ ] In the Firebase console, add the **SHA-1 and SHA-256** of the upload key *and* of the Play App
      Signing key (shown in Play Console -> Setup -> App signing, after the first upload) to each app.
      Get the upload key's fingerprints with `keytool -list -v -keystore openscreentime-upload.jks`.

## 4. Play Console

- [ ] Create a Play developer account (one-time fee, identity verification). *(verify current
      requirements for new personal accounts - Google has added testing requirements before
      production access.)*
- [ ] Create the app (start with the **parent** app if you want the smallest first step).
- [ ] Opt in to **Play App Signing** when you upload the first bundle.
- [ ] **App content** section - fill in every form. Draft answers are in
      [`PLAY_CONSOLE_ANSWERS.md`](PLAY_CONSOLE_ANSWERS.md):
  - [ ] Privacy policy URL (host [`PRIVACY.md`](PRIVACY.md), e.g. with GitHub Pages)
  - [ ] Data safety
  - [ ] Target audience and content / Families questions
  - [ ] Permissions declarations (Accessibility, VPN, and the others listed there)
- [ ] Testing -> **Internal testing** -> create a release, upload the `.aab`, add testers (by email or
      a Google Group), and share the opt-in link.
- [ ] Accept and install through the opt-in link on a tester's phone.

## 5. After the first testers install

- [ ] Confirm a crash shows up in Crashlytics.
- [ ] Look at Firestore usage and cost after a few days.
- [ ] Tell testers plainly: it's a beta, the passcode is a deterrent (not a lock), and their data goes
      to your Firebase project. Provide the deletion contact from the privacy policy.
- [ ] Stay on internal testing unless you have a reason to widen it.

## Deleting a family's data (when someone asks)

In the Firebase console: Authentication -> delete the parent's user, then Firestore -> delete
`parents/{that user's uid}` (including its `children` and `dailyStats` subcollections - the console's
delete offers to remove subcollections too) and any `pairingCodes` documents whose `parentUid` is that
uid. Kid devices use anonymous accounts; deleting the parent's data ends what they can read.
