### OpenScreenTime Kid

* Package: `org.openscreentime.kid`
* Licence: MIT
* Source: https://github.com/jsconu/OpenScreenTime
* Issues: https://github.com/jsconu/OpenScreenTime/issues
* Latest release: https://github.com/jsconu/OpenScreenTime/releases/tag/v0.2.0-beta (versionCode 2)

I am the author of this app.

### What it is

The child's half: it counts how the phone is used and keeps to the limits a parent has set - a daily limit, per-app limits, a bedtime window and a lock - all stored on the phone itself, behind a family passcode.

OpenScreenTime is a screen-time tool for families, written deliberately as the opposite of the apps in
that category: no account, no subscription, no adverts, no analytics, no engagement loops, and no
server.

This is one of two apps built from the same repository — `org.openscreentime.parent` and
`org.openscreentime.kid` — which are installed on different phones. The parent app is being requested separately. This app works on its own: limits can be set on the phone itself behind the passcode, with no second phone involved.

### Why it should qualify

* **MIT licensed**, full source public, tagged `v0.2.0-beta`.
* **No proprietary libraries in the flavour to be built.** See the note below, which is the one
  thing that needs a decision from a reviewer.
* **No tracking, ads, analytics or crash reporting** of any kind.
* **No network service to depend on.** The published flavour contains no cloud code at all. Two
  phones can sync directly over the user's own Wi-Fi, encrypted, with nothing in between.
* Builds with Gradle, AGP, Kotlin, AndroidX and Compose. No bundled binaries — the repository
  deliberately checks in no `gradle-wrapper.jar`.

### The thing that needs a decision

The repository has two product flavours:

* **`local`** — what I am asking to have packaged. Contains no Firebase or Play Services code.
* **`cloud`** — for people who want a parent to see a child's phone from anywhere. It needs a
  Firebase project that *they* run themselves, and it is never distributed as a binary by me,
  precisely because whoever owns that project could read every family's data in it.

`gradle/libs.versions.toml` and `cloud/build.gradle.kts` therefore declare Firebase and Play
Services coordinates, and the scanner will find them even though the `local` flavour resolves none
of them. The artefact really is clean:

```
unzip -p parent-local-release.apk 'classes*.dex' | grep -ac 'com/google/firebase'
0
```

Build recipes with `gradle: [local]` and `scanignore` limited to those two files are ready here, and
I am happy to restructure the repository instead if you would rather not carry scanignore entries:

https://github.com/jsconu/OpenScreenTime/tree/main/fdroid

### Things I would rather declare up front

* **Accessibility service.** Both apps use one to see which app is in the foreground — that is how
  they count time and show a block screen. They never read screen contents.
* **Cloudflare DNS.** An optional website-blocking filter answers the phone's DNS queries locally
  and forwards anything it does not block to `1.1.1.1`. Off unless a parent turns it on; the apps
  work fully without it.
* **Device admin** (kid app only), used solely for optional uninstall protection that a parent turns
  on deliberately.
* **This is a beta.** Feature-complete and in daily use on real phones, but no independent security
  audit and limited coverage across manufacturers.

Fastlane metadata (descriptions, screenshots, changelogs) is in the repository at
`kid/src/main/fastlane/metadata/android/en-US/`.
