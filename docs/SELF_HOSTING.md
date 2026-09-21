# Run the cloud build on your own Firebase project

This is for linking **two** phones — a parent's and a child's — so limits can be seen and changed
remotely, a phone can be locked from elsewhere, and a child can ask for more time from school. You
create a free Firebase project, build the two apps against it, and your family's data lives in an
account only you control. Nobody else — including this project's maintainer — can see it.

> **You may not need any of this.** If you want limits on one phone, set in person, download the
> **local** build from [the latest release](https://github.com/jsconu/OpenScreenTime/releases/latest)
> and stop reading: no Firebase project, no account, nothing to set up, and nothing leaves the
> phone. [LOCAL_AND_CLOUD.md](LOCAL_AND_CLOUD.md) sets out exactly what each build can and cannot
> do. Come back here when you want the remote half.

It takes about **45 minutes** the first time. You don't need to be a developer, but you do need
to be comfortable following exact instructions and waiting for a build. If you get stuck, open
an issue and say which step — the guide is a bug if a step doesn't work.

**What you need:** a computer (Windows, macOS or Linux), a Google account, two Android phones
(one for the parent, one for the child), and [Android Studio](https://developer.android.com/studio).
Android 8.0 or newer on both phones.

**What it costs:** nothing, in practice. Firebase's free Spark plan needs no credit card, and one
family's usage is a rounding error against its daily free quota — a few thousand small document
reads and writes a day. You'd have to be running this for dozens of families before paying
anything.

---

## Part 1 — Create your Firebase project

1. Go to [console.firebase.google.com](https://console.firebase.google.com) and click **Create a
   project**. Name it anything (`our-family-screentime` is fine). You can turn Google Analytics
   off; this project doesn't use it.

2. Inside the project, add **two** Android apps — the Android icon on the project overview. The
   package names must match exactly, including the dots:

   - `org.openscreentime.parent`
   - `org.openscreentime.kid`

   Leave the nickname and SHA-1 fields blank; neither is needed.

3. After the second app, download **`google-services.json`**. One file covers both apps, and it
   goes in one place in the source you downloaded:

   - `cloud/google-services.json`

   That's the `cloud` module - the only part of the project that talks to Firebase at all. It's
   already in `.gitignore`, so it won't be committed if you fork.

4. In the left sidebar, open **Build → Authentication → Get started**, and enable two sign-in
   providers:

   - **Email/Password** — how a parent signs in
   - **Anonymous** — how a kid's phone identifies itself, with no account

5. Open **Build → Firestore Database → Create database**. Choose a location near you and start
   in **production mode** (the rules you publish next are what actually protect the data).

6. Open the **Rules** tab, delete everything in the editor, and paste the entire contents of
   [`firebase/firestore.rules`](../firebase/firestore.rules) from this repository. Click
   **Publish**.

   > **This step is the one people get wrong.** The apps and these rules are one design. If the
   > rules in your project are older than the apps you build, things fail in ways that look like
   > unrelated bugs — pairing refusing a brand-new code, most commonly. Any time you update the
   > apps, re-publish this file too.

---

## Part 2 — Build the two apps

1. Open Android Studio, choose **Open**, and select the folder you downloaded. Wait for the first
   sync to finish — it downloads a lot the first time, and several minutes is normal.

2. If it complains about a missing SDK or JDK, accept the prompts to install them. This project
   needs **Android SDK 35** and **JDK 17**.

3. In the **Build Variants** panel (bottom left), set both modules to a **cloud** variant -
   `cloudDebug`. This is the step that decides whether the build talks to your Firebase project at
   all; a `local` variant ignores it completely.

4. Choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**. When it finishes, the notification
   has a **locate** link; the files are at:

   - `parent/build/outputs/apk/cloud/debug/parent-cloud-debug.apk`
   - `kid/build/outputs/apk/cloud/debug/kid-cloud-debug.apk`

   These are debug builds, which is fine for your own family — they're signed with Android
   Studio's automatic key and install like any other app. (If you'd rather make proper signed
   release builds, for example to put them on a private Play Store track,
   [docs/PUBLISHING.md](PUBLISHING.md) covers that.)

5. Copy each APK to the right phone: **parent** on the adult's phone, **kid** on the child's.
   Email them to yourself, use a USB cable, or any file transfer you like.

---

## Part 3 — Install, permit, and pair

[**docs/INSTALL_ANDROID.md**](INSTALL_ANDROID.md) walks through the rest in plain language: the
"install unknown apps" and Play Protect prompts, creating the parent account, every permission
the kid app needs and why, Android's "Allow restricted settings" step for Accessibility, and the
phone-maker battery settings that keep tracking alive.

The short version: install the parent app, create an account, add a child, and it shows a
six-digit code. Install the kid app on the child's phone, type that code, and grant the
permissions it asks for. A code is good for 30 minutes and works once.

---

## Part 4 — Keeping it running

**When you update the apps,** pull the latest source, rebuild, reinstall — and re-publish
`firebase/firestore.rules` to your project (Part 1, step 6). Rules and apps ship together.

**Your data stays yours.** You're now the operator of your own Firebase project. Nothing leaves
it, there's no server in the middle, and this project's maintainer has no access to it. The
flip side is that nobody else is backing it up either: if you delete the Firebase project, the
history goes with it.

**If you're setting this up for another family,** be honest with them that *you* can see their
child's usage data, because it's in your project. That's exactly the reason this guide exists
instead of a download link.

---

## If something doesn't work

| What you see | What it usually means |
| --- | --- |
| "That code isn't active" on a brand-new code | The rules in your Firebase project are older than the apps. Re-publish `firebase/firestore.rules` (Part 1, step 6). |
| Sign-up fails in the parent app | Email/Password isn't enabled under Authentication (Part 1, step 4). |
| The kid app can't pair at all, no error about the code | Anonymous sign-in isn't enabled (Part 1, step 4). |
| The build fails complaining about `google-services.json` | The file isn't at `cloud/google-services.json`, or the package names in Firebase don't match exactly (Part 1, steps 2-3). |
| A cloud build acts like a local one - no sign-in, no pairing | The Build Variants panel is still set to a `local` variant (Part 2, step 3). |
| Tracking stops after the screen is off a while | A phone-maker battery setting is killing the accessibility service. See the battery section of [INSTALL_ANDROID.md](INSTALL_ANDROID.md), and tell us which phone in an issue — that list is built from reports. |

Still stuck? Open an issue with the step number and the exact error text. Please don't include a
real child's name or usage data.
