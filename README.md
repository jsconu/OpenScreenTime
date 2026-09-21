# OpenScreenTime

> **Beta.** The Android apps are ready to use, but young: no security audit and limited
> device/OEM coverage. The iOS parent app is built but not yet in the App Store, and an iOS kid app
> and smartwatch tracking are open opportunities for the community. Details in the
> [status table](#current-status) below.

OpenScreenTime is a free, open-source, no-nonsense screen time platform for families. The project is designed to work across Android and iOS, starting with a functional Android implementation for parents and kids.

No account fees, no ads, no dark patterns. MIT licensed — fork it, self-host
your own backend, send patches back.

## Current status

| App | Platform | Status | Notes |
| --- | --- | --- | --- |
| **Parent** | Android | ✅ **Ready** | Pairing, limits, bedtime, locking, weekly report, optional tracking, time that doesn't count, help bot. Build it from source; not on Google Play yet. |
| **Kid** | Android | ✅ **Ready** | Calm status icon, block screen, friction pause, enforcement, parent controls. Build it from source; not on Google Play yet. |
| **Parent** | iOS | 🛠 **Built, not yet in the App Store** | Manages Android kids, limits, bedtime and tracking toggles. Build it yourself with Xcode. No weekly report yet. |
| **Kid** | iOS | 🌱 **Future project - community welcome** | Needs Apple's Family Controls entitlement, which this project doesn't have. See [issue #7](https://github.com/jsconu/OpenScreenTime/issues/7). |

**Local or cloud.** Both apps build two ways, and it is the first thing to decide. A **local** build
keeps everything on the phone it is installed on: no account, no pairing, no setup, and no Firebase
code in the APK at all. A **cloud** build adds what needs a second phone - a parent seeing and
changing a child's limits from their own phone, locking it, granting more time - and needs a
Firebase project you run yourself. What each one can and cannot do is set out in
[docs/LOCAL_AND_CLOUD.md](docs/LOCAL_AND_CLOUD.md).

"Ready" means feature-complete and used day to day on real Android devices - it is still a beta: no
independent security audit and limited phone-maker coverage (see [Project status](#project-status)).
A child's phone must be Android for now. Smartwatch tracking is another open community item (see
[What's missing](#whats-missing-and-could-use-a-contributor)).

**iOS note:** the iOS app is parent-only. There is no OpenScreenTime Kid for iPhone yet — Apple hasn't granted this project the Family Controls entitlement a kid-side app on iOS requires. A child must be paired using OpenScreenTime Kid on an Android phone; the iOS app can then manage that same child (and share access with another parent on Android or iOS), but can't pair or self-track on an iPhone itself. See [`ios/README.md`](ios/README.md) for details.

**Why OpenScreenTime?**

Most screen-time tools are built around subscriptions, engagement, and increasingly complex feature sets. OpenScreenTime takes a different approach: give parents a small set of useful controls, make the software transparent, and get out of the way.

The goal isn't to maximize time spent in an app. It's to help families use technology intentionally.

OpenScreenTime is MIT licensed so families and developers can inspect it, modify it, self-host it, and contribute improvements.

## Screens

[`design/screens.html`](design/screens.html) is a from-source reproduction of
every screen in both apps — real copy, real component states, pulled
directly from the Compose/SwiftUI source, not mockup placeholder text.
Open it in a browser to see the whole UI, including the "calm by design"
callouts explaining the reasoning behind specific choices. It's a faithful
rebuild, not photographs of a real device — if you're running this on your
own phone, real screenshots to replace it are a very welcome contribution.

| Kid: calm status | Kid: block screen | Kid: friction pause |
| :---: | :---: | :---: |
| <img src="design/screenshots/kid-02-status.png" width="220" alt="Kid status screen"> | <img src="design/screenshots/kid-06-block-overlay.png" width="220" alt="Kid block screen"> | <img src="design/screenshots/kid-09-pause-overlay.png" width="220" alt="Kid friction pause"> |

| Parent: dashboard | Parent: child detail | Parent: weekly report |
| :---: | :---: | :---: |
| <img src="design/screenshots/parent-04-dashboard.png" width="220" alt="Parent dashboard"> | <img src="design/screenshots/parent-07-child-detail.png" width="220" alt="Parent child detail"> | <img src="design/screenshots/parent-11-weekly-report.png" width="220" alt="Parent weekly report"> |

| Parent: optional tracking | Parent: unlocks and notifications | Help bot |
| :---: | :---: | :---: |
| <img src="design/screenshots/parent-10-optional-tracking.png" width="220" alt="Parent optional tracking toggles"> | <img src="design/screenshots/parent-12-weekly-report-unlocks-notifications.png" width="220" alt="Weekly report unlocks and notifications"> | <img src="design/screenshots/parent-18-help-parent.png" width="220" alt="Help bot"> |

| Kid: settings | Parent: bedtime (step 1) | Parent: forgot passcode |
| :---: | :---: | :---: |
| <img src="design/screenshots/kid-03-settings-kid.png" width="220" alt="Kid settings page"> | <img src="design/screenshots/parent-08-bedtime-starts.png" width="220" alt="Two-step bedtime editor with AM/PM"> | <img src="design/screenshots/parent-03-forgot-passcode.png" width="220" alt="Forgot passcode recovery"> |

All 30 screens are in [`design/screenshots/`](design/screenshots/), rendered from
the gallery above. The gallery was last updated before dumb phone mode, "Time that doesn't count" and the
hide-notifications controls; screens for those (or real screenshots of any screen) are a welcome contribution.

## How it works

- **`kid`** — installed on the child's phone. Runs quietly in the background,
  measures screen time and unlocks, enforces the limits a parent has set, and
  syncs a daily summary to the cloud.
- **`parent`** — installed on the parent's phone. Shows live stats for each
  paired child, edits limits, and can opt into tracking the parent's *own*
  screen time the same way.
- **`shared`** — the data models, Firestore access code, and enforcement
  logic both apps use: the foreground guard, the day's usage ledger, dumb phone
  mode, and the rule for what a phone may upload.
- **`shared-ui`** — the Compose screens and pieces both apps share (dialogs,
  the dumb-phone home screen, the app picker).
- New to the code? [`CONTEXT.md`](CONTEXT.md) explains the domain terms and
  [`CONTRIBUTING.md`](CONTRIBUTING.md) has a short code map.
- **`ios`** — an iOS build of the parent app (Swift/SwiftUI), for a parent on
  an iPhone pairing with an Android kid device. Built, but not yet in the App Store. See
  [`ios/README.md`](ios/README.md) for setup and current scope; there is no iOS kid app yet (see
  [issue #7](https://github.com/jsconu/OpenScreenTime/issues/7) and
  [What's missing](#whats-missing-and-could-use-a-contributor) below).

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

### What a parent can do

- Set a **daily time limit** and, per app, a **per-app time limit**.
- Set a **bedtime window** — a full block independent of the minute-count
  limit, with its own "wind down" copy on the block screen rather than an
  alternative-activity suggestion.
- Mark specific apps **"always allowed"** so they stay usable (a phone/calling
  app, maps) even once the daily limit or bedtime hits.
- Leave chosen apps out of the overall daily limit (**"Time that doesn't
  count"**): an audiobook, reading, maps or school app then doesn't use up the
  day. Their time still shows in the app list and their own limit still
  applies; pair it with "always allowed" to keep one usable after the daily
  limit is reached.
- Turn on **Dumb phone** for a child, or for your own phone: calls, texts,
  contacts, two-step sign-in apps and a few apps you choose stay on a plain
  home screen and everything else is sent back to it. A child needs the family
  passcode to open more ("Parent unlock"); an adult gets an **All apps** button
  (after a short pause, for 10 minutes) and a **Travel** profile that also lets
  through tickets, maps, mail, the camera and similar. On your own phone,
  **Hide other notifications** collects everything but calls, texts, alarms and
  sign-in codes into the calm list.
- Mark specific **phone numbers as always-allowed during bedtime** — every
  other call is screened and blocked (Android's call-screening/redirection
  roles, Android 10+), and texts from anyone else are muted rather than
  alerting, so the phone is never fully unreachable overnight but also isn't
  a distraction magnet.
- **Lock now / End screen time now** at any point, for any reason, undone
  with "Resume."
- Turn on **uninstall protection** (Android device-admin) so removing the
  kid app shows a warning first — a deterrent, not a hard block.
- Block specific **websites/domains** device-wide, in any browser, via a
  local DNS sinkhole.
- Approve or decline a kid's own **suggested limit change**, and a kid's
  **request for more time** when blocked (including during bedtime) — both
  need an actual parent tap, never self-granted.
- View a **rolling 4-week report** (total screen time and a by-app trend) —
  reached with one deliberate tap, not shown on the main dashboard, and
  capped to 4 weeks on purpose so it stays a tool for noticing a trend, not
  an archive to pore over. Opening it repeatedly in one day triggers its own
  "check in, not check up" prompt, the same friction-pause idea used on the
  kid side, turned back on the parent.
- Optional **website tracking**, per phone (a kid's, or your own): which sites a browser looked up, site names
  only - never pages, searches or time - using the same on-device website filter that does the blocking. Off by
  default; see [docs/INSTALL_ANDROID.md](docs/INSTALL_ANDROID.md#website-tracking-optional-what-has-to-happen)
  for what has to be turned on.
- Turn on optional **unlock tracking** (which app was opened first after each
  unlock) and **notification-count tracking** (overall and by app - counts
  only, never content), each per kid and for the parent's own device. Off by
  default (the plain daily unlock count is always kept for the unlock goal; a
  category adds more detail), and each one adds to
  the weekly report - including a 4-week trend per app for which apps are
  opened first after unlocking and which send the most notifications - plus a
  day-by-day digest. A parent can also choose to
  show today's count on the kid's phone, with a note that watching counts
  can make phone use feel more compulsive.
- Ask the built-in **help bot** (the "?" on the dashboard) how to use the app,
  why it's designed the way it is, or what public guidance says about
  reasonable screen-time limits for a given age - answers cite their sources
  (WHO, the Canadian 24-Hour Movement Guidelines, the AAP, the UK Chief
  Medical Officers). It's a small offline retriever over a curated knowledge
  base, not an AI model: nothing you type leaves the phone, and it says so
  plainly when it has no answer.
- Opt the **parent's own device** into the exact same tracking/limits a kid
  gets — reusing the same dashboard card, same stats, same lock button.

### What a kid sees and can do

- A **calm status icon** (comfortably under / approaching / at-or-over
  today's goal) instead of a running number — explained right there in the
  app, along with *why* only this signal is shown.
- A **non-punitive streak** that only ever counts up; there's no "you missed
  a day" state anywhere.
- A **friction pause** (a few quiet seconds, once per app per day) crossing
  halfway into a per-app limit — not a block, a beat to notice the automatic
  reach for the app.
- A block screen with a **concrete alternative activity** suggestion (except
  at bedtime) and a **request more time** option that a parent has to
  approve.
- **Suggest a change** to their own limit, no passcode needed — sent to a
  parent for approval, not applied directly.
- If a parent turned on **Dumb phone**, a plain home screen (calls, texts and
  chosen apps) when the phone's home app is set to OpenScreenTime, with a
  "Parent unlock" button that asks for the family passcode.
- An optional, **local-only notification digest** (plain text, grouped by
  app) — nothing here is ever synced to a parent.
- The same **help bot** behind a "?" on their home screen, for how the app
  works and why.
- A passcode-gated **Parent controls** screen, for a parent to edit limits
  directly on the kid's device without needing their own phone in hand.
- **Never** the weekly report, exact usage numbers, or a by-app breakdown —
  that detail stays with the parent by design, meant to start a
  conversation rather than run silent surveillance.

### Pairing

1. In the parent app, tap "Add kid" and name the child. This shows a 6-digit
   pairing code (copied for you; it works for 30 minutes) and creates a child
   profile in Firestore. If a code runs out, tap **New code** on that child's
   card for a fresh one.
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

The block screen (bedtime, a limit reached, or a lock) also has a **Parent unlock** button on the kid's phone, and
**Unlock with passcode** on your own phone: enter the family passcode and choose how long, and the phone opens through
bedtime and the limits for that long, then goes back on its own. A lock is lifted the same way.

From the same passcode-gated "Parent controls" screen, a parent can also turn
on "Uninstall protection" (Android's device-admin API) so that removing the
kid app first shows a warning to ask a parent. This is a deterrent, not a
hard block - Android doesn't let a plain (non-Device-Owner) device admin
actually veto uninstalling, only warn - so a kid who taps through the
warning can still remove it.

The same screen also offers "Time that doesn't count" and "Dumb phone" for
that child, the same as on the parent's own phone.

The same screen also has "Bedtime calls": during the bedtime window, only
phone numbers a parent has explicitly allowed (Android's call-screening and
call-redirection roles, Android 10+) can call or text through - everyone
else is blocked until bedtime ends, so the phone can still reach a parent
overnight. Texts are muted rather than fully blocked (opening the messaging
app directly can still show one) - true SMS blocking would require becoming
the phone's default messaging app, a much larger undertaking left out of
scope for now.

### What "screen time" means here

- **Screen time** = time spent unlocked and interactive, measured from
  `ACTION_USER_PRESENT` (unlock) to the next `ACTION_SCREEN_OFF`. Time spent
  sitting on the lock screen doesn't count.
- **Unlocks** = number of `ACTION_USER_PRESENT` broadcasts per day.
- The daily total the limit uses is screen time **less** any time in apps a
  parent chose to leave out ("Time that doesn't count"); that counted figure is
  what syncs to the parent, so the number a parent sees matches the limit.
- **Per-app time** = attributed via an `AccessibilityService` watching
  foreground window changes — this is also how app and daily limits are
  enforced (a full-screen block is shown once a limit is hit).

Everything resets at local midnight on the kid's device.

## Project status

The Android apps (kid and parent) are ready to use - see the [status table](#current-status)
above - and have been through
several rounds of real-device installation, pairing, and day-to-day use,
with bugs found that way fixed as they came up (see the closed issues for
specifics). That's still one household's worth of devices, not broad OEM
coverage — Samsung/Xiaomi/etc.-specific background-execution behavior in
particular is still an open question (see
[issue #5](https://github.com/jsconu/OpenScreenTime/issues/5)). There's also
been no independent security audit; review `firebase/firestore.rules`
yourself before trusting it with real family data. Treat it as a solid,
actively-used starting point, not a finished, broadly-verified product.

### What's missing (and could use a contributor)

Two things are explicitly out of scope for now, not because they're
undesirable, but because they're each roughly as much work as a whole
additional platform target — real community-contributor territory:

- **An iOS kid app.** A future project and a real opportunity for community
  involvement. Requires Apple's Family Controls entitlement, which
  this project hasn't been granted (see
  [issue #7](https://github.com/jsconu/OpenScreenTime/issues/7) for the
  requirements analysis). Someone with an active Apple Developer account and
  the patience for Apple's entitlement-request process could unblock this.
- **Smartwatch tracking/limits.** Technically feasible only for Wear OS (not
  Tizen, Fitbit, Garmin, or Apple Watch - none of those expose a third-party
  API for this at all), but it means a genuinely separate app: its own
  Wear Compose UI, its own on-watch AccessibilityService-based enforcement
  loop, its own sync layer back to the phone over Google's Wearable Data
  Layer API, and real physical watch hardware to test background-survival
  on, since watch battery/Doze constraints are stricter than a phone's.
  Worth doing if someone's motivated and has the hardware; not something to
  start without knowing that scope going in.

Two more areas are ordinary, approachable feature work rather than a new platform, and are a good way to get
involved:

- **More sophisticated dumb-phone features.** Dumb phone mode (a parent-chosen Focus mode that keeps calls, texts,
  sign-in-code apps and a short list of allowed apps on a plain home screen) works today, and there is a lot of room
  to make it smarter. This is a good place to get involved: it is Android and Kotlin, it builds on well-tested
  pieces (`FocusMode`, the shared launcher screen, the foreground guard), and the design goal is clear: calm, plain,
  and never a hook. Ideas, roughly from small to large:
  - **Show what's blocked.** In the "All apps" list, grey out and label apps that are blocked right now (limit
    reached, bedtime, lock, or not allowed by dumb phone) and say why when tapped, instead of opening them. (Android
    doesn't let one app grey out icons on someone else's launcher, so this only applies to our own home screen.)
  - **Schedules.** Turn dumb phone on by itself for school hours, evenings or weekends, for a child or an adult.
  - **Richer travel and situation profiles.** More than Everyday and Travel: a per-trip profile with an end date,
    "work", "school", "weekend", each with its own allowed apps.
  - **Per-contact rules.** Allow calls and texts only from chosen contacts during dumb phone, or hold back the rest.
  - **Smarter notifications.** Allow chosen apps through "Hide other notifications", a digest at set times, and
    quiet-hours rules.
  - **A nicer home screen.** Widgets, an e-ink friendly high-contrast layout, gesture shortcuts, and larger text.
  - **A dumb phone on iPhone and Wear OS.** iOS has no equivalent of a replacement home screen for apps, so this would
    lean on Focus filters and Shortcuts; the parent iOS app can't set any of this yet either.
  - **Measuring it.** Anonymous, local-only feedback for a person on whether it helped, without turning it into a
    score or a streak to chase.

  Please open an issue first to agree the approach. Keep to the project's design principle (calm, opt-in, explained
  to the person it affects), and see `CONTEXT.md` for the domain terms and the code map in `CONTRIBUTING.md`.
- **A more sophisticated calm-notifications interface.** The calm list today is a plain, read-only list of the day's
  notifications, and "Hide other notifications" (parent app) takes everything but calls, texts, alarms and sign-in
  codes out of the shade and into it. It works, and there is a lot of room to make it more considered and easier to
  live with. Ideas:
  - **A proper inbox.** Group by app and by conversation, mark things read, dismiss or snooze, search, and open the
    original app from an item.
  - **Digests at set times.** Deliver the calm list as a summary a few times a day instead of one always-on line.
  - **Fine-grained rules.** Let chosen apps or people through, set quiet hours, and treat urgent things (a school
    message, a family group) differently from the rest.
  - **A central, gesture-friendly entry.** A richer Quick Settings tile, a shade-style pull-down on the dumb-phone home
    screen, and a lock-screen view, in the spirit of minimal launchers.
  - **The kid side.** Today the kid app's calm list is separate and opt-in; a considered, child-friendly version is open.
  - **Accessibility and readability.** Large text, screen-reader order, and a low-stimulation theme.

  It must stay local (notification content is never uploaded), opt-in, and explained plainly. Open an issue first.

Smaller, more approachable gaps: weekly-report iOS parity (see
[`ios/README.md`](ios/README.md)), broader OEM background-survival testing
(#5), and a from-scratch security review of the Firestore rules (#2 covered
an initial pass, not a full audit).

## Building

> **Want a parent's phone and a child's phone linked?** That is the **cloud** build, and
> [**docs/SELF_HOSTING.md**](docs/SELF_HOSTING.md) is the whole process written out step by step
> for someone who isn't a developer — about 45 minutes, start to finish, no cost. The summary
> below assumes you already know your way around Android Studio.

You'll need [Android Studio](https://developer.android.com/studio)
(Ladybug or newer) with a JDK 17 and Android SDK 35.

> **For one phone, with nothing to set up,** build the **local** flavor:
> `gradle :parent:assembleLocalRelease :kid:assembleLocalRelease`, or choose `localRelease` in
> Android Studio's Build Variants panel. No Firebase project, no account, no configuration, and
> no cloud code in the APK. Skip the Firebase steps below entirely - they are only for the
> **cloud** flavor, which is what the rest of this section covers. See
> [docs/LOCAL_AND_CLOUD.md](docs/LOCAL_AND_CLOUD.md) for the difference.

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
   `firebase.json` pointing at that file. **Re-publish them whenever
   `firebase/firestore.rules` changes in this repo** - the apps and the rules
   are one design, and a build that writes something the deployed rules don't
   know about yet is refused (pairing is the first place you'd notice).
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
A few more are optional, granted separately, and not part of that core
checklist: a **calm notification list** (local-only digest), **notification
access** (also powers muting non-allowed texts during bedtime), and, from
the passcode-gated Parent controls screen, **uninstall protection** and
**bedtime call blocking** (Android 10+ only for the latter).

**Hide other notifications** (on a parent's own phone) collects everything but calls, texts, alarms and sign-in
codes into the calm list, reachable from one summary notification or a Quick Settings tile. See
[`docs/INSTALL_ANDROID.md`](docs/INSTALL_ANDROID.md).

> **Dumb phone is not in this release.** A plain home screen keeping only calls, texts, sign-in codes and a few
> allowed apps is built - `FocusMode`, `FocusHome`, `FocusDevice` and the rest are all in the tree - but it isn't
> good enough to put in front of a family yet, so it's switched off in one place (`Features.DUMB_PHONE`) rather
> than deleted. Replacing a phone's home screen touches the launcher role, the foreground guard and the app list
> at once, and getting it wrong leaves someone holding a phone that won't open anything. **It would make an
> excellent community feature**, and whoever takes it on starts from working code rather than a blank page - see
> [issue #46](https://github.com/jsconu/OpenScreenTime/issues/46).

A parent can also leave chosen apps out of the overall daily limit (**Time that doesn't count**, on a person's page): an
audiobook, reading, maps or school app then doesn't use up the day. Their time still shows in the app list and their own
limit still applies; pair it with **Always allow** to keep one usable after the daily limit is reached.

## Official builds and forks

OpenScreenTime is free and open source under the [MIT license](LICENSE), so anyone may use, modify,
fork and even sell it. That's intentional. But because this is an app that can see a child's phone
use, it matters who you trust to run it, so here is how to tell the official project apart:

- **The only official source is this repository:**
  <https://github.com/jsconu/OpenScreenTime>. Any official release or store listing will be linked
  from here. If you found the app somewhere that this page doesn't link to, it isn't an official
  build.
- **Released APKs are always local builds, and only local builds.** A local build contains no
  cloud code at all, so it has nowhere to send anything and nobody - including this project - can
  see what it records. A cloud build is permanently wired at build time to one Firebase project,
  and whoever owns that project can read the data of every family using that build, so publishing
  one would quietly make this project's maintainer the operator of your child's usage data. That
  is why cloud builds are never handed out: you build one yourself, against a Firebase project of
  your own ([docs/SELF_HOSTING.md](docs/SELF_HOSTING.md)), and nobody but you holds the data. See
  [docs/LOCAL_AND_CLOUD.md](docs/LOCAL_AND_CLOUD.md) for what each build can do.
- **This project has no ads, no in-app purchases and no paid tier.** A local build has no analytics or
  crash reporting either - it sends nothing anywhere, and its Feedback menu opens
  [the issue tracker](https://github.com/jsconu/OpenScreenTime/issues) rather than a box that goes
  nowhere. If an app using this code asks you to pay or shows ads, it's someone else's fork.
- **Check where the data goes.** Every build talks to a Firebase project run by whoever published it.
  In the official project that's described in [docs/PRIVACY.md](docs/PRIVACY.md). A fork may send data
  somewhere else, so read its privacy policy before pairing a child's phone.
- **Please don't use the name "OpenScreenTime" for a modified version.** The MIT license doesn't
  give anyone the right to the project's name or branding. Forks are welcome; call yours something
  else and say it's based on OpenScreenTime, so families aren't confused about who's behind it.
- The design principles here (calm signals for kids, parent-only detailed reports, tracking that's
  opt-in and clearly explained) aren't enforceable by a license. If a fork drops them, that's the
  fork's choice, not this project's.

## Installing the Android apps

New to installing an app that isn't from the Play Store? [docs/INSTALL_ANDROID.md](docs/INSTALL_ANDROID.md) is
a plain-language, step-by-step guide: downloading the two APKs, the "install unknown apps" and Play Protect
prompts, pairing, each permission, Android's "Allow restricted settings" step for Accessibility, phone-maker
battery settings, and what to do if a blocked website still opens.

## Publishing your own build

Building a signed release, and putting it on a private Google Play internal-testing track, is
covered step by step in [docs/PUBLISHING.md](docs/PUBLISHING.md). Draft answers for Play Console's
forms are in [docs/PLAY_CONSOLE_ANSWERS.md](docs/PLAY_CONSOLE_ANSWERS.md), and a privacy policy
template to fill in and host is in [docs/PRIVACY.md](docs/PRIVACY.md). Whoever publishes a build
is the operator of the Firebase project it talks to, and responsible for the data in it.

## Security & privacy notes for anyone deploying this

- The kid app never reads screen *content* — the accessibility service only
  observes which app's window is in front, nothing more
  (`canRetrieveWindowContent="false"`).
- Firestore security rules (`firebase/firestore.rules`) keep one family's
  data from another's: a kid device can only touch the one child record it
  claimed via a live, unexpired pairing code, and what it may write there
  is limited to a fixed set of fields with bounded types and sizes. Please
  review them yourself before relying on this for anything sensitive; this
  is a community project, not an audited product.
- **The passcode is a deterrent, not a lock.** Because there is no server,
  the family passcode is checked on the device, and the paired kid device
  holds its own Firebase credentials and can read the passcode hash (a
  short numeric passcode can be guessed offline) and write the limit fields.
  Someone with the skill and physical access to that phone could change
  their own limits without it. "Requests are never self-granted" and "uninstall
  needs approval" are likewise app behavior, not something the rules can
  enforce. This is fine for its purpose - a conversation-starting friction
  for a curious kid - but don't rely on it against a determined teenager.
  Closing it fully would need a small server that verifies the passcode
  with a lockout, which this project deliberately doesn't have. On the
  kid device, repeated wrong passcodes lock the screen for 15 minutes.
- If you track your own device, its per-app numbers live in the same Firebase
  project, where devices linked to your family can technically read them;
  the kid app only ever shows a calm status from them.
- Bedtime call blocking blocks calls from numbers that aren't on your list,
  but Android may not consult the app for numbers saved in the phone's
  contacts, and texts can only be quieted (notification muted), not blocked -
  test both on your own devices.
- This kind of app is inherently powerful (it can see app usage and block
  apps). Use it thoughtfully and talk to your kid about it — it's meant to
  support a conversation about healthy screen time, not to be sprung on
  someone unannounced. The detailed weekly report and by-app breakdown are
  deliberately parent-only and never reach the kid app, for the same reason.
- The optional bedtime-call-blocking and notification-access permissions are
  powerful too (they can screen calls and dismiss notifications) - both are
  opt-in, separate from the core tracking checklist, and only take effect
  during the configured bedtime window.
- All three apps report crashes to Firebase Crashlytics (same Firebase
  project as everything else): stack traces plus device model/OS/app
  version, never any family data. CI/emulator builds never report.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Issues and PRs welcome.

## License

MIT — see [LICENSE](LICENSE). Free forever, use it however you like.
