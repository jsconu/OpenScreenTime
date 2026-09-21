# Contributing to OpenScreenTime

Thanks for considering it — this project only stays free and good if people
chip in over time. A few ground rules to keep it easy to maintain:

## Philosophy

The whole point of this app is to be simpler than the alternatives. Before
adding a feature, ask whether it earns its place on the kid's status screen
or the parent's dashboard. Prefer removing a setting to adding one. If
something needs a paragraph of explanation to use, it's probably too
complicated.

## Getting set up

Follow the [Building](README.md#building) section of the README — you'll
need your own free Firebase project to run and test against, since there's
no shared backend.

## A short code map

Domain terms are in [`CONTEXT.md`](CONTEXT.md). The pieces you will meet most:

- **`shared/model`** - plain Kotlin data and rules with unit tests (`decideEnforcement`, `FocusConfig`,
  `shouldHideNotification`, `AppList`). Prefer putting a new rule here so it can be tested without a phone.
- **`shared/util`** - the parts that touch Android but are shared: `BaseAppLimitAccessibilityService` (the foreground
  guard both apps run), `BaseScreenMonitorService`, `DayLedger` (today's usage, over a key-value seam so it is
  testable), `DeviceProfileState` (a phone's saved copy of its person's profile), `FocusMode` (dumb phone) and
  `buildDailyStats` (the one place that decides what a phone may upload).
- **`shared-ui`** - Compose screens and pieces both apps use, including the dumb-phone home screen and the app picker.
- **`kid`, `parent`** - thin adapters: each names its own storage, notifications and overlays and leaves the rest to `shared`.
- **`firebase/firestore.rules`** - what each device may read and write. If you add a field a paired kid device
  must write, it also has to be added to the allowlist there (and the rules republished).

## Local and cloud builds

Both apps build two ways, and a change usually has to work in both. A **local** build keeps
everything on one phone and contains no Firebase code at all; a **cloud** build pairs two phones
through Firestore. `FamilyRepository` in `:shared` is the interface both sides implement, and each
app's per-flavor `Backend` object is the only place that knows which one it is in. If a screen only
makes sense with a second phone, check `Backend.IS_LOCAL` and leave it out rather than letting it
fail. See [docs/LOCAL_AND_CLOUD.md](docs/LOCAL_AND_CLOUD.md) - which also sets out the most
valuable thing anyone could build here: a cloud that nobody has to trust.

## Making a change

1. Open an issue first for anything non-trivial (new features, permission
   changes, data model changes), so we can agree on the approach before you
   put time into it. Small fixes and bug reports can just be a PR.
2. Keep PRs focused — one change per PR is much easier to review than a
   grab-bag.
3. If you touch `shared/`, remember both apps depend on it — check both
   still build.
4. If you change `firebase/firestore.rules`, explain *why* in the PR
   description; these rules are the only thing standing between one
   family's data and another's, so they get extra scrutiny.
5. Match the existing code style (plain Kotlin + Jetpack Compose, no extra
   architecture layers beyond what's already there).

## Bigger things the community could tackle

Where things stand: the Android parent and kid apps are ready, the iOS parent app is built but not
yet in the App Store, and the iOS kid app is a future project - see the
[status table](README.md#current-status).

Two gaps are explicitly not on the maintainer's near-term roadmap, not
because they're unwanted, but because each is closer in scope to standing
up a whole additional platform target than to a normal feature PR. If
either interests you and you have what it takes to start, please open an
issue first so the approach can be agreed on before you sink real time in.

- **An iOS kid app** - a future project and a great way to get involved (see [issue #7](https://github.com/jsconu/OpenScreenTime/issues/7)).
  Blocked on Apple's Family Controls entitlement, which this project hasn't
  been granted - you'd need an active Apple Developer account and to go
  through Apple's entitlement-request process yourself. Once granted, the
  actual screen-time enforcement (via Apple's Screen Time / Family Controls
  APIs) is a genuinely different mechanism than the Android kid app's
  AccessibilityService approach, so this is closer to a fresh design than a
  port of existing Kotlin code.
- **More sophisticated dumb-phone features** - unlike the two above this is a normal, approachable Android and
  Kotlin area, and a good first big contribution. Ideas: grey out and explain blocked apps in the "All apps" list,
  schedules (dumb phone on for school hours), per-trip and other situation profiles, per-contact call and text rules,
  smarter notification handling, a nicer or e-ink friendly home screen, and an approach for iPhone. See the list in the
  README's [What's missing](README.md#whats-missing-and-could-use-a-contributor). Please open an issue first, and keep to
  the calm, opt-in, explained-to-the-person design principle.
- **A more sophisticated calm-notifications interface** - the calm list is a plain read-only list today. A proper inbox
  (group by app or conversation, snooze, search), digests at set times, fine-grained rules for who or what gets
  through, and a richer central entry point (Quick Settings tile, pull-down, lock-screen view) are all open. It has to
  stay local (content is never uploaded), opt-in and plainly explained. See the README's
  [What's missing](README.md#whats-missing-and-could-use-a-contributor).
- **Smartwatch tracking and limits.** Only Wear OS has any viable path at
  all - Tizen, Fitbit, Garmin, and Apple Watch expose no third-party API for
  this. Even Wear OS means a genuinely separate app: its own Wear Compose
  UI (round-screen layouts, rotary/bezel input), its own on-watch
  AccessibilityService-based enforcement loop, and its own sync layer back
  to the phone over Google's Wearable Data Layer API. Watch battery/Doze
  constraints are stricter than a phone's, so reliable background survival
  is a harder version of a problem this project has already had to solve
  once for phones (see #5). Real physical watch hardware is close to
  required for meaningful testing.

Smaller, more approachable gaps if you want something scoped down: porting
the Android parent app's weekly report to iOS (see `ios/README.md`),
broader OEM background-survival testing (#5), and a from-scratch security
review of `firebase/firestore.rules` beyond the initial pass in #2.

## Reporting bugs

Feedback from a released (local) build comes here, to the issue tracker: those builds have no server to
send anything to, and the in-app Feedback menu simply opens this repository's issues.


Please include: your Android version, which app (kid or parent), and steps
to reproduce. If it's a tracking/limit-enforcement bug, note the device
manufacturer — background execution and accessibility service behavior
varies a lot between OEMs (Samsung, Xiaomi, etc. are known to be aggressive
about killing background services; see if it happens on stock Android too).

## Code of conduct

Be respectful. This is a project built by parents, for parents — assume good
faith, and keep discussion focused on the software. The full text is in
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md); the short version is that you should
disagree about the software rather than about how someone raises their child,
and never post a real child's details in an issue.

## Security

Please report anything exploitable privately rather than in a public issue —
see [SECURITY.md](SECURITY.md), which also lists the trade-offs that are already
known and documented, so you don't spend an evening on one of them.
