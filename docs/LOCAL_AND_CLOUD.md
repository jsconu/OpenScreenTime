# Two builds: local and cloud

OpenScreenTime builds in two flavors. A build is one or the other, never both, and the difference
is not a setting you can toggle at runtime — it is what code is in the APK at all.

| | **local** | **cloud** |
| --- | --- | --- |
| Where your family's data lives | This phone, and nowhere else | A Firebase project someone runs |
| Account | None | A parent signs in with email and password |
| Pairing a second phone | No | Yes |
| Firebase code in the APK | **None at all** | Yes |
| Needs setting anything up | No | A Firebase project, about 45 minutes |
| Who else can see the data | Nobody | Whoever owns that Firebase project |

The local flavor is what gets published, because it is the only one that can be handed to a
stranger honestly: there is no server in it, so there is nobody to trust.

**That is why local APKs are safe to download and install.** A cloud build is permanently tied to
one Firebase project - the project id and API key are compiled into it - so whoever owns that
project can read the data of every family using that build. A published cloud APK would quietly
make this project's maintainer the operator of your child's usage data. A local build has nowhere
to send anything, so that whole problem does not exist: the download is just an app, and what it
records stays on the phone it records it on. Releases therefore ship local APKs, and anyone who
wants the remote features builds the cloud flavor against a Firebase project of their own.

## What a local build does

Everything one phone can do by itself, which is most of what this project is:

- Counts screen time, unlocks, per-app time and, if turned on, notifications and sites
- Enforces the daily limit, per-app limits, bedtime and a lock
- Dumb phone mode, including the Travel profile and the plain home screen
- The calm notification list, and hiding everything else from the shade
- Time that doesn't count, always-allowed apps, blocked sites, allowed contacts at bedtime
- The weekly report and the under-goal streak, from a history kept on the phone
- All of it behind the family passcode, in Parent controls

Limits are set **on the phone they apply to**, in person: open Parent controls, enter the family
passcode, change what you need. For a younger child, and for an adult setting limits for
themselves, that is often the whole job.

## What a local build cannot do

Everything that needs a second phone in the picture:

- A parent seeing a child's usage from their own phone
- Setting or changing a child's limits remotely
- Locking a child's phone from somewhere else
- A child asking for 15 more minutes from school and a parent granting it from work
- A child proposing a limit change for a parent to approve
- Pairing at all — there is no code to type and no account to pair to

**There is no Wi-Fi or Bluetooth link either.** It would be reasonable to assume two phones in the
same house could talk directly, and nothing in a local build does that: no local network sync, no
Bluetooth pairing, no discovery. A local build never opens a connection to anything. Where a screen
would otherwise offer to send something to a parent - the kid app's "Suggest a change", and asking
for more time on the block screen - it says to ask a parent in person instead, because a parent
standing next to the phone can change any of it in Parent controls with the family passcode.

None of these are missing because they were hard. They are missing because two phones that are not
in the same room cannot reach each other without something in the middle, and the local build
deliberately has nothing in the middle. If you need them, build the cloud flavor
([docs/SELF_HOSTING.md](SELF_HOSTING.md)).

## Building each one

```
gradle :parent:assembleLocalRelease :kid:assembleLocalRelease    # no setup needed
gradle :parent:assembleCloudRelease :kid:assembleCloudRelease    # needs cloud/google-services.json
```

In Android Studio, pick the build variant from the Build Variants panel.

The local flavor needs nothing configured. The cloud flavor needs a `google-services.json` in the
`cloud/` module directory — that file is the only reason the cloud build knows where to talk to.

## How it fits together

`FamilyRepository` in `:shared` is an interface: everything the apps ask of whatever is storing a
family's profiles, limits and usage. Two implementations, one per flavor:

- `LocalFamilyRepository` (`:shared`) — this phone's own storage, and nothing else
- `FirebaseFamilyRepository` (`:cloud`) — Firestore, pairing, and the security rules in
  `firebase/firestore.rules`

Each app has one `Backend` object per flavor (`src/local/java/.../Backend.kt` and
`src/cloud/java/.../Backend.kt`). It is the only place in either app that knows which world it is
in — it builds the repository, does any startup wiring, and says which profile this phone keeps. A
screen that shouldn't exist in a local build checks `Backend.IS_LOCAL` and leaves itself out.

The `:cloud` module is a dependency of the cloud flavor only, so a local build genuinely contains
no Firebase code. That is checkable, not a promise:

```
unzip -p kid-local-release.apk 'classes*.dex' | grep -ac 'com/google/firebase'
```

It prints `0` for a local build.

---

## Help wanted: give this project a cloud that nobody has to trust

The cloud flavor works, and it has a real problem: it needs a Firebase project, and whoever owns
that project can read every family's data in it. That is why this project publishes local builds
and asks anyone who wants the remote features to run their own backend — a 45-minute setup that
most parents will never do.

**There is a much better answer, and it would be a genuinely satisfying thing to build.**

### 1. End-to-end encryption over a dumb relay

If the two phones encrypted everything with a key the server never sees, the server would hold
nothing but ciphertext — and then it would not matter whose server it was. One build, published
for everyone, no setup, no operator, and the remote features back.

The pieces:

- A key agreed at pairing. The pairing step is already a private channel between two phones that
  are in the same room; a QR code shown by the parent's app and scanned by the child's could carry
  a key as easily as it carries a six-digit code.
- Encrypt the profile and the daily stats client-side. Both are small and well-defined
  (`ChildProfile`, `DailyStats`), so this is a contained change at the repository seam rather than
  a rewrite.
- Security rules that enforce ownership and shape but no longer read contents, because they
  can't. The threat model in `firebase/firestore.rules` would need rewriting honestly.
- Answers for the hard parts: what happens when a passcode is forgotten and the key is gone with
  it, and how a family adds a second parent's phone later.

### 2. Something other than Firebase underneath

Firebase was chosen because it gave this project live sync, auth and offline caching for free. It
also rules out F-Droid, drags in Google Play Services, and makes "just run your own" mean "create a
Google account." A self-hostable backend — or a peer-to-peer design that only falls back to a relay
when both phones can't reach each other — would open doors this project currently has closed.

Be warned that the obvious shapes have sharp edges. Same-network sync fails exactly when a parent
needs it, because the phones are in different places. Peer-to-peer over the internet needs NAT
traversal, so STUN and TURN servers, which is infrastructure again. And Android's Doze will kill
any long-lived connection, so waking the other phone means a push channel — Google's, or a
self-hosted UnifiedPush one. A design that has thought about those three problems is worth much
more than one that hasn't.

### If you want to take this on

Open an issue first and say which approach you want to try — this is the kind of change worth
agreeing on before anyone writes code. `CONTEXT.md` has the domain language, `CONTRIBUTING.md` has
the code map, and the seam you would be working at is `FamilyRepository` in `:shared`.

This is the most valuable thing anyone could contribute to OpenScreenTime right now. The local
build makes the project honest; a cloud that nobody has to trust would make it useful to families
who will never open Android Studio.
